package com.cinema.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reservation Management is the single owner of the reservation lifecycle:
 * it decides every transition (DRAFT -> CONFIRMED / PENDING_APPROVAL at
 * confirm, cancel, DRAFT -> EXPIRED). It implements OP-01 createDraft,
 * OP-03 confirm and OP-04 cancel from docs/cv2 with BR-02 (seat conflicts,
 * delegated to Seat Availability), BR-03 idempotent cancel, BR-04 the
 * 30-minute cancellation deadline and BR-05 future screenings only.
 * The PENDING_APPROVAL -> CONFIRMED / REJECTED transitions belong to the
 * Approval Workflow.
 */
@Service
public class ReservationManagement {
    private static final Logger log = LoggerFactory.getLogger(ReservationManagement.class);

    static final long DRAFT_TTL_MINUTES = 15;
    static final long PENDING_APPROVAL_TTL_MINUTES = 30;
    static final long CANCEL_DEADLINE_MINUTES = 30;
    static final long LARGE_RESERVATION_SEATS = 10;

    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;
    private final SeatAvailability seatAvailability;

    public ReservationManagement(
            ScreeningRepository screeningRepository,
            SeatRepository seatRepository,
            CustomerRepository customerRepository,
            ReservationRepository reservationRepository,
            SeatAvailability seatAvailability) {
        this.screeningRepository = screeningRepository;
        this.seatRepository = seatRepository;
        this.customerRepository = customerRepository;
        this.reservationRepository = reservationRepository;
        this.seatAvailability = seatAvailability;
    }

