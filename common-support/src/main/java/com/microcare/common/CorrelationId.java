package com.microcare.common;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utilities for working with the {@code X-Correlation-Id} trace identifier.
 *
 * <p>The correlation ID is generated at the API Gateway, propagated to downstream
 * services via HTTP headers (Feign/REST) and RabbitMQ message headers, and stored
 * in the SLF4J MDC so that every log line carries the trace id.
 */
public final class CorrelationId {

    /** HTTP header / RabbitMQ message header name. */
    public static final String HEADER = "X-Correlation-Id";

    /** MDC key — referenced by the logging pattern via {@code %X{correlationId:-}}. */
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    /** Returns the current correlation id from the MDC, or generates one. */
    public static String getOrGenerate() {
        String current = get();
        return current != null ? current : generate();
    }

    /** Returns the current correlation id from the MDC (may be {@code null}). */
    public static String get() {
        return MDC.get(MDC_KEY);
    }

    /** Stores the correlation id in the MDC (clears it first if {@code null}). */
    public static void set(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, correlationId);
        }
    }

    /** Removes the correlation id from the MDC (call in finally blocks). */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /** Generates a fresh correlation id. */
    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
