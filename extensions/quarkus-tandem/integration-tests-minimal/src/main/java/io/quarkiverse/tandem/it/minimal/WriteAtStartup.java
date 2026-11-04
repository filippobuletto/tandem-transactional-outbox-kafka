package io.quarkiverse.tandem.it.minimal;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import com.codingful.tandem.core.OutboxMessage;
import com.codingful.tandem.core.exception.PayloadSerializationException;

import io.quarkiverse.tandem.producer.TransactionalOutboxTemplate;
import io.quarkus.runtime.StartupEvent;

/**
 * Writes once the application has started, observable from the database alone so the same scenario runs
 * against the native executable: an object payload any JSON binding could write, which must fail with no
 * serializer present, then a pre-built message, written only when that failure was the expected one.
 */
@ApplicationScoped
public class WriteAtStartup {

    public static final String AGGREGATE_TYPE = "Order";
    public static final String AGGREGATE_ID = "order-at-startup";
    public static final String PAYLOAD = "{\"orderId\": \"order-at-startup\"}";

    @Inject
    TransactionalOutboxTemplate template;

    void onStart(@Observes StartupEvent event) {
        template.executeWithoutResult(collector -> {
            try {
                collector.record(AGGREGATE_TYPE, AGGREGATE_ID, 1, Map.of("writtenBy", "serializer"));
            } catch (PayloadSerializationException expected) {
                collector.add(OutboxMessage.builder()
                        .aggregateType(AGGREGATE_TYPE)
                        .aggregateId(AGGREGATE_ID)
                        .payload(PAYLOAD.getBytes(StandardCharsets.UTF_8))
                        .seq(1)
                        .build());
            }
        });
    }
}
