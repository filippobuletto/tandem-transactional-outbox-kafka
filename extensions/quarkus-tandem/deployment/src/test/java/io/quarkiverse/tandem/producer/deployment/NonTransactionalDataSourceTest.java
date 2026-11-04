package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusExtensionTest;

class NonTransactionalDataSourceTest {

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.base()
            .overrideConfigKey("quarkus.datasource.jdbc.transactions", "disabled")
            .assertException(failure -> assertThat(failure)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageStartingWith(TandemProducerProcessor.TRANSACTIONS_DISABLED));

    @Test
    void GIVEN_a_tandem_datasource_with_JTA_transactions_disabled_WHEN_the_application_is_built_THEN_the_build_fails() {
        fail("The build should have failed");
    }
}
