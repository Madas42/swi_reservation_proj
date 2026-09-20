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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "seat_row", "seat_number"}))
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private CinemaRoom room;
    @jakarta.persistence.Column(name = "seat_row", nullable = false)
    private int row;
    @jakarta.persistence.Column(name = "seat_number", nullable = false)
    private int number;

    protected Seat() {
    }

    public Seat(CinemaRoom room, int row, int number) {
        this.room = room;
        this.row = row;
        this.number = number;
    }

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return room.getId();
    }

    public int getRow() {
        return row;
    }

    public int getNumber() {
        return number;
    }
}
