package com.cinema.reservation;

/**
 * Boundary to the external Notification Service. Anything on the other side
 * of this interface is outside the reservation application's trust and
 * failure boundary: calls may fail at any time and must never be allowed to
 * roll back a reservation decision already stored in the database.
 */
public interface NotificationClient {

    void send(String recipientEmail, String subject, String body);
}
