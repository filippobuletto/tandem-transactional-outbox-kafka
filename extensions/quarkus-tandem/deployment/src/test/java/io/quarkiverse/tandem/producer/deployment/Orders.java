package io.quarkiverse.tandem.producer.deployment;

import java.nio.charset.StandardCharsets;

import com.codingful.tandem.core.OutboxMessage;

/** The events the tests write: an order placed, as a pre-built message. */
final class Orders {

    static final String AGGREGATE_TYPE = "Order";

    private Orders() {
    }

    static OutboxMessage placed(String orderId, long seq) {
        return OutboxMessage.builder()
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(orderId)
                .payload(("{\"orderId\": \"" + orderId + "\"}").getBytes(StandardCharsets.UTF_8))
                .seq(seq)
                .build();
    }
}
