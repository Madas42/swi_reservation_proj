package com.cinema.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class ReservationPersistenceTest {

    @Autowired private FilmRepository filmRepository;
    @Autowired private CinemaRoomRepository cinemaRoomRepository;
    @Autowired private ScreeningRepository screeningRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservedSeatRepository reservedSeatRepository;

    @Test
    @Transactional
    void persistsReservationAndLoadsItsSeatsFromDatabase() {
        Film film = filmRepository.save(new Film("Persistence Test Film", "Test", 90));
        CinemaRoom room = cinemaRoomRepository.save(new CinemaRoom("Persistence Room", 1, 3));
        Screening screening = screeningRepository.save(new Screening(
                film, room, LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusMinutes(90)));
        List<Seat> seats = seatRepository.saveAll(IntStream.rangeClosed(1, 3)
                .mapToObj(number -> new Seat(room, 1, number)).toList());
        Customer customer = customerRepository.save(new Customer(
                "Persistence User", "persistence@example.com"));
        Reservation reservation = reservationRepository.save(new Reservation(
                customer, screening, ReservationStatus.PENDING,
                LocalDateTime.now().plusMinutes(30)));
        reservedSeatRepository.saveAll(List.of(
                new ReservedSeat(reservation, seats.get(0)),
                new ReservedSeat(reservation, seats.get(1))));

        reservationRepository.flush();
        reservedSeatRepository.flush();
        Reservation loaded = reservationRepository
                .findByIdAndCustomer_Id(reservation.getId(), customer.getId()).orElseThrow();
        Set<Long> assignedSeatIds = reservedSeatRepository.findByReservation_Id(loaded.getId())
                .stream().map(reservedSeat -> reservedSeat.getSeat().getId())
                .collect(Collectors.toSet());

        assertThat(loaded.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(loaded.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(assignedSeatIds).containsExactlyInAnyOrder(
                seats.get(0).getId(), seats.get(1).getId());
    }
}
