package io.quarkiverse.tandem.it;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** An aggregate's outbox rows over HTTP, so the tests assert on the database from a native executable too. */
@Path("/rows")
public class OutboxRowsResource {

    @Inject
    OutboxRows outboxRows;

    @GET
    @Path("/{aggregateId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<OutboxRows.Row> of(@PathParam("aggregateId") String aggregateId) {
        return outboxRows.of(aggregateId);
    }
}
