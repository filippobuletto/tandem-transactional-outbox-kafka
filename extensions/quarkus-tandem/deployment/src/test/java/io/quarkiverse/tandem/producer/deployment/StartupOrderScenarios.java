package io.quarkiverse.tandem.producer.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.codingful.tandem.core.port.OutboxRepository;

/** A write once the application has started, shared by the startup tests. */
@ApplicationScoped
public class StartupOrderScenarios {

    @Inject
    OutboxRepository outbox;

    @Transactional
    public void record(String orderId) {
        outbox.insert(Orders.placed(orderId, 1));
    }
}
