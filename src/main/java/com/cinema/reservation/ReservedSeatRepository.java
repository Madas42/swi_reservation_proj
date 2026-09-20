package com.cinema.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReservedSeatRepository extends JpaRepository<ReservedSeat, Long> {
    List<ReservedSeat> findByReservation_Id(Long reservationId);

    long countByReservation_Screening_IdAndReservation_Status(
            Long screeningId, ReservationStatus status);

    List<ReservedSeat> findByReservation_Screening_IdAndReservation_Status(
            Long screeningId, ReservationStatus status);
}
