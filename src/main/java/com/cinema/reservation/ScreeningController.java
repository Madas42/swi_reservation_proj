package com.cinema.reservation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ScreeningController {
    private final ScreeningRepository screeningRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final SeatRepository seatRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;

    public ScreeningController(
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

    @GetMapping("/")
    public String screenings(Model model) {
        List<ScreeningView> screenings = screeningRepository
                .findByStartTimeAfterOrderByStartTime(LocalDateTime.now())
                .stream()
                .map(screening -> new ScreeningView(
                        screening.getId(),
                        screening.getFilm().getTitle(),
                        screening.getStartTime(),
                        screening.getRoom().getName(),
                        screening.getRoom().getCapacity()
                                - (int) reservedSeatRepository
                                .countByReservation_Screening_IdAndReservation_Status(
                                        screening.getId(), ReservationStatus.CONFIRMED)))
                .toList();

        model.addAttribute("screenings", screenings);
        model.addAttribute("selectedDate", LocalDate.now());
        return "screenings";
    }

    @GetMapping("/screenings/{screeningId}")
    public String screeningSeats(@PathVariable Long screeningId, Model model) {
        Screening screening = screeningRepository.findById(screeningId).orElse(null);
        if (screening == null) {
            return "redirect:/";
        }

        Set<Long> reservedSeatIds = reservedSeatRepository
                .findByReservation_Screening_IdAndReservation_Status(
                        screeningId, ReservationStatus.CONFIRMED)
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
    @Transactional
    public String reserveSeats(
            @PathVariable Long screeningId,
            @RequestParam(required = false) List<Long> seatIds,
            @RequestParam String customerName,
            @RequestParam String customerEmail,
            RedirectAttributes redirectAttributes) {
        Screening screening = screeningRepository.findById(screeningId).orElse(null);
        if (screening == null) {
            return "redirect:/";
        }

        List<Long> requestedSeatIds = seatIds == null ? List.of() : seatIds.stream().distinct().toList();
        List<Seat> seats = seatRepository.findAllById(requestedSeatIds);
        Set<Long> reservedSeatIds = reservedSeatRepository
                .findByReservation_Screening_IdAndReservation_Status(
                        screeningId, ReservationStatus.CONFIRMED)
                .stream()
                .map(reservedSeat -> reservedSeat.getSeat().getId())
                .collect(Collectors.toSet());

        boolean validCustomer = !customerName.isBlank() && !customerEmail.isBlank();
        boolean validSeats = !requestedSeatIds.isEmpty()
                && seats.size() == requestedSeatIds.size()
                && seats.stream().allMatch(seat -> seatIdsForRoom(seat, screening))
                && requestedSeatIds.stream().noneMatch(reservedSeatIds::contains);
        if (!validCustomer || !validSeats) {
            redirectAttributes.addFlashAttribute("reservationError",
                    "Please provide your details and choose only available seats.");
            return "redirect:/screenings/" + screeningId;
        }

        Customer customer = customerRepository.save(
                new Customer(customerName.trim(), customerEmail.trim()));
        Reservation reservation = reservationRepository.save(
                new Reservation(customer, screening, ReservationStatus.CONFIRMED));
        reservedSeatRepository.saveAll(
                seats.stream().map(seat -> new ReservedSeat(reservation, seat)).toList());

        redirectAttributes.addFlashAttribute("reservationSuccess",
                "Your seats have been reserved successfully.");
        return "redirect:/screenings/" + screeningId;
    }

    private boolean seatIdsForRoom(Seat seat, Screening screening) {
        return seat.getRoomId().equals(screening.getRoom().getId());
    }

    record ScreeningView(Long id, String filmTitle, LocalDateTime startTime,
                         String roomName, int availableSeats) {}

    record SeatView(Long id, int row, int number, boolean reserved) {}
}
