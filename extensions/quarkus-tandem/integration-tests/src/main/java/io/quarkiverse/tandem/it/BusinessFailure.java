package io.quarkiverse.tandem.it;

/** The business change failing after the event was recorded. */
public final class BusinessFailure extends RuntimeException {

    public BusinessFailure() {
        super("The business change failed");
    }
}
