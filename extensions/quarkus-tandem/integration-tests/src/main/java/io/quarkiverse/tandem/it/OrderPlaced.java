package io.quarkiverse.tandem.it;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** A payload type used only by the outbox, registered for reflection as the guide asks (HLD-quarkus §5.7). */
@RegisterForReflection
public record OrderPlaced(String orderId) {
}
