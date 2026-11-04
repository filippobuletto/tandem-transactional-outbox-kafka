package io.quarkiverse.tandem.producer.runtime;

import java.util.Optional;

import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.codingful.tandem.core.exception.TandemConfigurationException;
import com.codingful.tandem.core.port.OutboxRepository;
import com.codingful.tandem.core.port.TracePropagator;
import com.codingful.tandem.jdbc.BucketCountGuard;
import com.codingful.tandem.jdbc.JdbcOutboxRepository;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Opens the outbox for writes at runtime init, once every migration has run (LLD-quarkus §7).
 */
@Recorder
public class TandemProducerRecorder {

    public static final String DATASOURCE_KEY = "tandem.outbox.datasource";
    public static final String DATASOURCE_CHANGED = "Tandem outbox datasource is fixed at build time and differs at"
            + " runtime; rebuild the application to change it";
    public static final String OTEL_ADAPTER_MISSING = "Tandem trace capture records the correlation id only, the"
            + " OpenTelemetry adapter is missing";
    public static final String DATASOURCE_INACTIVE = "Tandem outbox datasource is not active";

    private static final Logger LOG = Logger.getLogger(TandemProducerRecorder.class);

    private final RuntimeValue<TandemOutboxRuntimeConfig> outboxConfig;
    private final RuntimeValue<TandemTracingRuntimeConfig> tracingConfig;

    /**
     * Created by Quarkus with the runtime configuration.
     *
     * @param outboxConfig the {@code tandem.outbox} keys
     * @param tracingConfig the {@code tandem.tracing} keys
     */
    public TandemProducerRecorder(RuntimeValue<TandemOutboxRuntimeConfig> outboxConfig,
            RuntimeValue<TandemTracingRuntimeConfig> tracingConfig) {
        this.outboxConfig = outboxConfig;
        this.tracingConfig = tracingConfig;
    }

    /**
     * Checks the datasource and the bucket count, then opens the repository adapter for writes. Any
     * exception stops the application's startup.
     *
     * @param dataSourceName the datasource fixed at build time
     * @param otelAdapterMissing whether the build saw OpenTelemetry without {@code tandem-tracing-otel}
     */
    public void openOutbox(String dataSourceName, boolean otelAdapterMissing) {
        requireUnchangedDataSource(dataSourceName);
        requireNoTransaction(dataSourceName);
        AgroalDataSource dataSource = AgroalDataSourceUtil.dataSourceIfActive(dataSourceName)
                .orElseThrow(() -> new TandemConfigurationException(
                        DATASOURCE_INACTIVE + " dataSource:" + dataSourceName));
        TandemOutboxRuntimeConfig outbox = outboxConfig.getValue();
        BucketCountGuard.check(dataSource, outbox.bucketCount());

        try (InstanceHandle<TracePropagator> tracePropagator = Arc.container().instance(TracePropagator.class)) {
            OutboxRepository jdbcRepository = JdbcOutboxRepository.builder(dataSource, outbox.bucketCount())
                    .wakeup(outbox.wakeup())
                    .tracePropagator(tracePropagator.get())
                    .build();
            ((TandemOutboxRepository) Arc.container().instance(OutboxRepository.class).get())
                    .openForWrites(jdbcRepository);
            boolean tracing = tracingConfig.getValue().enabled();
            LOG.infof("Tandem outbox open for writes dataSource:%s, bucketCount:%d, wakeup:%s, tracing:%s",
                    dataSourceName, outbox.bucketCount(), outbox.wakeup(), tracing);
            if (otelAdapterMissing && tracing && tracePropagator.getBean().isDefaultBean()) {
                LOG.warn(OTEL_ADAPTER_MISSING + " artifact:com.codingful:tandem-tracing-otel");
            }
        }
    }

    // A build-time key: Quarkus would ignore a runtime value without a word (HLD-quarkus §6.1).
    private static void requireUnchangedDataSource(String buildTimeName) {
        Optional<String> runtimeValue = ConfigProvider.getConfig().getOptionalValue(DATASOURCE_KEY, String.class);
        // MicroProfile Config reads an empty value as absent, so an empty key is the default datasource.
        if (runtimeValue.isEmpty() || runtimeValue.get().equals(buildTimeName)) {
            return;
        }
        throw new TandemConfigurationException(DATASOURCE_CHANGED + " key:" + DATASOURCE_KEY
                + ", buildTimeValue:" + buildTimeName + ", runtimeValue:" + runtimeValue.get());
    }

    // The guard commits a transaction of its own, so it must never run inside one (HLD-quarkus §5.2).
    // Runtime init never runs in a JTA transaction: a guard against a change in Quarkus's startup.
    private static void requireNoTransaction(String dataSourceName) {
        int status;
        try {
            status = Arc.container().instance(TransactionManager.class).get().getStatus();
        } catch (SystemException e) {
            throw new TandemConfigurationException(
                    "JTA transaction status unavailable at Tandem startup dataSource:" + dataSourceName, e);
        }
        if (status != Status.STATUS_NO_TRANSACTION) {
            throw new TandemConfigurationException("A JTA transaction is active at Tandem startup; the bucket-count"
                    + " check must run outside any transaction dataSource:" + dataSourceName
                    + ", transactionStatus:" + status);
        }
    }
}
