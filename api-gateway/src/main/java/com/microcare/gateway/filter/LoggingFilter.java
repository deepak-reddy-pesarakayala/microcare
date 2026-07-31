package com.microcare.gateway.filter;

import com.microcare.common.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = uri.getPath();
        String query = uri.getQuery();
        String correlationId = CorrelationId.get();

        log.info("Incoming Request: {} {}, queryParams={}, correlationId={}",
                method, path, query != null ? query : "none", correlationId);

        return chain.filter(exchange).then(Mono.fromRunnable(() ->
                log.info("Completed Response: {} {} -> Status: {}",
                        method, path,
                        exchange.getResponse().getStatusCode())));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
