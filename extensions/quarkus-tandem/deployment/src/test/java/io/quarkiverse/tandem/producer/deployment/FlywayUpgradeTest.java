package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * An application on Flyway upgrades Tandem by copying the new schema version's file as its next migration,
 * after its own later ones (HLD-quarkus §6.3, LLD-quarkus §12.2).
 */
class FlywayUpgradeTest {

    private static final String LOCATION = "db/upgrade/flyway";
    private static final String[] MIGRATIONS = { "V1__app_orders.sql", "V2__tandem_v1_baseline.sql",
            "V3__tandem_v2_replays.sql", "V4__tandem_v3_managed_seq.sql", "V5__tandem_v4_optional_seq.sql",
            "V6__app_order_placed_at.sql", "V7__tandem_v5_claim_under_backlog.sql" };
    private static final String BEFORE_THE_UPGRADE = "6";
    private static final String ORDER_ID = "order-1";

    static final UpgradeDatabase DATABASE = UpgradeDatabase.prepare("flyway",
            database -> Flyway.configure()
                    .dataSource(database.url(), database.username(), database.password())
                    .locations("classpath:" + LOCATION)
                    .target(BEFORE_THE_UPGRADE)
                    .load()
                    .migrate());

    @RegisterExtension
    static final QuarkusExtensionTest APP = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> {
                jar.addClasses(OutboxRows.class, OutboxRows.Row.class, Orders.class, StartupOrderScenarios.class,
                        UpgradeDatabase.class);
                for (String migration : MIGRATIONS) {
                    jar.addAsResource(LOCATION + "/" + migration);
                }
            })
            .overrideConfigKey("quarkus.datasource.db-kind", "postgresql")
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc.url", DATABASE.url())
            .overrideConfigKey("quarkus.datasource.username", DATABASE.username())
            .overrideConfigKey("quarkus.datasource.password", DATABASE.password())
            .overrideConfigKey("quarkus.flyway.migrate-at-start", "true")
            .overrideConfigKey("quarkus.flyway.locations", LOCATION);

    @Inject
    StartupOrderScenarios scenarios;

    @Inject
    DataSource dataSource;

    @Test
    void GIVEN_a_database_on_an_earlier_tandem_schema_WHEN_the_application_starts_THEN_the_copied_version_is_applied_last_and_writes_succeed() {
        List<String> history = OutboxRows.flywayHistory(dataSource);

        assertThat(history).containsExactlyElementsOf(Arrays.asList(MIGRATIONS));
        assertThat(OutboxRows.hasIndex(dataSource, OutboxRows.FLOOR_LAST_SCHEMA_INDEX)).isTrue();
        scenarios.record(ORDER_ID);
        assertThat(OutboxRows.all(dataSource)).hasSize(1);
    }
}
