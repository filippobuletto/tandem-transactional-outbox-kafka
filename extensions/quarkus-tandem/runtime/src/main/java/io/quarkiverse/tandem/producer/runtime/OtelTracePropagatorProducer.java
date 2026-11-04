package io.quarkiverse.tandem.producer.runtime;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import com.codingful.tandem.core.port.TracePropagator;
import com.codingful.tandem.tracing.otel.OtelTracePropagator;

import io.opentelemetry.api.OpenTelemetry;

/**
 * Registered only with {@code quarkus-opentelemetry} and {@code tandem-tracing-otel} both present: its
 * signature names {@link OpenTelemetry} (LLD-quarkus §6.2).
 */
@Singleton
final class OtelTracePropagatorProducer {

    @Produces
    @Singleton
    @TandemTraceCapture
    TracePropagator tandemOtelTracePropagator(OpenTelemetry openTelemetry) {
        return new OtelTracePropagator(openTelemetry);
    }
}
