package io.quarkiverse.tandem.producer.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

/**
 * The build-time part of the {@code tandem.outbox} keys (LLD-quarkus §8.1).
 */
@ConfigMapping(prefix = "tandem.outbox")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface TandemOutboxBuildTimeConfig {

    /**
     * The Agroal datasource the outbox rows are written to, the default datasource when unset. The
     * business tables belong on the same datasource: only then do the business change and the outbox
     * row commit together. Fixed at build time; the application refuses to start when the runtime
     * value differs.
     */
    Optional<String> datasource();
}
