package io.quarkiverse.tandem.it;

/**
 * A payload type used only by the outbox and not registered for reflection: in native mode nothing else
 * registers it, so serialization fails (HLD-quarkus §5.7).
 */
public class UnregisteredPayload {

    private final String orderId;

    public UnregisteredPayload(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
