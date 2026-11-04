package io.quarkiverse.tandem.producer.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.codingful.tandem.core.exception.TandemException;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * Without a migration extension the guard checks whatever schema the database holds: on an empty one it
 * stops the startup with the library's error, whose cause names the table (HLD-quarkus §5.2).
 */
class MissingSchemaTest {

    @RegisterExtension
    static final QuarkusExtensionTest APP = TestApps.base()
            .overrideConfigKey("quarkus.datasource.devservices.db-name", "missing_schema")
            .assertException(failure -> assertThat(failure)
                    .isInstanceOf(TandemException.class)
                    .cause().hasMessageContaining("tandem_meta"));

    @Test
    void GIVEN_a_database_without_the_tandem_schema_WHEN_the_application_starts_THEN_the_startup_fails() {
        fail("The startup should have failed");
    }
}
