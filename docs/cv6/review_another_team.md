# C06 — Assessment Form 

## Projekt

```text
Hodnotící tým: Absolute Cinema
Hodnocený tým:Cinema
Projekt: Cinema
Assessed commit/tag:
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

Vybraná operace: OP-03- Confirm Reservation
Vybraný business rule / invariant: BR-02-Exclusive Resource Invariant (No Double Booking)

| ID | Odpověď | Evidence / komentář |
| :--- | :--- | :--- |
| R1 Operation: input + precondition + postcondition/state | Splněno | Operace `Confirm Reservation` přijímá `reservationId`, vyžaduje, aby rezervace existovala, byla ve stavu `DRAFT`, uživatel byl jejím vlastníkem a projekce ještě nezačala (`currentTime < screening.startTime`). Po úspěšném provedení přechází stav na `CONFIRMED`. |
| R2 Business rule/invariant je jednoznačný | Splněno | Pravidlo `BR-02` jasně stanoví, že pro stejný zdroj `(Screening, Seat)` může existovat maximálně jedna rezervace ve stavu `CONFIRMED`. |
| R3 Operation/rule je konzistentní se state modelem | Splněno | Přechod `DRAFT -> CONFIRMED` přesně odpovídá stavovému modelu z dokumentace. |
| R4 Implementace odpovídá specifikované semantics | Splněno | Logika endpointu `POST /reservations/{id}/confirm` kontroluje kolize sedadel a vrací kód `409 Conflict` v případě duplicitní rezervace nebo začaté projekce. |
| R5 Existuje vhodná verification evidence | Splněno | V dokumentaci a testovacích scénářích jsou uvedeny konkrétní příklady (např. 200 OK při volných sedadlech, 409 Conflict při pokusu o potvrzení obsazeného sedadla). |


## C03 — Architecture

Vybraná responsibility: Manage pending expiration

Vybraná interaction: Atomic allocation attempt (DB enforces BR-02) při operaci Confirm Reservation.

Vybrané ADR/decision: Authoritative Resource Allocation under Concurrent Confirmation

| ID | Odpověď | Evidence / komentář |
|---|---|---|
| A1 Responsibility + owner jsou explicitní | YES | `C03.md` sekce C2 a G2 jasně definuje responsibility Manage pending expiration a jejího vlastníka Reservation Management |
| A2 Skutečný code ownership odpovídá architektuře | YES | Rozdělení v kódu přesně kopíruje návrh. Třída ExpirationSweep.java funguje jen jako časovač (Trigger) a ExpirationService.java řeší logiku |
| A3 Dynamic interaction odpovídá skutečnému call path | NO | Dynamická interakce v sekvenčním diagramu H1 počítá s tím, že databáze zamítne konflikt při alokaci (vynucení na úrovni DB). Skutečný call path v kódu (ReservationService) ale databázový zámek nem8 a kolize kontroluje pouze aplikačním dotazem (countOtherAllocations) |
| A4 ADR/decision odpovídá současné implementaci | PARTLY | Podle `ADR-01` má exkluzivitu sedadel hlídat přímo databáze. Kód v ReservationService se tím ale zatím neřídí a řeší kolize jen na aplikační vrstvě. Tým se o teto chybějící implementaci zmiňuje v gap analýze (bod J-01). |

## C04 — Quality & Measurement

Quality concern:  
Criterion:

| ID | Odpověď | Evidence / komentář |
|---|---|---|
| Q1 Criterion je skutečně evaluable | | |
| Q2 Measurement měří criterion ve správném contextu | | |
| Q3 Decision odpovídá výsledku měření | | |

## C05 — Detailed Design & Unit Verification

Vybraná software unit:

| ID | Odpověď | Evidence / komentář |
|---|---|---|
| D1 Design intent je explicitní a pochopitelný | | |
| D2 DD odpovídá skutečné implementaci | | |
| D3 Unit/component tests jsou odvoditelné z DD | | |
| D4 Relevantní branches/boundaries/states/failures jsou ověřeny | | |
| D5 Vybraný test má smysluplný oracle/expected result | | |

## Candidate findings

### F1

```text
Source: C03
Engineering claim: Systém podle ADR-01 používá k zabránění dvojí rezervace stejného sedadla autoritativní kontrolu přímo na úrovni databáze.
Expected: Podle ADR-01 a diagramu H1 má databáze exkluzivitu sedadel vynucovat (např. přes unique constraint) a při kolizi zápis zamítnout.
Observed: Kód tuto databázovou kontrolu neimplementuje. Místo toho spoléhá na aplikační pre-check (metoda countOtherAllocations() v ReservationService).
Evidence: Zdrojový kód ReservationService.java, C03 (položka J-01).
Verification performed: inspect concrete code path
Result: VERIFIED FINDING
```

### F2

```text
Source:
Engineering claim:
Expected:
Observed:
Evidence:
Verification performed:
Result: VERIFIED FINDING / REJECTED CANDIDATE
```

### F3

```text
Source:
Engineering claim:
Expected:
Observed:
Evidence:
Verification performed:
Result: VERIFIED FINDING / REJECTED CANDIDATE
```

## Owner disposition

| Finding | ACCEPTED / REJECTED / DEFERRED | Reason/evidence | Action / no-change |
|---|---|---|---|
| F1 | | | |
| F2 | | | |
| F3 | | | |
