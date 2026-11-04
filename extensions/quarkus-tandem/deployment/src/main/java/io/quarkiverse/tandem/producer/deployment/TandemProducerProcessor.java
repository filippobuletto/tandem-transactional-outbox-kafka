package io.quarkiverse.tandem.producer.deployment;

import java.util.List;
import java.util.stream.Collectors;

import org.jboss.jandex.DotName;

import io.quarkiverse.tandem.producer.runtime.TandemProducerRecorder;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.agroal.spi.JdbcDataSourceSchemaReadyBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.datasource.common.runtime.DatabaseKind;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class TandemProducerProcessor {

    static final String NOT_CONFIGURED = "Tandem outbox datasource is not a configured JDBC datasource";
    static final String NOT_POSTGRESQL = "Tandem outbox datasource must be PostgreSQL";
    static final String TRANSACTIONS_DISABLED = "Tandem outbox datasource has JTA transactions disabled, every"
            + " insert would autocommit apart from the business change";

    private static final String FEATURE = "tandem-producer";
    private static final String OTEL_ADAPTER = "com.codingful.tandem.tracing.otel.OtelTracePropagator";
    private static final String TRACE_PROPAGATOR = "com.codingful.tandem.core.port.TracePropagator";
    private static final String RUNTIME_PACKAGE = "io.quarkiverse.tandem.producer.runtime.";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    // By class name: the deployment module never loads the runtime classes (LLD-quarkus §6.2).
    // A class whose signatures name an optional library is registered only when that library is present.
    @BuildStep
    void beans(Capabilities capabilities, BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.unremovableOf(RUNTIME_PACKAGE + "TandemOutboxRepository"));
        beans.produce(new AdditionalBeanBuildItem(RUNTIME_PACKAGE + "DefaultTransactionalOutboxTemplate"));
        beans.produce(AdditionalBeanBuildItem.unremovableOf(RUNTIME_PACKAGE + "TracePropagatorProducer"));
        if (capabilities.isPresent(Capability.JACKSON)) {
            beans.produce(new AdditionalBeanBuildItem(RUNTIME_PACKAGE + "JacksonPayloadSerializerProducer"));
        }
        if (capabilities.isPresent(Capability.OPENTELEMETRY_TRACER) && otelAdapterPresent()) {
            beans.produce(new AdditionalBeanBuildItem(RUNTIME_PACKAGE + "OtelTracePropagatorProducer"));
        }
    }

    // The recorder looks the TracePropagator up programmatically, which ArC cannot see: an application's
    // replacement bean would otherwise be removed as unused, and the default one used instead.
    @BuildStep
    UnremovableBeanBuildItem tracePropagatorBeans() {
        return UnremovableBeanBuildItem.beanTypes(DotName.createSimple(TRACE_PROPAGATOR));
    }

    @BuildStep
    TandemDataSourceBuildItem outboxDataSource(TandemOutboxBuildTimeConfig config,
            List<JdbcDataSourceBuildItem> dataSources) {
        String name = config.datasource().orElse(DataSourceUtil.DEFAULT_DATASOURCE_NAME);
        JdbcDataSourceBuildItem dataSource = dataSources.stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new ConfigurationException(
                        NOT_CONFIGURED + " dataSource:" + name
                                + ", configured:" + dataSources.stream().map(JdbcDataSourceBuildItem::getName)
                                        .sorted().collect(Collectors.joining(",", "[", "]"))));
        if (!DatabaseKind.isPostgreSQL(dataSource.getDbKind())) {
            throw new ConfigurationException(NOT_POSTGRESQL + " dataSource:" + name
                    + ", dbKind:" + dataSource.getDbKind());
        }
        if (!dataSource.isTransactionIntegrationEnabled()) {
            throw new ConfigurationException(TRANSACTIONS_DISABLED + " dataSource:" + name);
        }
        return new TandemDataSourceBuildItem(name);
    }

    // Each parameter is there for its ordering effect (LLD-quarkus §6.4): the schema-ready list makes the
    // step run after every migration at start, and the returned item completes before StartupEvent.
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(BeanContainerBuildItem.class)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    ServiceStartBuildItem openOutbox(TandemProducerRecorder recorder, TandemDataSourceBuildItem dataSource,
            Capabilities capabilities, List<JdbcDataSourceSchemaReadyBuildItem> schemaReady) {
        boolean otelAdapterMissing = capabilities.isPresent(Capability.OPENTELEMETRY_TRACER) && !otelAdapterPresent();
        recorder.openOutbox(dataSource.name(), otelAdapterMissing);
        return new ServiceStartBuildItem(FEATURE);
    }

    private static boolean otelAdapterPresent() {
        return QuarkusClassLoader.isClassPresentAtRuntime(OTEL_ADAPTER);
    }
}
