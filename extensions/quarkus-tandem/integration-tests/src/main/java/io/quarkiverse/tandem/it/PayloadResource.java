package io.quarkiverse.tandem.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import com.codingful.tandem.core.exception.PayloadSerializationException;

import io.quarkiverse.tandem.producer.TransactionalOutboxTemplate;

/** Records an object payload of a registered or an unregistered type (LLD-quarkus §13.2). */
@Path("/payloads")
public class PayloadResource {

    public static final int SERIALIZATION_FAILED = 422;

    @Inject
    TransactionalOutboxTemplate template;

    @POST
    @Path("/registered/{orderId}")
    public Response registered(@PathParam("orderId") String orderId) {
        return record(orderId, new OrderPlaced(orderId));
    }

    @POST
    @Path("/unregistered/{orderId}")
    public Response unregistered(@PathParam("orderId") String orderId) {
        return record(orderId, new UnregisteredPayload(orderId));
    }

    private Response record(String orderId, Object payload) {
        try {
            template.executeWithoutResult(collector -> collector.record("Order", orderId, 1, payload));
            return Response.noContent().build();
        } catch (PayloadSerializationException e) {
            return Response.status(SERIALIZATION_FAILED).build();
        }
    }
}
