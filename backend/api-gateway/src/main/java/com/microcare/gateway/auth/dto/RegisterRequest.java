package com.microcare.gateway.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

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

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private String gender;

    @Pattern(regexp = "^\\+?[0-9. ()-]{7,25}$", message = "Invalid contact number format")
    private String contactNumber;

    private String address;

    private String bloodGroup;
}
