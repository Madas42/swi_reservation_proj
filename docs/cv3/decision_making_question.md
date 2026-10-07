# Rozhodovací otázka — struktální varianty (E1, E2, E3)

**Kde má být provedeno autoritativní rozhodnutí o alokaci sedadel při potvrzení rezervace (Confirm Reservation), aby při souběhu dvou potvrzení na totéž sedadlo zůstalo pravidlo BR-02 pravdivé?**

---

## Alternativa A — rozhodnutí u jednoho vlastníka, stejný deployable

```
[Reservation Application]
├─ [Reservation]                      — potvrzení, životní cyklus
│    └─ [Seat Allocation]             — JEDINÉ místo rozhodnutí
│         ├─ kontrola obsazenosti
│         └─ zápis ReservedSeat       — kontrola + zápis v jedné transakci
└─ [Seat Availability View]           — pouze čtení (projekce)
```

Rozhodnutí „sedadlo je volné a tudíž alokovatelné“ provádí jediný prvek
([Seat Allocation]) v rámci téhož aplikace. Kontrola obsazenosti a zápis
`ReservedSeat` tvoří jednu nedělitelnou jednotku (jedna transakce nad sdíleným
úložištěm). Souběh řeší sdílené úložiště samo — druhé potvrzení dostane
jednoznačný odmítavý výsledek, když se pokusí zapsat totéž sedadlo.
Zobrazení dostupnosti ([Seat Availability View]) je jen projekce: čte stav,
ale nikdy nerozhoduje.

## Alternativa B — rozhodnutí ve samostatném runtime prvku

```
[Reservation Application]
|  — žádost o alokaci (request)
v
[Seat Allocation Worker]              — serializované rozhodnutí
├─ fronta požadavků                   — vždy zpracovává jeden požadavek
└─ zápis ReservedSeat
```

Autoritativní rozhodnutí je vytaženo z aplikace do samostatného runtime
prvku ([Seat Allocation Worker]). Aplikace posílá žádost o alokaci a prvek
rozhoduje vždy po jednom (fronta = přirozená serializace) — v okamžiku
rozhodnutí nemůže existovat žádný souběžný druhý požadavek na totéž
sedadlo. Potvrzení rezervace pak není jedno volání, ale vyžádání si výsledku
(odpověď/zpráva, která dorazí později).

---

## Porovnání alternativ

| Driver / kritérium                  | Alternativa A                                                                | Alternativa B                                                                                       |
|-------------------------------------|------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| BR-02 (souběh potvrzení)            | Kontrola i zápis v jedné transakci; druhý souběžný zápis odmítne unikátní omezení nad ReservedSeat. | Fronta zabraňuje vzniku konfliktu; odmítnutí se ale dorozhodne až po vyřízení požadavku (zpoždění odpovědi). |
| OP-03 a OP-04 (TTL / synchronní interakce) | Potvrzení hotové v jednom requestu; vypršení TTL vyhodnotí tentýž kód při potvrzení. | Request se vrací před výsledkem; TTL (vyprší mezitím) se musí vyhodnotit znovu ve workeru.          |
| OP-05 (mezistav přežívající request) | Jediný mezistav PENDING_APPROVAL, perzistovaný v databázi.                     | Přibývá další mezistav „čekám na výsledek alokace“ — musí se perzistovat a mít vlastní timeout.      |
| Notification Service (failure chování) | Notifikaci lze odeslat hned po transakci potvrzení; retry jen vůči externímu API. | Spouštěčem notifikace je až výsledek workera; výpadek mezi requestem a výsledkem = chybějící notifikace, nutný retry/outbox. |

---

## Průběh scénářem (OP-03 Confirm Reservation + souběžný konflikt na totéž sedadlo)

| Krok / událost                              | Alternativa A                                                                                       | Alternativa B                                                                                            |
|---------------------------------------------|------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Confirm Reservation začne (DRAFT, TTL 15 min) | Request zůstává otevřený; uživatel čeká na odpověď aplikace.                                          | Aplikace uloží žádost do fronty; request skončí hned bez výsledku, uživatel vidí „čeká se na vyřízení“.     |
| Kontrola obsazenosti sedadel (BR-02)        | Kontrola i zápis ReservedSeat v jedné transakci — rozhodnutí a alokace najednou.                      | Worker vybere jeden požadavek z fronty; kontrola proběhne bez souběhu, zápis ihned po ní.                  |
| Druhý uživatel mezitém potvrdí totéž sedadlo | Jeho transakce narazí na unikátní omezení (screening_id, seat_id), insert selže.                      | Jeho žádost čeká ve frontě; po vyřízení první zjistí sedadlo obsazené až z úložiště.                         |
| Druhý request dostává výsledek (CONFIRMED_SEAT_CONFLICT) | Odmítnutí hned v rámci téhož requestu; rezervace zůstává DRAFT, sedadla drží nadále.          | Výsledek dorazí až po vyřízení fronty; do té doby druhý request visí v mezistavu „čekám na výsledek“.    |
| Failure: pád mezi rozhodnutím a oznámením výsledku | Transakce se celá vrátí (rollback) — buď potvrzeno, nebo nic, žádný částečný stav.                | Worker spadne po zápisu, před odesláním výsledku — alokace existuje, uživatel výsledek nezná; nutná kompenzace při návratu. |
