package com.cinema.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@SpringBootApplication
public class CinemaReservationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CinemaReservationApplication.class, args);
    }

    @Bean
    @Order(1)
    CommandLineRunner seedDatabase(
            FilmRepository filmRepository,
            CinemaRoomRepository roomRepository,
            SeatRepository seatRepository) {
        return args -> {
            if (filmRepository.count() > 0) {
                return;
            }

            filmRepository.save(new Film(
                    "Interstellar", "A journey beyond the stars.", 169));
            filmRepository.save(new Film(
                    "Inception", "A thief who enters the dreams of others.", 148));
            filmRepository.save(new Film(
                    "The Dark Knight", "Batman faces a criminal mastermind.", 152));

            CinemaRoom room = roomRepository.save(new CinemaRoom("Main Hall", 5, 8));
            seatRepository.saveAll(IntStream.rangeClosed(1, 5)
                    .boxed()
                    .flatMap(row -> IntStream.rangeClosed(1, 8)
                            .mapToObj(number -> new Seat(room, row, number)))
                    .toList());
        };
    }

    @Bean
    @Order(2)
    CommandLineRunner seedDailyScreenings(
            FilmRepository filmRepository,
            CinemaRoomRepository roomRepository,
            ScreeningRepository screeningRepository) {
        return args -> {
            List<CinemaRoom> rooms = roomRepository.findAll();
            if (rooms.isEmpty()) {
                return;
            }
            CinemaRoom room = rooms.get(0);
            for (Film film : filmRepository.findAll()) {
                for (int day = 1; day <= 7; day++) {
                    LocalDateTime dayStart = LocalDate.now().plusDays(day).atStartOfDay();
                    if (!screeningRepository.findByFilm_IdAndStartTimeBetween(
                            film.getId(), dayStart, dayStart.plusDays(1)).isEmpty()) {
                        continue;
                    }
                    LocalDateTime start = dayStart.plusHours(startHourOf(film.getTitle()));
                    screeningRepository.save(new Screening(
                            film, room, start, start.plusMinutes(film.getDurationMinutes())));
                }
            }
        };
    }

    private static int startHourOf(String title) {
        return switch (title) {
            case "The Dark Knight" -> 15;
            case "Interstellar" -> 18;
            case "Inception" -> 21;
            default -> 12;
        };
    }

    @Bean
    @Order(3)
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
