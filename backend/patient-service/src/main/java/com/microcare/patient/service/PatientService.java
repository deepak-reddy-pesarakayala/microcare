package com.microcare.patient.service;

import com.microcare.patient.dto.PatientRequest;
import com.microcare.patient.dto.PatientResponse;
import com.microcare.patient.entity.Patient;
import com.microcare.patient.exception.ResourceNotFoundException;
import com.microcare.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    @Transactional
    public PatientResponse createPatient(PatientRequest request) {
        Patient patient = Patient.builder()
                .fullName(request.getFullName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .contactNumber(request.getContactNumber())
                .email(request.getEmail())
                .address(request.getAddress())
                .bloodGroup(request.getBloodGroup())
                .build();

        Patient saved = patientRepository.save(patient);
        return mapToResponse(saved);
    }

    public PatientResponse getPatientById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));
        return mapToResponse(patient);
    }

    public List<PatientResponse> getAllPatients() {
        return patientRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public PatientResponse updatePatient(Long id, PatientRequest request) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));

        patient.setFullName(request.getFullName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setContactNumber(request.getContactNumber());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setBloodGroup(request.getBloodGroup());

        Patient updated = patientRepository.save(patient);
        return mapToResponse(updated);
    }

    private PatientResponse mapToResponse(Patient patient) {
        return PatientResponse.builder()
                .id(patient.getId())
                .fullName(patient.getFullName())
                .dateOfBirth(patient.getDateOfBirth())
                .gender(patient.getGender())
                .contactNumber(patient.getContactNumber())
                .email(patient.getEmail())
                .address(patient.getAddress())
                .bloodGroup(patient.getBloodGroup())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .build();
    }
}
