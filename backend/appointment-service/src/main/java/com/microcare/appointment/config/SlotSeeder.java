package com.microcare.appointment.config;

import com.microcare.appointment.entity.DoctorSlot;
import com.microcare.appointment.enums.SlotStatus;
import com.microcare.appointment.repository.DoctorSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds a rolling window of AVAILABLE doctor slots on startup so patients can
 * always book — the slot table is created by Hibernate (after the MySQL
 * first-boot scripts run), so it cannot be seeded from {@code init-scripts}.
 *
 * <p>The seeding is <b>idempotent</b>: a slot (doctor, date, start time) that
 * already exists is never duplicated, so restarts only fill in missing days.
 * Doctors can still manage slots manually via {@code POST /api/appointments/slots}.
 *
 * <p>Tune via config: {@code app.slot-seeding.*} (or the {@code SLOT_SEED_*}
 * environment variables).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotSeeder implements ApplicationRunner {

    private final DoctorSlotRepository doctorSlotRepository;

    @Value("${app.slot-seeding.enabled:true}")
    private boolean enabled;

    @Value("${app.slot-seeding.doctor-ids:100,3}")
    private List<Long> doctorIds;

    @Value("${app.slot-seeding.days-ahead:7}")
    private int daysAhead;

    @Value("${app.slot-seeding.start-hour:9}")
    private int startHour;

    @Value("${app.slot-seeding.end-hour:17}")
    private int endHour;

    @Value("${app.slot-seeding.slot-minutes:60}")
    private int slotMinutes;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Slot seeding is disabled (app.slot-seeding.enabled=false)");
            return;
        }
        if (daysAhead < 1 || slotMinutes < 1 || endHour <= startHour) {
            log.warn("Slot seeding skipped: invalid configuration (days-ahead={}, slot-minutes={}, hours={}-{})",
                    daysAhead, slotMinutes, startHour, endHour);
            return;
        }
        for (Long doctorId : doctorIds) {
            if (doctorId == null) {
                continue;
            }
            seedDoctor(doctorId);
        }
    }

    private void seedDoctor(Long doctorId) {
        Set<String> existing = doctorSlotRepository.findByDoctorId(doctorId).stream()
                .map(s -> s.getSlotDate() + "|" + s.getStartTime())
                .collect(Collectors.toSet());

        int created = 0;
        LocalDate today = LocalDate.now();
        for (int day = 1; day <= daysAhead; day++) {
            LocalDate date = today.plusDays(day);
            for (LocalTime time = LocalTime.of(startHour, 0);
                 time.isBefore(LocalTime.of(endHour, 0));
                 time = time.plusMinutes(slotMinutes)) {
                String key = date + "|" + time;
                if (existing.contains(key)) {
                    continue;
                }
                doctorSlotRepository.save(DoctorSlot.builder()
                        .doctorId(doctorId)
                        .slotDate(date)
                        .startTime(time)
                        .endTime(time.plusMinutes(slotMinutes))
                        .status(SlotStatus.AVAILABLE)
                        .build());
                created++;
            }
        }

        log.info("Slot seeding for doctor {}: created {} slots over the next {} days ({} already existed)",
                doctorId, created, daysAhead, existing.size());
    }
}
