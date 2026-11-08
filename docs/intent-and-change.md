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

## C08 Step 3 / CR-08 — Controlled Change Card

Request source and need: manager — old undecided requests must not become valid bookings later.
Before: 3f8a2b1; spec REQ-03/BR-02 @ <spec-revision>; ADR <C03 link>; schema Hibernate; no secrets.
Old behavior: PENDING_APPROVAL without deadline; approval respects BR-02.
New behavior: after createdAt + 30 min no transition to CONFIRMED. Out of scope: new worker, UI redesign, technology change.
AC-01 approval before deadline succeeds; AC-02 after deadline no CONFIRMED; AC-03 exact semantics at 30:00; AC-04 BR-02 preserved.
Impact method: traced AC-02 from the status owner (call-site search at 3f8a2b1) through persistence and consumers; reviewed test suite incl. ReservationPersistenceTest.

| Dimension / exact artifact | CHANGE/CHECK/NO IMPACT + why | Delta/verification | Owner/evidence |
|---|---|---|---|
| Spec/BR/state | CHANGE — expiry rule; PENDING_APPROVAL → EXPIRED edge | define ACs, exact 30:00 boundary | <owner> reviewed spec |
| Architecture/ownership/interfaces | CHECK — single status writer (call-site search); admin/import/worker: NO IMPACT — this write path does not exist | confirm owner + all writer paths | <owner> C03/ADR link |
| Detailed design/code | CHANGE — guard now(clock) vs createdAt + TTL; injected Clock; idempotent retries | unit contract + rejection type | <owner> unit tests |
| Data/schema/migration | CHECK — no migration tool (Hibernate ddl-auto); EXPIRED enum; policy for old PENDING rows in ./data/cinema-reservation | enum change; test with pre-existing rows | <owner> ReservationPersistenceTest |
| Configuration/CI/release | CHECK — TTL configurable, documented default; server clock UTC | config smoke; units in README | <owner> |
| Tests/integration/risks | CHANGE — regression of approval + BR-02; boundary oracle before implementation | re-run TC-C08-01/02; expiry tests 29:59 / 30:00 / 30:01 | <owner> ./mvnw verify report |
| Operation/recovery | CHECK — existing pending rows reinterpretation; approval/expiry race; EXPIRED not reversible | rollback/compatibility in release record | <owner> |

Alternatives: (Step 4)
Inaction consequences: undecided requests remain approvable indefinitely.
Peer reviewer: (Step 5)
Decision ACCEPT/REJECT/DEFER/NO_CHANGE: (Step 5)
If ACCEPT: smallest delta + C09 tests: (Step 5)
If NO_CHANGE/REJECT: N/A
Open uncertainty: approval/expiry race — race test or declared residual risk.
