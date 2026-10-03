package com.cinema.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class ReservationPersistenceTest {

    @Autowired
    private FilmRepository filmRepository;
    @Autowired
    private CinemaRoomRepository cinemaRoomRepository;
    @Autowired
    private ScreeningRepository screeningRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    @Transactional
    void persistsDraftReservationAndLoadsItsRequestedSeatsFromDatabase() {
        Film film = filmRepository.save(new Film("Persistence Test Film", "Test", 90));
        CinemaRoom room = cinemaRoomRepository.save(new CinemaRoom("Persistence Room", 1, 3));
        Screening screening = screeningRepository.save(new Screening(
                film, room, LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusMinutes(90)));
        List<Seat> seats = seatRepository.saveAll(IntStream.rangeClosed(1, 3)
                .mapToObj(number -> new Seat(room, 1, number))
                .toList());

        Customer customer = customerRepository.save(new Customer(
                "Persistence User", "persistence@example.com"));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
        Reservation reservation = reservationRepository.save(new Reservation(
                customer, screening, ReservationStatus.DRAFT, expiresAt));
        reservation.requestSeats(List.of(seats.get(0), seats.get(1)));
        reservationRepository.save(reservation);

        reservationRepository.flush();

        Reservation loaded = reservationRepository
                .findByIdAndCustomer_Id(reservation.getId(), customer.getId())
                .orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(ReservationStatus.DRAFT);
        assertThat(loaded.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(loaded.getRequestedSeatIds()).containsExactlyInAnyOrder(
                seats.get(0).getId(), seats.get(1).getId());
    }
}
