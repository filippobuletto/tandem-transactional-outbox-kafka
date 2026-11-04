package io.quarkiverse.tandem.it;

import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import com.codingful.tandem.core.OutboxMessage;

/** Places orders through Hibernate ORM and builds the events the scenarios write. */
@ApplicationScoped
public class Orders {

    public static final String AGGREGATE_TYPE = "Order";

    @Inject
    EntityManager entityManager;

    // Flushed at once so the business change runs before the outbox insert, as a JDBC write would: a
    // change left for the commit's flush would fail there when PostgreSQL has aborted the transaction.
    void place(String orderId) {
        entityManager.persist(new PlacedOrder(orderId));
        entityManager.flush();
    }

    static OutboxMessage placed(String orderId, long seq) {
        return placedBuilder(orderId, seq).build();
    }

    static OutboxMessage.Builder placedBuilder(String orderId, long seq) {
        return OutboxMessage.builder()
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(orderId)
                .payload(("{\"orderId\": \"" + orderId + "\"}").getBytes(StandardCharsets.UTF_8))
                .seq(seq);
    }
}
