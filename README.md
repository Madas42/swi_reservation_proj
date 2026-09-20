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
