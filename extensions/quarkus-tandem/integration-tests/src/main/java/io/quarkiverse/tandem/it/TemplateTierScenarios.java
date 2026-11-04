package io.quarkiverse.tandem.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.codingful.tandem.core.exception.PayloadSerializationException;

import io.quarkiverse.tandem.producer.TransactionalOutboxTemplate;

/** The template tier, started and joined (HLD-quarkus §5.3, §5.4). */
@ApplicationScoped
public class TemplateTierScenarios {

    @Inject
    TransactionalOutboxTemplate template;

    @Inject
    Orders orders;

    @Inject
    NewTransaction newTransaction;

    public void placeInStartedTransaction(String orderId) {
        template.executeWithoutResult(collector -> {
            orders.place(orderId);
            collector.record(Orders.AGGREGATE_TYPE, orderId, 1, new OrderPlaced(orderId));
        });
    }

    // Recorded out of seq order, so the rows' order shows the recording order.
    public void recordOutOfSeqOrder(String orderId) {
        template.executeWithoutResult(collector -> {
            collector.add(Orders.placed(orderId, 3));
            collector.record(Orders.AGGREGATE_TYPE, orderId, 1, new OrderPlaced(orderId));
            collector.add(Orders.placed(orderId, 2));
        });
    }

    public void failInStartedTransaction(String orderId) {
        template.executeWithoutResult(collector -> {
            orders.place(orderId);
            collector.add(Orders.placed(orderId, 1));
            throw new BusinessFailure();
        });
    }

    public void unserializableInStartedTransaction(String orderId) {
        template.executeWithoutResult(collector -> {
            orders.place(orderId);
            collector.record(Orders.AGGREGATE_TYPE, orderId, 1, new Unserializable());
        });
    }

    public void unserializableCaughtInsideTheWork(String orderId) {
        template.executeWithoutResult(collector -> {
            orders.place(orderId);
            try {
                collector.record(Orders.AGGREGATE_TYPE, orderId, 1, new Unserializable());
            } catch (PayloadSerializationException expected) {
                collector.add(Orders.placed(orderId, 1));
            }
        });
    }

    @Transactional
    public void placeInJoinedTransaction(String orderId) {
        placeInStartedTransaction(orderId);
    }

    @Transactional
    public void placeInJoinedTransactionAndFail(String orderId) {
        placeInStartedTransaction(orderId);
        throw new BusinessFailure();
    }

    // Where Quarkus differs from Spring: the outer commit of a transaction the template marked
    // rollback-only rolls back without throwing.
    @Transactional
    public void unserializableCaughtAroundJoinedTemplate(String orderId) {
        try {
            unserializableInStartedTransaction(orderId);
        } catch (PayloadSerializationException expected) {
            // the caller goes on without the event; the template has already marked the transaction
        }
    }

    @Transactional
    public void placeThenOtherInNewTransactionAndFail(String orderId, String otherOrderId) {
        orders.place(orderId);
        newTransaction.place(otherOrderId);
        throw new BusinessFailure();
    }

    @ApplicationScoped
    public static class NewTransaction {

        @Inject
        TransactionalOutboxTemplate template;

        @Inject
        Orders orders;

        @Transactional(Transactional.TxType.REQUIRES_NEW)
        public void place(String orderId) {
            template.executeWithoutResult(collector -> {
                orders.place(orderId);
                collector.add(Orders.placed(orderId, 1));
            });
        }
    }
}
