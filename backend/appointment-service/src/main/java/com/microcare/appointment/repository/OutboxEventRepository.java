package com.microcare.appointment.repository;

import com.microcare.appointment.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for OutboxEvent — supports the transactional outbox poller
 * by querying for PENDING events that need to be published to RabbitMQ.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Find all events that are still PENDING, ordered by creation time
     * to preserve event ordering within an aggregate.
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
}
