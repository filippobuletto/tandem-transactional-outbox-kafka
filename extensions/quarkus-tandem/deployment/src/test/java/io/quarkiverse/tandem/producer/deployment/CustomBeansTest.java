package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.codingful.tandem.core.port.PayloadSerializer;
import com.codingful.tandem.core.port.TracePropagator;

import io.quarkiverse.tandem.producer.TransactionalOutboxTemplate;
import io.quarkiverse.tandem.producer.runtime.TandemProducerRecorder;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The application replaces the extension's {@link PayloadSerializer} and {@link TracePropagator} by declaring
 * beans of those types (LLD-quarkus §3.3, §10.2). With the application's own trace capture, the startup does not
 * warn about the missing OpenTelemetry adapter (§10.3).
 */
class CustomBeansTest {

    static final String FIXED_PAYLOAD = "{\"serializedBy\": \"application\"}";
    static final String TRACE_HEADER = "x-app-trace";
    static final String TRACE_VALUE = "app-trace-1";
    private static final String ORDER_ID = "order-1";

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.liquibase(ApplicationSerializer.class, ApplicationTrace.class)
            .overrideConfigKey("tandem.tracing.enabled", "true")
            .overrideConfigKey("quarkus.otel.traces.exporter", "none")
            .setLogRecordPredicate(record -> record.getMessage().startsWith(TandemProducerRecorder.OTEL_ADAPTER_MISSING))
            .assertLogRecords(records -> assertThat(records).isEmpty());

    @Inject
    TransactionalOutboxTemplate template;

    @Inject
    DataSource dataSource;

    @Test
    void GIVEN_application_serializer_and_trace_beans_WHEN_an_object_payload_is_recorded_THEN_the_row_uses_both() {
        template.executeWithoutResult(
                collector -> collector.record(Orders.AGGREGATE_TYPE, ORDER_ID, 1, new Object()));

        assertThat(OutboxRows.all(dataSource)).singleElement().satisfies(row -> {
            assertThat(row.payload()).isEqualTo(FIXED_PAYLOAD);
            assertThat(OutboxRows.headersOf(row)).containsEntry(TRACE_HEADER, TRACE_VALUE);
        });
    }

    @Singleton
    public static class ApplicationSerializer implements PayloadSerializer {

        @Override
        public byte[] serialize(Object payload) {
            return FIXED_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String contentType() {
            return "application/json";
        }
    }

    @Singleton
    public static class ApplicationTrace implements TracePropagator {

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public Map<String, String> capture() {
            return Map.of(TRACE_HEADER, TRACE_VALUE);
        }
    }
}
