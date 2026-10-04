## A1. Scénář a související požadavky / pravidla

**Scénář / operace:** Confirm Reservation (`POST /api/reservations/{id}/confirm`) 

**Požadavky:** 
- REQ-03 (Potvrzení rezervace)
- REQ-01 (Vytvoření rezervace)
- 
**Pravidla / invarianty:**
- BR-01 (Dvě potvrzené rezervace na stejné sedadlo a promítání se nesmí překrývat)
- BR-02 (Rezervaci lze potvrdit pouze ze stavu PENDING)
- 
**Baseline:** v0.2

## A2. Mapování hlavního průchodu scénáře na kód

1. **Přijetí HTTP požadavku**:
   * **Kód**: `cz.vsb.cs.swi.reservation.controller.ReservationController.confirmReservation(Long id)`
   * **Popis**: Controller přijme HTTP POST požadavek na `/api/reservations/{id}/confirm`.

2. **Načtení rezervace z databáze**:
   * **Kód**: `cz.vsb.cs.swi.reservation.service.ReservationService.confirmReservation(Long id)` -> `ReservationRepository.findById(id)`
   * **Popis**: Servis načte entitu `Reservation` podle ID. Pokud neexistuje, vyhodí výjimku `ResourceNotFoundException`.

3. **Kontrola stavu a obchodního pravidla (Invariant BR-02)**:
   * **Kód**: `cz.vsb.cs.swi.reservation.model.Reservation.confirm()`
   * **Popis**: Entity kontroluje, zda je aktuální stav `ReservationStatus.PENDING`. Pokud ne, vyhodí `IllegalStateException`.

4. **Kontrola dostupnosti sedadel / překryvu (Invariant BR-01)**:
   * **Kód**: `cz.vsb.cs.swi.reservation.service.ReservationService.validateSeatAvailability(Reservation reservation)`
   * **Popis**: Servisa ověří, zda vybraná sedadla (`ReservedSeat`) pro dané promítání (`Screening`) již nejsou blokována jinou potvrzenou rezervací.

5. **Změna stavu na CONFIRMED a perzistence**:
   * **Kód**: `cz.vsb.cs.swi.reservation.model.Reservation.setStatus(ReservationStatus.CONFIRMED)` -> `ReservationRepository.save(reservation)`
   * **Popis**: Stav rezervace je změněn na `CONFIRMED` a uložen do databáze.

6. **Odeslání notifikace (Externí závislost)**:
   * **Kód**: `cz.vsb.cs.swi.reservation.integration.NotificationServiceClient.sendReservationConfirmedNotification(Reservation reservation)`
   * **Popis**: Volání externího notifikačního servisu pro informování zákazníka.

7. **Návrat odpovědi klientovi**:
   * **Kód**: `cz.vsb.cs.swi.reservation.controller.ReservationController` -> `ResponseEntity.ok(DTO)`
   * **Popis**: Návrat HTTP 200 OK s aktualizovaným DTO rezervace.

## A3. Mapování doplňkové / chybové větve

**Chybová větev: Pokus o potvrzení již potvrzené nebo zrušené rezervace (Porušení BR-02)**

1. **Vstup**: Klient pošle požadavek na potvrzení rezervace, která má stav `CONFIRMED` nebo `CANCELLED`.
2. **Detekce chyby**:
   * **Kód**: `cz.vsb.cs.swi.reservation.model.Reservation.confirm()`
   * **Mechanism**: Metoda zkontroluje `this.status != ReservationStatus.PENDING` a vyhodí `IllegalStateException("Reservation cannot be confirmed from current state")`.
3. **Zpracování chyby**:
   * **Kód**: `cz.vsb.cs.swi.reservation.exception.GlobalExceptionHandler.handleIllegalState(IllegalStateException ex)`
   * **Výstup**: Výjimka je zachycena v ControllerAdvice a převedena na HTTP status `400 Bad Request` s chybovou zprávou v JSON odpovědi. Databáze zůstane nezměněna.


## A4. Seznam hlavních částí implementace

* **`ReservationController`**: REST endpointy, mapování DTO a správa HTTP odpovědí.
* **`ReservationService`**: Aplikační logika, řízení transakcí, koordinace doménové logiky a externích služeb.
* **`Reservation` (Domain Entity)**: Držitel stavu rezervace a doménových правил přechodu stavů (PENDING -> CONFIRMED / CANCELLED).
* **`ReservationRepository`**: Rozhraní Spring Data JPA pro perzistenci a dotazování nad entitou `Reservation`.
* **`NotificationServiceClient`**: Komponenta pro REST/HTTP integraci s externím notifikačním systémem.

## A5. Stav, změna stavu a pravidlo

* **Kde je stav uložen**: Fyzicky v relaci DB tabulky `reservations` (sloupec `status`). V běžící aplikaci v doménové entitě `Reservation.status`.
* **Kdo rozhoduje o změně stavu**: Doménová entita `Reservation` ve své metodě `confirm()`.
* **Kde se vynucuje business pravidlo (BR-01 / BR-02)**: 
  * Pravidlo stavového přechodu (BR-02) se vynucuje přímo uvnitř entity `Reservation`.
  * Pravidlo nepřípustnosti překryvu sedadel (BR-01) se vynucuje v `ReservationService` před změnou stavu.


## A6. Relevantní externí a perzistenční závislosti

| Závislost | Typ | Bod integrace v kódu | Použitá metoda / rozhraní |
| :--- | :--- | :--- | :--- |
| **Relační databáze (H2 / PostgreSQL)** | Perzistenční | `ReservationRepository` | Spring Data JPA (`findById`, `save`) |
| **Notification Service** | Externí HTTP API | `NotificationServiceClient` | `RestTemplate` / `WebClient` (`POST /api/notifications`) |




## A8. Architektonická otázka pro další návrh

> **Otázka**: Jakým způsobem zajistíme konzistenci a zabráníme race condition (souběžným požadavkům na potvrzení/rezervaci stejného sedadla na stejné promítání v jeden okamžik), pokud databázová kontrola v `ReservationService` probíhá na úrovni aplikační logiky bez použití pesimistického/optimistického zamykání nebo databázových unikátních indexů?


