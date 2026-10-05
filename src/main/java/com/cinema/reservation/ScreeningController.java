package com.cinema.reservation;

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
import java.util.stream.Collectors;

@Controller
public class ScreeningController {
    private final ScreeningRepository screeningRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    public ScreeningController(
            ScreeningRepository screeningRepository,
            ReservedSeatRepository reservedSeatRepository,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            ReservationService reservationService) {
        this.screeningRepository = screeningRepository;
        this.reservedSeatRepository = reservedSeatRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
    }

    @GetMapping("/")
    public String screenings(@RequestParam(required = false) String date, Model model) {
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
                                - (int) reservedSeatRepository
                                .countByReservation_Screening_IdAndReservation_StatusIn(
                                        screening.getId(),
                                        List.of(ReservationStatus.CONFIRMED,
                                                ReservationStatus.PENDING_APPROVAL))))
                .toList();

        model.addAttribute("screenings", screenings);
        model.addAttribute("selectedDate", selectedDate);
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
        Screening screening = screeningRepository.findById(screeningId).orElse(null);
        if (screening == null) {
            return "redirect:/";
        }
        if (!screening.getStartTime().isAfter(LocalDateTime.now())) {
            redirectAttributes.addFlashAttribute("reservationError",
                    "Seat selection is only available for upcoming screenings.");
            return "redirect:/";
        }

        Set<Long> reservedSeatIds = reservedSeatRepository
                .findByReservation_Screening_IdAndReservation_StatusIn(
                        screeningId, List.of(ReservationStatus.CONFIRMED,
                                ReservationStatus.PENDING_APPROVAL))
                .stream()
                .map(reservedSeat -> reservedSeat.getSeat().getId())
                .collect(Collectors.toSet());

        List<SeatView> seats = seatRepository
                .findByRoom_IdOrderByRowAscNumberAsc(screening.getRoom().getId())
                .stream()
                .map(seat -> new SeatView(
                        seat.getId(), seat.getRow(), seat.getNumber(), reservedSeatIds.contains(seat.getId())))
                .toList();

        model.addAttribute("screening", screening);
        model.addAttribute("seats", seats);
        model.addAttribute("maxSeats", seats.size() - reservedSeatIds.size());
        return "seats";
    }

    @PostMapping("/screenings/{screeningId}/reservations")
    public String reserveSeats(
            @PathVariable Long screeningId,
            @RequestParam(required = false) List<Long> seatIds,
            @RequestParam String customerName,
            @RequestParam String customerEmail,
            RedirectAttributes redirectAttributes) {
        try {
            Reservation reservation = reservationService
                    .createDraft(screeningId, seatIds, customerName, customerEmail);
            redirectAttributes.addFlashAttribute("reservationSuccess",
                    "Draft reservation created. Confirm it within "
                            + ReservationService.DRAFT_TTL_MINUTES
                            + " minutes to secure your seats.");
            return "redirect:/reservations/" + reservation.getId();
        } catch (ReservationRuleException e) {
            redirectAttributes.addFlashAttribute("reservationError", e.getMessage());
            return "redirect:/screenings/" + screeningId;
        }
    }

    @GetMapping("/reservations/{reservationId}")
    public String reservationDetail(@PathVariable Long reservationId, Model model,
                                    HttpSession session) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            return "redirect:/";
        }

        List<SeatView> seats;
        if (reservation.getStatus() == ReservationStatus.CONFIRMED
                || reservation.getStatus() == ReservationStatus.PENDING_APPROVAL) {
            seats = reservedSeatRepository.findByReservation_Id(reservationId)
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
        return "reservation";
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public String confirmReservation(@PathVariable Long reservationId,
                                     RedirectAttributes redirectAttributes) {
        ReservationService.Outcome outcome = reservationService.confirm(reservationId);
        return reservationRedirect(outcome, "reservationSuccess", "reservationError",
                redirectAttributes);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public String cancelReservation(@PathVariable Long reservationId,
                                    RedirectAttributes redirectAttributes) {
        ReservationService.Outcome outcome = reservationService.cancel(reservationId);
        return reservationRedirect(outcome, "reservationSuccess", "reservationError",
                redirectAttributes);
    }

    private String reservationRedirect(ReservationService.Outcome outcome,
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
