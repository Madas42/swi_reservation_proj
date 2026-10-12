## L1 Ověřte chování

Znovu jsme spustili relevantní verification examples nad naší upravenou (TO-BE) implementací. Testy prokázaly, že nově oddělené komponenty fungují správně.

| Ověření | Výsledek | Doklad |
| :--- | :--- | :--- |
| **Success path:** Úspěšné potvrzení rezervace a alokace sedadel přes novou komponentu SeatAvailability. | ÚSPĚCH | JUnit test prošel: `ReservationLifecycleTest.confirmAllocatesSeatsAndConfirmsReservation()` |
| **Vybraná alternative/failure:** Pokus o zrušení rezervace v čase méně než 30 minut před začátkem promítání (porušení BR-04) je systémem zamítnut. | ÚSPĚCH | JUnit test prošel: `ReservationLifecycleTest.cancelRejectedWhenLessThanThirtyMinutesRemain()` |
| **Relevantní boundary/concurrency test:** Pokus o potvrzení DRAFT rezervace na sedadlo, které už mezitím stihla zabrat jiná potvrzená rezervace, je zamítnut. | ÚSPĚCH | JUnit test prošel: `ReservationLifecycleTest.confirmRejectsSeatTakenByAnotherConfirmedReservation()` |




## L2. Ověřte jedno architektonické pravidlo

Vybrali jsme pravidlo, které se týká schvalování rezervací.

**Architektonické pravidlo:**
Třídu `ApprovalWorkflow` smí volat a používat výhradně `AdminController`.

**Kontrola:**
script/repository check (příkaz v terminálu)

Využili jsme terminálový příkaz pro prohledání zdrojových kódů, abychom ověřili, které třídy do schvalovacího procesu zasahují:
`findstr /s /m "ApprovalWorkflow" src\main\java\*.java`

**Výsledek:**
ÚSPĚCH – Příkaz vypsal pouze očekávané soubory (`ApprovalWorkflow.java` a `AdminController.java`). Kontrola potvrdila, že žádná jiná část systému pravidlo neporušuje a schvalování je bezpečně izolováno.
