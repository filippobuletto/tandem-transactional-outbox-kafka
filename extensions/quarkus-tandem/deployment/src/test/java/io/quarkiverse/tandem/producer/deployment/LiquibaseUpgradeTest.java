package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import liquibase.command.CommandScope;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;

/**
 * A database migrated with the changelog of an earlier Tandem release is upgraded by the application's
 * changelog at start, which includes Tandem's from the jar (HLD-quarkus §6.3, LLD-quarkus §12.1).
 */
class LiquibaseUpgradeTest {

    private static final String ORDER_ID = "order-1";

    static final UpgradeDatabase DATABASE = UpgradeDatabase.prepare("liquibase", database -> {
        try (Connection connection = database.connect()) {
            Database liquibaseDatabase = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            new CommandScope("update")
                    .addArgumentValue("changelogFile", "db/upgrade/liquibase-before.xml")
                    .addArgumentValue("database", liquibaseDatabase)
                    .execute();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    });

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.liquibase(StartupOrderScenarios.class, UpgradeDatabase.class)
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc.url", DATABASE.url())
            .overrideConfigKey("quarkus.datasource.username", DATABASE.username())
            .overrideConfigKey("quarkus.datasource.password", DATABASE.password());

    @Inject
    StartupOrderScenarios scenarios;

    @Inject
    DataSource dataSource;

    @Test
    void GIVEN_a_database_on_an_earlier_tandem_schema_WHEN_the_application_starts_THEN_only_the_new_version_is_applied_last_and_writes_succeed() {
        List<String> history = OutboxRows.liquibaseHistory(dataSource);

        assertThat(history).doesNotHaveDuplicates();
        assertThat(history.get(history.size() - 1)).contains(OutboxRows.FLOOR_LAST_SCHEMA_FILE);
        assertThat(OutboxRows.hasIndex(dataSource, OutboxRows.FLOOR_LAST_SCHEMA_INDEX)).isTrue();
        scenarios.record(ORDER_ID);
        assertThat(OutboxRows.all(dataSource)).hasSize(1);
    }
}
