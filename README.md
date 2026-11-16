# Absolute Cinema  

## Setup
```
mvn spring-boot:run
```
Open on localhost:
http://localhost:8080/

## Running tests
```
mvn test
```
Runs all tests (`ReservationPersistenceTest`, `ReservationLifecycleTest`)
against the real H2 database; tests are transactional and rolled back.

## Exact Commands (Dependencies, Schema, Test DB, Build/Test/Run & Smoke) (C09; 5)

- **1. Dependencies (stažení a ověření závislostí z `pom.xml`):**
  `mvn dependency:resolve`

- **2. Schema & Test DB (inicializace schématu a testovací H2 databáze):**
  Managed automatically via Hibernate (`ddl-auto=update`) on startup/test run — no manual SQL migration scripts required. To reset/recreate a clean file database (`./data/cinema-reservation`), delete `./data` and run:
  `mvn test-compile`

- **3. Build & Test (kompilace + unit a integrační testy nad reálnou H2 DB):**
  `mvn clean test`
  *(Runs `ReservationPersistenceTest` and `ReservationLifecycleTest` against the real H2 database; tests are transactional and rolled back. CI runs `mvn --batch-mode clean test`.)*

- **4. Package release artifact (vytvoření spustitelného JAR):**
  `mvn clean package`

- **5. Run application (spuštění serveru):**
  `mvn spring-boot:run`

- **6. Smoke check (ověření běžící aplikace po startu):**
  Open `http://localhost:8080/` in a browser (expected: cinema reservation UI loads with HTTP 200)

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
