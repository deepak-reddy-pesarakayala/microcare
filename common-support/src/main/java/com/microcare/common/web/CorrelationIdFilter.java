package com.microcare.common.web;

import com.microcare.common.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that injects a correlation ID into the SLF4J MDC for the
 * duration of the request and echoes it back on the response.
 *
 * <p>The correlation ID is taken from the {@code X-Correlation-Id} request header
 * (set by the API Gateway) or generated if absent. It is made available to the
 * entire request thread (including Feign calls) via the MDC, so every log line
 * produced while handling the request carries the trace id.
 *
 * <p>The MDC is cleared in the {@code finally} block to avoid leaking the value
 * into reused threads (e.g. container worker threads).
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationId.HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationId.generate();
        }

        CorrelationId.set(correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationId.clear();
        }
    }
}
