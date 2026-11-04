package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * The bucket-count check runs after Liquibase's migrations at start (HLD-quarkus §5.2): run before them, it
 * would find no {@code tandem_meta} on the fresh database and stop the startup.
 */
class LiquibaseStartupOrderTest {

    private static final String BUCKET_COUNT = "64";
    private static final String ORDER_ID = "order-1";

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.liquibase(StartupOrderScenarios.class)
            .overrideConfigKey("quarkus.datasource.devservices.db-name", "liquibase_startup_order")
            .overrideConfigKey("tandem.outbox.bucket-count", BUCKET_COUNT);

    @Inject
    StartupOrderScenarios scenarios;

    @Inject
    DataSource dataSource;

    @Test
    void GIVEN_a_fresh_database_migrated_at_start_WHEN_the_application_starts_THEN_the_bucket_count_is_recorded_and_writes_succeed() {
        scenarios.record(ORDER_ID);

        assertThat(OutboxRows.recordedBucketCount(dataSource)).isEqualTo(BUCKET_COUNT);
        assertThat(OutboxRows.all(dataSource)).extracting(OutboxRows.Row::aggregateId).containsExactly(ORDER_ID);
    }
}
