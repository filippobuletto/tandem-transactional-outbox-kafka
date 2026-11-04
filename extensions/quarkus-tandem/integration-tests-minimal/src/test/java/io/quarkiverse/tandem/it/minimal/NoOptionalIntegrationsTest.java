package io.quarkiverse.tandem.it.minimal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * An application with no optional integration on its classpath starts, passing the startup step without a
 * schema-ready item, refuses an object payload at the call, and writes a pre-built message (HLD-quarkus §12).
 */
@QuarkusTest
@QuarkusTestResource(value = TandemSchemaDatabase.class, restrictToAnnotatedClass = true)
class NoOptionalIntegrationsTest {

    record Row(String aggregateId, String payload) {
    }

    @Test
    void GIVEN_no_serializer_and_no_migration_extension_WHEN_the_application_starts_and_writes_THEN_only_the_pre_built_message_is_written()
            throws SQLException {
        assertThat(rows()).containsExactly(new Row(WriteAtStartup.AGGREGATE_ID, WriteAtStartup.PAYLOAD));
    }

    private static List<Row> rows() throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Connection connection = TandemSchemaDatabase.connect();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT aggregate_id, payload::text FROM tandem_outbox")) {
            while (result.next()) {
                rows.add(new Row(result.getString(1), result.getString(2)));
            }
        }
        return rows;
    }
}
