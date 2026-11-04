package io.quarkiverse.tandem.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Ordering under concurrency (HLD-quarkus §5.8, §12): publication follows commit order, so two unserialized
 * writers to one aggregate can publish out of business order, and {@code lockedWrite()} serializes them.
 */
@QuarkusTest
class OrderingTest {

    @Test
    void GIVEN_two_unserialized_writers_WHEN_the_first_to_insert_commits_second_THEN_the_relay_publishes_out_of_business_order() {
        OrderingScenarios.Ordering ordering = Scenarios.ordering("unserialized", Scenarios.newOrderId());

        assertThat(ordering.published()).containsExactly(2L, 1L);
    }

    @Test
    void GIVEN_two_writers_using_the_writer_lock_WHEN_the_second_inserts_while_the_first_is_open_THEN_it_waits_and_order_holds() {
        OrderingScenarios.Ordering ordering = Scenarios.ordering("locked-write", Scenarios.newOrderId());

        assertThat(ordering.secondWaitedOnTheLock()).isTrue();
        assertThat(ordering.secondDoneWhileFirstOpen()).isFalse();
        assertThat(ordering.published()).containsExactly(1L, 2L);
    }
}
