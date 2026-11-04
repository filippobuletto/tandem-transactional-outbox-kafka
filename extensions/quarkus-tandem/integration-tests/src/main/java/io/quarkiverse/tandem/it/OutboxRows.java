package io.quarkiverse.tandem.it;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reads an aggregate's outbox rows with plain JDBC, outside any transaction. The schema's names appear here
 * only (LLD-quarkus §13.3).
 */
@ApplicationScoped
public class OutboxRows {

    /** One row; the payload and headers as the stored JSON text. */
    public record Row(String aggregateId, long seq, String payload, String headers) {
    }

    @Inject
    DataSource dataSource;

    public List<Row> of(String aggregateId) {
        List<Row> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT aggregate_id, seq, payload::text,"
                        + " headers::text FROM tandem_outbox WHERE aggregate_id = ? ORDER BY id")) {
            statement.setString(1, aggregateId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new Row(result.getString(1), result.getLong(2), result.getString(3), result.getString(4)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }

    public boolean orderCommitted(String orderId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM orders WHERE id = ?")) {
            statement.setString(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
