Selected architecture unit / responsibility: Reservation lifecycle

Related requirement(s): 
- REQ-01 system vytvori DRAFT normalni rezervace ro existujici sedadla, kdyz pozadovany interval sedadel je volny
- REQ-02: Pro existující promítání systém vrátí seznam všech sedadel v sále s jejich aktuálním stavem
- REQ-03: Systém změní stav rezervace z DRAFT na CONFIRMED a trvale zapiše alokaci sedadel pro dané promítání.
- REQ-04 Systém umožní zrušit rezervaci ve stavu DRAFT nebo CONFIRMED, a to nejdéle 30 minut před začátkem promítání. Po zrušení rezervace přestane blokovat dostupné zdroje (sedadel).
  
Related scenario(s): vytvareni rezervace, kontrola podminek, potvrzeni rezervace (adminem)

Relevant business rule(s): 

- BR-01 — Interval semantics Reservation intervals use [start,end) semantics.
- BR-02 — Exclusive Resource invariant At no committed system state may two CONFIRMED Reservations overlap for the same exclusive Resource.
- BR-03 — Cancellation policy [team-defined, domain-specific accepted policy]
- BR-04 — Domain-specific rule from C01 [team-defined] Zruseni rezervace je mozne do 30 minut pred promitanim
- BR-05 — Only future change Uzivatel muze vytvorit/upravit/zrusit rezervaci pouze na budouci promitani
