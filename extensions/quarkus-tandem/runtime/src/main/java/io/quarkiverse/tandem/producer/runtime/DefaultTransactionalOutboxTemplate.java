package io.quarkiverse.tandem.producer.runtime;

import java.util.Objects;
import java.util.function.Function;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Singleton;

import com.codingful.tandem.core.port.OutboxRepository;
import com.codingful.tandem.core.port.PayloadSerializer;

import io.quarkiverse.tandem.producer.OutboxCollector;
import io.quarkiverse.tandem.producer.TransactionalOutboxTemplate;
import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * The template on {@link QuarkusTransaction#joiningExisting()}: the meaning of {@code REQUIRED}, with
 * Narayana's own rollback handling (LLD-quarkus §5.1).
 */
@Singleton
@Typed(TransactionalOutboxTemplate.class)
final class DefaultTransactionalOutboxTemplate implements TransactionalOutboxTemplate {

    private final OutboxRepository outboxRepository;
    private final Instance<PayloadSerializer> payloadSerializer;

    DefaultTransactionalOutboxTemplate(OutboxRepository outboxRepository, Instance<PayloadSerializer> payloadSerializer) {
        this.outboxRepository = outboxRepository;
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public <T> T execute(Function<OutboxCollector, T> work) {
        Objects.requireNonNull(work, "work");
        return QuarkusTransaction.joiningExisting().call(() -> {
            CollectingOutboxCollector collector = new CollectingOutboxCollector(serializer());
            T result = work.apply(collector);
            outboxRepository.insertAll(collector.collected());
            return result;
        });
    }

    // Resolved per call so the template does not depend on whether Jackson is present; several
    // application beans make get() throw CDI's AmbiguousResolutionException, which names them.
    private PayloadSerializer serializer() {
        return payloadSerializer.isUnsatisfied() ? null : payloadSerializer.get();
    }
}
