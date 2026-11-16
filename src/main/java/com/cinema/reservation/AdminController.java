package com.cinema.reservation;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
 * reservations awaiting approval, and the VALIDATE_RESERVATION operation.
 * The approve/reject transitions are requested from the Approval Workflow,
 * which owns the PENDING_APPROVAL state.
 */
@Controller
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    static final String SESSION_ATTRIBUTE = "adminLoggedIn";
    static final String ADMIN_USERNAME = "admin";
    static final String ADMIN_PASSWORD = "admin";

    private final ReservationRepository reservationRepository;
    private final SeatAvailability seatAvailability;
    private final ApprovalWorkflow approvalWorkflow;

    public AdminController(ReservationRepository reservationRepository,
                           SeatAvailability seatAvailability,
                           ApprovalWorkflow approvalWorkflow) {
        this.reservationRepository = reservationRepository;
        this.seatAvailability = seatAvailability;
        this.approvalWorkflow = approvalWorkflow;
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
        log.info("[HTTP] POST /admin/login (username={})", username);
        if (ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password)) {
            session.setAttribute(SESSION_ATTRIBUTE, Boolean.TRUE);
            log.info("[HTTP] POST /admin/login -> admin logged in");
            return "redirect:/admin";
        }
        log.warn("[HTTP] POST /admin/login -> invalid admin credentials");
        redirectAttributes.addFlashAttribute("adminError",
                "Invalid admin credentials.");
        return "redirect:" + (referer != null ? referer : "/");
    }

    @PostMapping("/admin/logout")
    public String logout(HttpSession session) {
        log.info("[HTTP] POST /admin/logout");
        session.removeAttribute(SESSION_ATTRIBUTE);
        log.info("[HTTP] POST /admin/logout -> admin logged out");
        return "redirect:/";
    }

    @GetMapping("/admin")
    public String adminPanel(HttpSession session, Model model) {
        log.info("[HTTP] GET /admin (admin panel)");
        if (!isLoggedIn(session)) {
            log.warn("[HTTP] GET /admin -> not logged in, redirecting to /");
            return "redirect:/";
        }
        List<PendingView> pending = reservationRepository
                .findByStatus(ReservationStatus.PENDING_APPROVAL)
                .stream()
                .map(reservation -> new PendingView(
                        reservation,
                        seatAvailability.allocationOf(reservation.getId())
                                .stream()
                                .map(reservedSeat -> new ScreeningController.SeatView(
                                        reservedSeat.getSeat().getId(),
                                        reservedSeat.getSeat().getRow(),
                                        reservedSeat.getSeat().getNumber(),
                                        true))
                                .toList()))
                .toList();
        model.addAttribute("pendingReservations", pending);
        log.info("[HTTP] GET /admin -> rendered admin panel with {} pending approval reservation(s)",
                pending.size());
        return "admin";
    }

    @PostMapping("/admin/reservations/{reservationId}/approve")
    public String approve(@PathVariable Long reservationId,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        log.info("[HTTP] POST /admin/reservations/{}/approve", reservationId);
        if (!isLoggedIn(session)) {
            log.warn("[HTTP] POST /admin/reservations/{}/approve -> not logged in, redirecting to /",
                    reservationId);
            return "redirect:/";
        }
        try {
            ReservationManagement.Outcome outcome = approvalWorkflow.approve(reservationId);
            log.info("[HTTP] POST /admin/reservations/{}/approve -> success={}, message={}",
                    reservationId, outcome.success(), outcome.message());
            flash(redirectAttributes, outcome);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("[HTTP] POST /admin/reservations/{}/approve -> concurrent modification: {}",
                    reservationId, e.getMessage());
            redirectAttributes.addFlashAttribute("adminError",
                    "The reservation was just modified by another process, please retry.");
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/reservations/{reservationId}/reject")
    public String reject(@PathVariable Long reservationId,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        log.info("[HTTP] POST /admin/reservations/{}/reject", reservationId);
        if (!isLoggedIn(session)) {
            log.warn("[HTTP] POST /admin/reservations/{}/reject -> not logged in, redirecting to /",
                    reservationId);
            return "redirect:/";
        }
        ReservationManagement.Outcome outcome = approvalWorkflow.reject(reservationId);
        log.info("[HTTP] POST /admin/reservations/{}/reject -> success={}, message={}",
                reservationId, outcome.success(), outcome.message());
        flash(redirectAttributes, outcome);
        return "redirect:/admin";
    }

    private void flash(RedirectAttributes redirectAttributes,
                       ReservationManagement.Outcome outcome) {
        if (outcome.success()) {
            redirectAttributes.addFlashAttribute("adminSuccess", outcome.message());
        } else {
            redirectAttributes.addFlashAttribute("adminError", outcome.message());
        }
    }

    record PendingView(Reservation reservation, List<ScreeningController.SeatView> seats) {
    }
}
