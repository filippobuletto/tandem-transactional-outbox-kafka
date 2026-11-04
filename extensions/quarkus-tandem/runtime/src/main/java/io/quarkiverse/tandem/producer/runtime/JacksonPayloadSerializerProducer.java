package io.quarkiverse.tandem.producer.runtime;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import com.codingful.tandem.core.port.PayloadSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.DefaultBean;

/**
 * Registered only with {@code quarkus-jackson}: its signature names {@link ObjectMapper}, which ArC
 * could not read without it (LLD-quarkus §6.2).
 */
@Singleton
final class JacksonPayloadSerializerProducer {

    @Produces
    @Singleton
    @DefaultBean
    PayloadSerializer tandemJacksonPayloadSerializer(ObjectMapper objectMapper) {
        return new JacksonPayloadSerializer(objectMapper);
    }
}
