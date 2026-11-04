package io.quarkiverse.tandem.it;

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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.codingful.tandem.core.BucketHash;
import com.codingful.tandem.core.OutboxMessage;
import com.codingful.tandem.core.OutboxRecord;
import com.codingful.tandem.core.port.OutboxRepository;
import com.codingful.tandem.jdbc.JdbcOutboxStore;

import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * Two writers to one aggregate, with the library's own relay claim between their commits (HLD-quarkus §5.8,
 * LLD-quarkus §13.2). The first writer inserts and holds its transaction open until the scenario releases it.
 */
@ApplicationScoped
public class OrderingScenarios {

    private static final Duration WAIT = Duration.ofSeconds(10);

    /** What the relay published, in order, and what the database showed of the second writer meanwhile. */
    public record Ordering(List<Long> published, boolean secondWaitedOnTheLock, boolean secondDoneWhileFirstOpen) {
    }

    @Inject
    OutboxRepository outbox;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "tandem.outbox.bucket-count", defaultValue = "256")
    int bucketCount;

    public Ordering unserialized(String orderId) throws Exception {
        return run(orderId, false);
    }

    public Ordering lockedWrite(String orderId) throws Exception {
        return run(orderId, true);
    }

    private Ordering run(String orderId, boolean lockedWrite) throws Exception {
        ExecutorService writers = Executors.newFixedThreadPool(2);
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        try {
            Future<?> first = writers.submit(() -> QuarkusTransaction.requiringNew().run(() -> {
                outbox.insert(message(orderId, 1, lockedWrite));
                firstPid.set(backendPid());
                firstInserted.countDown();
                await(releaseFirst);
            }));
            await(firstInserted);
            Future<?> second = writers.submit(
                    () -> QuarkusTransaction.requiringNew().run(() -> outbox.insert(message(orderId, 2, lockedWrite))));
            List<Long> published = new ArrayList<>();
            boolean waited;
            boolean secondDoneWhileFirstOpen;
            try {
                if (lockedWrite) {
                    waited = awaitAdvisoryLockWaitBlockedBy(firstPid.get());
                    secondDoneWhileFirstOpen = second.isDone();
                } else {
                    second.get(WAIT.toSeconds(), TimeUnit.SECONDS);
                    waited = false;
                    secondDoneWhileFirstOpen = true;
                    published.addAll(relayPublishes(orderId));
                }
            } finally {
                releaseFirst.countDown();
            }
            first.get(WAIT.toSeconds(), TimeUnit.SECONDS);
            second.get(WAIT.toSeconds(), TimeUnit.SECONDS);
            published.addAll(relayPublishes(orderId));
            return new Ordering(published, waited, secondDoneWhileFirstOpen);
        } finally {
            releaseFirst.countDown();
            writers.shutdownNow();
        }
    }

    private static OutboxMessage message(String orderId, long seq, boolean lockedWrite) {
        OutboxMessage.Builder builder = Orders.placedBuilder(orderId, seq);
        return lockedWrite ? builder.lockedWrite().build() : builder.build();
    }

    // Inside a JTA transaction Agroal hands out the connection enlisted in it: the one the insert ran on.
    private int backendPid() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()");
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // Proves the wait from the database rather than from timing.
    private boolean awaitAdvisoryLockWaitBlockedBy(int blockingPid) throws InterruptedException, SQLException {
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
                        return true;
                    }
                }
            }
            Thread.sleep(50);
        }
        return false;
    }

    // The relay's own claim, polled until the aggregate has nothing claimable left: it hands out one
    // aggregate's rows head of chain first. Other aggregates sharing the bucket are claimed too, and ignored.
    private List<Long> relayPublishes(String orderId) {
        JdbcOutboxStore store = new JdbcOutboxStore(dataSource, 3);
        List<Long> published = new ArrayList<>();
        List<OutboxRecord> claimed;
        do {
            claimed = store.claimBatch(Set.of(BucketHash.bucketFor(orderId, bucketCount)), "test-relay",
                    Duration.ofSeconds(30), 10);
            store.markDoneBatch(claimed);
            claimed.stream().filter(record -> record.aggregateId().value().equals(orderId))
                    .forEach(record -> published.add(record.seq()));
        } while (!claimed.isEmpty());
        return published;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT.toSeconds(), TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for a writer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
