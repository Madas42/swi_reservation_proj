# Doménový třídní model (C1, C2, D)

Scénář: **rezervace míst na konkrétní filmové promítání** (OP-01..OP-05, BR-01..BR-05 z `docs/cv2/`).

## Doménové pojmy

| Pojem | Typ | Popis |
|-------|-----|-------|
| **Customer** | entita | Zákazník (uživatel), který vytváří rezervace. Identified by jménem a e-mailem; e-mail slouží jako kontaktní údaj pro rezervaci. |
| **Film** | entita | Film — Promítaný titul (název, popis, délka v minutách). Je nabízen v promítáních, sám o sobě žádná sedadla nerezervuje. |
| **CinemaRoom** | entita | Kinosál — Fyzický sál s pevnou rozvrženou kapacitou (počet řádků × sedadel na řádek). Vlastní sadu sedadel a hostí promítání. |
| **Seat** | entita | Sedadlo — Konkrétní místo v sále určené řádkem a číslem. Klíčový exkluzivní zdroj scénáře — na jedno promítání lze každé sedadlo obsadit nejvýše jednou. |
| **Screening** | entita | Promítání — Konkrétní promítání filmu v sále v daném čase (startTime/endTime). Rezervace se váží vždy na jedno konkrétní promítání, nikoli na film obecně. |
| **Reservation** | entita | Rezervace — Záměr zákazníka obsadit vybraná sedadla na dané promítání. Nese stav životního cyklu (DRAFT → PENDING_APPROVAL / CONFIRMED / REJECTED / EXPIRED / CANCELLED), čas vytvoření, čas vypršení (TTL pro DRAFT) a požadovaná sedadla (držení výběru ve stavu DRAFT, ještě bez alokace). |
| **ReservationStatus** | výčtový typ | Stav rezervace — DRAFT (dočasná bez alokace), PENDING_APPROVAL (> 10 sedadel, čeká na schválení adminem), CONFIRMED (potvrzená, sedadla alokována), REJECTED, EXPIRED (propadlý DRAFT), CANCELLED. REJECTED a EXPIRED jsou koncové stavy. |
| **ReservedSeat** | asociační entita | Alokané sedadlo — Trvalý zápis alokace jednoho sedadla jedné rezervaci na dané promítání. Vzniká až při potvrzení (CONFIRMED nebo PENDING_APPROVAL); při zrušení/odmítnutí rezervace se záznamy mažou a sedadla se uvolňují. |

## Vztahy a násobnosti

| Vztah | Násobnosti | Business význam / invariant vztahu |
|-------|-----------|-------------------------------------|
| **CinemaRoom** — **Seat** | 1 — 1..* | Sál obsahuje svá sedadla; rozložení sálu je pevné. Sedadlo existuje právě v jednom sále. Invariant: kombinace (sál, řada, číslo) je v rámci sálu unikátní — každé fyzické místo existuje jen jednou. Kapacita sálu = počet řádků × sedadel na řádek. |
| **Film** — **Screening** | 1 — 1..* | Film se promítá v jednom či více promítáních. Promítání bez filmu nemá smysl (povinná). |
| **CinemaRoom** — **Screening** | 1 — 1..* | Promítání probíhá právě v jednom sále; sál hostí mnoho promítání. Invariant (BR-05): rezervovat lze pouze na budoucí promítání (startTime > teď). |
| **Customer** — **Reservation** | 1 — 1..* | Zákazník vytváří rezervace; rezervace bez zákazníka nemá smysl. Invariant: zákazník má na jedno promítání nejvýše jednu aktivní rezervaci (DRAFT / PENDING_APPROVAL / CONFIRMED). |
| **Screening** — **Reservation** | 1 — 1..* | Rezervace se vždy váže na jedno konkrétní promítání. |
| **Reservation** — **ReservationStatus** | 1 — 1 | Každá rezervace je právě v jednom stavu; stavy tvoří životní cyklus: DRAFT → CONFIRMED (≤ 10 sedadel), DRAFT → PENDING_APPROVAL (> 10 sedadel) → CONFIRMED / REJECTED; DRAFT → EXPIRED po TTL (15 min); CANCELLED z DRAFT / PENDING_APPROVAL / CONFIRMED (BR-03, BR-04). |
| **Reservation** — **ReservedSeat** | 1 — 1..* | Potvrzená rezervace alokuje alespoň jedno sedadlo, nejvýše kapacitu sálu. Alokační záznamy vznikají až při potvrzení; ve stavu DRAFT jsou sedadla pouze „držená“ v požadavku (requestedSeatIds) bez zápisu. Při CANCELLED/REJECTED se záznamy ruší a místa se uvolňují. |
| **Screening** — **ReservedSeat** | 1 — 1..* | Ke každému alokovanému sedadlu je vždy jedno konkrétní promítání. Invariant (BR-02): dvojice (promítání, sedadlo) je unikátní — žádné sedadlo nelze alokovat dvěma rezervacím na totéž promítání. Stavy CONFIRMED a PENDING_APPROVAL se oba počítají jako obsazené. |
| **Seat** — **ReservedSeat** | 1 — 1..* | Jedno sedadlo může být v čase alokováno pro mnohá promítání, ale v rámci jednoho promítání jen jednou (viz BR-02 výše). Tím je zachována opakovaná využitelnost sedadla v čase (interval [start, end), BR-01). |

