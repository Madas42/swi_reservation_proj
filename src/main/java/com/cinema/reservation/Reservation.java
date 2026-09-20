package com.cinema.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;

@Entity
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "screening_id")
    private Screening screening;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    protected Reservation() {
    }

    public Reservation(Customer customer, Screening screening, ReservationStatus status) {
        this.customer = customer;
        this.screening = screening;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Reservation(Customer customer, Screening screening, ReservationStatus status,
                       LocalDateTime expiresAt) {
        this(customer, screening, status);
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public Screening getScreening() {
        return screening;
    }
}
