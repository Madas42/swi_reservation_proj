package com.cinema.reservation;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Admin actor from change card C02: login, the admin panel listing
 * reservations awaiting approval, and the VALIDATE_RESERVATION operation
 * (approve / reject of PENDING_APPROVAL reservations).
 */
@Controller
public class AdminController {
    static final String SESSION_ATTRIBUTE = "adminLoggedIn";
    static final String ADMIN_USERNAME = "admin";
    static final String ADMIN_PASSWORD = "admin";

    private final ReservationRepository reservationRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final ReservationService reservationService;

    public AdminController(ReservationRepository reservationRepository,
                           ReservedSeatRepository reservedSeatRepository,
                           ReservationService reservationService) {
        this.reservationRepository = reservationRepository;
        this.reservedSeatRepository = reservedSeatRepository;
        this.reservationService = reservationService;
    }

    static boolean isLoggedIn(HttpSession session) {
        return session != null
                && Boolean.TRUE.equals(session.getAttribute(SESSION_ATTRIBUTE));
    }

    @PostMapping("/admin/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestHeader(value = "Referer", required = false) String referer,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        if (ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password)) {
            session.setAttribute(SESSION_ATTRIBUTE, Boolean.TRUE);
            return "redirect:/admin";
        }
        redirectAttributes.addFlashAttribute("adminError",
                "Invalid admin credentials.");
        return "redirect:" + (referer != null ? referer : "/");
    }

    @PostMapping("/admin/logout")
    public String logout(HttpSession session) {
        session.removeAttribute(SESSION_ATTRIBUTE);
        return "redirect:/";
    }

    @GetMapping("/admin")
    public String adminPanel(HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        List<PendingView> pending = reservationRepository
                .findByStatus(ReservationStatus.PENDING_APPROVAL)
                .stream()
                .map(reservation -> new PendingView(
                        reservation,
                        reservedSeatRepository.findByReservation_Id(reservation.getId())
                                .stream()
                                .map(reservedSeat -> new ScreeningController.SeatView(
                                        reservedSeat.getSeat().getId(),
                                        reservedSeat.getSeat().getRow(),
                                        reservedSeat.getSeat().getNumber(),
                                        true))
                                .toList()))
                .toList();
        model.addAttribute("pendingReservations", pending);
        return "admin";
    }

    @PostMapping("/admin/reservations/{reservationId}/approve")
    public String approve(@PathVariable Long reservationId,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        ReservationService.Outcome outcome = reservationService.approve(reservationId);
        flash(redirectAttributes, outcome);
        return "redirect:/admin";
    }

    @PostMapping("/admin/reservations/{reservationId}/reject")
    public String reject(@PathVariable Long reservationId,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        ReservationService.Outcome outcome = reservationService.reject(reservationId);
        flash(redirectAttributes, outcome);
        return "redirect:/admin";
    }

    private void flash(RedirectAttributes redirectAttributes, ReservationService.Outcome outcome) {
        if (outcome.success()) {
            redirectAttributes.addFlashAttribute("adminSuccess", outcome.message());
        } else {
            redirectAttributes.addFlashAttribute("adminError", outcome.message());
        }
    }

    record PendingView(Reservation reservation, List<ScreeningController.SeatView> seats) {
    }
}
