package io.quarkiverse.tandem.producer.deployment;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * The application configurations the tests start: PostgreSQL through Dev Services on a pinned image,
 * and the schema applied by the migration route under test.
 */
final class TestApps {

    private TestApps() {
    }

    static QuarkusExtensionTest base(Class<?>... classes) {
        return new QuarkusExtensionTest()
                .withApplicationRoot(jar -> jar.addClasses(OutboxRows.class, OutboxRows.Row.class, Orders.class,
                        TestApps.class).addClasses(classes)
                        .addAsResource("db/changelog.xml").addAsResource("db/app/changes.sql"))
                .overrideConfigKey("quarkus.datasource.db-kind", "postgresql")
                .overrideConfigKey(OutboxRows.DEV_SERVICES_IMAGE_KEY, OutboxRows.POSTGRES_IMAGE);
    }

    static final String[] FLYWAY_MIGRATIONS = { "V1__tandem_v1_baseline.sql", "V2__tandem_v2_replays.sql",
            "V3__tandem_v3_managed_seq.sql", "V4__tandem_v4_optional_seq.sql",
            "V5__tandem_v5_claim_under_backlog.sql", "V6__app_orders.sql" };

    static QuarkusExtensionTest flyway(Class<?>... classes) {
        QuarkusExtensionTest app = new QuarkusExtensionTest()
                .withApplicationRoot(jar -> {
                    jar.addClasses(OutboxRows.class, OutboxRows.Row.class, Orders.class, TestApps.class)
                            .addClasses(classes);
                    for (String migration : FLYWAY_MIGRATIONS) {
                        jar.addAsResource("db/flyway/" + migration);
                    }
                })
                .overrideConfigKey("quarkus.datasource.db-kind", "postgresql")
                .overrideConfigKey(OutboxRows.DEV_SERVICES_IMAGE_KEY, OutboxRows.POSTGRES_IMAGE);
        return app.overrideConfigKey("quarkus.flyway.migrate-at-start", "true")
                .overrideConfigKey("quarkus.flyway.locations", "db/flyway");
    }

    static QuarkusExtensionTest liquibase(Class<?>... classes) {
        return base(classes)
                .overrideConfigKey("quarkus.liquibase.migrate-at-start", "true")
                .overrideConfigKey("quarkus.liquibase.change-log", "db/changelog.xml");
    }
}
