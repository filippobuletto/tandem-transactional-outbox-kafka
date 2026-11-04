package io.quarkiverse.tandem.it;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The outbox rows an aggregate has, read with plain JDBC, so the tests assert on the database over HTTP and
 * run unchanged against the native executable. The schema's names appear here only (LLD-quarkus §13.3).
 */
@Path("/rows")
public class OutboxRowsResource {

    /** One row; the payload and headers as the stored JSON text. */
    public record Row(String aggregateId, long seq, String payload, String headers) {
    }

    @Inject
    DataSource dataSource;

    @GET
    @Path("/{aggregateId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Row> of(@PathParam("aggregateId") String aggregateId) throws SQLException {
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
        }
        return rows;
    }
}
