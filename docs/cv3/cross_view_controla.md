## I. Proveďte cross-view kontrolu

| Kontrola | Otázka | Výsledek kontroly / Stav |
| :--- | :--- | :--- |
| **C02 ↔ G2** | Umí architektura realizovat požadované chování a pravidla? | **OK** — Komponenta `Reservation Management` plně pokrývá celý životní cyklus rezervace a zajišťuje ověřování překryvů podle pravidla BR-02. |
| **C2 ↔ G2** | Má každá významná odpovědnost jednoho ownera? | **OK** — Každá definovaná odpovědnost má v G2 přiřazeného právě jednoho jednoznačného vlastníka (owner). |
| **G2 ↔ H1** | Používá sekvence pouze existující/povolené závislosti? | **OK** — Diagram sekvence (H1) volá výhradně komponenty a rozhraní definované ve statické architektuře (G2). |
| **H1 ↔ H2** | Má každá významná zpráva/operace strukturálního vlastníka? | **OK** — Všechny zprávy a operace v sekvenčním diagramu odpovídají metodám v návrhovém třídním diagramu (H2). |
| **statechart ↔ G3/H1** | Rozhoduje transition správný owner? | **OK** — Přechody mezi stavy ve statechartu řídí výhradně `Reservation Management`, což přesně odpovídá tabulce vlastnictví přechodů. |
| **G2 ↔ G4** | Je každý logický prvek realisticky namapovaný do runtime? | **OK** — Všechny logické moduly a komponenty z G2 jsou korektně obsaženy v jednom běhovém procesu aplikace s vazbou na relační databázi (G4). |
| **ADR ↔ G2/G4** | Je přijaté rozhodnutí skutečně vidět v architektuře? | **OK** — Zvolené architektonické řešení transakčního vlastníka a perzistence z ADR-01 je explicitně zobrazeno v G2 i G4. |

