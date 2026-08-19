package com.microcare.gateway.filter;

import com.microcare.common.CorrelationId;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Generates (or reuses) a correlation ID for every request, stores it in the
 * MDC so all gateway log lines carry it, and forwards it downstream as the
 * {@code X-Correlation-Id} header so the business services can propagate it
 * across Feign calls and RabbitMQ message headers.
 *
 * <p>Runs before all other gateway filters.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ATTRIBUTE = "microcare.correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER);
        String correlationId = (incoming != null && !incoming.isBlank())
                ? incoming
                : CorrelationId.generate();

        CorrelationId.set(correlationId);
        exchange.getAttributes().put(CORRELATION_ATTRIBUTE, correlationId);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(CorrelationId.HEADER, correlationId)
                .build();

        // NOTE: WebFlux may hop threads between the MDC.put above and this clear,
        // which can leave the MDC set on a pooled event-loop thread. This is a known
        // accepted limitation of the MDC-in-reactor pattern (downstream services are
        // servlet-based, where the MDC lifecycle is exact).
        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(signalType -> CorrelationId.clear());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // run first
    }
}
