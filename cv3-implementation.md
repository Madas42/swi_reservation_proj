# CV3 — Implemented TO-BE Changes (docs/cv3/to_be_delta.md)

This document summarizes the implementation of all rows marked **CHANGE** in
`docs/cv3/to_be_delta.md`, and what a user (customer or admin) actually
notices in the running application.

## What was implemented

The single all-in-one `ReservationService` was split into components, each
with one clearly owned responsibility (all in the flat `com.cinema.reservation`
package, as required by the project conventions):

| Component | Owns | Delta row |
|-----------|------|-----------|
| `ReservationManagement` | The reservation lifecycle: it is the only component that decides transitions — OP-01 createDraft, OP-03 confirm (DRAFT → CONFIRMED / PENDING_APPROVAL), OP-04 cancel, and DRAFT → EXPIRED (BR-03, BR-04, BR-05) | Reservation lifecycle ownership |
| `SeatAvailability` | The seat allocation state (ReservedSeat rows): the OP-02 seat-map projection (`checkAvailability`), the authoritative BR-02 decision + allocation (`allocateIfAvailable`, check and write in one transaction, backed by the DB unique constraint), and release of seats on cancel/reject/expire | OP-02 seat availability |
| `ApprovalWorkflow` | The deferred approval state (OP-05): the only component performing PENDING_APPROVAL → CONFIRMED / REJECTED; rejecting releases the seats; both decisions trigger an outcome notification | Deferred approval (> 10 seats) |
| `ExpirationManager` | A `@Scheduled` TTL background job (every 60 s) that expires stale DRAFT reservations in the background and frees their seats. The EXPIRED transition itself is still decided by `ReservationManagement` | DRAFT expiration (TTL) |
| `NotificationIntegration` | Formats the approve/reject outcome message and sends it through the `NotificationClient` boundary; retries 3 times; a failure is only logged and never propagates — it can never roll back an approval already stored in the DB | Notification of outcome |
| `NotificationClient` + `LoggingNotificationClient` | The boundary to the external Notification Service. The real service does not exist in this course project, so delivery is simulated by logging the message; the boundary, retry and failure isolation are real | External Notification Service integration |

Supporting changes:

- `ScreeningController` and `AdminController` no longer read the seat
  allocation tables directly — all seat-map, capacity and allocation reads go
  through `SeatAvailability`.
- `ReservationRepository` gained `findByStatusAndExpiresAtBefore` for the TTL job.
- `CinemaReservationApplication` enables Spring scheduling (`@EnableScheduling`).
- `ReservationService` was deleted; all its logic lives on in the components above.
- `AGENTS.md` was updated to describe the new structure.

## What changed from the user side

The routes, pages, forms and all user-facing messages are unchanged — the
refactoring was internal. The observable differences are:

1. **Expired drafts free seats automatically.** Previously a DRAFT was only
   marked EXPIRED when the customer opened its detail page or tried to
   confirm it. Now the background job expires it within about a minute of the
   15-minute TTL running out, so the seat map and remaining-capacity counts
   on the home page reflect the freed seats even if the customer never
   returns. The lazy checks remain as a fallback and behave exactly as before.

2. **The customer is informed about the admin decision.** When the admin
   approves or rejects a large reservation (> 10 seats), an outcome message
   is now formatted and sent through the notification boundary. Because the
   external Notification Service does not exist in this project, the message
   is simulated (logged by `LoggingNotificationClient`) — the customer still
   sees the result on their reservation page as before, but the sending path,
   including retry and failure isolation, is implemented and tested.

3. **A failing notification never breaks a decision.** If the external
   service were down, the approval/rejection is still persisted; the system
   retries delivery three times and then only logs that it gave up.

4. **Admin flow is unchanged.** Login, the pending-approval panel and the
   approve/reject buttons work exactly as before; internally they now call
   the `ApprovalWorkflow` component.

## Verification

`mvn test` — 19 tests, all passing. Two new tests cover the new behavior:

- `backgroundJobExpiresStaleDraft` — the Expiration Manager job moves a stale
  DRAFT to EXPIRED.
- `approveSurvivesNotificationServiceFailure` — with the NotificationClient
  mocked to always fail, approve still succeeds, the reservation is CONFIRMED,
  and exactly 3 delivery attempts are made.
