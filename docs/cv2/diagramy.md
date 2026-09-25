9a. Diagram případů užití — aktéři a cíle




9b. Stavový diagram životního cyklu Reservation

[initial] --create [OP-01: showtime not started & seats free]--> DRAFT
DRAFT --confirm [OP-03: state=DRAFT & not expired & no seat conflict (BR-02)]--> CONFIRMED
DRAFT --cancel [OP-04: user request OR TTL expired]--> CANCELLED
CONFIRMED --cancel [OP-04: cancellation time limit met (BR-03)]--> CANCELLED


9c. Diagram aktivit
