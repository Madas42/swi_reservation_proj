## OP-01 — Create Reservation

Cíl / hodnota pro uživatele: udelat reyervace pro uzivatele. hodnota pro uživatelenavrch uspesna rezervace
Spouštěcí událost: pozadavek pro vytvoreni rezervace.
Pozorovatelný požadavek / požadavky:
REQ-01
system vytvori DRAFT normalni rezervace ro existujici sedadla, kdyz pozadovany interval sedadel je volny

Předpoklady: 
- zdroj existuje (misto, sal, film, promitani)
- rezervace musi byt vytvorena pred zacatkem filmu
- uzivatel nema na dane promitani jiz vytvorenou rezervaci 

Stav po úspěšném provedení: 
- existuje nova rezervace
- rezervace ve stavu DRAFT
- zdroje (sedadla) jeste nejsou zapsane do databaze 

Změna stavu:
- (none) -> DRAFT
  
Odkaz na doménová pravidla / invarianty: 
BR-01 (none) 

Hlavní úspěšný scénář:
1. uzivatel zada pocet mist na danem promitani a vybere interval sedadel
2. system validuje uzivatelem zadana data
3. system vytvari DRAFT rezervace
4. system vrati ID rezervace a jeho aktualni stav
...

Alternativní / chybové výsledky:
1. Uzivatel zada pocet sedadel mimo povolene rozsahy -> reject ; no Reservation created
2. Chybejici/chybne kontaktni udaje -> reject ; no Reservation created

Příklady ověření:
1. Validni udaje plus validni zdroje -> DRAFT created
2. start rovna se end -> rejected
3. Nevalidni kontaktni udaje -> rejected

Zdůvodnění / zdroj: Vytvorit zaznam uzivatelskeho zameru bez alokace zdroju
Předpoklad / neznámá / TBD: 
jen pokud je něco skutečně nerozhodnuté


## OP-02 — Check Availability Adela

Cíl / hodnota pro uživatele:
Uživatel potřebuje vidět aktuální mapu sálu pro konkrétní promítání, aby si mohl vybrat volné sedadlo.

Spouštěcí událost:
Uživatel vybere promítání a systém požaduje zobrazení stavu sedadel

Pozorovatelný požadavek / požadavky:
REQ-02:
Pro existující promítání systém vrátí seznam všech sedadel v sále s jejich aktuálním stavem

Předpoklady:
- Promítání existuje
- Promítání je v budoucnosti
  
Stav po úspěšném provedení:
- Systém vrátí výsledek dostupnosti (mapu sedadel)
- Stav žádné rezervace se nemění
  
Změna stavu:
[none] -> [none]

Odkaz na doménová pravidla / invarianty:
?Jedno sedadlo na jedno promítání může mít max jednu rezervaci

Hlavní úspěšný scénář:
1. Uživatel zadá požadavek na zjištění dostupnosti pro ID promítání
2. Systém ověří, že ID promítání existuje
3. Systém načte všechna sedadla sálu
4. Systém zkontroluje existující CONFIRMED rezervace pro toto ID promítání
5. Systém vrátí seznam sedadel AVAILABLE / UNAVAILABLE

Alternativní / chybové výsledky:
- Promítání neexistuje (neplatné ID) → reject (not found), stav se nemění
- Promítání je již v minulosti → reject 

Příklady ověření:
- Existující promítání bez jakýchkoliv rezervací → všechna sedadla vrácena jako AVAILABLE.
- Existující promítání, kde sedadlo A1 je v CONFIRMED rezervaci → sedadlo A1 UNAVAILABLE, ostatní AVAILABLE.
- Dotaz na neexistující ID promítání → rejected.

Zdůvodnění / zdroj:
Uživatel nemůže vytvořit validní rezervaci, pokud neví, které sedadla jsou volné

Předpoklad / neznámá / TBD:


## OP-03 — Confirm Reservation Karolina

Cíl / hodnota pro uživatele: Uživatel potvrzuje dočasnou rezervaci (DRAFT), čímž získá trvalou garanci vybraných sedadel na dané promítání.


Spouštěcí událost: Požadavek uživatele na potvrzení/zaplacení existující rezervace (podle ID rezervace).


Pozorovatelný požadavek / požadavky: REQ-03: Systém změní stav rezervace z DRAFT na CONFIRMED a trvale zapiše alokaci sedadel pro dané promítání.


Předpoklady:

- Rezervace existuje (podle ID)
- Reservation.state == DRAFT
- Rezervace nevypršela (časový limit dočasné blokace TTL neuplynul)
- currentTime < Reservation.start (promítání ještě nezačalo)
- Sedadla požadovaného intervalu jsou v okamžiku potvrzení stále volná (dle BR-02)

Stav po úspěšném provedení:

- Rezervace je ve stavu CONFIRMED
- Zdroje (sedadla) jsou trvale zapsány do databáze jako obsazené pro danou rezervaci
- Změna stavu: DRAFT -> CONFIRMED

Odkaz na doménová pravidla / invarianty:

- BR-01 — Interval semantics: Potvrzení alokuje sedadla v intervalu [start, end)
- BR-02 — Exclusive Resource invariant: V žádném potvrzeném stavu se nesmí překrývat dvě CONFIRMED rezervace pro stejné sedadlo na totéž promítání
- BR-05 — Only future change: Potvrdit lze pouze rezervaci na budoucí promítání

Hlavní úspěšný scénář:

