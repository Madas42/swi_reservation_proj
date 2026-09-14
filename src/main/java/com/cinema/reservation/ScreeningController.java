package com.cinema.reservation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
public class ScreeningController {

    @GetMapping("/")
    public String screenings(Model model) {
        List<Screening> screenings = List.of(
                new Screening("Interstellar", LocalDateTime.now().plusDays(1).withHour(18).withMinute(0)),
                new Screening("Inception", LocalDateTime.now().plusDays(2).withHour(20).withMinute(30)),
                new Screening("The Dark Knight", LocalDateTime.now().plusDays(3).withHour(17).withMinute(45))
        );

        model.addAttribute("screenings", screenings);
        model.addAttribute("selectedDate", LocalDate.now());
        return "screenings";
    }

    record Screening(String filmTitle, LocalDateTime startTime) {}
}
