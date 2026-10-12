| Detailed responsibility | Why is it needed? |
|---|---|
| ... | ... |

| Detailed responsibility | Why is it needed? |
|---|---|
| Draft creation — validace vstupů a promítání, vytvoření DRAFT s TTL, bez alokace sedadel | Základní stav Rezervace — zaznamená uživatelův záměr (REQ-01) a odděluje výběr sedadel od jejich trvalé alokace (BR-02 se kontroluje až při potvrzení) |
| Draft expiry — detekce prošlých DRAFTů (lazy při přístupu + eager přes background job) a přechod DRAFT -> EXPIRED | Sedadla držená v DRAFT nesmí blokovat rezervace jiných uživatelů donekonečna; TTL (15 minut) zaručuje, že se nevyužité záměry uvolní |
| Confirmation — ověření čerstvosti DRAFT a budoucnosti promítání, rozhodnutí přímé potvrzení vs. schválení adminem, spuštění alokace sedadel | Dokončuje nákupní proces (REQ-03); DRAFT -> CONFIRMED (<= 10 míst) nebo DRAFT -> PENDING_APPROVAL (> 10 míst, C02); BR-02 a BR-05 se musí ověřit před zápisem alokace |
| Cancellation — kontrola stavu a 30minutového deadline, idempotentní opakované zrušení, uvolnění sedadel | Umožňuje uživateli odstoupit od záměru (OP-04); BR-03 idempotence, BR-04 deadline, BR-05 budoucí promítání; uvolněná sedadla se zpřístupní ostatním |
| Admin validation — schválení/zamítnutí PENDING_APPROVAL (> 10 míst), uvolnění sedadel při rejectu | Řízeno změnovou kartou C02 (REQ-05); PENDING_APPROVAL -> CONFIRMED (sedadla zůstávají) nebo -> REJECTED (sedadla se uvolní); REJECTED je terminální stav |


## OP-01 — Create Reservation

Operation: POST /screenings/{screeningId}/reservations
Responsibility: Draft creation
Input: screeningId, jméno a email zákazníka, požadovaná sedadla (requested seat IDs)
Output: ID nové rezervace ve stavu DRAFT, přesměrování na detail rezervace
Preconditions: promítání existuje a je v budoucnosti (BR-05); zákazník nemá na dané promítání jinou aktivní rezervaci; požadovaná sedadla existují v sále; počet sedadel je v povoleném rozsahu
Postconditions: existuje nová Reservation ve stavu DRAFT s createdAt/expiresAt (TTL 15 minut); requestedSeatIds jsou držena v rámci DRAFT; žádné záznamy v reserved_seat
Side effects: nic — sedadla nejsou ještě alokována (BR-02 se kontroluje až při potvrzení); běží časovač TTL draftu
Failure behavior: ReservationRuleException s uživatelskou zprávou; žádná rezervace se nevytvoří (nevalidní počet sedadel, chybějící kontaktní údaje, proběhlé promítání, duplicitní aktivní rezervace)


## OP-03 — Confirm Reservation

Operation: POST /reservations/{reservationId}/confirm
Responsibility: Confirmation
Input: reservationId
Output: aktualizovaný stav rezervace — CONFIRMED (<= 10 míst) nebo PENDING_APPROVAL (> 10 míst, C02)
Preconditions: rezervace existuje; Reservation.state == DRAFT; draft nevypršel (TTL); promítání ještě nezačalo (BR-05); požadovaná sedadla jsou stále volná (BR-02)
Postconditions: rezervace je ve stavu CONFIRMED nebo PENDING_APPROVAL; alokace sedadel je trvale zapsána do reserved_seat (v obou případech); sedadla se počítají jako obsazená
Side effects: sedadla se stanou nedostupnými pro ostatní uživatele (seat map i kapacita na výpisu promítání); u > 10 míst se rezervace objeví v admin panelu k schválení
Failure behavior: reject se zprávou pro uživatele; prošlý draft přechází do EXPIRED; konflikt sedadel (BR-02) -> reject beze změny stavu; jiný stav než DRAFT -> reject beze změny


## OP-04 — Cancel Reservation

Operation: POST /reservations/{reservationId}/cancel
Responsibility: Cancellation
Input: reservationId
Output: stav rezervace CANCELLED (nebo potvrzení beze změny při idempotentním opakování)
Preconditions: rezervace existuje; Reservation.state v {DRAFT, PENDING_APPROVAL, CONFIRMED}; do začátku promítání zbývá alespoň 30 minut (BR-04); promítání je v budoucnosti (BR-05)
Postconditions: rezervace je ve stavu CANCELLED; záznamy v reserved_seat jsou smazány; sedadla jsou opět volná a rezervovatelná
Side effects: uvolněná sedadla se zpřístupní ostatním uživatelům (seat map, kapacita)
Failure behavior: již CANCELLED -> idempotentní úspěch (BR-03); REJECTED/EXPIRED (terminální) -> reject; méně než 30 minut do začátku -> reject (BR-04)


## OP-05 — Approve / Reject Reservation (Admin)

Operation: POST /admin/reservations/{reservationId}/approve | /reject (VALIDATE_RESERVATION)
Responsibility: Admin validation
Input: reservationId, rozhodnutí admina (Approve/Reject), přihlášená admin session
Output: stav rezervace CONFIRMED (Approve) nebo REJECTED (Reject)
Preconditions: admin je přihlášen (session admin/admin); rezervace existuje; Reservation.state == PENDING_APPROVAL (tj. > 10 míst, C02)
Postconditions: Approve -> CONFIRMED, alokace sedadel zůstává beze změny; Reject -> REJECTED (terminální), záznamy v reserved_seat se mažou a sedadla se uvolňují
Side effects: výsledek rozhodnutí se odesílá uživateli přes NotificationIntegration (3 pokusy; selhání notifikace se pouze loguje)
Failure behavior: stav mimo PENDING_APPROVAL -> reject beze změny; nepřihlášený uživatel -> přesměrování na domovskou stránku, operace se neprovede; souběh (Cancel mezitím / dvě rozhodnutí) -> první operace proběhne, druhá je odmítnuta; selhání notifikace nikdy nevrací rozhodnutí zpět