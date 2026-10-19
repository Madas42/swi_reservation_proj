# C01 Engineering Spike

Question / unknown:
Funguje nám uložení a načtení rezervace místa v kině z databáze?

What we did:
Vytvořili jsme jednoduchou entitu rezervace a napsali test, který ji uloží do DB a hned zase přečte.

Observed result:
Test prošel, záznam se bez chyb zapsal i načetl a DB vygenerovala ID.

Decision / what changes because of the result:
Databáze je ověřená a funkční.

Spuštění testu
``
mvn test -Dtest=ReservationPersistenceTest
``

## Evidence C02: specifikace → běžící aplikace

Přijatá baseline:
CP1 walking skeleton, na který bylo napsáno celé cv2 (základní operace + změnová karta C02).

Předvedené základní operace:
OP-01 Create Reservation (DRAFT bez alokace sedadel), OP-02 Check Availability (mapa sedadel),
OP-03 Confirm (DRAFT -> CONFIRMED / PENDING_APPROVAL u > 10 míst) a OP-04 Cancel (do 30 minut před promítáním).
Po přihlášení admina navíc admin panel a operace VALIDATE_RESERVATION (Approve/Reject).

Skutečně provedené příklady ověření:
Živý end-to-end test celého flow (draft -> confirm -> cancel, opětovná rezervace uvolněného sedadla),
admin login/logout, špatné přihlašovací údaje, 11-místná rezervace -> PENDING_APPROVAL -> approve -> CONFIRMED
a reject -> REJECTED s uvolněním sedadel. Dále "mvn test": 17/17 testů prošlo (ReservationLifecycleTest 16,
ReservationPersistenceTest 1).

Nalezený nesoulad a způsob vyřešení:
Zrušení rezervace dříve měnilo jen status — řádky reserved_seat zůstaly a nová rezervace téhož sedadla
končila chybou unique constraint (HTTP 500). Opraveno smazáním alokačních řádků při cancel/reject
a přidán regresní test (zrušené sedadlo lze znovu rezervovat).

Shrnutí dopadu změny:
C02 přidala stavy PENDING_APPROVAL, REJECTED a EXPIRED a nového aktora admina s operací VALIDATE_RESERVATION.
Confirm u rezervací nad 10 míst nyní vede na schválení manažerem místo okamžitého CONFIRMED;
stav CANCEL byl rozšířen i na PENDING_APPROVAL, OP-01 a OP-02 zůstaly beze změny.

Zbývající předpoklad / neznámá:
Neřešeno placení a storno poplatky (TBD v dokumentaci) i bezpečnostní vrstva (jednoduché přihlášení admin/admin záměrně).

Architektonické drivery přenesené do C03:
Notifikace uživateli o výsledku schválení/zamítnutí jeho rezervace.

Commit / tag aplikace:
TBD — v dokumentaci neuvedeno (doplnit hash commitu / tag z repozitáře).

## C03-C04 — Architecture Evidence

Baseline: v0.2
Part A:

Drivers: Požadavek na zajištění konzistence stavu rezervace a správného vynucení doménových pravidel bez vzniku race conditions.
Decision question: Jakým způsobem efektivně oddělit validační logiku od perzistence v rámci zpracování scénáře, aby se předešlo nekonzistenci dat při souběžných požadavcích?
Alternatives: 
- Použití databázových zámků (Pessimistic Locking / `@Version`) přímo v perzistenční vrstvě.
- Přesun validace invariantů do doménové vrstvy a ošetření v transakční službě.
Scenario walkthrough: 
1. Klient odešle HTTP požadavek na potvrzení rezervace.
2. `ReservationController` přijme požadavek a předá jej do `ReservationService`.
3. `ReservationService` ověří stav (`PENDING`) a zavolá `SeatAvailabilityService` pro kontrolu překryvu míst.
4. Po úspěšné kontrole se aktualizuje stav na `CONFIRMED` a uloží se přes `ReservationRepository`.
ADR: Rozhodnuto ponechat validaci v `ReservationService` s využitím Spring Data JPA transakcí, přičemž do budoucna bude doplněna optimalistická/pesimistická blokace pro řešení konfliktů míst.

