package com.cinema.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter for the external Notification Service. The real service is not
 * available in this course project, so delivery is simulated by logging the
 * message; the boundary (and the retry/failure handling in
 * NotificationIntegration) is what the architecture relies on.
 */
@Component
public class LoggingNotificationClient implements NotificationClient {
    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationClient.class);

    @Override
    public void send(String recipientEmail, String subject, String body) {
        log.info("Simulated notification to {}: [{}] {}", recipientEmail, subject, body);
    }
}
