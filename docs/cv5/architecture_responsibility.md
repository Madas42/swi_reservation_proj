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
| Interface contract (`ReservationPolicy`) | Vrací výsledek validace administrátora a ověřuje oprávnění | unit | `testAdminValidationPolicySuccess()` |
| Algorithm branch (BR-02 conflict) | Vyhodnotí překryv termínů a vyvolá výjimku při kolizi | component / unit | `testConflictDetectionTriggersException()` |
| State transition (`Reservation::confirm()`) | Stav se úspěšně změní z DRAFT/PENDING na CONFIRMED | unit | `testValidStateTransitionToConfirmed()` |
| Failure behavior (`IllegalStateException`) | Zabrání neplatnému přechodu stavu a hodí chybu | unit | `testInvalidStateTransitionFails()` |
| Collaboration (`ReservationService` + Repo) | Zajišťuje transakční zápis a persistenci vytvořeného draftu | component / integration | `testServicePersistsDraftSuccessfully()` |

### Detailní zdůvodnění vybraných testů

#### 1. Test pro Algorithm branch (BR-02 conflict)
* **Selected level:** Component / Unit test
* **Why this level:** Pravidlo BR-02 zajišťuje klíčový business invariant (zamezení překryvu rezervací)[cite: 1, 2]. Test na této úrovni nejlépe ověří chování logiky detekce konfliktů bez nutnosti spouštět celou webovou vrstvu, přičemž spolehlivě odhalí chyby v algoritmickém porovnávání intervalů.
* **What design decision does this test verify?** Ověřuje správnost navrženého algoritmu pro kontrolu dostupnosti a vynucení konzistence v komponentě `Reservation Management`.

#### 2. Test pro State transition (`Reservation::confirm()`)
* **Selected level:** Unit test
* **Why this level:** Životní cyklus doménové entitě `Reservation` je čistě doménovou zodpovědností. Unit test přímo na entitě zaručuje, že zapouzdření stavů a přechodů funguje izolovaně od databáze či frameworku.
* **What design decision does this test verify?** Ověřuje návrhový záměr, že stavy a přechody vlastní výhradně doménová entita (`Reservation`), čímž se zamezí obcházení pravidel z aplikační vrstvy.
