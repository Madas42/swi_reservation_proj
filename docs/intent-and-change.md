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