## Klíčové invarianty domény (shrnutí)

1. **BR-02 — exkluzivita zdroje:** na jedno promítání je každé sedadlo obsazeno nejvýše jednou (jedinečnost dvojice Screening–Seat v `ReservedSeat`; platí pro CONFIRMED i PENDING_APPROVAL).
2. **Životní cyklus rezervace:** DRAFT má TTL 15 minut; jeho potvrzením po vypršení přechází do EXPIRED. REJECTED a EXPIRED jsou koncové, nelze je rušit.
3. **C02 — prahlo schválení:** rezervace s více než 10 sedadly nepřechází přímo do CONFIRMED, ale do PENDING_APPROVAL a k jejímu potvrzení/odmítnutí je potřeba schvalovatel (admin).
4. **BR-03/BR-04 — rušení:** zrušení je idempotentní (opakované zrušení nevyhazuje chybu) a je možné nejvýše 30 minut před začátkem promítání.
5. **BR-05 — pouze budoucnost:** vytvořit, potvrdit i zrušit lze jen rezervaci na budoucí promítání.

Pozn.: Admin (schvalovatel) je v doméně reprezentován pouze jako role v rámci operace
VALIDATE_RESERVATION (OP-05) — nemá vlastní doménovou entitu.

## Odpovědnosti pro slice „rezervace míst na konkrétní promítání“

| Zdroj                | Odpovědnost                                        | Co musí rozhodnout / vlastnit                                                                                      | Potřebuje jednoho jasného vlastníka? | Důvod                                                                              |
|----------------------|----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|--------------------------------------|------------------------------------------------------------------------------------|
| OP-01                | Vytvoření DRAFT rezervace s držením výběru sedadel | Rozhodnout, zda lze požadavek přijmout (budoucí promítání, volná sedadla, úplné kontaktní údaje, žádná duplicitní aktivní rezervace zákazníka) a vlastnit držený výběr sedadel (requestedSeatIds). | Ano                                  | Pravidla přijetí požadavku musí systém vyhodnotit jednotně; dvojí interpretace by umožnila neplatné rezervace. |
| OP-03                | Potvrzení rezervace a trvalá alokace sedadel       | Rozhodnout, zda sedadla lze alokovat (BR-02 konflikt, TTL, budoucí promítání) a vlastnit zápis alokace (ReservedSeat). | Ano                                  | Souběžná potvrzení nesmí porušit exkluzivitu sedadla na promítání.                 |
| OP-01, OP-03, OP-05  | Řízení životního cyklu rezervace                   | Vlastnit stav rezervace a rozhodnout, zda je přechod (DRAFT → PENDING_APPROVAL → CONFIRMED / REJECTED / EXPIRED / CANCELLED) povolen. | Ano                                  | Různé části systému nesmějí interpretovat stavy a jejich přechody odlišně.        |
| BR-02                | Vyhodnocení exkluzivity a konfliktů sedadel        | Vlastnit autoritativní rozhodnutí „sedadlo na promítání je volné/obsazené“ (CONFIRMED i PENDING_APPROVAL se počítají jako obsazené). | Ano                                  | Dva souběžné requesty nesmí nezávisle rozhodnout, že je totéž sedadlo volné.       |
| OP-02                | Určování dostupnosti sedadel (mapa sálu)           | Vlastnit výpočet dostupnosti všech sedadel sálu pro konkrétní promítání (AVAILABLE / UNAVAILABLE).                  | Ano                                  | Uživatel i validace musí vidět tutéž pravdu o obsazenosti, jinak vzniknou chybné rezervace. |
| OP-05                | Správa odloženého schvalování (> 10 sedadel)       | Vlastnit mezistav PENDING_APPROVAL přežívající původní request a rozhodnout o přijetí/odmítnutí schvalovatelem.     | Ano                                  | Mezistav musí bezpečně přečkat konec requestu a čekat na pozdější akci schvalovatele. |
| OP-03, OP-04, BR-04  | Časová správa platnosti (TTL DRAFT, 30min lhůta rušení) | Rozhodnout o vypršení DRAFT (TTL 15 min → EXPIRED) a o způsobilosti ke zrušení (nejvýše 30 min před promítáním, idempotence). | Ano                                  | Čas plyne nezávisle na akcích uživatele; propadlé rezervace musí uvolnit sedadla.  |
| C03 (plánované)      | Doručování notifikací o výsledku rezervace          | Rozhodnout o odeslání zprávy uživateli (přijato/odmítnuto) a ošetřit retry při selhání externí služby.              | Ano                                  | Externí notifikační služba může spadnout; její výpadek nesmí zvrátit potvrzenou rezervaci. |

