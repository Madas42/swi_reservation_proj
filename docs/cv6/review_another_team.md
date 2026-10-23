# C06 — Assessment Form 

## Projekt

```text
Hodnotící tým: Absolute Cinema
Hodnocený tým: Team Deadlock
Projekt: Cinema Reservation System
Assessed commit/tag: 07e5018 (main)
Datum: 23.10.2026
```

## Stavy

`YES / PARTLY / NO / N/A`

Každý řádek musí obsahovat Evidence.

## Baseline

| ID | Odpověď | Evidence / komentář |
|---|---|---|
| B1 Stejná identifikovaná baseline pro docs/code/tests | **YES** | Evidence: Repozitář obsahuje jednotnou strukturu – dokumentace (`docs/`), zdrojový kód (`src/`) a testy (`src/test/`) odpovídají shodnému stavu a aktuální verzi projektu na hlavní větvi. |
| B2 Relevantní produkt nebo testy lze spustit | **YES** | Evidence: Projekt lze úspěšně sestavit a spustit testy pomocí standardních nástrojů pro správu závislostí a build (např. Maven/Gradle), přičemž testovací suite proběhne bez chyb. |

## C02 — Engineering Specification

Vybraná operace: OP-03 — Confirm Reservation  
Vybraný business rule / invariant: BR-02 — Exclusive Resource Invariant (No Double Booking)

| ID | Odpověď | Evidence / komentář |
| :--- | :--- | :--- |
| R1 Operation: input + precondition + postcondition/state | YES | Operace `Confirm Reservation` přijímá `reservationId`, vyžaduje, aby rezervace existovala, byla ve stavu `DRAFT`, uživatel byl jejím vlastníkem a projekce ještě nezačala (`currentTime < screening.startTime`). Po úspěšném provedení přechází stav na `CONFIRMED`. |
| R2 Business rule/invariant je jednoznačný | YES | Pravidlo `BR-02` jasně stanoví, že pro stejný zdroj `(Screening, Seat)` může existovat maximálně jedna rezervace ve stavu `CONFIRMED`. |
| R3 Operation/rule je konzistentní se state modelem | YES | Přechod `DRAFT -> CONFIRMED` přesně odpovídá stavovému modelu z dokumentace. |
| R4 Implementace odpovídá specifikované semantics | YES | Logika endpointu `POST /reservations/{id}/confirm` kontroluje kolize sedadel a vrací kód `409 Conflict` v případě duplicitní rezervace nebo začaté projekce. |
| R5 Existuje vhodná verification evidence | YES | V dokumentaci a testovacích scénářích jsou uvedeny konkrétní příklady (např. 200 OK při volných sedadlech, 409 Conflict při pokusu o potvrzení obsazeného sedadla). |


## C03 — Architecture

Vybraná responsibility: Manage pending expiration

Vybraná interaction: Atomic allocation attempt (DB enforces BR-02) při operaci Confirm Reservation.

Vybrané ADR/decision: Authoritative Resource Allocation under Concurrent Confirmation

| ID | Odpověď | Evidence / komentář |
|---|---|---|
| A1 Responsibility + owner jsou explicitní | YES | `C03.md` sekce C2 a G2 jasně definuje responsibility Manage pending expiration a jejího vlastníka Reservation Management |
| A2 Skutečný code ownership odpovídá architektuře | YES | Rozdělení v kódu přesně kopíruje návrh. Třída ExpirationSweep.java funguje jen jako časovač (Trigger) a ExpirationService.java řeší logiku |
| A3 Dynamic interaction odpovídá skutečnému call path | NO | Dynamická interakce v sekvenčním diagramu H1 počítá s tím, že databáze zamítne konflikt při alokaci (vynucení na úrovni DB). Skutečný call path v kódu (ReservationService) ale databázový zámek nemá a kolize kontroluje pouze aplikačním dotazem (countOtherAllocations) |
| A4 ADR/decision odpovídá současné implementaci | PARTLY | Podle `ADR-01` má exkluzivitu sedadel hlídat přímo databáze. Kód v ReservationService se tím ale zatím neřídí a řeší kolize jen na aplikační vrstvě. Tým se o tuto chybějící implementaci zmiňuje v gap analýze (bod J-01). |

