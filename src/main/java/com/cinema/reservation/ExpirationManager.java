package com.cinema.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
        int expired = reservationManagement.expireStaleDrafts();
        if (expired > 0) {
            log.info("Expired {} stale draft reservation(s).", expired);
        }
    }
}
