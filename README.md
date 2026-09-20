# Absolute Cinema  

## Setup
```
mvn spring-boot:run
```
Open on localhost:
http://localhost:8080/

## Database

The application uses an H2 file database stored in `./data/cinema-reservation`.
Hibernate creates the schema on startup and the application seeds one cinema
room with 40 seats, three films, and one upcoming screening for each film.

The core tables are `film`, `cinema_room`, `seat`, `screening`, `customer`,
`reservation`, and `reserved_seat`. A reserved seat is linked to both a
reservation and a physical seat, so the same seat can be reserved independently
for different screenings.


## CP1 walking skeleton

Pro náš rezervační systém kina definujeme následující end-to-end cestu pro CP1:

- **POST /reservations** (vytvoření rezervace pro dané představení a sedadla)
- **validate** (ověření dostupnosti sedadel)
- **persist** (uložení entity `Reservation` ve stavu `PENDING` do H2 DB)
- **return reservation ID** (vrácení ID vytvořené rezervace)
- **automated check** (ověření pomocí integration testu `ReservationPersistenceTest`)
