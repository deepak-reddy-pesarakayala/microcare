package com.microcare.gateway.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

/**
 * Persists audit log entries for authenticated requests handled by the gateway.
 *
 * <p>Writes are fire-and-forget and isolated on the bounded-elastic scheduler so
 * a slow database never blocks the reactive request path. Failures are logged
 * but never propagate to the caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Asynchronously records an audit entry.
     *
     * @param userId        authenticated user id (may be null for public endpoints)
     * @param username      authenticated username
     * @param role          authenticated role
     * @param method        HTTP method
     * @param path          request path
     * @param statusCode    HTTP status code of the completed request
     * @param correlationId trace id
     * @param remoteIp      client IP
     */
    public void recordAsync(Long userId, String username, String role,
                            String method, String path, Integer statusCode,
                            String correlationId, String remoteIp) {
        MonoHolder.run(() -> {
            try {
                jdbcTemplate.update("""
                        INSERT INTO audit_logs
                            (user_id, username, role, method, path, status_code, correlation_id, remote_ip, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        userId, username, role, method, path, statusCode,
                        correlationId, remoteIp, LocalDateTime.now());
            } catch (Exception e) {
                log.warn("Failed to write audit log for {} {}: {}", method, path, e.getMessage());
            }
        });
    }

    /**
     * Small holder keeps the scheduler construction isolated.
     */
    private static final class MonoHolder {
        static void run(Runnable task) {
            reactor.core.publisher.Mono.fromRunnable(task)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
        }
    }
}
