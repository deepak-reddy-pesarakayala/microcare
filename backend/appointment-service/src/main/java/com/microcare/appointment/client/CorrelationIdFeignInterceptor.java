package com.microcare.appointment.client;

import com.microcare.common.CorrelationId;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Propagates the correlation ID (stored in the SLF4J MDC by
 * {@code CorrelationIdFilter}) onto every outbound Feign call, so the gateway's
 * trace id flows through patient-service as well.
 */
@Configuration
public class CorrelationIdFeignInterceptor {

    @Bean
    public RequestInterceptor correlationIdRequestInterceptor() {
        return (RequestTemplate template) -> {
            String correlationId = CorrelationId.get();
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(CorrelationId.HEADER, correlationId);
            }
        };
    }
}
