package io.quarkiverse.tandem.producer.runtime;

import java.util.Map;

import org.jboss.logging.MDC;

import com.codingful.tandem.core.TandemContext;
import com.codingful.tandem.core.TandemHeaders;
import com.codingful.tandem.core.port.TracePropagator;

/**
 * Captures the correlation id from JBoss Logging's MDC, which is also SLF4J's in Quarkus, falling back
 * to {@link TandemContext} (LLD-quarkus §10.2).
 */
final class MdcCorrelationTracePropagator implements TracePropagator {

    private final String mdcKey;

    MdcCorrelationTracePropagator(String mdcKey) {
        this.mdcKey = mdcKey;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Map<String, String> capture() {
        Object fromMdc = MDC.get(mdcKey);
        String correlationId = fromMdc != null ? fromMdc.toString() : TandemContext.currentCorrelationId();
        return correlationId == null ? Map.of() : Map.of(TandemHeaders.CORRELATION_ID, correlationId);
    }
}
