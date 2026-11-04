package io.quarkiverse.tandem.producer.deployment;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.codingful.tandem.core.OutboxMessage;

/**
 * The business side of the tests: an {@code orders} table on the outbox's datasource, written in the same
 * JTA transaction as the outbox rows.
 */
@ApplicationScoped
public class Orders {

    static final String AGGREGATE_TYPE = "Order";

    @Inject
    DataSource dataSource;

    void place(String orderId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO orders (id, status) VALUES (?, 'PLACED')")) {
            statement.setString(1, orderId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    boolean exists(String orderId) {
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

    static OutboxMessage placed(String orderId, long seq) {
        return OutboxMessage.builder()
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(orderId)
                .payload(payloadOf(orderId))
                .seq(seq)
                .build();
    }

    static byte[] payloadOf(String orderId) {
        return ("{\"orderId\": \"" + orderId + "\"}").getBytes(StandardCharsets.UTF_8);
    }
}
