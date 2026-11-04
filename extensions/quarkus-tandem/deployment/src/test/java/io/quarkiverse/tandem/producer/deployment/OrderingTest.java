package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.codingful.tandem.core.BucketHash;
import com.codingful.tandem.core.OutboxMessage;
import com.codingful.tandem.core.OutboxRecord;
import com.codingful.tandem.core.port.OutboxRepository;
import com.codingful.tandem.jdbc.JdbcOutboxStore;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Ordering under concurrency (HLD-quarkus §5.8, §12): publication follows commit order, so two unserialized
 * writers to one aggregate can publish out of business order, and {@code lockedWrite()} serializes them.
 */
class OrderingTest {

    private static final String ORDER_ID = "order-1";
    private static final int BUCKET_COUNT = 256;
    private static final Duration WAIT = Duration.ofSeconds(10);

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.liquibase();

    @Inject
    OutboxRepository outbox;

    @Inject
    DataSource dataSource;

    private ExecutorService writers;
    private CountDownLatch releaseFirstWriter;

    @BeforeEach
    void clean() {
        OutboxRows.clear(dataSource);
        writers = Executors.newFixedThreadPool(2);
        releaseFirstWriter = new CountDownLatch(1);
    }

    @AfterEach
    void stopWriters() {
        releaseFirstWriter.countDown();
        writers.shutdownNow();
    }

    @Test
    void GIVEN_two_unserialized_writers_WHEN_the_first_to_insert_commits_second_THEN_the_relay_publishes_out_of_business_order()
            throws Exception {
        CountDownLatch firstInserted = new CountDownLatch(1);
        Future<?> first = writers.submit(() -> insertAndHold(Orders.placed(ORDER_ID, 1), firstInserted, new AtomicInteger()));
        await(firstInserted);
        writers.submit(() -> QuarkusTransaction.requiringNew().run(() -> outbox.insert(Orders.placed(ORDER_ID, 2))))
                .get(WAIT.toSeconds(), TimeUnit.SECONDS);

        List<Long> published = new ArrayList<>(relayPublishes());
        releaseFirstWriter.countDown();
        first.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        published.addAll(relayPublishes());

        assertThat(published).containsExactly(2L, 1L);
    }

    @Test
    void GIVEN_two_writers_using_the_writer_lock_WHEN_the_second_inserts_while_the_first_is_open_THEN_it_waits_and_order_holds()
            throws Exception {
        CountDownLatch firstInserted = new CountDownLatch(1);
        AtomicInteger firstWriterPid = new AtomicInteger();
        Future<?> first = writers.submit(
                () -> insertAndHold(lockedWrite(1), firstInserted, firstWriterPid));
        await(firstInserted);
        Future<?> second = writers
                .submit(() -> QuarkusTransaction.requiringNew().run(() -> outbox.insert(lockedWrite(2))));
        try {
            awaitAdvisoryLockWaitBlockedBy(firstWriterPid.get());
            assertThat(second).isNotDone();
        } finally {
            releaseFirstWriter.countDown();
        }
        first.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        second.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        assertThat(relayPublishes()).containsExactly(1L, 2L);
    }

    private static OutboxMessage lockedWrite(long seq) {
        return OutboxMessage.builder()
                .aggregateType(Orders.AGGREGATE_TYPE)
                .aggregateId(ORDER_ID)
                .payload(Orders.payloadOf(ORDER_ID))
                .seq(seq)
                .lockedWrite()
                .build();
    }

    private void insertAndHold(OutboxMessage message, CountDownLatch inserted, AtomicInteger backendPid) {
        QuarkusTransaction.requiringNew().run(() -> {
            outbox.insert(message);
            backendPid.set(backendPidOfTransaction());
            inserted.countDown();
            await(releaseFirstWriter);
        });
    }

    // Inside a JTA transaction Agroal hands out the connection enlisted in it: the one the insert ran on.
    private int backendPidOfTransaction() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()");
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // Proves the wait from the database rather than from timing (LLD-quarkus §13.2).
    private void awaitAdvisoryLockWaitBlockedBy(int blockingPid) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM pg_stat_activity"
                            + " WHERE wait_event_type = 'Lock' AND wait_event = 'advisory'"
                            + " AND ? = ANY(pg_blocking_pids(pid))")) {
                statement.setInt(1, blockingPid);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    if (result.getInt(1) == 1) {
                        return;
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No session waited on the first writer's advisory lock blockingPid:" + blockingPid);
    }

    // The relay's own claim, polled until the aggregate has nothing claimable left: it hands out one
    // aggregate's rows head of chain first.
    private List<Long> relayPublishes() {
        JdbcOutboxStore store = new JdbcOutboxStore(dataSource, 3);
        List<Long> published = new ArrayList<>();
        List<OutboxRecord> claimed;
        do {
            claimed = store.claimBatch(Set.of(BucketHash.bucketFor(ORDER_ID, BUCKET_COUNT)), "test-relay",
                    Duration.ofSeconds(30), 10);
            store.markDoneBatch(claimed);
            claimed.forEach(record -> published.add(record.seq()));
        } while (!claimed.isEmpty());
        return published;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT.toSeconds(), TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for a writer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
