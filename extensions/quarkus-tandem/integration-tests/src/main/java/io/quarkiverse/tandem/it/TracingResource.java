package io.quarkiverse.tandem.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.slf4j.MDC;

import io.opentelemetry.api.trace.Span;
import io.quarkiverse.tandem.producer.TransactionalOutboxTemplate;

/**
 * Writes an event inside the server span {@code quarkus-opentelemetry} opens, with a correlation id in the
 * MDC, and returns both so the test can find them on the row (LLD-quarkus §13.2).
 */
@Path("/tracing")
public class TracingResource {

    public static final String MDC_KEY = "correlationId";

    /** What the request carried when it wrote. */
    public record Written(String traceId, String correlationId) {
    }

    @Inject
    TransactionalOutboxTemplate template;

    @POST
    @Path("/{orderId}/{correlationId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Written write(@PathParam("orderId") String orderId, @PathParam("correlationId") String correlationId) {
        MDC.put(MDC_KEY, correlationId);
        try {
            template.executeWithoutResult(
                    collector -> collector.record("Order", orderId, 1, new OrderPlaced(orderId)));
            return new Written(Span.current().getSpanContext().getTraceId(), correlationId);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
