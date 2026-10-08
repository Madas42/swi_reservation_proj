package com.cinema.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seat Availability owns the seat allocation state (ReservedSeat rows) of the
 * system: the checkAvailability projection for the seat map (OP-02), the
 * authoritative BR-02 decision and allocation at confirm, and the release of
 * seats when a reservation is cancelled, rejected or expired. Check and write
 * happen in one place, inside the caller's transaction, backed by the DB
 * unique constraint (screening_id, seat_id).
 */
@Service
public class SeatAvailability {
    private static final List<ReservationStatus> HELD_STATUSES = List.of(
            ReservationStatus.CONFIRMED, ReservationStatus.PENDING_APPROVAL);

    private final ReservedSeatRepository reservedSeatRepository;
    private final SeatRepository seatRepository;

    public SeatAvailability(ReservedSeatRepository reservedSeatRepository,
                            SeatRepository seatRepository) {
        this.reservedSeatRepository = reservedSeatRepository;
        this.seatRepository = seatRepository;
    }

    /**
     * OP-02: ids of seats currently unavailable for the screening
     * (held by CONFIRMED and PENDING_APPROVAL reservations).
     */
    public Set<Long> checkAvailability(Long screeningId) {
        return heldSeatIds(screeningId);
    }

    /** Number of seats held for the screening (for remaining capacity). */
    public long heldSeatCount(Long screeningId) {
        return reservedSeatRepository
                .countByReservation_Screening_IdAndReservation_StatusIn(screeningId, HELD_STATUSES);
    }

    /** Seats allocated to a reservation (empty unless CONFIRMED/PENDING_APPROVAL). */
    public List<ReservedSeat> allocationOf(Long reservationId) {
        return reservedSeatRepository.findByReservation_Id(reservationId);
    }

    /** True when none of the seats is held for the screening. */
    public boolean areAvailable(Long screeningId, List<Long> seatIds) {
        Set<Long> heldSeatIds = heldSeatIds(screeningId);
        return seatIds.stream().noneMatch(heldSeatIds::contains);
    }

    /**
     * Authoritative BR-02 decision + allocation: checks availability and, if
     * all seats are free, writes the ReservedSeat rows. Must run inside the
     * caller's transaction so the check and the write are one unit; the DB
     * unique constraint is the backstop for concurrent confirms.
     *
     * @return true when the seats were allocated, false on a conflict
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean allocateIfAvailable(Reservation reservation, List<Long> seatIds) {
        if (!areAvailable(reservation.getScreening().getId(), seatIds)) {
            return false;
        }
        reservedSeatRepository.saveAll(seatRepository.findAllById(seatIds)
                .stream()
                .map(seat -> new ReservedSeat(reservation, seat))
                .toList());
        return true;
    }

    /** Releases the seats allocated to a reservation (cancel/reject/expire). */
    public void release(Long reservationId) {
        reservedSeatRepository.deleteAllByReservation_Id(reservationId);
    }

    private Set<Long> heldSeatIds(Long screeningId) {
        return reservedSeatRepository
                .findByReservation_Screening_IdAndReservation_StatusIn(screeningId, HELD_STATUSES)
                .stream()
                .map(reservedSeat -> reservedSeat.getSeat().getId())
                .collect(Collectors.toSet());
    }
}
