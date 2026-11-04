package io.quarkiverse.tandem.it;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Runs a tier scenario and reports what the database holds afterwards, so the tests run unchanged against
 * the native executable (LLD-quarkus §13.1). The scenario's exception is reported, never rethrown.
 */
@Path("/scenarios")
public class ScenarioResource {

    /** The other order a scenario may write, under its own id. */
    public static final String OTHER = "-other";

    /**
     * What the scenario left: the simple name of the exception that reached this resource ({@code null} when
     * none did), whether each order committed, and each order's outbox rows.
     */
    public record Outcome(String failure, boolean orderCommitted, List<OutboxRows.Row> rows,
            boolean otherOrderCommitted, List<OutboxRows.Row> otherRows) {
    }

    @Inject
    PlainTierScenarios plain;

    @Inject
    TemplateTierScenarios template;

    @Inject
    OrderingScenarios ordering;

    @Inject
    OutboxRows outboxRows;

    @POST
    @Path("/plain/{scenario}/{orderId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Outcome plain(@PathParam("scenario") String scenario, @PathParam("orderId") String orderId) {
        return run(orderId, switch (scenario) {
            case "commit" -> () -> plain.placeAndRecord(orderId);
            case "business-failure" -> () -> plain.placeRecordAndFail(orderId);
            case "collision" -> () -> plain.placeAndRecordTwice(orderId, false);
            case "collision-caught" -> () -> plain.placeAndRecordTwice(orderId, true);
            case "no-transaction" -> () -> plain.recordWithoutTransaction(orderId);
            case "empty-batch-no-transaction" -> plain::recordEmptyBatchWithoutTransaction;
            case "rollback-only" -> () -> plain.markRollbackOnlyThenRecord(orderId);
            default -> throw new NotFoundException(scenario);
        });
    }

    @POST
    @Path("/ordering/{scenario}/{orderId}")
    @Produces(MediaType.APPLICATION_JSON)
    public OrderingScenarios.Ordering ordering(@PathParam("scenario") String scenario,
            @PathParam("orderId") String orderId) throws Exception {
        return switch (scenario) {
            case "unserialized" -> ordering.unserialized(orderId);
            case "locked-write" -> ordering.lockedWrite(orderId);
            default -> throw new NotFoundException(scenario);
        };
    }

    @POST
    @Path("/template/{scenario}/{orderId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Outcome template(@PathParam("scenario") String scenario, @PathParam("orderId") String orderId) {
        return run(orderId, switch (scenario) {
            case "started-commit" -> () -> template.placeInStartedTransaction(orderId);
            case "recording-order" -> () -> template.recordOutOfSeqOrder(orderId);
            case "started-work-fails" -> () -> template.failInStartedTransaction(orderId);
            case "started-serialization-failure" -> () -> template.unserializableInStartedTransaction(orderId);
            case "serialization-failure-caught-inside" -> () -> template.unserializableCaughtInsideTheWork(orderId);
            case "joined-commit" -> () -> template.placeInJoinedTransaction(orderId);
            case "joined-caller-fails" -> () -> template.placeInJoinedTransactionAndFail(orderId);
            case "joined-serialization-failure-caught" ->
                () -> template.unserializableCaughtAroundJoinedTemplate(orderId);
            case "requires-new" -> () -> template.placeThenOtherInNewTransactionAndFail(orderId, orderId + OTHER);
            default -> throw new NotFoundException(scenario);
        });
    }

    private Outcome run(String orderId, Scenario scenario) {
        String failure = null;
        try {
            scenario.run();
        } catch (Exception e) {
            failure = e.getClass().getSimpleName();
        }
        String otherOrderId = orderId + OTHER;
        return new Outcome(failure, outboxRows.orderCommitted(orderId), outboxRows.of(orderId),
                outboxRows.orderCommitted(otherOrderId), outboxRows.of(otherOrderId));
    }

    @FunctionalInterface
    interface Scenario {
        void run() throws Exception;
    }
}
