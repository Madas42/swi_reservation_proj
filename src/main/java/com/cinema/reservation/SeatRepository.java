package com.cinema.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByRoom_IdOrderByRowAscNumberAsc(Long roomId);
}
