REQ-01 — Create Reservation
System vytvoří DRAFT normální rezervace pro existující sedadla, když požadovaný interval sedadel je volný.

REQ-02 — Check Availability
Pro existující promítání systém vrátí seznam všech sedadel v sále s jejich aktuálním stavem.

REQ-03 — Confirm Reservation
Systém změní stav rezervace z DRAFT na CONFIRMED a trvale zapíše alokaci sedadel pro dané promítání.

REQ-04 — Cancel Reservation
Systém umožní zrušit rezervaci ve stavu DRAFT nebo CONFIRMED, a to nejdéle 30 minut před začátkem promítání. Po zrušení rezervace přestane blokovat dostupné zdroje (sedadla).

REQ-05 — Approve / Reject Reservation (Admin Approval)
Přihlášený admin může u rezervace ve stavu PENDING_APPROVAL provést operaci VALIDATE_RESERVATION:
- Approve: PENDING_APPROVAL -> CONFIRMED (sedadla zůstávají alokovaná)
- Reject: PENDING_APPROVAL -> REJECTED (alokovaná sedadla se uvolní)
