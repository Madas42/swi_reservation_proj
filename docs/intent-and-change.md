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
Refund 30 mins before screening is not allowed.

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

## C08 Step 4

**Alternative Analysis :**
| Varianta | Jak řeší potřebu (Splnění AC) | Výhody | Náklady / rizika | Jak ověříme |
| :--- | :--- | :--- | :--- | :--- |
| **A: Lazy expiry (Guard v ApprovalWorkflow)** | Kontrola času se přidá do `approve()`. Opožděný pokus admina po uplynutí TTL uloží `EXPIRED`. | Nejtriviálnější úprava. Zcela bez asynchronních procesů. | Nevyřízené rezervace zůstanou viset v DB, dokud na ně admin neklikne. Sál se proaktivně neuvolní. | Test `approve()` těsně před a po hranici TTL. |
| **B: Využití existujícího ExpirationManageru** | Guard v `approve()` + rozšíření stávajícího `@Scheduled 60 s` workeru, aby čistil i `PENDING_APPROVAL`. | Sál se uvolňuje sám. Nevyžaduje vývoj *nového* workeru (recykluje stávající). | Zavedení race condition (souběhu) mezi kliknutím admina a běžícím workerem ve stejnou vteřinu. | Test souběhu (`approve` vs. worker). |
| **C: Procesní upozornění (Bez úpravy kódu)** | Backend se nemění, instruuje se personál, že žádosti starší než TTL nesmí potvrdit. | Nezasahuje se do kódu. Bez rizika regrese. | Nesplňuje AC-02, systém technicky dál umožňuje omylem schválit expirovanou žádost. | Prověření provozního manuálu kina. |

## C08 Step 5

**Inaction consequences / feasible compensation:** 
Nevyřízené žádosti (`PENDING_APPROVAL`) zůstanou schválitelné donekonečna.

**Peer reviewer and challenged assumption:** 

**Decision ACCEPT / REJECT / DEFER / NO_CHANGE, owner, date, reason:** 
`ACCEPT`, Vlastník: Tým C08, Datum: 2.11.2026, Důvod: Varianta B plní všechna akceptační kritéria a proaktivně uvolňuje kapacity kina pomocí rozšíření již existujícího skriptu.

**If ACCEPT: smallest change and acceptance tests to perform in C09:**
*   **Nejmenší změna (Smallest delta):** 
    1. Přidat časový guard do `ApprovalWorkflow.approve` (porovnání s konfigurovatelným TTL pro pending). 
    2. Rozšířit existující `ExpirationManager` (@Scheduled) o zpracování stavu `PENDING_APPROVAL`.
*   **Akceptační testy pro C09:** 
    1. Hranice (Boundary testy pro přesný okamžik vypršení zvoleného TTL). 
    2. Regrese původního schvalování (`approvePendingReservationConfirmsIt`). 
    3. Test souběhu (Race condition test) pro střet mezi workerem a adminem.

**If NO_CHANGE/REJECT:** 
N/A

**Open uncertainty and next trigger:** 
1. **Chybějící přesná hodnota TTL pro pending rezervace** 
2. **Approval/expiry race condition**



## C09 — Integration, CI and Release Record

**C08 Change Card and reviewed ACCEPT/NO_CHANGE/REJECT rationale:** 
ACCEPT (schváleno v C08: zavedení 30minutového TTL pro PENDING_APPROVAL žádosti, proaktivní exspirace přes ExpirationManager a oprava asynchronního zpracování požadavků).

**Before and after commit/tag (identical if no-change):** 
Before: `70931c8`, After: `2a3b37d4c661f5b9f49bd8c1108a0cbf697e66ac` (implementační commit CR-08: "CR-08: 30-minute approval TTL for PENDING_APPROVAL (guard + ExpirationManager extension + optimistic locking)")

**Affected claims/new AC and unchanged regression basis:** 
Životní cyklus PENDING_APPROVAL. Nová AC: AC-01 (schválení před 30 min -> CONFIRMED), AC-02 (pokus o schválení po 30 min -> EXPIRED), AC-03 (chování na časové hranici). Regresní základ: BR-02 (zákaz překryvu potvrzených rezervací).

**Changed code/data/config/ADR and justified N/A:** 
Časový guard v `ApprovalWorkflow.approve()`, rozšíření `ExpirationManager` (@Scheduled), oprava asynchronního neblokujícího zpracování požadavků. N/A: Uživatelské rozhraní a notifikační služba se nemění.

**Peer review: reviewer, challenged assumption, disposition:** 
[nevyplněno / neproběhlo]

