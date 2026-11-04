package io.quarkiverse.tandem.producer.runtime;

import java.util.Collection;

import jakarta.enterprise.inject.Typed;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import com.codingful.tandem.core.OutboxMessage;
import com.codingful.tandem.core.exception.OutboxInsertException;
import com.codingful.tandem.core.port.OutboxRepository;

/**
 * The one insert path of every tier (HLD-quarkus §5.3, LLD-quarkus §4): refuses a write before startup
 * has checked the bucket count, and a write outside an active JTA transaction, then delegates to the
 * {@code JdbcOutboxRepository} the startup step built. Failures of the delegate reach the caller
 * unchanged, and the transaction is never marked rollback-only.
 */
@Singleton
@Typed(OutboxRepository.class)
public final class TandemOutboxRepository implements OutboxRepository {

    public static final String NOT_OPEN = "Outbox not open for writes: Tandem startup has not completed,"
            + " so the bucket count is not checked yet";
    public static final String NO_ACTIVE_TRANSACTION = "Outbox insert refused: no active JTA transaction,"
            + " the row would not commit with the business change";
    public static final String STATUS_UNAVAILABLE = "Outbox insert refused: JTA transaction status unavailable";

    private final TransactionManager transactionManager;
    private volatile OutboxRepository delegate; // null until the startup step opens the outbox

    TandemOutboxRepository(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    void openForWrites(OutboxRepository jdbcRepository) {
        this.delegate = jdbcRepository;
    }

    @Override
    public void insert(OutboxMessage message) {
        checked().insert(message);
    }

    @Override
    public void insertAll(Collection<OutboxMessage> messages) {
        checked().insertAll(messages);
    }

    private OutboxRepository checked() {
        OutboxRepository open = delegate;
        if (open == null) {
            throw new OutboxInsertException(NOT_OPEN);
        }
        int status;
        try {
            status = transactionManager.getStatus();
        } catch (SystemException e) {
            throw new OutboxInsertException(STATUS_UNAVAILABLE, e);
        }
        if (status != Status.STATUS_ACTIVE) {
            throw new OutboxInsertException(NO_ACTIVE_TRANSACTION + " transactionStatus:" + status);
        }
        return open;
    }
}
