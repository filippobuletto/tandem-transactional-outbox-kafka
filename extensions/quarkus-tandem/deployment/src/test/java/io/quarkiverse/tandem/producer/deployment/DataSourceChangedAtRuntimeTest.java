package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.codingful.tandem.core.exception.TandemConfigurationException;

import io.quarkiverse.tandem.producer.runtime.TandemProducerRecorder;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The outbox datasource is fixed at build time; a different value at runtime stops the startup instead of
 * being ignored (HLD-quarkus §6.1).
 */
class DataSourceChangedAtRuntimeTest {

    private static final String RUNTIME_DATASOURCE = "elsewhere";

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.liquibase()
            .overrideRuntimeConfigKey(TandemProducerRecorder.DATASOURCE_KEY, RUNTIME_DATASOURCE)
            .assertException(failure -> assertThat(failure)
                    .isInstanceOf(TandemConfigurationException.class)
                    .hasMessageStartingWith(TandemProducerRecorder.DATASOURCE_CHANGED)
                    .hasMessageContaining(RUNTIME_DATASOURCE));

    @Test
    void GIVEN_another_outbox_datasource_at_runtime_WHEN_the_application_starts_THEN_the_startup_fails() {
        fail("The startup should have failed");
    }
}
