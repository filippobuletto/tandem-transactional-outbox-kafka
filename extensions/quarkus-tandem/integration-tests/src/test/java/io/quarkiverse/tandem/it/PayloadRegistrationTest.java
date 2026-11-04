package io.quarkiverse.tandem.it;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkus.test.junit.QuarkusTest;

/**
 * A payload type used only by the outbox serializes when the application registers it for reflection; in
 * native mode an unregistered one fails, with the default {@code ObjectMapper} (HLD-quarkus §5.7, §11).
 */
@QuarkusTest
class PayloadRegistrationTest {

    @Test
    void GIVEN_a_payload_type_registered_for_reflection_WHEN_it_is_recorded_THEN_it_is_serialized_on_the_row() {
        String orderId = "registered-order";

        when().post("/payloads/registered/{orderId}", orderId).then().statusCode(204);

        OutboxRowsResource.Row[] rows = when().get("/rows/{aggregateId}", orderId)
                .then().statusCode(200).extract().as(OutboxRowsResource.Row[].class);
        assertThat(rows).singleElement().satisfies(row -> assertThat(row.payload()).contains(orderId));
    }

    @Test
    @EnabledIfSystemProperty(named = "native.image.path", matches = ".+")
    void GIVEN_a_native_executable_and_an_unregistered_payload_type_WHEN_it_is_recorded_THEN_serialization_fails_and_nothing_is_written() {
        String orderId = "unregistered-order";

        when().post("/payloads/unregistered/{orderId}", orderId).then().statusCode(PayloadResource.SERIALIZATION_FAILED);

        OutboxRowsResource.Row[] rows = when().get("/rows/{aggregateId}", orderId)
                .then().statusCode(200).extract().as(OutboxRowsResource.Row[].class);
        assertThat(rows).isEmpty();
    }
}
