package com.cinema.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    Optional<Reservation> findByIdAndCustomer_Id(Long reservationId, Long customerId);
}