### Seskupení a oddělení odpovědností

Názvy komponent zatím neurčujeme — uvádíme pouze, s čím musí být odpovědnost seskupena a od čeho oddělena.

1. **Vytvoření DRAFT rezervace s držením výběru sedadel**
   - *Seskupit s:* řízením životního cyklu rezervace — obě sdílejí tentýž stav (Reservation.status) a invariant jedné aktivní rezervace zákazníka na promítání.
   - *Oddělit od:* doručování notifikací (externí technologie, failure boundary) a od prezentace (HTTP formulář, Thymeleaf) — důvod změny UI je jiný než důvod změny pravidel přijetí rezervace.

2. **Potvrzení rezervace a trvalá alokace sedadel**
   - *Seskupit s:* vyhodnocením exkluzivity sedadel (BR-02) — obě sdílejí invariant „(promítání, sedadlo) je obsazeno nejvýše jednou“ a zápis ReservedSeat musí proběhnou pod tentýž autoritativní rozhodnutí.
   - *Oddělit od:* vytvoření DRAFT — potvrzení mění trvale uloženou alokaci a má jiné pravidlo selhání (konflikt při souběhu), zatímco držení výběru ve DRAFT je dočasné a reverzibilní.

3. **Řízení životního cyklu rezervace**
   - *Seskupit s:* správou odloženého schvalování — PENDING_APPROVAL je součást téhož stavového automatu a sdílí tentýž invariant koncových stavů (REJECTED/EXPIRED nejsou rušitelné).
   - *Oddělit od:* fyzického ukládání dat (perzistence, JPA/H2) — pravidla cyklu se mění podle zadání kurzu, ne podle změny databázové technologie.

4. **Vyhodnocení exkluzitivity a konfliktů sedadel (BR-02)**
   - *Seskupit s:* trvalým úložištěm alokací (ReservedSeat) — invariant je vynucen až nad uloženými daty (unikátní omezení), rozhodnutí musí být autoritativní a serializované.
   - *Oddělit od:* mapy dostupnosti sedadel (OP-02) — ta je pouze projekcí stavu pro uživatele a nesmí být místem, kde se invariant vynucuje (jiný důvod změny: vzhled/čitelnost mapy).

5. **Určování dostupnosti sedadel (mapa sálu)**
   - *Seskupit s:* vyhodnocením exkluzivity — čte tentýž zdroj pravdy o obsazenosti, aby uživatel viděl totéž, co později rozhodne validace.
   - *Oddělit od:* rozhodovací logiky rezervace — mapa je operace jen pro čtení bez vedlejších efektů; její důvod změny (prezentace sedadel) je odlišný od důvodu změny pravidel potvrzení.

6. **Správa odloženého schvalování (> 10 sedadel)**
   - *Seskupit s:* řízením životního cyklu rezervace — sdílejí stav Reservation a invariant prahla 10 sedadel (C02).
   - *Oddělit od:* standardního (malého) prodeje lístků i od trust boundary směrem ke schvalovateli — schvalovatel je samostatný aktér s odlišným oprávněním; velké rezervace nesmějí komplikovat běžný průběh.

7. **Časová správa platnosti (TTL DRAFT, 30min lhůta rušení)**
   - *Seskupit s:* časovými údaji rezervace (createdAt, expiresAt) a promítání (startTime) — rozhodnutí o expiraci čerpá výhradně z tohoto stavu.
   - *Oddělit od:* notifikací a uživatelských akcí — expirace běží na pozadí nezávisle na interakci uživatele; oddělením zajistíme, že žádný request není povinen ji vyvolat.

8. **Doručování notifikací o výsledku rezervace**
   - *Seskupit s:* ničím z jádra — naopak musí být izolováno na hranici systému (externí technologie, retry/failure boundary).
   - *Oddělit od:* potvrzování a alokace sedadel — výpadek externí služby nesmí zvrátit již potvrzenou rezervaci ani blokovat uvolňování sedadel; jedná se o odlišný důvod změny (technologie třetí strany).

## Rozhodovací otázka:

**Kde má být provedeno autoritativní rozhodnutí o alokaci sedadel při potvrzení rezervace (Confirm Reservation), aby při souběhu dvou potvrzení na totéž sedadlo zůstalo pravidlo BR-02 pravdivé?**
