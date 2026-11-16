package com.cinema.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Approval Workflow owns the deferred approval state (OP-05, reservations
 * with more than 10 seats): it is the only component that decides and
 * performs the PENDING_APPROVAL -> CONFIRMED / REJECTED transitions,
 * requested by the admin actor. Rejecting releases the seats via Seat
 * Availability. Both decisions are reported to the customer through the
 * Notification Integration; a notification failure never reverts the
 * decision already persisted here.
 */
@Service
public class ApprovalWorkflow {
    private static final Logger log = LoggerFactory.getLogger(ApprovalWorkflow.class);

    private final ReservationRepository reservationRepository;
    private final SeatAvailability seatAvailability;
    private final NotificationIntegration notificationIntegration;

    public ApprovalWorkflow(ReservationRepository reservationRepository,
                            SeatAvailability seatAvailability,
                            NotificationIntegration notificationIntegration) {
        this.reservationRepository = reservationRepository;
        this.seatAvailability = seatAvailability;
        this.notificationIntegration = notificationIntegration;
    }

    @Transactional
    public ReservationManagement.Outcome approve(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.warn("[APPROVE] Rejected: reservation {} not found", reservationId);
            return new ReservationManagement.Outcome(null, false, "Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_APPROVAL) {
            log.warn("[APPROVE] Rejected: reservation {} is {}, only PENDING_APPROVAL can be approved",
                    reservationId, reservation.getStatus());
            return new ReservationManagement.Outcome(reservation, false,
                    "Only reservations awaiting approval can be approved.");
        }
        LocalDateTime now = LocalDateTime.now();
        log.info("[APPROVE] Reservation {} approval check: expiresAt={}, now={}",
                reservationId, reservation.getExpiresAt(), now);
        if (reservation.getExpiresAt() == null) {
            log.info("[APPROVE] Reservation {} is a legacy row with expiresAt == null; "
                    + "policy: treated as not expired, approval proceeds", reservationId);
        }
        if (reservation.isPendingApprovalExpired(now)) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            seatAvailability.release(reservation.getId());
            log.info("Reservation {} state change: {} -> {} (approval window passed, expiresAt={} < now={})",
                    reservationId, ReservationStatus.PENDING_APPROVAL, ReservationStatus.EXPIRED,
                    reservation.getExpiresAt(), now);
            log.info("[EXPIRE] Reservation {} expired at approval, seats released", reservationId);
            notificationIntegration.notifyOutcome(reservation, false);
            return new ReservationManagement.Outcome(reservation, false,
                    "The approval window of 30 minutes has passed; the reservation expired and the seats were released.");
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        log.info("Reservation {} state change: {} -> {} (approved by admin before deadline)",
                reservationId, ReservationStatus.PENDING_APPROVAL, ReservationStatus.CONFIRMED);
        log.info("[APPROVE] Reservation {} approved before deadline, now CONFIRMED", reservationId);
        notificationIntegration.notifyOutcome(reservation, true);
        return new ReservationManagement.Outcome(reservation, true,
                "Reservation approved and confirmed.");
    }

    @Transactional
    public ReservationManagement.Outcome reject(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.warn("[REJECT] Rejected: reservation {} not found", reservationId);
            return new ReservationManagement.Outcome(null, false, "Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_APPROVAL) {
            log.warn("[REJECT] Rejected: reservation {} is {}, only PENDING_APPROVAL can be rejected",
                    reservationId, reservation.getStatus());
            return new ReservationManagement.Outcome(reservation, false,
                    "Only reservations awaiting approval can be rejected.");
        }
        reservation.setStatus(ReservationStatus.REJECTED);
        seatAvailability.release(reservation.getId());
        log.info("Reservation {} state change: {} -> {} (rejected by admin, seats released)",
                reservationId, ReservationStatus.PENDING_APPROVAL, ReservationStatus.REJECTED);
        log.info("[REJECT] Approval rejected for reservation {} reason=admin decision, seats released",
                reservationId);
        notificationIntegration.notifyOutcome(reservation, false);
        return new ReservationManagement.Outcome(reservation, true,
                "Reservation rejected and seats released.");
    }
}
