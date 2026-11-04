package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * The same configuration may carry the keys of the relay's role; the write side neither reads nor refuses
 * them (HLD-quarkus §6.1, LLD-quarkus §8.2).
 */
class OtherRoleKeysTest {

    private static final String ORDER_ID = "order-1";

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.liquibase(StartupOrderScenarios.class)
            .overrideConfigKey("tandem.relay.poll-interval", "250ms")
            .overrideConfigKey("tandem.kafka.source", "https://example.com/orders")
            .overrideConfigKey("tandem.tracing.publish-span", "true");

    @Inject
    StartupOrderScenarios scenarios;

    @Inject
    DataSource dataSource;

    @Test
    void GIVEN_relay_and_kafka_keys_in_the_configuration_WHEN_the_application_starts_THEN_it_writes_as_usual() {
        scenarios.record(ORDER_ID);

        assertThat(OutboxRows.all(dataSource)).hasSize(1);
    }
}