| Test ID | Independent accepted oracle | Exact command/data | Expected | Actual | PASS/FAIL/BLOCKED | Evidence |
|---|---|---|---|---|---|---|
| New behavior | TC-CR08-03 (Boundary TTL) | `mvn test` — `approveJustBeforeDeadlineConfirmsReservation` (29:59), `approveAtExactDeadlineExpiresReservation` (30:00), `approveJustAfterDeadlineExpiresReservation` (30:01) | Odmítnutí schválení po 30 min; na hranici (now >= expiresAt) EXPIRED, těsně před hranicí CONFIRMED | Těsně před hranicí CONFIRMED se zachovanými sedadly; na hranici i po ní EXPIRED s uvolněnými sedadly | PASS | `mvn clean test`: Tests run: 28, Failures: 0, Errors: 0; `target/surefire-reports/com.cinema.reservation.ReservationLifecycleTest.txt` |
| C08 regression TC-01 | approvePendingReservationConfirmsIt | `mvn test` | Schválení před uplynutím limitu | success=true, stav CONFIRMED, sedadla alokována | PASS | `mvn clean test`: Tests run: 28, Failures: 0, Errors: 0; `target/surefire-reports/com.cinema.reservation.ReservationLifecycleTest.txt` |
| C08 regression TC-02 | confirmRejectsSeatTakenByAnotherConfirmedReservation | `mvn test` | Zákaz překryvu sedadel dle BR-02 | Druhé potvrzení odmítnuto, rezervace zůstala DRAFT bez alokace | PASS | `mvn clean test`: Tests run: 28, Failures: 0, Errors: 0; `target/surefire-reports/com.cinema.reservation.ReservationLifecycleTest.txt` |
| New behavior AC-02 | approveAfterDeadlineExpiresReservationAndReleasesSeats | `mvn test` | Po deadline: nikdy CONFIRMED; EXPIRED + uvolněná sedadla | success=false, stav EXPIRED, `reserved_seat` řádky smazány | PASS | `mvn clean test`: Tests run: 28, Failures: 0, Errors: 0; `target/surefire-reports/com.cinema.reservation.ReservationLifecycleTest.txt` |
| New behavior (worker) | backgroundJobExpiresStalePendingApproval | `mvn test` | Sweep ExpirationManageru expiruje PENDING_APPROVAL po deadline a uvolní sedadla | Stav EXPIRED, sedadla uvolněna | PASS | `mvn clean test`: Tests run: 28, Failures: 0, Errors: 0; `target/surefire-reports/com.cinema.reservation.ReservationLifecycleTest.txt` |
| New behavior (legacy) | backgroundJobAssignsDeadlineToLegacyPendingApprovalRowAndExpiresIt | `mvn test` | Řádky PENDING_APPROVAL s expiresAt == null dostanou při prvním sweep deadline createdAt + 30 min a expirují | Deadline přiřazen z createdAt, stav EXPIRED, sedadla uvolněna | PASS | `mvn clean test`: Tests run: 28, Failures: 0, Errors: 0; `target/surefire-reports/com.cinema.reservation.ReservationLifecycleTest.txt` |
| New behavior (race — deterministická simulace) | approveAndExpirationWorkerRaceNeverLeavesInconsistentState | `mvn test` | Sekvenční simulace obou prokládání approve/worker: vždy konzistentní stav, nikdy CONFIRMED po deadline, žádná dvojitá alokace (vlákna běží za sebou, optimistický zámek se neuplatní) | Approve-first -> CONFIRMED (přesně 12 alokací), worker-first -> EXPIRED (0 držených sedadel) | PASS | `mvn clean test`: Tests run: 28, Failures: 0, Errors: 0; `target/surefire-reports/com.cinema.reservation.ReservationLifecycleTest.txt` |
| New behavior (race — skutečný souběh) | concurrentApproveAndWorkerSweepIsSafeUnderOptimisticLocking | `mvn test` | Skutečný souběh dvou vláken (CyclicBarrier, commitnuté fixture): approve vs. sweep; optimistický zámek (@Version) zajistí, že vyhraje právě jeden zapisovatel, konflikt je zachycen a stav zůstává konzistentní | Vždy přesně jeden z {CONFIRMED (sedadla držena), EXPIRED (sedadla uvolněna)}; konflikt optimistického zámku zachycen bez nekonzistence | PASS | `mvn clean test`: Tests run: 28, Failures: 0, Errors: 0; `target/surefire-reports/com.cinema.reservation.ReservationLifecycleTest.txt` |
| New behavior AC-04 | seatsCanBeRebookedAfterPendingApprovalExpiry | `mvn test` | Po expiraci pending rezervace lze její sedadla znovu zarezervovat; BR-02 zachováno | Druhý zákazník úspěšně CONFIRMED na uvolněném sedadle (jediná alokace) | PASS | `mvn clean test`: Tests run: 28, Failures: 0, Errors: 0; `target/surefire-reports/com.cinema.reservation.ReservationLifecycleTest.txt` |

**Actual integration boundary and committed-state check:** 
Lokální H2 souborová databáze (`./data/cinema-reservation`), schéma spravované přes Hibernate (`ddl-auto=update`).