Views:
- domain class: `Reservation`, `Seat`, `User`
- context: Interakce klienta s REST API a volání externí notifikační služby
- static architecture: Vazba mezi `ReservationController`, `ReservationService` a `ReservationRepository`
- state ownership: Stav rezervace je držen v entitě `Reservation` a perzistován v relační databázi
- runtime/deployment: Běh v kontejneru Spring Boot s napojením na H2 / PostgreSQL databázi
- design sequence: Postup od HTTP requestu přes kontrolér, službu, validaci až po uložení do repozitáře
- focused design class: Detailní pohled na třídu `ReservationService` a její závislosti

Cross-view issues found/resolved: Zjištěna chybějící kontrola souběžných zápisů na stejná místa, vyřešeno návrhem na doplnění explicitního ošetření v transakci.
AS-IS → TO-BE delta: Přechod od jednoduchého sekvenčního ukládání k robustnímu ověřování dostupnosti a ošetření chybových stavů.
Implementation changes: Úprava metod v `ReservationService` a doplnění kontroly stavových přechodů.

Behaviour verification: 
- Architektura conformance rule + result: Každý pokus o změnu stavu neplatného nebo již potvrzeného záznamu musí skončit výjimkou. Ověřeno automatizovanými testy.

Remaining uncertainty / risk: Chování systému při vysoké zátěži a paralelním vytváření rezervací na identická místa.
Commit/tag: v0.2-c03-part-a

