package com.cinema.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"screening_id", "seat_id"}))
public class ReservedSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "screening_id")
    private Screening screening;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    protected ReservedSeat() {
    }

    public ReservedSeat(Reservation reservation, Seat seat) {
        this.reservation = reservation;
        this.screening = reservation.getScreening();
        this.seat = seat;
    }

    public Seat getSeat() {
        return seat;
    }
}
