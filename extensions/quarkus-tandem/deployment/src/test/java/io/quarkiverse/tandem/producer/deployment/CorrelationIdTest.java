package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;

import com.codingful.tandem.core.TandemContext;
import com.codingful.tandem.core.TandemHeaders;

import io.quarkiverse.tandem.producer.runtime.TandemProducerRecorder;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * With tracing on, the correlation id an application puts in SLF4J's MDC reaches the row, since Quarkus binds
 * SLF4J to the JBoss LogManager (HLD-quarkus §6.1). OpenTelemetry is present and its Tandem adapter is not:
 * the row carries the correlation id only, and the startup says so once (LLD-quarkus §10.3).
 */
class CorrelationIdTest {

    private static final String MDC_KEY = "requestId";
    private static final String CORRELATION_ID = "corr-42";
    private static final String ORDER_ID = "order-1";

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.liquibase(StartupOrderScenarios.class)
            .overrideConfigKey("tandem.tracing.enabled", "true")
            .overrideConfigKey("tandem.tracing.correlation-id-mdc-key", MDC_KEY)
            .overrideConfigKey("quarkus.otel.traces.exporter", "none")
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue()
                    && record.getMessage().startsWith(TandemProducerRecorder.OTEL_ADAPTER_MISSING))
            .assertLogRecords(records -> assertThat(records).hasSize(1));

    @Inject
    StartupOrderScenarios scenarios;

    @Inject
    DataSource dataSource;

    @BeforeEach
    void clean() {
        OutboxRows.clear(dataSource);
    }

    @AfterEach
    void clearContext() {
        MDC.clear();
        TandemContext.clear();
    }

    @Test
    void GIVEN_a_correlation_id_in_the_logging_MDC_WHEN_an_event_is_written_THEN_the_row_carries_it_and_no_trace_context() {
        MDC.put(MDC_KEY, CORRELATION_ID);

        scenarios.record(ORDER_ID);

        assertThat(OutboxRows.all(dataSource)).singleElement()
                .extracting(OutboxRows::headersOf)
                .isEqualTo(java.util.Map.of(TandemHeaders.CORRELATION_ID, CORRELATION_ID));
    }

    @Test
    void GIVEN_a_correlation_id_set_through_the_tandem_context_only_WHEN_an_event_is_written_THEN_the_row_carries_it() {
        TandemContext.setCorrelationId(CORRELATION_ID);

        scenarios.record(ORDER_ID);

        assertThat(OutboxRows.all(dataSource)).singleElement()
                .extracting(row -> OutboxRows.headersOf(row).get(TandemHeaders.CORRELATION_ID))
                .isEqualTo(CORRELATION_ID);
    }

    @Test
    void GIVEN_no_correlation_id_anywhere_WHEN_an_event_is_written_THEN_the_row_carries_no_header() {
        scenarios.record(ORDER_ID);

        assertThat(OutboxRows.all(dataSource)).singleElement()
                .extracting(OutboxRows::headersOf)
                .isEqualTo(java.util.Map.of());
    }
}
