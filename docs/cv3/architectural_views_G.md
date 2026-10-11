#Architectural Views (G)

##G1 - Context diagram
![img.png](diagrams/diagram contexxt system.png)

##G2 - To-Be diagram
![img.png](diagrams/tobe_diagram.png)

##G3 - Ownership přechodů
| Přechod                       | Owner rozhodnutí         | Kdo může transition pouze vyžádat |
|-------------------------------|--------------------------|-----------------------------------|
| DRAFT -> PENDING_APPROVAL     | [Reservation Management] | Zákazník (Uživatel)               |
| PENDING_APPROVAL -> CONFIRMED | [Approval Workflow]      | Admin                             |
| PENDING_APPROVAL-> REJECTED   | [Approval Workflow]      | Admin                             |


##G4 - Runtime/Deployment mapping
![img.png](diagrams/runtime_deployment mapping.png)
