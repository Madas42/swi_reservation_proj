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

## C03 — Architecture Evidence

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
