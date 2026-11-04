package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.codingful.tandem.core.exception.OutboxInsertException;
import com.codingful.tandem.core.exception.TandemException;
import com.codingful.tandem.core.port.OutboxRepository;

import io.quarkiverse.tandem.producer.runtime.TandemOutboxRepository;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The plain tier (HLD-quarkus §5.3, §12): the application injects {@link OutboxRepository} and inserts in
 * its own {@code @Transactional} method.
 */
class PlainTierTest {

    private static final String ORDER_ID = "order-1";

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.liquibase(Scenarios.class);

    @Inject
    Scenarios scenarios;

    @Inject
    OutboxRepository outbox;

    @Inject
    Orders orders;

    @Inject
    DataSource dataSource;

    @BeforeEach
    void clean() {
        OutboxRows.clear(dataSource);
    }

    @Test
    void GIVEN_an_order_placed_with_its_event_WHEN_the_transaction_commits_THEN_both_are_written() {
        scenarios.placeAndRecord(ORDER_ID);

        assertThat(orders.exists(ORDER_ID)).isTrue();
        List<OutboxRows.Row> rows = OutboxRows.all(dataSource);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.aggregateType()).isEqualTo(Orders.AGGREGATE_TYPE);
            assertThat(row.aggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.seq()).isEqualTo(1);
        });
    }

    @Test
    void GIVEN_an_order_placed_with_its_event_WHEN_the_business_change_fails_THEN_neither_is_written() {
        assertThatThrownBy(() -> scenarios.placeRecordAndFail(ORDER_ID)).isInstanceOf(BusinessFailure.class);

        assertThat(orders.exists(ORDER_ID)).isFalse();
        assertThat(OutboxRows.all(dataSource)).isEmpty();
    }

    @Test
    void GIVEN_an_event_that_collides_WHEN_the_failure_reaches_the_transaction_boundary_THEN_the_order_is_not_committed() {
        assertThatThrownBy(() -> scenarios.placeAndRecordTwice(ORDER_ID, false)).isInstanceOf(TandemException.class);

        assertThat(orders.exists(ORDER_ID)).isFalse();
        assertThat(OutboxRows.all(dataSource)).isEmpty();
    }

    @Test
    void GIVEN_an_event_that_collides_WHEN_the_method_catches_the_failure_THEN_it_returns_and_nothing_commits() {
        scenarios.placeAndRecordTwice(ORDER_ID, true);

        assertThat(orders.exists(ORDER_ID)).isFalse();
        assertThat(OutboxRows.all(dataSource)).isEmpty();
    }

    @Test
    void GIVEN_no_transaction_WHEN_an_event_is_inserted_THEN_it_is_refused_and_nothing_is_written() {
        assertThatThrownBy(() -> outbox.insert(Orders.placed(ORDER_ID, 1)))
                .isInstanceOf(OutboxInsertException.class)
                .hasMessageStartingWith(TandemOutboxRepository.NO_ACTIVE_TRANSACTION);

        assertThat(OutboxRows.all(dataSource)).isEmpty();
    }

    @Test
    void GIVEN_no_transaction_WHEN_an_empty_batch_is_inserted_THEN_it_is_refused_too() {
        assertThatThrownBy(() -> outbox.insertAll(List.of()))
                .isInstanceOf(OutboxInsertException.class)
                .hasMessageStartingWith(TandemOutboxRepository.NO_ACTIVE_TRANSACTION);
    }

    @Test
    void GIVEN_a_transaction_marked_rollback_only_WHEN_an_event_is_inserted_THEN_it_is_refused() {
        assertThatThrownBy(() -> scenarios.markRollbackOnlyThenRecord(ORDER_ID))
                .isInstanceOf(OutboxInsertException.class)
                .hasMessageStartingWith(TandemOutboxRepository.NO_ACTIVE_TRANSACTION);

        assertThat(OutboxRows.all(dataSource)).isEmpty();
    }

    static final class BusinessFailure extends RuntimeException {
        BusinessFailure() {
            super("The business change failed");
        }
    }

    @ApplicationScoped
    public static class Scenarios {

        @Inject
        OutboxRepository outbox;

        @Inject
        Orders orders;

        @Inject
        TransactionManager transactionManager;

        @Transactional
        public void placeAndRecord(String orderId) {
            orders.place(orderId);
            outbox.insert(Orders.placed(orderId, 1));
        }

        @Transactional
        public void placeRecordAndFail(String orderId) {
            placeAndRecord(orderId);
            throw new BusinessFailure();
        }

        @Transactional
        public void placeAndRecordTwice(String orderId, boolean catchTheFailure) {
            placeAndRecord(orderId);
            try {
                outbox.insert(Orders.placed(orderId, 1));
            } catch (TandemException e) {
                if (!catchTheFailure) {
                    throw e;
                }
            }
        }

        @Transactional
        public void markRollbackOnlyThenRecord(String orderId) throws SystemException {
            transactionManager.setRollbackOnly();
            outbox.insert(Orders.placed(orderId, 1));
        }
    }
}
