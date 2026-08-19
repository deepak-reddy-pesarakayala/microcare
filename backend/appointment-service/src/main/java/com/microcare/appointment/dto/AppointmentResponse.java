package com.microcare.appointment.dto;

import com.microcare.appointment.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentResponse {

    private Long id;
    private Long patientId;
    private Long doctorId;
    private Long slotId;
    private AppointmentStatus status;
    private LocalDateTime createdAt;
}