1. Uživatel zadá požadavek na potvrzení rezervace (ID rezervace).
2. Systém ověří, že rezervace existuje a nachází se ve stavu DRAFT.
3. Systém ověří, že rezervace nevypršela (vypršení dočasného časového limitu TTL).
4. Systém ověří, že promítání ještě nezačalo (BR-05).
5. Systém zkontroluje, že žádné ze sedadel v intervalu nebylo mezitím potvrzeno jinou rezervací (BR-02).
6. Systém nastaví stav rezervace na CONFIRMED.
7. Systém trvale zapíše alokaci sedadel do databáze.
8. Systém vrátí ID rezervace a její nový stav CONFIRMED.


Alternativní / chybové výsledky:

1. Rezervace neexistuje -> reject, nic se nemění
2. Rezervace je v jiném stavu než DRAFT (např. již CONFIRMED nebo CANCELLED) -> reject, stav se nemění
3. Vypršel časový limit rezervace (TTL timeout) -> reject (RESERVATION_EXPIRED), rezervace přechází do CANCELLED
4. Promítání již začalo (currentTime >= Reservation.start) -> reject (BR-05), stav se nemění
5. Souběh potvrzení s jinou rezervací na stejná sedadla (detekce kolize dle BR-02): i. První potvrzení proběhne -> druhá rezervace je odmítnuta (reject; CONFIRMED_SEAT_CONFLICT) ii. Souběh Cancel vs Confirm na téže rezervaci -> viz OP-04

Příklady ověření:

- DRAFT rezervace v časovém limitu u volných sedadel -> CONFIRMED a sedadla se trvale obsadí
- Rezervace ve stavu DRAFT po uplynutí časového limitu TTL -> rejected (RESERVATION_EXPIRED)
- Pokus o opětovné potvrzení již potvrzené rezervace (CONFIRMED) -> rejected
- Pokus o potvrzení zrušené rezervace (CANCELLED) -> rejected
- Pokus o potvrzení, kdy jedno ze sedadel v intervalu získala jiná CONFIRMED rezervace -> rejected (BR-02)

Zdůvodnění / zdroj: Dokončit nákupní proces uživatele a zaručit exkluzivitu alokovaných sedadel v databázi bez rizika double-bookingu (BR-02).

Předpoklad / neznámá / TBD: Přesná délka časového limitu (TTL) pro stav DRAFT (např. 10–15 minut od vytvoření) — určí tým/business.


## OP-04 — Cancel Reservation Kuba

Cíl / hodnota pro uživatele:
Uživatel může zrušit svou rezervaci, kterou už nechcce nebo němůže využít, uvolněná sedadla se zpřístupní ostatním uživatelům.

Spouštěcí událost:
Požadavek uživatele na zrušení existující rezervace (podle ID rezervace).

Pozorovatelný požadavek / požadavky:
REQ-04
Systém umožní zrušit rezervaci ve stavu DRAFT nebo CONFIRMED, a to nejdéle 30 minut před začátkem promítání.
Po zrušení rezervace přestane blokovat dostupné zdroje (sedadel).

Předpoklady:
- Rezervace existuje (podle ID)
- Reservation.state {DRAFT, CONFIRMED}
- currentTime < Reservation.start - 30 minut (dle BR-04)

Stav po úspěšném provedení:
- Rezervace je ve stavu CANCELLED
- Rezervace již neblokuje dostupnost zdroje (sedadla jsou opět volná).

Změna stavu:
- DRAFT -> CANCELLED
- CONFIRMED -> CANCELLED

Odkaz na doménová pravidla / invarianty:
- BR-03 - Cancellation policy: opakované zrušení již zrušené rezervace je idempotentní úspěch (nic se nemění).
- BR-04 - Domain-specific rule (C01): zrušení rezervace je možné nejdéle 30 minut před promítáním
- BR-05 - Only future change: zrušit lze pouze rezervaci na budoucí promítání

Hlavní úspěšný scénář:
1. Uživatel zadá požadavek na zrušení rezervace (ID rezervace).
2. Systém ověří, že rezervace existuje a je ve stavu DRAFT nebo CONFIRMED.
3. Systém ověří, že do začátku promítání zbývá alespoň 30 minut (BR-04).
4. Systém nastaví stav rezervace na CANCELLED.
5. Systém uvolní zdroje (sedadla) blokované rezervací.
6. Systém vrátí ID rezervace a její nový stav.
...

Alternativní / chybové výsledky:
1. Rezervace neexistuje -> reject, nic se nemění
2. Rezervace je již ve stavu CANCELLED -> idempotentní úspěchu (BR-03), nic se nemění, systém vrátí stejné potvrzení jako při zrušení
3. Do začátku promítání zbývá méně než 30 minut (včetně >= start) -> reject (BR-04, BR-05), stav se nemění
4. Souběh zrušení s potvrzením (Cancel vs Confirm na téže rezervaci):
     i.  Confirm proběhne první -> rezervace je CONFIRMED, Cnacel ji následně úplně zruší (CONFIRMED lze také zrušit)
     ii. Cancel proběhne první -> rezervace je CANCELLED, následný Confirm je odmítnut (nelze potvrdit zrušenou rezervaci)
...

Příklady ověření:
1. DRAFT, do začátku promítání zbývá více než 30 minut -> CANCELLED
2. CONFIRMED, do začátku promítání zbývá více než 30 minut -> CANCELLED a sedadla se uvolní
3. CONFIRMED, do začátku promítání zbývá přesně 30 minut nebo méně -> rejected (BR-04)
4. Již zrušená rezervace (CANCELLED) -> idempotentní úspěch (BR-03)
5. Cancel souběžně s Confirm -> dle pravidel 4i / 4ii
...

Zdůvodnění / zdroj:
Umožnit uživateli odstoupit od záměru a uvolnit zdroje pro ostatní uživatele.
Politika zrušení vychází z BR-03, BR-04 a BR-05.

Předpoklad / neznámá / TBD:
Zda je zrušení CONFIRMED rezervace podmíněno poplatkem/stornem - zatím neřešeno
