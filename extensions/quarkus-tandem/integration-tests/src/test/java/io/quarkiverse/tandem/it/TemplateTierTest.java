package io.quarkiverse.tandem.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.codingful.tandem.core.exception.PayloadSerializationException;

import io.quarkus.test.junit.QuarkusTest;

/**
 * The template tier (HLD-quarkus §5.3, §5.4, §12): {@code execute} joins the caller's transaction or starts
 * one, and inserts what the work recorded in it.
 */
@QuarkusTest
class TemplateTierTest {

    @Test
    void GIVEN_no_transaction_WHEN_the_work_records_an_event_THEN_the_template_commits_it_with_the_order() {
        String orderId = Scenarios.newOrderId();

        ScenarioResource.Outcome outcome = Scenarios.template("started-commit", orderId);

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.orderCommitted()).isTrue();
        assertThat(outcome.rows()).singleElement().satisfies(row -> assertThat(row.payload()).contains(orderId));
    }

    @Test
    void GIVEN_several_recorded_events_WHEN_the_template_commits_THEN_they_are_inserted_in_recording_order() {
        ScenarioResource.Outcome outcome = Scenarios.template("recording-order", Scenarios.newOrderId());

        assertThat(outcome.rows()).extracting(OutboxRows.Row::seq).containsExactly(3L, 1L, 2L);
    }

    @Test
    void GIVEN_a_template_it_started_WHEN_the_work_fails_THEN_neither_the_order_nor_the_event_is_written() {
        ScenarioResource.Outcome outcome = Scenarios.template("started-work-fails", Scenarios.newOrderId());

        assertThat(outcome.failure()).isEqualTo(BusinessFailure.class.getSimpleName());
        assertNothingCommitted(outcome);
    }

    @Test
    void GIVEN_a_joined_transaction_WHEN_it_commits_THEN_the_event_commits_with_the_order() {
        ScenarioResource.Outcome outcome = Scenarios.template("joined-commit", Scenarios.newOrderId());

        assertThat(outcome.orderCommitted()).isTrue();
        assertThat(outcome.rows()).hasSize(1);
    }

    @Test
    void GIVEN_a_joined_transaction_WHEN_the_caller_fails_after_the_template_THEN_the_event_rolls_back_with_it() {
        ScenarioResource.Outcome outcome = Scenarios.template("joined-caller-fails", Scenarios.newOrderId());

        assertThat(outcome.failure()).isEqualTo(BusinessFailure.class.getSimpleName());
        assertNothingCommitted(outcome);
    }

    @Test
    void GIVEN_a_template_it_started_WHEN_a_serialization_failure_is_caught_around_it_THEN_nothing_commits() {
        ScenarioResource.Outcome outcome = Scenarios.template("started-serialization-failure", Scenarios.newOrderId());

        assertThat(outcome.failure()).isEqualTo(PayloadSerializationException.class.getSimpleName());
        assertNothingCommitted(outcome);
    }

    // Where Quarkus differs from Spring: the outer commit of a transaction the template marked
    // rollback-only rolls back without throwing (HLD-quarkus §5.3).
    @Test
    void GIVEN_a_joined_template_WHEN_its_serialization_failure_is_caught_by_the_caller_THEN_the_caller_returns_and_nothing_commits() {
        ScenarioResource.Outcome outcome = Scenarios.template("joined-serialization-failure-caught",
                Scenarios.newOrderId());

        assertThat(outcome.failure()).isNull();
        assertNothingCommitted(outcome);
    }

    @Test
    void GIVEN_a_serialization_failure_caught_inside_the_work_WHEN_the_work_returns_THEN_the_transaction_still_commits() {
        ScenarioResource.Outcome outcome = Scenarios.template("serialization-failure-caught-inside",
                Scenarios.newOrderId());

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.orderCommitted()).isTrue();
        assertThat(outcome.rows()).hasSize(1);
    }

    @Test
    void GIVEN_a_template_inside_a_new_transaction_WHEN_the_outer_one_fails_THEN_only_the_new_transaction_commits() {
        ScenarioResource.Outcome outcome = Scenarios.template("requires-new", Scenarios.newOrderId());

        assertThat(outcome.failure()).isEqualTo(BusinessFailure.class.getSimpleName());
        assertNothingCommitted(outcome);
        assertThat(outcome.otherOrderCommitted()).isTrue();
        assertThat(outcome.otherRows()).hasSize(1);
    }

    private static void assertNothingCommitted(ScenarioResource.Outcome outcome) {
        assertThat(outcome.orderCommitted()).isFalse();
        assertThat(outcome.rows()).isEmpty();
    }
}
