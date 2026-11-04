package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.codingful.tandem.core.exception.PayloadSerializationException;

import io.quarkiverse.tandem.producer.TransactionalOutboxTemplate;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The template tier (HLD-quarkus §5.3, §5.4, §12): {@code execute} joins the caller's transaction or starts
 * one, and inserts what the work recorded in it.
 */
class TemplateTierTest {

    private static final String ORDER_ID = "order-1";
    private static final String OTHER_ORDER_ID = "order-2";

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.liquibase(Scenarios.class, NewTransaction.class,
            OrderPlaced.class, Unserializable.class, PlainTierTest.BusinessFailure.class);

    @Inject
    TransactionalOutboxTemplate template;

    @Inject
    Scenarios scenarios;

    @Inject
    Orders orders;

    @Inject
    DataSource dataSource;

    @BeforeEach
    void clean() {
        OutboxRows.clear(dataSource);
    }

    @Test
    void GIVEN_no_transaction_WHEN_the_work_records_an_event_THEN_the_template_commits_it_with_the_order() {
        String result = template.execute(collector -> {
            orders.place(ORDER_ID);
            collector.record(Orders.AGGREGATE_TYPE, ORDER_ID, 1, new OrderPlaced(ORDER_ID));
            return ORDER_ID;
        });

        assertThat(result).isEqualTo(ORDER_ID);
        assertThat(orders.exists(ORDER_ID)).isTrue();
        assertThat(OutboxRows.all(dataSource)).singleElement().satisfies(row -> {
            assertThat(row.aggregateId()).isEqualTo(ORDER_ID);
            assertThat(row.seq()).isEqualTo(1);
            assertThat(row.payload()).isEqualTo(new String(Orders.payloadOf(ORDER_ID), StandardCharsets.UTF_8));
        });
    }

    @Test
    void GIVEN_several_recorded_events_WHEN_the_template_commits_THEN_they_are_inserted_in_recording_order() {
        template.executeWithoutResult(collector -> {
            collector.add(Orders.placed(ORDER_ID, 1));
            collector.record(Orders.AGGREGATE_TYPE, OTHER_ORDER_ID, new OrderPlaced(OTHER_ORDER_ID));
            collector.add(Orders.placed(ORDER_ID, 2));
        });

        assertThat(OutboxRows.all(dataSource)).extracting(OutboxRows.Row::aggregateId)
                .containsExactly(ORDER_ID, OTHER_ORDER_ID, ORDER_ID);
    }

    @Test
    void GIVEN_a_template_it_started_WHEN_the_work_fails_THEN_neither_the_order_nor_the_event_is_written() {
        assertThatThrownBy(() -> template.executeWithoutResult(collector -> {
            orders.place(ORDER_ID);
            collector.add(Orders.placed(ORDER_ID, 1));
            throw new PlainTierTest.BusinessFailure();
        })).isInstanceOf(PlainTierTest.BusinessFailure.class);

        assertThat(orders.exists(ORDER_ID)).isFalse();
        assertThat(OutboxRows.all(dataSource)).isEmpty();
    }

    @Test
    void GIVEN_a_joined_transaction_WHEN_it_commits_THEN_the_event_commits_with_the_order() {
        scenarios.placeWithTemplate(ORDER_ID);

        assertThat(orders.exists(ORDER_ID)).isTrue();
        assertThat(OutboxRows.all(dataSource)).hasSize(1);
    }

    @Test
    void GIVEN_a_joined_transaction_WHEN_the_caller_fails_after_the_template_THEN_the_event_rolls_back_with_it() {
        assertThatThrownBy(() -> scenarios.placeWithTemplateAndFail(ORDER_ID))
                .isInstanceOf(PlainTierTest.BusinessFailure.class);

        assertThat(orders.exists(ORDER_ID)).isFalse();
        assertThat(OutboxRows.all(dataSource)).isEmpty();
    }

