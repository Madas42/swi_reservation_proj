package com.cinema.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements the operations and business rules from docs/cv2:
 * OP-01 createDraft, OP-03 confirm (splits into PENDING_APPROVAL for large
 * reservations, per change card C02), OP-04 cancel, the admin
 * VALIDATE_RESERVATION operation (approve/reject), BR-02 exclusive seat
 * invariant, BR-03 idempotent cancel, BR-04 30-minute cancellation deadline,
 * BR-05 future screenings only.
 */
@Service
public class ReservationService {
    static final long DRAFT_TTL_MINUTES = 15;
    static final long CANCEL_DEADLINE_MINUTES = 30;
    static final long LARGE_RESERVATION_SEATS = 10;

    private final ScreeningRepository screeningRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final SeatRepository seatRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;

    public ReservationService(
            ScreeningRepository screeningRepository,
            ReservedSeatRepository reservedSeatRepository,
            SeatRepository seatRepository,
            CustomerRepository customerRepository,
            ReservationRepository reservationRepository) {
        this.screeningRepository = screeningRepository;
        this.reservedSeatRepository = reservedSeatRepository;
        this.seatRepository = seatRepository;
        this.customerRepository = customerRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public Reservation createDraft(Long screeningId, List<Long> seatIds,
                                   String customerName, String customerEmail) {
        Screening screening = screeningRepository.findById(screeningId).orElse(null);
        if (screening == null) {
            throw new ReservationRuleException("The selected screening does not exist.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!screening.getStartTime().isAfter(now)) {
            throw new ReservationRuleException("Reservations can only be created for future screenings.");
        }

        List<Long> requestedSeatIds = seatIds == null ? List.of() : seatIds.stream().distinct().toList();
        List<Seat> seats = seatRepository.findAllById(requestedSeatIds);
        Set<Long> blockedSeatIds = blockedSeatIds(screeningId);
        boolean validCustomer = customerName != null && !customerName.isBlank()
                && customerEmail != null && !customerEmail.isBlank();
        boolean validSeats = !requestedSeatIds.isEmpty()
                && seats.size() == requestedSeatIds.size()
                && seats.stream().allMatch(seat -> seat.getRoomId().equals(screening.getRoom().getId()))
                && requestedSeatIds.stream().noneMatch(blockedSeatIds::contains);
        if (!validCustomer || !validSeats) {
            throw new ReservationRuleException(
                    "Please provide your details and choose only available seats.");
        }

        Customer customer = customerRepository.findByEmail(customerEmail.trim())
                .orElseGet(() -> customerRepository.save(
                        new Customer(customerName.trim(), customerEmail.trim())));
        boolean hasActiveReservation = reservationRepository
                .findByCustomer_EmailAndScreening_Id(customerEmail.trim(), screeningId)
                .stream()
                .anyMatch(reservation -> reservation.getStatus() == ReservationStatus.DRAFT
                        || reservation.getStatus() == ReservationStatus.PENDING_APPROVAL
                        || reservation.getStatus() == ReservationStatus.CONFIRMED);
        if (hasActiveReservation) {
            throw new ReservationRuleException(
                    "You already have an active reservation for this screening.");
        }

        Reservation reservation = reservationRepository.save(new Reservation(
                customer, screening, ReservationStatus.DRAFT,
                now.plusMinutes(DRAFT_TTL_MINUTES)));
        reservation.requestSeats(seats);
        return reservationRepository.save(reservation);
    }

    @Transactional
    public Outcome confirm(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            return new Outcome(null, false, "Reservation not found.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (reservation.isDraftExpired(now)) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            return new Outcome(reservation, false,
                    "Your draft reservation has expired and can no longer be confirmed.");
        }
        if (reservation.getStatus() != ReservationStatus.DRAFT) {
            return new Outcome(reservation, false, "Only draft reservations can be confirmed.");
        }
        if (!reservation.getScreening().getStartTime().isAfter(now)) {
            return new Outcome(reservation, false,
                    "This screening has already started.");
        }
        List<Long> requestedSeatIds = reservation.getRequestedSeatIds();
        if (requestedSeatIds.stream()
                .anyMatch(blockedSeatIds(reservation.getScreening().getId())::contains)) {
            return new Outcome(reservation, false,
                    "Sorry, one or more of your selected seats has just been reserved by someone else.");
        }
        allocateSeats(reservation, requestedSeatIds);
        if (requestedSeatIds.size() > LARGE_RESERVATION_SEATS) {
            reservation.setStatus(ReservationStatus.PENDING_APPROVAL);
            return new Outcome(reservation, true,
                    "Your reservation has more than " + LARGE_RESERVATION_SEATS
                            + " seats and is now awaiting admin approval.");
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return new Outcome(reservation, true, "Reservation confirmed. Your seats are secured.");
    }

    @Transactional
    public Outcome approve(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            return new Outcome(null, false, "Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_APPROVAL) {
            return new Outcome(reservation, false,
                    "Only reservations awaiting approval can be approved.");
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return new Outcome(reservation, true, "Reservation approved and confirmed.");
    }

    @Transactional
    public Outcome reject(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            return new Outcome(null, false, "Reservation not found.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_APPROVAL) {
            return new Outcome(reservation, false,
                    "Only reservations awaiting approval can be rejected.");
        }
        reservation.setStatus(ReservationStatus.REJECTED);
        reservedSeatRepository.deleteAllByReservation_Id(reservation.getId());
        return new Outcome(reservation, true, "Reservation rejected and seats released.");
    }

    @Transactional
    public Outcome cancel(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            return new Outcome(null, false, "Reservation not found.");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            reservedSeatRepository.deleteAllByReservation_Id(reservation.getId());
            return new Outcome(reservation, true, "Your reservation has been cancelled.");
        }
        if (reservation.getStatus() == ReservationStatus.REJECTED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            return new Outcome(reservation, false,
                    "This reservation is already closed and cannot be cancelled.");
        }
        LocalDateTime deadline = reservation.getScreening().getStartTime()
                .minusMinutes(CANCEL_DEADLINE_MINUTES);
        if (!LocalDateTime.now().isBefore(deadline)) {
            return new Outcome(reservation, false,
                    "Reservations can be cancelled until 30 minutes before the screening starts.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservedSeatRepository.deleteAllByReservation_Id(reservation.getId());
        return new Outcome(reservation, true, "Your reservation has been cancelled.");
    }

    private void allocateSeats(Reservation reservation, List<Long> requestedSeatIds) {
        reservedSeatRepository.saveAll(seatRepository.findAllById(requestedSeatIds)
                .stream()
                .map(seat -> new ReservedSeat(reservation, seat))
                .toList());
    }

    private Set<Long> blockedSeatIds(Long screeningId) {
        return reservedSeatRepository
                .findByReservation_Screening_IdAndReservation_StatusIn(screeningId,
                        List.of(ReservationStatus.CONFIRMED, ReservationStatus.PENDING_APPROVAL))
                .stream()
                .map(reservedSeat -> reservedSeat.getSeat().getId())
                .collect(Collectors.toSet());
    }

    record Outcome(Reservation reservation, boolean success, String message) {
    }
}
