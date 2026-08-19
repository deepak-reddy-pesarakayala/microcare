package com.microcare.gateway.config;

/**
 * Route definitions moved to {@code application.yml} ({@code spring.cloud.gateway.routes})
 * so they can be overridden per-environment (e.g. the integration-test module
 * points the gateway at fixed local ports instead of {@code lb://} URIs).
 */
public final class GatewayConfig {

    private GatewayConfig() {
    }
}
