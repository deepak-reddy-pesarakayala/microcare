package com.microcare.appointment.client;

import com.microcare.appointment.dto.PatientResponse;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
public class PatientServiceFallbackFactory implements FallbackFactory<PatientServiceClient> {

    @Override
    public PatientServiceClient create(Throwable cause) {
        // A genuine 404 from the downstream must propagate so that booking
        // rejects non-existent patients — regardless of whether the custom
        // ErrorDecoder translated it (ResponseStatusException) or the raw
        // FeignException surfaced. Only degrade on transport/unavailability
        // failures (connection refused, timeouts, 5xx, ...).
        if (cause instanceof ResponseStatusException rse
                && rse.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            throw rse;
        }
        if (cause instanceof FeignException.NotFound) {
            throw (FeignException) cause;
        }
        log.error("Fallback triggered for patient-service call: {}", cause.getMessage());
        return new PatientServiceClient() {
            @Override
            public ResponseEntity<PatientResponse> getPatientById(Long id) {
                log.warn("patient-service is unavailable. Returning 503 for patient ID: {}", id);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
        };
    }
}
