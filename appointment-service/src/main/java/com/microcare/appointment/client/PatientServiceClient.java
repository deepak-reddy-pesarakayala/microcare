package com.microcare.appointment.client;

import com.microcare.appointment.dto.PatientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "patient-service",
        path = "/api/patients",
        fallbackFactory = PatientServiceFallbackFactory.class
)
public interface PatientServiceClient {

    @GetMapping("/{id}")
    ResponseEntity<PatientResponse> getPatientById(@PathVariable("id") Long id);
}
