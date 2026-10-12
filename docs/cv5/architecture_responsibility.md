# Detailed Design
## Selected architecture unit / responsibility: Reservation lifecycle

Related requirement(s): 
- REQ-01 system vytvori DRAFT normalni rezervace ro existujici sedadla, kdyz pozadovany interval sedadel je volny
- REQ-03: Systém změní stav rezervace z DRAFT na CONFIRMED a trvale zapiše alokaci sedadel pro dané promítání.
- REQ-05 — Approve / Reject Reservation (Admin Approval)
  
Related scenario(s): OP-01 (CREATE), OP-03 (CONFIRM), OP-04 (CANCEL), OP-05 (APPROVE / REJECT)

Relevant business rule(s):
- BR-02 — Exclusive Resource invariant At no committed system state may two CONFIRMED Reservations overlap for the same exclusive Resource.
- BR-03 — Cancellation policy [team-defined, domain-specific accepted policy]
- BR-04 — Domain-specific rule from C01 [team-defined] Zruseni rezervace je mozne do 30 minut pred promitanim
- BR-05 — Only future change Uzivatel muze vytvorit/upravit/zrusit rezervaci pouze na budouci promitani

## SubResponsibilities
| Detailed responsibility                                                                                                                     | Why is it needed?                                                                                                                                                     |
|---------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Draft creation — validace vstupů a promítání, vytvoření DRAFT s TTL, bez alokace sedadel                                                    | Základní stav Rezervace — zaznamená uživatelův záměr (REQ-01) a odděluje výběr sedadel od jejich trvalé alokace (BR-02 se kontroluje až při potvrzení)                |
| Draft expiry — detekce prošlých DRAFTů (lazy při přístupu + eager přes background job) a přechod DRAFT -> EXPIRED                           | Sedadla držená v DRAFT nesmí blokovat rezervace jiných uživatelů donekonečna; TTL (15 minut) zaručuje, že se nevyužité záměry uvolní                                  |
| Confirmation — ověření čerstvosti DRAFT a budoucnosti promítání, rozhodnutí přímé potvrzení vs. schválení adminem, spuštění alokace sedadel | Dokončuje nákupní proces (REQ-03); DRAFT -> CONFIRMED (<= 10 míst) nebo DRAFT -> PENDING_APPROVAL (> 10 míst, C02); BR-02 a BR-05 se musí ověřit před zápisem alokace |
| Cancellation — kontrola stavu a 30minutového deadline, idempotentní opakované zrušení, uvolnění sedadel                                     | Umožňuje uživateli odstoupit od záměru (OP-04); BR-03 idempotence, BR-04 deadline, BR-05 budoucí promítání; uvolněná sedadla se zpřístupní ostatním                   |
| Admin validation — schválení/zamítnutí PENDING_APPROVAL (> 10 míst), uvolnění sedadel při rejectu                                           | Řízeno změnovou kartou C02 (REQ-05); PENDING_APPROVAL -> CONFIRMED (sedadla zůstávají) nebo -> REJECTED (sedadla se uvolní); REJECTED je terminální stav              |


## 3 Design elements and ownership (Struktura a data)

| Responsibility | Owner | State/data | Why here? |
|---|---|---|---|
| **Draft creation** | `ReservationManagement` | `Reservation` (stav DRAFT, TTL, screeningId) | Hlavní orchestrátor životního cyklu. Zaznamená uživatelův záměr a odděluje výběr od trvalé alokace. |
| **Draft expiry** | `ReservationManagement` (příp. `ExpirationManager`) | `Reservation` (přechod na EXPIRED) | Hlídá časové limity (15 min TTL), aby nevyužité záměry neblokovaly sedadla donekonečna. |
| **Confirmation** | `ReservationManagement` a `SeatAvailability` | `Reservation` (CONFIRMED / PENDING), `AllocatedSeat` | `ReservationManagement` řídí proces a změnu stavu. `SeatAvailability` je zavolána k trvalému zápisu alokace a garantuje BR-02 (zabraňuje overbookingu). |
| **Cancellation** | `ReservationManagement` a `SeatAvailability` | `Reservation` (stav CANCELLED) | `ReservationManagement` hlídá 30minutový deadline a idempotenci. Pokud je storno úspěšné, zavolá `SeatAvailability` pro uvolnění sedadel. |
| **Admin validation** | `ApprovalWorkflow` | `Reservation` (přechod z PENDING na CONFIRMED / REJECTED) | Striktně odděluje admin schvalování (REQ-05) od běžného toku. Při REJECTED se postará o uvolnění sedadel. |


