package com.microcare.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientResponse {

    private Long id;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String contactNumber;
    private String email;
    private String address;
    private String bloodGroup;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
