package com.cinema.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Notification Integration formats the outcome message of the admin approval
 * decision (accepted / rejected) and sends it to the external Notification
 * Service through the NotificationClient boundary. Delivery is retried a
 * limited number of times; a failure of the external service is only logged
 * and never propagated, so it can never roll back or revert an approval that
 * has already been persisted.
 */
@Service
public class NotificationIntegration {
    static final int MAX_SEND_ATTEMPTS = 3;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Logger log = LoggerFactory.getLogger(NotificationIntegration.class);

    private final NotificationClient notificationClient;

    public NotificationIntegration(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    public void notifyOutcome(Reservation reservation, boolean approved) {
        String subject = "Reservation update";
        String body = (approved
                ? "Your reservation has been approved and confirmed. "
                : "Your reservation has been rejected and your seats released. ")
                + "Reservation #" + reservation.getId()
                + " for " + reservation.getScreening().getFilm().getTitle()
                + " on " + reservation.getScreening().getStartTime().format(DATE_TIME) + ".";
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                notificationClient.send(reservation.getCustomer().getEmail(), subject, body);
                return;
            } catch (Exception e) {
                log.warn("Notification delivery attempt {}/{} failed for reservation {}: {}",
                        attempt, MAX_SEND_ATTEMPTS, reservation.getId(), e.getMessage());
            }
        }
        log.error("Giving up on notification for reservation {} after {} attempts; "
                + "the reservation decision itself remains unchanged.",
                reservation.getId(), MAX_SEND_ATTEMPTS);
    }
}
