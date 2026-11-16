package com.cinema.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Controller
public class ScreeningController {
    private static final Logger log = LoggerFactory.getLogger(ScreeningController.class);

    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final SeatAvailability seatAvailability;
    private final ReservationManagement reservationManagement;

    public ScreeningController(
            ScreeningRepository screeningRepository,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            SeatAvailability seatAvailability,
            ReservationManagement reservationManagement) {
        this.screeningRepository = screeningRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.seatAvailability = seatAvailability;
        this.reservationManagement = reservationManagement;
    }

    @GetMapping("/")
    public String screenings(@RequestParam(required = false) String date, Model model) {
        log.info("[HTTP] GET / (date={})", date);
        LocalDate selectedDate = parseDateOrDefault(date);
        LocalDateTime dayStart = selectedDate.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<ScreeningView> screenings = screeningRepository
                .findByStartTimeBetweenOrderByStartTime(dayStart, dayEnd)
                .stream()
                .filter(screening -> screening.getStartTime().isAfter(LocalDateTime.now()))
                .map(screening -> new ScreeningView(
                        screening.getId(),
                        screening.getFilm().getTitle(),
                        screening.getStartTime(),
                        screening.getRoom().getName(),
                        screening.getRoom().getCapacity()
                                - (int) seatAvailability.heldSeatCount(screening.getId())))
                .toList();

        model.addAttribute("screenings", screenings);
        model.addAttribute("selectedDate", selectedDate);
        log.debug("[HTTP] GET / -> returned {} screening(s) for {}", screenings.size(), selectedDate);
        return "screenings";
    }

    private LocalDate parseDateOrDefault(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }

    @GetMapping("/screenings/{screeningId}")
    public String screeningSeats(@PathVariable Long screeningId, Model model,
                                  RedirectAttributes redirectAttributes) {
        log.info("[HTTP] GET /screenings/{} (seat map)", screeningId);
        Screening screening = screeningRepository.findById(screeningId).orElse(null);
        if (screening == null) {
            log.warn("[HTTP] GET /screenings/{} -> screening not found, redirecting to /", screeningId);
            return "redirect:/";
        }
        if (!screening.getStartTime().isAfter(LocalDateTime.now())) {
            log.warn("[HTTP] GET /screenings/{} -> screening already started ({}), redirecting to /",
                    screeningId, screening.getStartTime());
            redirectAttributes.addFlashAttribute("reservationError",
                    "Seat selection is only available for upcoming screenings.");
            return "redirect:/";
        }

        Set<Long> reservedSeatIds = seatAvailability.checkAvailability(screeningId);

        List<SeatView> seats = seatRepository
                .findByRoom_IdOrderByRowAscNumberAsc(screening.getRoom().getId())
                .stream()
                .map(seat -> new SeatView(
                        seat.getId(), seat.getRow(), seat.getNumber(), reservedSeatIds.contains(seat.getId())))
                .toList();

        model.addAttribute("screening", screening);
        model.addAttribute("seats", seats);
        model.addAttribute("maxSeats", seats.size() - reservedSeatIds.size());
        log.info("[HTTP] GET /screenings/{} -> returned {} seats ({} reserved)",
                screeningId, seats.size(), reservedSeatIds.size());
        return "seats";
    }

    @PostMapping("/screenings/{screeningId}/reservations")
    public String reserveSeats(
            @PathVariable Long screeningId,
            @RequestParam(required = false) List<Long> seatIds,
            @RequestParam String customerName,
            @RequestParam String customerEmail,
            RedirectAttributes redirectAttributes) {
        log.info("[HTTP] POST /screenings/{}/reservations (seats={})", screeningId, seatIds);
        try {
            Reservation reservation = reservationManagement
                    .createDraft(screeningId, seatIds, customerName, customerEmail);
            log.info("[HTTP] POST /screenings/{}/reservations -> draft reservation {} created "
                    + "for customer {}", screeningId, reservation.getId(),
                    reservation.getCustomer().getId());
            redirectAttributes.addFlashAttribute("reservationSuccess",
                    "Draft reservation created. Confirm it within "
                            + ReservationManagement.DRAFT_TTL_MINUTES
                            + " minutes to secure your seats.");
            return "redirect:/reservations/" + reservation.getId();
        } catch (ReservationRuleException e) {
            log.warn("[HTTP] POST /screenings/{}/reservations -> failed: {}", screeningId,
                    e.getMessage());
            redirectAttributes.addFlashAttribute("reservationError", e.getMessage());
            return "redirect:/screenings/" + screeningId;
        }
    }