## C05 DD Verification Evidence ----- TODO rest of points
Tests derived from DD:
Unit/component tests executed:
Design ↔ implementation mismatch:
| DD decision | Implemented? | Evidence (file/test) | Action |
|---|---|---|---|
| Draft creation vlastněná `ReservationManagement`: validace vstupů, DRAFT s TTL 15 min, requestedSeatIds bez alokace (OP-01) | YES | `ReservationManagement.createDraft` (`DRAFT_TTL_MINUTES`); `LifecycleTest.createDraftCreatesUnallocatedDraftReservation` | keep |
| Duplicitní aktivní rezervace téhož zákazníka na promítání odmítnuta (OP-01 precondition) | YES | `createDraft` (check `findByCustomer_EmailAndScreening_Id`); `LifecycleTest.createDraftRejectsSecondActiveReservationForSameCustomerAndScreening` | keep |
| Draft expiry: lazy při confirm/render + eager background job každých 60 s, DRAFT -> EXPIRED (pseudokód) | YES | `ReservationManagement.expireStaleDrafts`, `ExpirationManager` (`@Scheduled(fixedDelay = 60_000)`), lazy v `confirm` (`isDraftExpired`); `LifecycleTest.backgroundJobExpiresStaleDraft`, `confirmExpiredDraftMarksReservationExpired` | keep |
| Confirmation: DRAFT -> CONFIRMED (<= 10 míst) nebo PENDING_APPROVAL (> 10 míst, C02), alokace zapsána v obou případech (OP-03) | YES | `ReservationManagement.confirm` (`LARGE_RESERVATION_SEATS`); `LifecycleTest.confirmLargeDraftRequiresAdminApproval`, `confirmAllocatesSeatsAndConfirmsReservation` | keep |
| BR-02 `allocateIfAvailable`: check + zápis v jedné transakci, DB unikátní omezení (screening_id, seat_id) jako zábrana souběhu | YES | `SeatAvailability.allocateIfAvailable` (`Propagation.MANDATORY`), `ReservedSeat` unique constraint; `LifecycleTest.confirmRejectsSeatTakenByAnotherConfirmedReservation`, `pendingApprovalSeatsBlockOtherReservations` | keep |
| BR-02 pseudokód hlásí konflikt výjimkou `throw "seats already taken"` | PARTLY | kód vrací `boolean false`, `confirm` z toho udělá `Outcome` s uživatelskou zprávou (stejná sémantika, jiný mechanismus) — `SeatAvailability.allocateIfAvailable`, `ReservationManagement.confirm` | update DD (pseudokód: návratová hodnota místo výjimky) |
| BR-05: only future — kontrola na create, confirm i cancel | YES | `createDraft`/`confirm`/`cancel` (`startTime.isAfter(now)`); `LifecycleTest.createDraftRejectsPastScreening`, `confirm` "This screening has already started." | keep |
| Cancellation: BR-03 idempotentní opakované zrušení, BR-04 deadline 30 min před promítáním, mazání reserved_seat, uvolnění sedadel (OP-04) | YES | `ReservationManagement.cancel` (`CANCEL_DEADLINE_MINUTES`), `SeatAvailability.release`; `LifecycleTest.cancelIsIdempotentForAlreadyCancelledReservation`, `cancelRejectedWhenLessThanThirtyMinutesRemain`, `cancelConfirmedReservationFreesSeats`, `cancelledSeatCanBeRebookedByAnotherCustomer` | keep |
| Idempotentní re-cancel v DD jen vrací OK; kód navíc volá `release` (releases rows of the already-cancelled reservation) | PARTLY | `ReservationManagement.cancel` — větev `CANCELLED` volá `seatAvailability.release` (defenzivně, vzhledem k bugu s unique constraintem v `evidence-and-evolution.md`) | update DD (doplnit release i v idempotentní větvi) |
| Admin validation: `ApprovalWorkflow` vlastní PENDING_APPROVAL -> CONFIRMED (sedadla zůstávají) / REJECTED (terminální, sedadla se uvolní) (OP-05) | YES | `ApprovalWorkflow.approve/reject`; `LifecycleTest.approvePendingReservationConfirmsIt`, `rejectPendingReservationReleasesSeats`, `approveAndRejectOnlyWorkOnPendingApproval` | keep |
| OP-05 side effect: výsledek přes `NotificationIntegration`, 3 pokusy, selhání nikdy nevrací rozhodnutí | YES | `NotificationIntegration` (`MAX_SEND_ATTEMPTS = 3`, catch pouze loguje); `LifecycleTest.approveSurvivesNotificationServiceFailure` | keep |
| OP-05 precondition: nepřihlášený admin -> přesměrování na domovskou stránku, operace se neprovede | YES | `AdminController.isLoggedIn` + `redirect:/` v `approve`/`reject` a `adminPanel` | keep |
| OP-05 souběh (cancel mezitím / dvě rozhodnutí): první operace proběhne, druhá je odmítnuta | PARTLY | hájí se jen kontrolou stavu uvnitř `@Transactional` (`ApprovalWorkflow`, `ReservationManagement.cancel`); u approve/approve souběhu chybí DB-level zábrana (na rozdíl od BR-02), dvojité schválení je ale konvergentní (CONFIRMED -> CONFIRMED) | update DD (zdokumentovat spoléhání na status check + konvergentní výsledek) |
| Kontrakt `ReservationRepository.findByStatusAndExpiresAtBefore(status, time)`: read-only, vrací prošlé DRAFTy | YES | `ReservationRepository.java:15`; použito v `ReservationManagement.expireStaleDrafts`; `LifecycleTest.backgroundJobExpiresStaleDraft` | keep |
| Kontrakt repository — failures `DATABASE_UNAVAILABLE` / `INVALID_ARGUMENT` | PARTLY | `DATABASE_UNAVAILABLE` pokrývá Spring Data/JPA (propagace výjimky, rollback transakce); `INVALID_ARGUMENT` (null) není explicitně ošetřeno, spoléhá se na framework | update DD (vymezit, že null-check zajišťuje Spring Data) |
| Ownership tabulka: `ReservationManagement` = lifecycle, `SeatAvailability` = alokační stav, `ApprovalWorkflow` = schvalování | YES | javadoc + struktura všech tří tříd; `SeatAvailability.checkAvailability` (OP-02), `heldSeatCount`, `allocationOf`, `release` | keep |
| Detailed sequence `ScreeningController -> ReservationManagement -> SeatAvailability -> ReservationRepository` | YES | `ScreeningController.java` (view volá jen `reservationManagement` a `seatAvailability`) | keep |
| Sekce 5 — odvozené testy `testAllocateSeatsSuccess()`, `testAllocateSeatsAlreadyTakenThrowsException()`, `testAllocateSeatsInvalidSeatThrowsException()`, `testAllocateSeatsPastScreeningFails()` jako unit testy `SeatAvailability` | PARTLY | ekvivalentní chování pokryto component/integration testy s jinými názvy: `confirmAllocatesSeatsAndConfirmsReservation`, `confirmRejectsSeatTakenByAnotherConfirmedReservation`, `createDraftRejectsPastScreening`, `createDraftCreatesUnallocatedDraftReservation` (`LifecycleTest`); izolované unit testy `SeatAvailability` neexistují | update DD (přejmenovat testy na skutečné, přenést úroveň na component/integration) |

Resolution:
Remaining design risk:
Commit/tag:
