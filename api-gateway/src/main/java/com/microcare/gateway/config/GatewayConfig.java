package com.microcare.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("patient-service", r -> r
                        .path("/api/patients/**")
                        .uri("lb://patient-service"))
                .route("appointment-service", r -> r
                        .path("/api/appointments/**")
                        .uri("lb://appointment-service"))
                .build();
    }
}