## C04 — Quality & Measurement

Quality concern: Konkurenční integrita dat a atomická alokace (Atomic exclusive multi-seat allocation)
Criterion: Systém garantuje, že při paralelních požadavcích na potvrzení (Confirm) pro stejné sedadlo (Resource) získá stav CONFIRMED nejvýše jedna rezervace (ochrana BR-02) a ostatní obdrží chybu 409 Conflict.

| ID | Odpověď | Evidence / komentář |
| :--- | :--- | :--- |
| Q1 Criterion je skutečně evaluable | YES | Kritérium je jednoznačně ověřitelné testem souběžných HTTP požadavků s očekávaným návratovým kódem 409. |
| Q2 Measurement měří criterion ve správném contextu | YES | Měření probíhá v kontextu transakčního zpracování endpointu `/confirm` při reálném kolidujícím vstupu. |
| Q3 Decision odpovídá výsledku měření | PARTLY | Měření potvrzuje ochranu BR-02 (409 při kolizi), ale v kódu je zajištěná aplikačním pre-checkem pod screening zámkem, nikoli vynucením na úrovni databáze, jak požaduje ADR-01 (viz F1). |

## C05 — Detailed Design & Unit Verification

Vybraná software unit: **správa expirace čekající rezervace (pending expiration)** — jednotka, kterou tým v C05 skutečně rozpracoval (C03 C2, návaznost REQ-11/BR-08). Baseline: commit `07e5018` (main), C05 slice z větve `feature/c05-detailed-design` je do main začleněn. Návrh: `docs/architecture-and-decisions.md` od ř. 60; evidence: `docs/evidence-and-evolution.md` od ř. 78.

| ID | Odpověď | Evidence / komentář |
|---|---|---|
| D1 Design intent je explicitní a pochopitelný | YES | `architecture-and-decisions.md` ř. 60–240: odpovědnosti D1–D6, kontrakty, algoritmus, UML i sequence. |
| D2 DD odpovídá skutečné implementaci | YES | Kontrakty sedí na kód: `FinitePendingExpirationPolicy.java`, `ExpirationService.java`, `Reservation.java:59`, `NotificationService.java:11`. |
| D3 Unit/component tests jsou odvoditelné z DD | YES | Evidence (ř. 84) mapuje všech 14 testů na D1–D6, všechny v kódu existují (8+4+2). |
| D4 Relevantní branches/boundaries/states/failures jsou ověřeny | YES | Větve z DD (ř. 167) pokryty testy: timeout/cap, overflow, ns hranice, stavy, rollback, 59/60/61 s, souběhy. |
| D5 Vybraný test má smysluplný oracle/expected result | YES | `expiresExactlyAtDeadlineWithNanosecondPrecision`: oracle plyne z pravidla now >= deadline a BR-08, ne z kódu. |

## Candidate findings

### F1

```text
Source:
C03

Engineering claim:
Systém podle ADR-01 používá k zabránění dvojí rezervace stejného sedadla
autoritativní kontrolu přímo na úrovni databáze.

Expected:
Podle ADR-01 a diagramu H1 má databáze exkluzivitu sedadel vynucovat
(např. přes unique constraint) a při kolizi zápis zamítnout.

Observed:
Kód tuto databázovou kontrolu neimplementuje. Místo toho spoléhá
na aplikační pre-check (metoda countOtherAllocations() v ReservationService).

Evidence:
Zdrojový kód ReservationService.java, C03 (položka J-01); blíže
docs/c03.md ř. 114 a 150 (ADR-01 volí Alternative B — „database
authoritatively enforces exclusive Resource allocation“) a ř. 459 (J-01:
reservation_seats je unique pouze na (reservation_id, seat_id));
Reservation.java:24 potvrzuje jen tento constraint, žádnou DB kontrolu
aktivních alokací.

Verification performed:
inspect concrete code path — kontrola Reservation.java (unikátní
omezení), ReservationService.confirm() (countOtherAllocations pod
screening zámkem) a srovnání s textem ADR-01/J-01; nezávisle potvrzeno
v hodnoceném commitu.

Result: VERIFIED FINDING
```

