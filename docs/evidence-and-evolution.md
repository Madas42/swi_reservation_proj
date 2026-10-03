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