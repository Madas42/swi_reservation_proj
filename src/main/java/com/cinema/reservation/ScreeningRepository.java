package com.cinema.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {
    List<Screening> findByStartTimeAfterOrderByStartTime(LocalDateTime time);
}
