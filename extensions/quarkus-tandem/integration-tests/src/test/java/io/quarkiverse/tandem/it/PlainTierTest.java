package io.quarkiverse.tandem.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.codingful.tandem.core.exception.DuplicateSeqException;
import com.codingful.tandem.core.exception.OutboxInsertException;

import io.quarkus.test.junit.QuarkusTest;

/**
 * The plain tier (HLD-quarkus §5.3, §12): the application injects the repository and inserts in its own
 * {@code @Transactional} method, next to a Hibernate ORM business change.
 */
@QuarkusTest
class PlainTierTest {

    @Test
    void GIVEN_an_order_placed_with_its_event_WHEN_the_transaction_commits_THEN_both_are_written() {
        String orderId = Scenarios.newOrderId();

        ScenarioResource.Outcome outcome = Scenarios.plain("commit", orderId);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.orderCommitted()).isTrue();
        assertThat(outcome.rows()).singleElement().satisfies(row -> {
            assertThat(row.aggregateId()).isEqualTo(orderId);
            assertThat(row.seq()).isEqualTo(1);
        });
    }

    @Test
    void GIVEN_an_order_placed_with_its_event_WHEN_the_business_change_fails_THEN_neither_is_written() {
        ScenarioResource.Outcome outcome = Scenarios.plain("business-failure", Scenarios.newOrderId());

        assertThat(outcome.failure()).isEqualTo(BusinessFailure.class.getSimpleName());
        assertNothingCommitted(outcome);
    }

    @Test
    void GIVEN_an_event_that_collides_WHEN_the_failure_reaches_the_transaction_boundary_THEN_the_order_is_not_committed() {
        ScenarioResource.Outcome outcome = Scenarios.plain("collision", Scenarios.newOrderId());

        assertThat(outcome.failure()).isEqualTo(DuplicateSeqException.class.getSimpleName());
        assertNothingCommitted(outcome);
    }

    @Test
    void GIVEN_an_event_that_collides_WHEN_the_method_catches_the_failure_THEN_it_returns_and_nothing_commits() {
        ScenarioResource.Outcome outcome = Scenarios.plain("collision-caught", Scenarios.newOrderId());

        assertThat(outcome.failure()).isNull();
        assertNothingCommitted(outcome);
    }

    @Test
    void GIVEN_no_transaction_WHEN_an_event_is_inserted_THEN_it_is_refused_and_nothing_is_written() {
        ScenarioResource.Outcome outcome = Scenarios.plain("no-transaction", Scenarios.newOrderId());

        assertThat(outcome.failure()).isEqualTo(OutboxInsertException.class.getSimpleName());
        assertThat(outcome.rows()).isEmpty();
    }

    @Test
    void GIVEN_no_transaction_WHEN_an_empty_batch_is_inserted_THEN_it_is_refused_too() {
        ScenarioResource.Outcome outcome = Scenarios.plain("empty-batch-no-transaction", Scenarios.newOrderId());

        assertThat(outcome.failure()).isEqualTo(OutboxInsertException.class.getSimpleName());
    }

    @Test
    void GIVEN_a_transaction_marked_rollback_only_WHEN_an_event_is_inserted_THEN_it_is_refused() {
        ScenarioResource.Outcome outcome = Scenarios.plain("rollback-only", Scenarios.newOrderId());

        assertThat(outcome.failure()).isEqualTo(OutboxInsertException.class.getSimpleName());
        assertThat(outcome.rows()).isEmpty();
    }

    private static void assertNothingCommitted(ScenarioResource.Outcome outcome) {
        assertThat(outcome.orderCommitted()).isFalse();
        assertThat(outcome.rows()).isEmpty();
    }
}
