package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.codingful.tandem.core.exception.TandemConfigurationException;

import io.quarkiverse.tandem.producer.runtime.TandemProducerRecorder;
import io.quarkus.test.QuarkusExtensionTest;

class InactiveDataSourceTest {

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.base()
            .overrideRuntimeConfigKey("quarkus.datasource.active", "false")
            .assertException(failure -> assertThat(failure)
                    .isInstanceOf(TandemConfigurationException.class)
                    .hasMessageStartingWith(TandemProducerRecorder.DATASOURCE_INACTIVE));

    @Test
    void GIVEN_an_outbox_datasource_deactivated_at_runtime_WHEN_the_application_starts_THEN_the_startup_fails() {
        fail("The startup should have failed");
    }
}
