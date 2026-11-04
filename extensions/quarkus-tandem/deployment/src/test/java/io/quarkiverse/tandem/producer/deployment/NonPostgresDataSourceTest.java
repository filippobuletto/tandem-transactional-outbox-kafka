package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusExtensionTest;

class NonPostgresDataSourceTest {

    @RegisterExtension
    static final QuarkusExtensionTest APP = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .assertException(failure -> assertThat(failure)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageStartingWith(TandemProducerProcessor.NOT_POSTGRESQL));

    @Test
    void GIVEN_a_tandem_datasource_that_is_not_PostgreSQL_WHEN_the_application_is_built_THEN_the_build_fails() {
        fail("The build should have failed");
    }
}
