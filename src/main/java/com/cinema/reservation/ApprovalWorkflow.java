package com.cinema.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            return new ReservationManagement.Outcome(null, false, "Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_APPROVAL) {
            return new ReservationManagement.Outcome(reservation, false,
                    "Only reservations awaiting approval can be approved.");
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        notificationIntegration.notifyOutcome(reservation, true);
        return new ReservationManagement.Outcome(reservation, true,
                "Reservation approved and confirmed.");
    }

    @Transactional
    public ReservationManagement.Outcome reject(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            return new ReservationManagement.Outcome(null, false, "Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_APPROVAL) {
            return new ReservationManagement.Outcome(reservation, false,
                    "Only reservations awaiting approval can be rejected.");
        }
        reservation.setStatus(ReservationStatus.REJECTED);
        seatAvailability.release(reservation.getId());
        notificationIntegration.notifyOutcome(reservation, false);
        return new ReservationManagement.Outcome(reservation, true,
                "Reservation rejected and seats released.");
    }
}
