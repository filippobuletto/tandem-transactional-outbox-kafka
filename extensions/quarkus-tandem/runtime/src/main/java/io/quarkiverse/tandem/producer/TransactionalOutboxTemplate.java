package io.quarkiverse.tandem.producer;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The template tier (HLD-quarkus §5.4, LLD-quarkus §5.1): wraps <em>transaction + collect + insert</em>
 * in one call. The unit of work receives an {@link OutboxCollector}, records the rows to emit, and the
 * template inserts them in the same JTA transaction, so the business change and the outbox rows commit
 * or roll back together. Its signatures are those of {@code tandem-spring-producer}'s template.
 *
 * <p>
 * <b>Provided:</b> the extension implements this interface and the application calls it. A minor
 * version may add abstract methods, so an application implementing it follows the extension's minor
 * versions.
 */
public interface TransactionalOutboxTemplate {

    /**
     * Run {@code work} in the caller's JTA transaction, or in a new one when none is active, insert
     * everything it recorded into the outbox in that transaction, and return the work's result.
     * A throwable from {@code work} or from the insert rolls back a transaction the template started,
     * or marks a joined one rollback-only, and is rethrown: catching it around this call does not
     * bring the commit back (HLD-quarkus §5.3).
     *
     * @param work the unit of work; records outbox rows via the supplied {@link OutboxCollector}
     * @param <T> the work's result type
     * @return the value {@code work} returned
     * @throws NullPointerException if {@code work} is null
     * @throws com.codingful.tandem.core.exception.OutboxInsertException if the insert is refused or fails
     */
    <T> T execute(Function<OutboxCollector, T> work);

    /**
     * As {@link #execute(Function)} for a unit of work that returns no value.
     *
     * @param work the unit of work; records outbox rows via the supplied {@link OutboxCollector}
     * @throws NullPointerException if {@code work} is null
     * @throws com.codingful.tandem.core.exception.OutboxInsertException if the insert is refused or fails
     */
    default void executeWithoutResult(Consumer<OutboxCollector> work) {
        Objects.requireNonNull(work, "work");
        execute(collector -> {
            work.accept(collector);
            return null;
        });
    }
}
