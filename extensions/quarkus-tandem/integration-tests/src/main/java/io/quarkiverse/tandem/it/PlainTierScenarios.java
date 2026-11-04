package io.quarkiverse.tandem.it;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;

import com.codingful.tandem.core.exception.TandemException;
import com.codingful.tandem.core.port.OutboxRepository;

/** The plain tier: the application injects the repository and inserts in its own transaction (HLD-quarkus §5.3). */
@ApplicationScoped
public class PlainTierScenarios {

    @Inject
    OutboxRepository outbox;

    @Inject
    Orders orders;

    @Inject
    TransactionManager transactionManager;

    @Transactional
    public void placeAndRecord(String orderId) {
        orders.place(orderId);
        outbox.insert(Orders.placed(orderId, 1));
    }

    @Transactional
    public void placeRecordAndFail(String orderId) {
        placeAndRecord(orderId);
        throw new BusinessFailure();
    }

    @Transactional
    public void placeAndRecordTwice(String orderId, boolean catchTheFailure) {
        placeAndRecord(orderId);
        try {
            outbox.insert(Orders.placed(orderId, 1));
        } catch (TandemException e) {
            if (!catchTheFailure) {
                throw e;
            }
        }
    }

    public void recordWithoutTransaction(String orderId) {
        outbox.insert(Orders.placed(orderId, 1));
    }

    public void recordEmptyBatchWithoutTransaction() {
        outbox.insertAll(List.of());
    }

    @Transactional
    public void markRollbackOnlyThenRecord(String orderId) throws SystemException {
        transactionManager.setRollbackOnly();
        outbox.insert(Orders.placed(orderId, 1));
    }
}