    @GetMapping("/reservations/{reservationId}")
    public String reservationDetail(@PathVariable Long reservationId, Model model,
                                    HttpSession session) {
        log.info("[HTTP] GET /reservations/{} (detail)", reservationId);
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.warn("[HTTP] GET /reservations/{} -> not found, redirecting to /", reservationId);
            return "redirect:/";
        }

        List<SeatView> seats;
        if (reservation.getStatus() == ReservationStatus.CONFIRMED
                || reservation.getStatus() == ReservationStatus.PENDING_APPROVAL) {
            seats = seatAvailability.allocationOf(reservationId)
                    .stream()
                    .map(reservedSeat -> new SeatView(
                            reservedSeat.getSeat().getId(),
                            reservedSeat.getSeat().getRow(),
                            reservedSeat.getSeat().getNumber(),
                            true))
                    .toList();
        } else {
            seats = seatRepository.findAllById(reservation.getRequestedSeatIds())
                    .stream()
                    .sorted(Comparator.comparing(Seat::getRow).thenComparing(Seat::getNumber))
                    .map(seat -> new SeatView(seat.getId(), seat.getRow(), seat.getNumber(), false))
                    .toList();
        }

        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("reservation", reservation);
        model.addAttribute("screening", reservation.getScreening());
        model.addAttribute("seats", seats);
        model.addAttribute("canConfirm", reservation.getStatus() == ReservationStatus.DRAFT
                && !reservation.isDraftExpired(now)
                && reservation.getScreening().getStartTime().isAfter(now));
        model.addAttribute("draftExpired", reservation.isDraftExpired(now));
        model.addAttribute("canCancel", reservation.getStatus() == ReservationStatus.DRAFT
                || reservation.getStatus() == ReservationStatus.PENDING_APPROVAL
                || reservation.getStatus() == ReservationStatus.CONFIRMED);
        model.addAttribute("canValidate", AdminController.isLoggedIn(session)
                && reservation.getStatus() == ReservationStatus.PENDING_APPROVAL);
        log.debug("[HTTP] GET /reservations/{} -> rendered reservation detail, status={}",
                reservationId, reservation.getStatus());
        return "reservation";
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public String confirmReservation(@PathVariable Long reservationId,
                                     RedirectAttributes redirectAttributes) {
        log.info("[HTTP] POST /reservations/{}/confirm", reservationId);
        ReservationManagement.Outcome outcome = reservationManagement.confirm(reservationId);
        log.info("[HTTP] POST /reservations/{}/confirm -> success={}, message={}",
                reservationId, outcome.success(), outcome.message());
        return reservationRedirect(outcome, "reservationSuccess", "reservationError",
                redirectAttributes);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public String cancelReservation(@PathVariable Long reservationId,
                                    RedirectAttributes redirectAttributes) {
        log.info("[HTTP] POST /reservations/{}/cancel", reservationId);
        ReservationManagement.Outcome outcome = reservationManagement.cancel(reservationId);
        log.info("[HTTP] POST /reservations/{}/cancel -> success={}, message={}",
                reservationId, outcome.success(), outcome.message());
        return reservationRedirect(outcome, "reservationSuccess", "reservationError",
                redirectAttributes);
    }

    private String reservationRedirect(ReservationManagement.Outcome outcome,
                                       String successAttribute, String errorAttribute,
                                       RedirectAttributes redirectAttributes) {
        if (outcome.reservation() == null) {
            redirectAttributes.addFlashAttribute(errorAttribute, outcome.message());
            return "redirect:/";
        }
        if (outcome.success()) {
            redirectAttributes.addFlashAttribute(successAttribute, outcome.message());
        } else {
            redirectAttributes.addFlashAttribute(errorAttribute, outcome.message());
        }
        return "redirect:/reservations/" + outcome.reservation().getId();
    }

    record ScreeningView(Long id, String filmTitle, LocalDateTime startTime,
                         String roomName, int availableSeats) {}

    record SeatView(Long id, int row, int number, boolean reserved) {}
}
