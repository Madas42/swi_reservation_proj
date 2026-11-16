package com.cinema.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Expiration Manager — the TTL background job. Time changes the validity of
 * draft reservations independently of any user action, so this job cancels
 * stale drafts on a fixed schedule and frees their seats. The transition
 * itself (DRAFT -> EXPIRED) is decided by Reservation Management, the sole
 * owner of the lifecycle.
 */
@Component
public class ExpirationManager {
    private static final Logger log = LoggerFactory.getLogger(ExpirationManager.class);

    private final ReservationManagement reservationManagement;

    public ExpirationManager(ReservationManagement reservationManagement) {
        this.reservationManagement = reservationManagement;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireStaleDrafts() {
        log.debug("[EXPIRE] Worker cycle start at {}", LocalDateTime.now());
        int draftsExpired = 0;
        int pendingApprovalsExpired = 0;
        try {
            draftsExpired = reservationManagement.expireStaleDrafts();
            if (draftsExpired > 0) {
                log.info("Expired {} stale draft reservation(s).", draftsExpired);
            }
        } catch (OptimisticLockingFailureException e) {
            log.warn("Draft expiration sweep hit a concurrently modified reservation"
                    + " and was rolled back; it will be retried on the next run.", e);
        }
        try {
            pendingApprovalsExpired = reservationManagement.expireStalePendingApprovals();
            if (pendingApprovalsExpired > 0) {
                log.info("Expired {} stale pending approval reservation(s).", pendingApprovalsExpired);
            }
        } catch (OptimisticLockingFailureException e) {
            log.warn("Pending approval expiration sweep hit a concurrently modified reservation"
                    + " and was rolled back; it will be retried on the next run.", e);
        }
        log.info("[EXPIRE] Cycle summary: draftsExpired={}, pendingApprovalsExpired={} "
                + "(each expiry and seat release is logged individually at INFO)",
                draftsExpired, pendingApprovalsExpired);
    }
}
