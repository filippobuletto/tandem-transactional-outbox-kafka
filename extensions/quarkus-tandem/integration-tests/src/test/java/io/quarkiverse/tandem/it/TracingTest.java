package io.quarkiverse.tandem.it;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.codingful.tandem.core.TandemHeaders;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

/**
 * With OpenTelemetry and its Tandem adapter present, the row carries the request's trace context and the
 * correlation id from the MDC, side by side (HLD-quarkus §6.1, LLD-quarkus §10).
 */
@QuarkusTest
class TracingTest {

    private static final String ORDER_ID = "traced-order";
    private static final String CORRELATION_ID = "corr-7";
    private static final String TRACEPARENT = "traceparent";

    @Test
    void GIVEN_a_request_in_a_server_span_with_a_correlation_id_WHEN_it_writes_an_event_THEN_the_row_carries_both()
            throws Exception {
        TracingResource.Written written = given().when().post("/tracing/{orderId}/{correlationId}", ORDER_ID, CORRELATION_ID)
                .then().statusCode(200).extract().as(TracingResource.Written.class);

        OutboxRows.Row[] rows = when().get("/rows/{aggregateId}", ORDER_ID)
                .then().statusCode(200).extract().as(OutboxRows.Row[].class);

        assertThat(rows).hasSize(1);
        Map<String, String> headers = new ObjectMapper().readValue(rows[0].headers(), new TypeReference<>() {
        });
        assertThat(written.traceId()).isNotBlank();
        assertThat(headers.get(TRACEPARENT)).contains(written.traceId());
        assertThat(headers).containsEntry(TandemHeaders.CORRELATION_ID, written.correlationId());
    }
}
