package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.codingful.tandem.core.port.OutboxRepository;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * {@code tandem.outbox.datasource} selects a named datasource, and the rows go there, not to the default
 * one (HLD-quarkus §5.1, §6.1).
 */
class NamedDataSourceTest {

    private static final String OUTBOX_DATASOURCE = "outbox";
    private static final String ORDER_ID = "order-1";

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.base(Scenarios.class)
            .overrideConfigKey("quarkus.datasource." + OUTBOX_DATASOURCE + ".db-kind", "postgresql")
            .overrideConfigKey("quarkus.datasource." + OUTBOX_DATASOURCE + ".devservices.image-name",
                    OutboxRows.POSTGRES_IMAGE)
            .overrideConfigKey("quarkus.liquibase." + OUTBOX_DATASOURCE + ".migrate-at-start", "true")
            .overrideConfigKey("quarkus.liquibase." + OUTBOX_DATASOURCE + ".change-log", "db/changelog.xml")
            .overrideConfigKey("tandem.outbox.datasource", OUTBOX_DATASOURCE);

    @Inject
    Scenarios scenarios;

    @Inject
    @io.quarkus.agroal.DataSource(OUTBOX_DATASOURCE)
    DataSource outboxDataSource;

    @Inject
    DataSource defaultDataSource;

    @Test
    void GIVEN_a_named_outbox_datasource_WHEN_an_event_is_written_THEN_it_lands_on_that_datasource() {
        scenarios.record(ORDER_ID);

        assertThat(OutboxRows.all(outboxDataSource)).extracting(OutboxRows.Row::aggregateId).containsExactly(ORDER_ID);
        assertThat(OutboxRows.hasOutboxTable(defaultDataSource)).isFalse();
    }

    @ApplicationScoped
    public static class Scenarios {

        @Inject
        OutboxRepository outbox;

        @Transactional
        public void record(String orderId) {
            outbox.insert(Orders.placed(orderId, 1));
        }
    }
}
