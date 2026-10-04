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