### F2

```text
Source:
C05 — docs/evidence-and-evolution.md

Engineering claim:
Evidence tvrdí: „Lokální implementační a dokumentační commit je označen
tagem `c05-detailed-design-v1` na větvi `c05-detailed-design`.“

Expected:
Existující tag na existující větvi, reproduceovatelný přes
`git rev-parse c05-detailed-design-v1`.

Observed:
`git tag -l` je prázdné, `git ls-remote --tags origin` nevrací žádný tag.
Větev se jmenuje `feature/c05-detailed-design`, lokální `c05-detailed-design`
neexistuje. Finální commit `a061558` je v main bez tagu.

Evidence:
git tag -l; git ls-remote --tags origin; git branch -a.

Verification performed:
přímo uvedené příkazy git nad hodnoceným repozitářem; navíc ověření,
že commity C05 (`8c61c98`, `f5e38d2`, `a061558`) jsou začleněny v main.

Result: VERIFIED FINDING — deklarovaný tag ani větev v repozitáři
neexistují, baseline C05 je dohledatelná jen přes commity.
```

### F3

```text
Source:
C05 — docs/evidence-and-evolution.md, Tests derived from DD

Engineering claim:
Test `batchObtainsLockBeforeLoadingPendingAndUsesClock` dokládá
„Lock před načtením, jeden Clock snapshot; due a live rozlišeny“.

Expected:
Test skutečně ověří všechny tři části tvrzení, včetně jediného
snapshotu času.

Observed:
Test staví službu s `Clock.fixed(...)`. Fixní hodiny vracejí při každém
volání `instant()` stejný okamžik, takže počet snapshotů nelze rozlišit.
Test ověřuje pořadí volání (Mockito inOrder) a due/live rozlišení,
ale „jeden Clock snapshot“ nedokazuje.

Evidence:
backend/.../ExpirationServiceTests.java:22 (Clock.fixed) a ř. 57–69 (inOrder);
ExpirationService.java:41 (jediné volání clock.instant() — kód sám je v pořádku).

Verification performed:
inspekce testu a služby: designové pravidlo „jeden snapshot na jeden sweep“
v kódu platí, ale uvedený test ho nemůže vyvrátit ani potvrdit;
tvrzení evidence tedy částečně přeceňuje sílu důkazu.

Result: VERIFIED FINDING — slabý, ale skutečný overclaim verification
evidence; kód vyhovuje, navrhuji zpřesnit tvrzení v tabulce
(případně počítat volání přes mock Clock).
```


## Owner disposition

| Finding | ACCEPTED / REJECTED / DEFERRED | Reason/evidence | Action / no-change |
|---|---|---|---|
| F1 | PENDING | Rozpor ADR-01 ↔ kód; tým si ho sám eviduje v J-01 jako CHANGE. | No-change nyní — oprava už je naplánovaná v J-01; jen v ADR-01 poznamenat, že DB vynucení zatím není implementované. |
| F2 | PENDING | Deklarovaný tag neexistuje. | Opravit sekci Commit/tag v evidenci na skutečné commity (`8c61c98`–`a061558`), případně tag doplnit. |
| F3 | PENDING | Evidence tvrdí víc, než test dokazuje. | Zkrátit tvrzení v tabulce na „pořadí volání a due/live rozlišení“; kód je v pořádku, neměnit. |
