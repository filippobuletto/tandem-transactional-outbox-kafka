package io.quarkiverse.tandem.producer.runtime;

import com.codingful.tandem.core.exception.PayloadSerializationException;
import com.codingful.tandem.core.port.PayloadSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serializes payloads with the application's own {@link ObjectMapper}, so a payload renders the way the
 * application's REST responses do (LLD-quarkus §9).
 */
final class JacksonPayloadSerializer implements PayloadSerializer {

    static final String CONTENT_TYPE = "application/json";

    private final ObjectMapper objectMapper;

    JacksonPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new PayloadSerializationException("Failed to serialize payload to JSON", e);
        }
    }

    @Override
    public String contentType() {
        return CONTENT_TYPE;
    }
}
