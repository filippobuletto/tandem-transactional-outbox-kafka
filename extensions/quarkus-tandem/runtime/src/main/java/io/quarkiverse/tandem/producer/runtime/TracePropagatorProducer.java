package io.quarkiverse.tandem.producer.runtime;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import com.codingful.tandem.core.port.TracePropagator;

import io.quarkus.arc.DefaultBean;

/**
 * The trace capture the extension uses unless the application supplies a {@link TracePropagator}
 * (LLD-quarkus §10.1): nothing when tracing is off, else the OpenTelemetry context when the adapter is
 * present, then the correlation id.
 */
@Singleton
final class TracePropagatorProducer {

    @Produces
    @Singleton
    @DefaultBean
    TracePropagator tandemTracePropagator(TandemTracingRuntimeConfig config,
            @TandemTraceCapture Instance<TracePropagator> openTelemetry) {
        if (!config.enabled()) {
            return TracePropagator.NOOP;
        }
        TracePropagator correlationId = new MdcCorrelationTracePropagator(config.correlationIdMdcKey());
        return openTelemetry.isResolvable()
                ? TracePropagator.composite(openTelemetry.get(), correlationId)
                : correlationId;
    }
}
