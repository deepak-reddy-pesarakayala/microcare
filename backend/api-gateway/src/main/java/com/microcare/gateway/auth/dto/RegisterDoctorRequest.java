package com.microcare.gateway.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Self-service registration payload for DOCTOR-role accounts.
 *
 * <p>Doctor accounts are created in {@code PENDING} status and can only sign in
 * after an ADMIN approves them via {@code POST /api/admin/doctors/{id}/approve}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterDoctorRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must be at most 150 characters")
    private String fullName;

    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Specialization is required")
    @Size(max = 100, message = "Specialization must be at most 100 characters")
    private String specialization;

    /**
     * Optional comma-separated list of diseases/conditions this doctor treats
     * (e.g. "Heart disease,Hypertension"). Used to classify doctors in the
     * patient-facing directory; when blank, diseases are derived from the
     * specialization.
     */
    @Size(max = 255, message = "Diseases must be at most 255 characters")
    private String diseases;

    @Pattern(regexp = "^\\+?[0-9. ()-]{7,25}$", message = "Invalid contact number format")
    private String contactNumber;

    @Size(max = 100, message = "License number must be at most 100 characters")
    private String licenseNumber;
}
