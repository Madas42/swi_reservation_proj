package com.cinema.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@SpringBootApplication
public class CinemaReservationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CinemaReservationApplication.class, args);
    }

    @Bean
    CommandLineRunner seedDatabase(
            FilmRepository filmRepository,
            CinemaRoomRepository roomRepository,
            SeatRepository seatRepository,
            ScreeningRepository screeningRepository) {
        return args -> {
            if (filmRepository.count() > 0) {
                return;
            }

            Film interstellar = filmRepository.save(new Film(
                    "Interstellar", "A journey beyond the stars.", 169));
            Film inception = filmRepository.save(new Film(
                    "Inception", "A thief who enters the dreams of others.", 148));
            Film darkKnight = filmRepository.save(new Film(
                    "The Dark Knight", "Batman faces a criminal mastermind.", 152));

            CinemaRoom room = roomRepository.save(new CinemaRoom("Main Hall", 5, 8));
            seatRepository.saveAll(IntStream.rangeClosed(1, 5)
                    .boxed()
                    .flatMap(row -> IntStream.rangeClosed(1, 8)
                            .mapToObj(number -> new Seat(room, row, number)))
                    .toList());

            LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
            screeningRepository.save(new Screening(
                    interstellar, room, tomorrow.withHour(18).withMinute(0),
                    tomorrow.withHour(20).withMinute(49)));
            screeningRepository.save(new Screening(
                    inception, room, tomorrow.plusDays(1).withHour(20).withMinute(30),
                    tomorrow.plusDays(1).withHour(22).withMinute(58)));
            screeningRepository.save(new Screening(
                    darkKnight, room, tomorrow.plusDays(2).withHour(17).withMinute(45),
                    tomorrow.plusDays(2).withHour(20).withMinute(17)));
        };
    }

    @Bean
    CommandLineRunner seedExampleReservation(
            CustomerRepository customerRepository,
            ReservationRepository reservationRepository,
            ScreeningRepository screeningRepository,
            SeatRepository seatRepository,
            ReservedSeatRepository reservedSeatRepository) {
        return args -> {
            if (reservationRepository.count() > 0 || screeningRepository.count() == 0) {
                return;
            }

            Screening screening = screeningRepository.findAll().get(0);
            List<Seat> seats = seatRepository
                    .findByRoom_IdOrderByRowAscNumberAsc(screening.getRoom().getId());
            Customer customer = customerRepository.save(
                    new Customer("Demo customer", "demo@example.com"));
            Reservation reservation = reservationRepository.save(
                    new Reservation(customer, screening, ReservationStatus.CONFIRMED));
            reservedSeatRepository.saveAll(List.of(
                    new ReservedSeat(reservation, seats.get(0)),
                    new ReservedSeat(reservation, seats.get(1))));
        };
    }
}
