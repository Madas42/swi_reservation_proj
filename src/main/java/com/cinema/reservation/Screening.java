package com.cinema.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;

@Entity
public class Screening {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "film_id")
    private Film film;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "room_id")
    private CinemaRoom room;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    protected Screening() {
    }

    public Screening(Film film, CinemaRoom room, LocalDateTime startTime, LocalDateTime endTime) {
        this.film = film;
        this.room = room;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Film getFilm() {
        return film;
    }

    public Long getId() {
        return id;
    }

    public CinemaRoom getRoom() {
        return room;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }
}
