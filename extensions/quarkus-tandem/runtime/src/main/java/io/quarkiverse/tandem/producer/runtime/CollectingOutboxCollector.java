package io.quarkiverse.tandem.producer.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.codingful.tandem.core.AggregateId;
import com.codingful.tandem.core.OutboxMessage;
import com.codingful.tandem.core.exception.PayloadSerializationException;
import com.codingful.tandem.core.port.PayloadSerializer;

import io.quarkiverse.tandem.producer.OutboxCollector;

/**
 * Accumulates messages in call order for the template to insert. Without a serializer an object
 * payload fails at the call, while {@link #add(OutboxMessage)} keeps working (HLD-quarkus §5.7).
 */
public final class CollectingOutboxCollector implements OutboxCollector {

    public static final String NO_SERIALIZER = "No PayloadSerializer available; add quarkus-jackson, supply a"
            + " PayloadSerializer bean, or record a pre-built OutboxMessage via add(...)";

    private final PayloadSerializer payloadSerializer; // null: object payloads fail at the call
    private final List<OutboxMessage> messages = new ArrayList<>();

    CollectingOutboxCollector(PayloadSerializer payloadSerializer) {
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public void add(OutboxMessage message) {
        messages.add(Objects.requireNonNull(message, "message"));
    }

    @Override
    public void record(String aggregateType, AggregateId aggregateId, long seq, Object payload) {
        messages.add(serialized(aggregateType, aggregateId, payload).seq(seq).build());
    }

    @Override
    public void record(String aggregateType, String aggregateId, long seq, Object payload) {
        record(aggregateType, AggregateId.of(aggregateId), seq, payload);
    }

    @Override
    public void record(String aggregateType, AggregateId aggregateId, Object payload) {
        messages.add(serialized(aggregateType, aggregateId, payload).unsequenced().build());
    }

    @Override
    public void record(String aggregateType, String aggregateId, Object payload) {
        record(aggregateType, AggregateId.of(aggregateId), payload);
    }

    private OutboxMessage.Builder serialized(String aggregateType, AggregateId aggregateId, Object payload) {
        if (payloadSerializer == null) {
            throw new PayloadSerializationException(NO_SERIALIZER);
        }
        return OutboxMessage.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .payload(payloadSerializer.serialize(payload))
                .contentType(payloadSerializer.contentType());
    }

    List<OutboxMessage> collected() {
        return messages;
    }
}
