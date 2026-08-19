package com.microcare.appointment.client;

import com.microcare.appointment.dto.PatientResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PatientServiceFallbackFactory implements FallbackFactory<PatientServiceClient> {

    @Override
    public PatientServiceClient create(Throwable cause) {
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
