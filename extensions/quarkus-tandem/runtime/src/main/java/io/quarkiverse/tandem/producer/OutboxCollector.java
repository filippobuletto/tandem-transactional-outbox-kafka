package io.quarkiverse.tandem.producer;

import com.codingful.tandem.core.AggregateId;
import com.codingful.tandem.core.OutboxMessage;

/**
 * The sink a {@link TransactionalOutboxTemplate} unit of work records outbox rows into
 * (LLD-quarkus §5.2). The template inserts what was recorded, in recording order, in the transaction
 * the work ran in.
 *
 * <p>
 * A collector is valid only during the {@code execute} call that supplied it and is not thread-safe.
 *
 * <p>
 * <b>Provided:</b> the extension implements this interface and the application calls it. A minor
 * version may add abstract methods, so an application implementing it follows the extension's minor
 * versions.
 */
public interface OutboxCollector {

    /**
     * Record a pre-built message: the full-control path, which needs no {@code PayloadSerializer}.
     * A managed sequence number ({@code managedSeq()}) and the writer lock ({@code lockedWrite()}) are
     * reached this way.
     *
     * @param message the row to insert
     * @throws NullPointerException if {@code message} is null
     */
    void add(OutboxMessage message);

    /**
     * Record a row from an object payload, serialized by the {@code PayloadSerializer} bean.
     *
     * @param aggregateType the aggregate type (topic routing, CloudEvents {@code type} fallback)
     * @param aggregateId the aggregate the row belongs to
     * @param seq the per-aggregate sequence number, the aggregate's {@code version} (HLD §4.2)
     * @param payload the payload object to serialize
     * @throws com.codingful.tandem.core.exception.PayloadSerializationException if no
     *         {@code PayloadSerializer} is available, or serialization fails
     */
    void record(String aggregateType, AggregateId aggregateId, long seq, Object payload);

    /**
     * As {@link #record(String, AggregateId, long, Object)}, with a String aggregate id.
     *
     * @param aggregateType the aggregate type
     * @param aggregateId the aggregate id
     * @param seq the per-aggregate sequence number, the aggregate's {@code version}
     * @param payload the payload object to serialize
     * @throws com.codingful.tandem.core.exception.PayloadSerializationException if no
     *         {@code PayloadSerializer} is available, or serialization fails
     */
    void record(String aggregateType, String aggregateId, long seq, Object payload);

    /**
     * Record a row with no sequence number ({@code unsequenced()}, HLD-managed-seq §4.5): consumers
     * deduplicate on the event id.
     *
     * @param aggregateType the aggregate type (topic routing, CloudEvents {@code type} fallback)
     * @param aggregateId the aggregate the row belongs to
     * @param payload the payload object to serialize
     * @throws com.codingful.tandem.core.exception.PayloadSerializationException if no
     *         {@code PayloadSerializer} is available, or serialization fails
     */
    void record(String aggregateType, AggregateId aggregateId, Object payload);

    /**
     * As {@link #record(String, AggregateId, Object)}, with a String aggregate id.
     *
     * @param aggregateType the aggregate type
     * @param aggregateId the aggregate id
     * @param payload the payload object to serialize
     * @throws com.codingful.tandem.core.exception.PayloadSerializationException if no
     *         {@code PayloadSerializer} is available, or serialization fails
     */
    void record(String aggregateType, String aggregateId, Object payload);
}
