# Project Frame

## Reservation domain
Rezeravce lístků s místem v kině.

## Purpose
- slouží pro rezervaci a nákup lítků klienty kina
- šetří čas klientů i zaměstnanců
- možnost slev, benefitů v kině

## Users / Stakeholders
- zákaznící / klienti
- pokladní
- admin

## Core concepts
- Reservation
- Cinema screening
- User
- Screening room

## Core operations
- Create reservation
- Confirm / approve reservation
- Cancel reservation
- Check availability

## Persistent state
### Reservation 
- name
- id_screeningu
- seat_numbers

### Resource - Cinema_screenig
- id_film
- time
- id_room

## State-changing operation
- výběr sedadel (state_of_seat)
- validace dostupnosti (seat_occupation)
- zarezervování místa (CONFIRMED)

## Common business rule
Confirmed reservations for the same resource must not overlap.

## Domain-specific business rule
Refund 30 mins before screening.

## External / system boundary
Notification Service.

## Assumption
Zákaznící budou platit online.

## Unknown
Rozhodnutí o přednosti rezervace ve stejný čas.

## Selected future pressure
Category: Q
Concrete pressure: Více zákazníků v jeden čas.
Why it is relevant to our reservation system: Reliable reservation system.

## C08 / Step 1 — Change Request & Acceptance Conditions

* **Zdroj a potřeba:** Správce kinofiltru / administrátor: stará nevyřízená žádost o rezervaci (např. ve stavu PENDING_APPROVAL) nesmí zůstatviset v systému neomezeně dlouho a blokovat sedadla pro ostatní diváky. Po uplynutí časového limitu (např. 30 minut) musí vypršet.
* **Předchozí chování:** Žádost ve stavu PENDING_APPROVAL zůstávala platná bez časového omezení, dokud ji správce ručně neschválil nebo nezrušil.
* **AC-01 (pozitivní):** Schválení (Confirm/Approve) žádosti před uplynutím 30minutového limitu při splnění ostatních podmínek proběhne úspěšně a změní stav na CONFIRMED.
* **AC-02 (negativní):** Pokus o schválení žádosti po uplynutí 30 minut nesmí vytvořit stav CONFIRMED; systém požadavek odmítne a stav se odpovídajícím způsobem aktualizuje (např. na EXPIRED).
* **AC-03 (hranice):** Přesný okamžik na hranici limitu (createdAt + 30 min) má jednoznačně definované a otestované očekávané chování.
* **AC-04 (regrese):** Základní invariant systému (dávej pozor na BR-02: dvě potvrzené rezervace stejného sedadla/zdroje se nesmí překrývat) zůstává plně zachován a nedotčen.
* **Mimo rozsah:** Automatický pozadový proces (cron worker / timer), který by čistil databázi proaktivně, nebo redesign uživatelského rozhraní – o těchto technických mechanismech se v tomto kroku nerozhoduje.


## C08 Step 3 / CR-08 — Controlled Change Card

Request source and need: manager — old undecided requests must not become valid bookings later.

Before: 70931c8 (last code commit; 3f8a2b1 was the assignment's demo value, not a real commit); spec REQ-03/BR-02 @ <spec-revision>; ADR <C03 link>; schema Hibernate ddl-auto=update; H2 file ./data/cinema-reservation; no secrets.

Old behavior: PENDING_APPROVAL (> 10 seats, seats already allocated at confirm) without deadline; approve via ApprovalWorkflow / AdminController POST /admin/reservations/{id}/approve.

New behavior: after deadline no PENDING_APPROVAL -> CONFIRMED; late attempt stores EXPIRED (enum value already exists for expired drafts). Out of scope: new worker, UI redesign, technology change.

AC-01 approval before deadline succeeds; AC-02 after deadline no CONFIRMED; AC-03 exact semantics at the boundary; AC-04 BR-02 preserved.

Impact method: traced AC-02 from ApprovalWorkflow.approve through ReservationRepository / ReservedSeatRepository to consumers (AdminController, NotificationIntegration); reviewed ReservationLifecycleTest at 70931c8.

| Dimension / exact artifact | CHANGE/CHECK/NO IMPACT and why | Delta/verification | Owner/evidence |
|---|---|---|---|
| Spec/BR/state | CHANGE — new PENDING_APPROVAL -> EXPIRED edge; EXPIRED already exists for expired DRAFTs | define ACs, exact boundary semantics | <owner>; reviewed spec |
| Architecture/ownership/interfaces | CHECK — status writers: ReservationManagement (lifecycle), ApprovalWorkflow (approve/reject); seat conflicts protected by DB unique constraint (screening_id, seat_id) incl. direct SQL; admin/import/worker: NO IMPACT — this write path does not exist | call-site search of status writes | <owner>; C03/ADR link |
| Detailed design/code | CHANGE — guard in ApprovalWorkflow.approve: deadline = accepted timestamp + TTL, Clock/now injectable; late repeated attempt ends EXPIRED | unit contract + outcome message | <owner>; ReservationLifecycleTest |
| Data/schema/migration | CHECK — no migration tool (ddl-auto=update); policy for existing PENDING_APPROVAL rows in ./data/cinema-reservation; Reservation.expiresAt persisted (LocalDateTime) | test with pre-existing rows; verify on regenerated schema | <owner> |
| Configuration/CI/release | CHECK — new TTL constant per existing pattern (DRAFT_TTL_MINUTES=15, CANCEL_DEADLINE_MINUTES=30 in ReservationManagement); times are LocalDateTime server-local — document | documented default; config smoke | <owner> |
| Tests/integration/risks | CHANGE — regression of approval + BR-02; boundary oracle before implementation | re-run approvePendingReservationConfirmsIt, confirmRejectsSeatTakenByAnotherConfirmedReservation, pendingApprovalSeatsBlockOtherReservations, approveAndRejectOnlyWorkOnPendingApproval; add expiry tests at 29:59 / 30:00 / 30:01 | <owner>; mvn test report |
| Operation/recovery | CHECK — pending rows in file DB become non-approvable after release; approval/expiry race; EXPIRED not reversible | rollback/compatibility in release record | <owner> |

Alternatives: (Step 4 — note: DRAFT expiry infra already exists: ReservationManagement.isDraftExpired + ExpirationManager @Scheduled 60 s)

Inaction consequences: undecided requests remain approvable indefinitely.

Peer reviewer: (Step 5)

Decision ACCEPT/REJECT/DEFER/NO_CHANGE: (Step 5)

If ACCEPT: smallest delta + C09 tests: (Step 5)

If NO_CHANGE/REJECT: N/A

Open uncertainty: approval/expiry race — race test or declared residual risk.