### Key interface(s) / contract(s)

`<<interface>> SeatAvailability`

`allocateSeats(screeningId, seatIds, reservationId) : AllocationResult`

**Contract:**

**Preconditions:**
- `screeningId` a `seatIds` jsou platné a nejsou prázdné.
- Promítání se odehrává v budoucnosti (odvozeno z BR-05).
- Rezervace je aktuálně ve stavu DRAFT.

**Postcondition:**
- `AllocationResult` obsahuje potvrzení o úspěchu.
- Všechna požadovaná sedadla z `seatIds` jsou exkluzivně a trvale zapsána pro dané `screeningId` a `reservationId`. Žádný jiný proces je nemůže alokovat.

**Failures:**
- `SEAT_ALREADY_TAKEN` (Porušení BR-02: o alokaci se ve stejný čas pokusil někdo jiný a byl rychlejší).
- `INVALID_INPUT` (Sedadlo v daném sále vůbec neexistuje).


## 5 — Odvoďte unit/component testy z DD

### Přehled odvozených testů

| DD element / decision | What must hold? | Test level | Test case |
|---|---|---|---|
| Interface contract (`SeatAvailability`) | Úspěšně alokuje sedadla pro platný screening, neprázdný seznam a DRAFT rezervaci | unit / component | `testAllocateSeatsSuccess()` |
| Algorithm branch (BR-02 / conflict) | Vyhodí chybu `SEAT_ALREADY_TAKEN`, pokud se o alokaci pokusil jiný proces dříve | component / unit | `testAllocateSeatsAlreadyTakenThrowsException()` |
| Precondition / validation | Vyhodí `INVALID_INPUT`, pokud požadované sedadlo v daném sále neexistuje | unit | `testAllocateSeatsInvalidSeatThrowsException()` |
| Future screening check (BR-05) | Odmítne alokaci pro promítání, které se neodehrává v budoucnosti | unit | `testAllocateSeatsPastScreeningFails()` |
| Collaboration (`ReservationService` + `SeatAvailability`) | Zajistí transakční alokaci sedadel a propojení s konkrétní rezervací | integration | `testServiceAllocatesSeatsAndPersists()` |

### Detailní zdůvodnění vybraných testů

#### 1. Test pro Algorithm branch (`SEAT_ALREADY_TAKEN` / BR-02)
* **Selected level:** Component / Unit test
* **Why this level:** Pravidlo zamezení duplicitní alokace stejného sedadla pro stejný screening je klíčovým invariantem rezervačního systému[cite: 1, 2, 3]. Test ověřuje, že při souběžném pokusu o alokaci systém správně zachytí konflikt, zabrání dvojité rezervaci a vrátí očekávaný chybový stav.
* **What design decision does this test verify?** Ověřuje dodržení kontraktu rozhraní `SeatAvailability` a odolnost vůči race conditions (konfliktním zápisům) na úrovni alokace sedadel.

#### 2. Test pro Precondition / Invalid Input (`INVALID_INPUT`)
* **Selected level:** Unit test
* **Why this level:** Ověření vstupních podmínek (preconditions) zajišťuje, že systém selže včas a bezpečně předtím, než se pokusí pracovat s neexistujícími daty v databázi[cite: 3]. Unit test isolovaně prověří reakci na neplatná ID sedadel.
* **What design decision does this test verify?** Ověřuje splnění předpokladů rozhraní `SeatAvailability` a správné ošetření neplatných vstupních parametrů.
