package io.quarkiverse.tandem.producer.runtime;

import com.codingful.tandem.jdbc.Wakeup;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * The runtime part of the {@code tandem.outbox} keys (LLD-quarkus §8.2), with the meaning and defaults
 * every Tandem process gives them.
 */
@ConfigMapping(prefix = "tandem.outbox")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface TandemOutboxRuntimeConfig {

    /**
     * The number of buckets the outbox rows are spread over. Must be identical in every writer and in
     * the relay: the first process to start records it in the database, and a process configured with
     * another value refuses to start.
     */
    @WithDefault("256")
    int bucketCount();

    /**
     * How the relay learns that rows were written: {@code none} (it polls) or {@code pg-notify} (each
     * insert notifies it through PostgreSQL). A setting of the writer and relay pair; set the same value
     * in both.
     */
    @WithDefault("none")
    Wakeup wakeup();
}