**Dependency lock, runtime, DB schema, nonsecret config:** 
Java 17 (source/target), Spring Boot, Maven, H2 databáze, bez citlivých údajů v kódu. Běhové prostředí finálního testu: OpenJDK 27.0.1.0.3 (Homebrew), Apache Maven 3.10.0, macOS 15 aarch64 (viz CI log níže).

**Clean checkout/build/test/run commands and environment:** 
`mvn clean test`, `mvn spring-boot:run` (lokální prostředí).

**CI job or local automated script and actual log:** 
Lokální automatizovaný skript `mvn clean test` (Apache Maven 3.10.0, OpenJDK 27.0.1.0.3/Homebrew — zdrojový kód projektu cílí Java 17, H2 souborová databáze `./data/cinema-reservation`, macOS 15, aarch64). Skutečný log z finálního běhu (2026-11-15):

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.cinema.reservation.ReservationPersistenceTest
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0 -- in com.cinema.reservation.ReservationLifecycleTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Independent reproducer, exact steps and outcome:** 
1. Clean checkout repozitáře na commitu `2a3b37d4c661f5b9f49bd8c1108a0cbf697e66ac`.
2. `mvn clean test` — všechny CR-08 testy procházejí: `approvePendingReservationConfirmsIt` (AC-01), `approveAfterDeadlineExpiresReservationAndReleasesSeats` (AC-02), `approveJustBeforeDeadlineConfirmsReservation` / `approveAtExactDeadlineExpiresReservation` / `approveJustAfterDeadlineExpiresReservation` (AC-03), `confirmRejectsSeatTakenByAnotherConfirmedReservation` + `pendingApprovalSeatsBlockOtherReservations` + `seatsCanBeRebookedAfterPendingApprovalExpiry` (AC-04), `backgroundJobExpiresStalePendingApproval`, `backgroundJobAssignsDeadlineToLegacyPendingApprovalRowAndExpiresIt`, `approveAndExpirationWorkerRaceNeverLeavesInconsistentState` (sekvenční simulace prokládání) a `concurrentApproveAndWorkerSweepIsSafeUnderOptimisticLocking` (skutečný souběh dvou vláken nad commitnutými daty). Výsledek: Tests run: 28, Failures: 0, Errors: 0, BUILD SUCCESS.
3. Manuální krok: `mvn spring-boot:run` → vytvořit rezervaci s více než 10 sedadly → confirm (stav PENDING_APPROVAL, sedadla držena) → nechat uplynout 30 minut (nebo počkat na sweep workeru, běží každých 60 s) → pokus o approve v admin panelu: rezervace přejde do EXPIRED, sedadla se uvolní a lze je znovu zarezervovat; approve před uplynutím limitu proběhne normálně (CONFIRMED).

**Artifact identity/file/hash/tag and migration assumptions:** 
Implementační commit: `2a3b37d4c661f5b9f49bd8c1108a0cbf697e66ac` (main). Souborová H2 databáze (`./data/cinema-reservation`), schéma spravované Hibernate `ddl-auto=update`. Migrace: nový sloupec `version` (BIGINT, nullable) na tabulce `reservation` přidaný anotací `@Version` — automaticky doplněn při startu aplikace, existující řádky mají NULL a Hibernate je při první aktualizaci inicializuje. Politika pro existující staré záznamy PENDING_APPROVAL bez explicitního `expiresAt`: při prvním sweep dostanou deadline `createdAt + 30 min` (PENDING_APPROVAL_TTL_MINUTES) a pokud už uplynul (staré záznamy), expirují ve stejném běhu — staré nerozhodnuté žádosti se nikdy později nestanou platnými rezervacemi.

**Rollback/recovery limitation, residual risks:** 
Přechod do stavu `EXPIRED` a exspirace dat v perzistentním úložišti jsou nevratné; případný rollback kódu automaticky nevrátí stav dat v databázi. Reziduální riziko: při souběhu admin approve a běžícího workeru může dojít ke konfliktu optimistického zámku (`ObjectOptimisticLockingFailureException`) — admin vidí "The reservation was just modified by another process, please retry." a musí akci zopakovat; sweep se opakuje při dalším běhu (60 s). Chování ověřeno skutečným souběžným testem (`concurrentApproveAndWorkerSweepIsSafeUnderOptimisticLocking`): konflikt optimistického zámku je vždy bezpečně zachycen, vyhraje právě jeden zapisovatel a stav rezervace i alokace sedadel zůstávají konzistentní.

**Release decision READY/CONDITIONAL/HOLD, owner, rationale:** 
`READY`, Owner: Tým C08, Datum: 15.11.2026, Rationale: `mvn clean test` plně zelený (Tests run: 28, Failures: 0, Errors: 0, BUILD SUCCESS); všechna akceptační kritéria AC-01..AC-04 ověřena automatizovanými testy včetně hraničních případů, legacy záznamů a souběhu approve/worker.

