package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import jakarta.inject.Singleton;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.codingful.tandem.core.exception.OutboxInsertException;
import com.codingful.tandem.core.port.OutboxRepository;

import io.quarkiverse.tandem.producer.runtime.TandemOutboxRepository;
import io.quarkus.arc.Arc;
import io.quarkus.flyway.FlywayConfigurationCustomizer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A write that arrives before startup has checked the bucket count is refused (HLD-quarkus §5.2): here, from
 * a Flyway callback that runs once the migrations are done, before the outbox is opened.
 */
class EarlyWriteTest {

    static final AtomicReference<Throwable> EARLY_WRITE_FAILURE = new AtomicReference<>();

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.flyway(WriteAfterMigrate.class)
            .overrideConfigKey("quarkus.datasource.devservices.db-name", "early_write");

    @jakarta.inject.Inject
    DataSource dataSource;

    @Test
    void GIVEN_a_write_from_a_migration_callback_WHEN_the_application_starts_THEN_it_is_refused_and_nothing_is_written() {
        assertThat(EARLY_WRITE_FAILURE.get())
                .isInstanceOf(OutboxInsertException.class)
                .hasMessage(TandemOutboxRepository.NOT_OPEN);
        assertThat(OutboxRows.all(dataSource)).isEmpty();
    }

    @Singleton
    public static class WriteAfterMigrate implements FlywayConfigurationCustomizer {

        @Override
        public void customize(FluentConfiguration configuration) {
            configuration.callbacks(new Callback() {
                @Override
                public boolean supports(Event event, Context context) {
                    return event == Event.AFTER_MIGRATE;
                }

                @Override
                public boolean canHandleInTransaction(Event event, Context context) {
                    return true;
                }

                @Override
                public void handle(Event event, Context context) {
                    try {
                        Arc.container().instance(OutboxRepository.class).get().insert(Orders.placed("early", 1));
                    } catch (RuntimeException e) {
                        EARLY_WRITE_FAILURE.set(e);
                    }
                }

                @Override
                public String getCallbackName() {
                    return "tandem-early-write";
                }
            });
        }
    }
}