    @Test
    void GIVEN_a_template_it_started_WHEN_a_serialization_failure_is_caught_around_it_THEN_nothing_commits() {
        assertThatThrownBy(() -> template.executeWithoutResult(collector -> {
            orders.place(ORDER_ID);
            collector.record(Orders.AGGREGATE_TYPE, ORDER_ID, 1, new Unserializable());
        })).isInstanceOf(PayloadSerializationException.class);

        assertThat(orders.exists(ORDER_ID)).isFalse();
        assertThat(OutboxRows.all(dataSource)).isEmpty();
    }

    // Where Quarkus differs from Spring: the outer commit of a transaction the template marked
    // rollback-only rolls back without throwing (HLD-quarkus §5.3).
    @Test
    void GIVEN_a_joined_template_WHEN_its_serialization_failure_is_caught_by_the_caller_THEN_the_caller_returns_and_nothing_commits() {
        scenarios.placeWithUnserializableEventCaughtAroundTemplate(ORDER_ID);

        assertThat(orders.exists(ORDER_ID)).isFalse();
        assertThat(OutboxRows.all(dataSource)).isEmpty();
    }

    @Test
    void GIVEN_a_serialization_failure_caught_inside_the_work_WHEN_the_work_returns_THEN_the_transaction_still_commits() {
        template.executeWithoutResult(collector -> {
            orders.place(ORDER_ID);
            try {
                collector.record(Orders.AGGREGATE_TYPE, ORDER_ID, 1, new Unserializable());
            } catch (PayloadSerializationException expected) {
                collector.add(Orders.placed(ORDER_ID, 1));
            }
        });

        assertThat(orders.exists(ORDER_ID)).isTrue();
        assertThat(OutboxRows.all(dataSource)).hasSize(1);
    }

    @Test
    void GIVEN_a_template_inside_a_new_transaction_WHEN_the_outer_one_fails_THEN_only_the_new_transaction_commits() {
        assertThatThrownBy(() -> scenarios.placeThenRecordOtherInNewTransactionAndFail(ORDER_ID, OTHER_ORDER_ID))
                .isInstanceOf(PlainTierTest.BusinessFailure.class);

        assertThat(orders.exists(ORDER_ID)).isFalse();
        assertThat(orders.exists(OTHER_ORDER_ID)).isTrue();
        assertThat(OutboxRows.all(dataSource)).extracting(OutboxRows.Row::aggregateId).containsExactly(OTHER_ORDER_ID);
    }

    public record OrderPlaced(String orderId) {
    }

    /** No property for Jackson to write: serialization fails with Quarkus's default mapper. */
    public static final class Unserializable {
    }

    @ApplicationScoped
    public static class Scenarios {

        @Inject
        TransactionalOutboxTemplate template;

        @Inject
        Orders orders;

        @Inject
        NewTransaction newTransaction;

        @Transactional
        public void placeWithTemplate(String orderId) {
            template.executeWithoutResult(collector -> {
                orders.place(orderId);
                collector.record(Orders.AGGREGATE_TYPE, orderId, 1, new OrderPlaced(orderId));
            });
        }

        @Transactional
        public void placeWithTemplateAndFail(String orderId) {
            placeWithTemplate(orderId);
            throw new PlainTierTest.BusinessFailure();
        }

        @Transactional
        public void placeWithUnserializableEventCaughtAroundTemplate(String orderId) {
            try {
                template.executeWithoutResult(collector -> {
                    orders.place(orderId);
                    collector.record(Orders.AGGREGATE_TYPE, orderId, 1, new Unserializable());
                });
            } catch (PayloadSerializationException expected) {
                // the caller goes on without the event; the template has already marked the transaction
            }
        }

        @Transactional
        public void placeThenRecordOtherInNewTransactionAndFail(String orderId, String otherOrderId) {
            orders.place(orderId);
            newTransaction.placeWithTemplate(otherOrderId);
            throw new PlainTierTest.BusinessFailure();
        }
    }

    @ApplicationScoped
    public static class NewTransaction {

        @Inject
        TransactionalOutboxTemplate template;

        @Inject
        Orders orders;

        @Transactional(Transactional.TxType.REQUIRES_NEW)
        public void placeWithTemplate(String orderId) {
            template.executeWithoutResult(collector -> {
                orders.place(orderId);
                collector.add(Orders.placed(orderId, 1));
            });
        }
    }
}
