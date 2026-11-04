package io.quarkiverse.tandem.producer.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * The {@code tandem.tracing} keys the write side reads (LLD-quarkus §8.2, §10).
 */
@ConfigMapping(prefix = "tandem.tracing")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface TandemTracingRuntimeConfig {

    /**
     * Whether each insert records the current trace context (with {@code quarkus-opentelemetry} and
     * {@code com.codingful:tandem-tracing-otel}) and the correlation id on the outbox row. An
     * application {@code TracePropagator} bean is used whatever this says.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * The logging MDC key the correlation id is read from.
     */
    @WithDefault("correlationId")
    String correlationIdMdcKey();

    /**
     * The relay's switch for its publish span. The write side ignores it; it is declared so that a
     * configuration shared with the relay starts the application as it is.
     */
    Optional<Boolean> publishSpan();
}
