package io.quarkiverse.tandem.producer.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.codingful.tandem.core.OutboxMessage;
import com.codingful.tandem.core.exception.PayloadSerializationException;

/**
 * Without a {@code PayloadSerializer} an object payload fails at the call and nothing is collected, while a
 * pre-built message is still accepted (HLD-quarkus §5.7).
 */
class CollectingOutboxCollectorTest {

    private static final String AGGREGATE_TYPE = "Order";
    private static final String AGGREGATE_ID = "order-1";

    private final CollectingOutboxCollector withoutSerializer = new CollectingOutboxCollector(null);

    @Test
    void GIVEN_no_serializer_WHEN_an_object_payload_is_recorded_THEN_it_fails_at_the_call_and_nothing_is_collected() {
        assertThatThrownBy(() -> withoutSerializer.record(AGGREGATE_TYPE, AGGREGATE_ID, 1, new Object()))
                .isInstanceOf(PayloadSerializationException.class)
                .hasMessage(CollectingOutboxCollector.NO_SERIALIZER);
        assertThatThrownBy(() -> withoutSerializer.record(AGGREGATE_TYPE, AGGREGATE_ID, new Object()))
                .isInstanceOf(PayloadSerializationException.class);

        assertThat(withoutSerializer.collected()).isEmpty();
    }

    @Test
    void GIVEN_no_serializer_WHEN_a_pre_built_message_is_added_THEN_it_is_collected() {
        OutboxMessage message = OutboxMessage.builder()
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(AGGREGATE_ID)
                .payload("{}".getBytes(StandardCharsets.UTF_8))
                .seq(1)
                .build();

        withoutSerializer.add(message);

        assertThat(withoutSerializer.collected()).containsExactly(message);
    }

    @Test
    void GIVEN_a_collector_WHEN_a_null_message_is_added_THEN_it_is_refused() {
        assertThatThrownBy(() -> withoutSerializer.add(null)).isInstanceOf(NullPointerException.class);
    }
}