    @Transactional
    public Reservation createDraft(Long screeningId, List<Long> seatIds,
                                   String customerName, String customerEmail) {
        Screening screening = screeningRepository.findById(screeningId).orElse(null);
        if (screening == null) {
            log.warn("[CREATE] Rejected: screening {} does not exist", screeningId);
            throw new ReservationRuleException("The selected screening does not exist.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!screening.getStartTime().isAfter(now)) {
            log.warn("[CREATE] Rejected: screening {} has already started (startTime={})",
                    screeningId, screening.getStartTime());
            throw new ReservationRuleException("Reservations can only be created for future screenings.");
        }

        List<Long> requestedSeatIds = seatIds == null ? List.of() : seatIds.stream().distinct().toList();
        List<Seat> seats = seatRepository.findAllById(requestedSeatIds);
        boolean validCustomer = customerName != null && !customerName.isBlank()
                && customerEmail != null && !customerEmail.isBlank();
        boolean validSeats = !requestedSeatIds.isEmpty()
                && seats.size() == requestedSeatIds.size()
                && seats.stream().allMatch(seat -> seat.getRoomId().equals(screening.getRoom().getId()))
                && seatAvailability.areAvailable(screeningId, requestedSeatIds);
        if (!validCustomer || !validSeats) {
            log.warn("[CREATE] Rejected: invalid customer details or seats for screening {} "
                    + "(requestedSeats={}, seatsFound={})", screeningId, requestedSeatIds, seats.size());
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
            log.warn("[CREATE] Rejected: customer {} already has an active reservation for screening {}",
                    customer.getId(), screeningId);
            throw new ReservationRuleException(
                    "You already have an active reservation for this screening.");
        }

        Reservation reservation = reservationRepository.save(new Reservation(
                customer, screening, ReservationStatus.DRAFT,
                now.plusMinutes(DRAFT_TTL_MINUTES)));
        reservation.requestSeats(seats);
        Reservation saved = reservationRepository.save(reservation);
        log.info("[CREATE] Reservation created id={} customer={} screening={} seats={} status={}",
                saved.getId(), customer.getId(), screeningId, requestedSeatIds, saved.getStatus());
        return saved;
    }

    @Transactional
    public Outcome confirm(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.warn("[CONFIRM] Rejected: reservation {} not found", reservationId);
            return new Outcome(null, false, "Reservation not found.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (reservation.isDraftExpired(now)) {
            log.info("Reservation {} state change: {} -> {} (draft TTL passed, expiresAt={})",
                    reservationId, reservation.getStatus(), ReservationStatus.EXPIRED,
                    reservation.getExpiresAt());
            reservation.setStatus(ReservationStatus.EXPIRED);
            log.warn("[CONFIRM] Rejected: reservation {} draft expired at {}",
                    reservationId, reservation.getExpiresAt());
            return new Outcome(reservation, false,
                    "Your draft reservation has expired and can no longer be confirmed.");
        }
        if (reservation.getStatus() != ReservationStatus.DRAFT) {
            log.warn("[CONFIRM] Rejected: reservation {} is {}, only DRAFT can be confirmed",
                    reservationId, reservation.getStatus());
            return new Outcome(reservation, false, "Only draft reservations can be confirmed.");
        }
        if (!reservation.getScreening().getStartTime().isAfter(now)) {
            log.warn("[CONFIRM] Rejected: screening of reservation {} already started (startTime={})",
                    reservationId, reservation.getScreening().getStartTime());
            return new Outcome(reservation, false,
                    "This screening has already started.");
        }
        List<Long> requestedSeatIds = reservation.getRequestedSeatIds();
        if (!seatAvailability.allocateIfAvailable(reservation, requestedSeatIds)) {
            log.warn("[CONFIRM] Rejected: seat conflict for reservation {} screening={} seats={}",
                    reservationId, reservation.getScreening().getId(), requestedSeatIds);
            return new Outcome(reservation, false,
                    "Sorry, one or more of your selected seats has just been reserved by someone else.");
        }
        if (requestedSeatIds.size() > LARGE_RESERVATION_SEATS) {
            reservation.setStatus(ReservationStatus.PENDING_APPROVAL);
            reservation.setExpiresAt(now.plusMinutes(PENDING_APPROVAL_TTL_MINUTES));
            log.info("Reservation {} state change: {} -> {} ({} seats requested, approval required)",
                    reservationId, ReservationStatus.DRAFT, ReservationStatus.PENDING_APPROVAL,
                    requestedSeatIds.size());
            log.info("[CONFIRM] Reservation {} moved to PENDING_APPROVAL, deadline/expiresAt={}",
                    reservationId, reservation.getExpiresAt());
            return new Outcome(reservation, true,
                    "Your reservation has more than " + LARGE_RESERVATION_SEATS
                            + " seats and is now awaiting admin approval.");
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        log.info("Reservation {} state change: {} -> {} (confirmed by customer)",
                reservationId, ReservationStatus.DRAFT, ReservationStatus.CONFIRMED);
        log.info("[CONFIRM] Reservation {} confirmed, deadline={}",
                reservationId, reservation.getExpiresAt());
        return new Outcome(reservation, true, "Reservation confirmed. Your seats are secured.");
    }

    @Transactional
    public Outcome cancel(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.warn("[CANCEL] Rejected: reservation {} not found", reservationId);
            return new Outcome(null, false, "Reservation not found.");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            log.info("[CANCEL] Reservation {} already CANCELLED, cancel repeated (idempotent)",
                    reservationId);
            seatAvailability.release(reservation.getId());
            return new Outcome(reservation, true, "Your reservation has been cancelled.");
        }
        if (reservation.getStatus() == ReservationStatus.REJECTED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            log.warn("[CANCEL] Rejected: reservation {} is {} and cannot be cancelled",
                    reservationId, reservation.getStatus());
            return new Outcome(reservation, false,
                    "This reservation is already closed and cannot be cancelled.");
        }
        LocalDateTime deadline = reservation.getScreening().getStartTime()
                .minusMinutes(CANCEL_DEADLINE_MINUTES);
        if (!LocalDateTime.now().isBefore(deadline)) {
            log.warn("[CANCEL] Rejected: cancellation deadline {} passed for reservation {}",
                    deadline, reservationId);
            return new Outcome(reservation, false,
                    "Reservations can be cancelled until 30 minutes before the screening starts.");
        }
        ReservationStatus previousStatus = reservation.getStatus();
        reservation.setStatus(ReservationStatus.CANCELLED);
        seatAvailability.release(reservation.getId());
        log.info("Reservation {} state change: {} -> {} (cancelled by customer, seats released)",
                reservationId, previousStatus, ReservationStatus.CANCELLED);
        log.info("[CANCEL] Reservation {} cancelled, seats released", reservationId);
        return new Outcome(reservation, true, "Your reservation has been cancelled.");
    }

    /**
     * DRAFT -> EXPIRED transition for reservations whose TTL has passed;
     * called by the Expiration Manager background job, releases any
     * allocated seats.
     *
     * @return number of reservations expired by this run
     */
    @Transactional
    public int expireStaleDrafts() {
        List<Reservation> staleDrafts = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.DRAFT, LocalDateTime.now());
        log.debug("[EXPIRE] Draft sweep scanned {} stale DRAFT reservation(s)", staleDrafts.size());
        for (Reservation reservation : staleDrafts) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            seatAvailability.release(reservation.getId());
            log.info("Reservation {} state change: {} -> {} (draft TTL passed)",
                    reservation.getId(), ReservationStatus.DRAFT, ReservationStatus.EXPIRED);
            log.info("[EXPIRE] Reservation {} expired, seats released", reservation.getId());
        }
        return staleDrafts.size();
    }

    /**
     * PENDING_APPROVAL -> EXPIRED transition for reservations whose approval
     * window has passed; called by the Expiration Manager background job,
     * releases the allocated seats. Legacy rows without an approval deadline
     * (created before the approval TTL existed) get one assigned from
     * createdAt on first contact, so old undecided requests expire on this
     * or the next sweep instead of becoming valid bookings later.
     *
     * @return number of reservations expired by this run
     */
    @Transactional
    public int expireStalePendingApprovals() {
        List<Reservation> legacy = reservationRepository
                .findByStatusAndExpiresAtIsNull(ReservationStatus.PENDING_APPROVAL);
        for (Reservation reservation : legacy) {
            reservation.setExpiresAt(reservation.getCreatedAt()
                    .plusMinutes(PENDING_APPROVAL_TTL_MINUTES));
            log.info("[EXPIRE] Legacy PENDING_APPROVAL reservation {} has no expiresAt; "
                            + "policy applied: deadline {} set from createdAt {}",
                    reservation.getId(), reservation.getExpiresAt(), reservation.getCreatedAt());
        }
        List<Reservation> stale = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.PENDING_APPROVAL, LocalDateTime.now());
        log.debug("[EXPIRE] Pending approval sweep scanned {} stale PENDING_APPROVAL reservation(s)",
                stale.size());
        for (Reservation reservation : stale) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            seatAvailability.release(reservation.getId());
            log.info("Reservation {} state change: {} -> {} (approval window passed, expiresAt={})",
                    reservation.getId(), ReservationStatus.PENDING_APPROVAL,
                    ReservationStatus.EXPIRED, reservation.getExpiresAt());
            log.info("[EXPIRE] Reservation {} expired, seats released", reservation.getId());
        }
        return stale.size();
    }

    record Outcome(Reservation reservation, boolean success, String message) {
    }
}
