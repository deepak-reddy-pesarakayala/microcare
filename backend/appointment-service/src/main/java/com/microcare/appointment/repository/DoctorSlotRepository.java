package com.microcare.appointment.repository;

import com.microcare.appointment.entity.DoctorSlot;
import com.microcare.appointment.enums.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorSlotRepository extends JpaRepository<DoctorSlot, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM DoctorSlot s WHERE s.id = :id")
    Optional<DoctorSlot> findByIdWithOptimisticLock(@Param("id") Long id);

    Optional<DoctorSlot> findByIdAndStatus(Long id, SlotStatus status);

    List<DoctorSlot> findByDoctorId(Long doctorId);

    List<DoctorSlot> findByStatus(SlotStatus status);
}
