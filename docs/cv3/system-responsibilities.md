| Zdroj               | Odpovědnost                                 | Co musí rozhodovat / vlastnit                                                                        | Potřebuje jednoho jasného vlastníka? | Důvod                                                                  |
|---------------------|---------------------------------------------|------------------------------------------------------------------------------------------------------|--------------------------------------|------------------------------------------------------------------------|
| OP-01, OP-03, OP-05 | Řízení životního cyklu rezervace            | Rozhodnout, zda je přechod mezi stavy (DRAFT → PENDING_APPROVAL → CONFIRMED / CANCELLED) povolen.    | Ano                                  | Různé části systému nesmějí interpretovat stavy odlišně.               |
| BR-02               | Vyhodnocení konfliktů a exkluzivity sedadel | Ověřit, zda vybraná sedadla nekolidují s jinou existující rezervací.                                 | Ano                                  | Paralelní requesty nesmí porušit pravidlo exkluzivity sedadel.         |
| OP-05, REQ-05       | Správa odloženého schvalování               | Vlastnit mezistav PENDING_APPROVAL (pro rezervace > 10 míst) a zpracovat pozdější rozhodnutí admina. | Ano                                  | Stav musí bezpečně přečkat konec prvotního requestu a čekat na admina. |
| OP-03, OP-04        | Časová správa a expirace (TTL)              | Detekovat uplynutí časového limitu (TTL) pro DRAFT nebo hranice 30 minut před promítáním.            | Ano                                  | Čas mění platnost zdrojů plynule a bez přímé akce uživatele.           |
| C01 / Notifikace    | Doručování externích zpráv                  | Rozhodnout o odeslání notifikace a ošetřit selhání komunikace s cizí službou.                        | Ano                                  | Výpadek externí třetí strany nesmí ohrozit data v databázi.            |



Seskupení a oddělení odpovědností (Grouping & Separation)

1. Řízení životního cyklu rezervace

S čím seskupit: S pravidly chování rezervace (jaké stavy po sobě logicky následují). Protože aktuální stav lístku a pravidla pro jeho změnu tvoří jeden neoddělitelný celek.

Od čeho oddělit: Od toho, jak se data fyzicky ukládají (databáze). Protože vnitřní pravidla kina se mohou měnit nezávisle na tom, jakou databázi systém zrovna používá.

2. Vyhodnocení konfliktů a exkluzivity (BR-02)

S čím seskupit: S databází a systémem pro bezpečné ukládání (transakcemi). Kontrola toho, zda se nepřekrývají dvě rezervace, vyžaduje okamžitý pohled na to, co je fyzicky uloženo.

Od čeho oddělit: Od uživatelského rozhraní (tlačítek na webu) a notifikací. Jde o čistě bezpečnostní pravidlo pro ochranu dat na pozadí.

3. Správa odloženého schvalování

S čím seskupit: S celkovým životním cyklem rezervace. Čekání na schválení od admina je jen další krok v "životě" dané rezervace.

Od čeho oddělit: Od běžného a rychlého prodeje malých rezervací. Schvalování velkých skupin je odlišný proces, který řeší manažer přes svůj vlastní panel, a nesmí nijak komplikovat standardní nákup lístků.

4. Časová správa a expirace (TTL)

S čím seskupit: S časovými údaji, které v sobě rezervace má (kdy byla vytvořena, kdy začíná film).

Od čeho oddělit: Od klikání a přímých akcí uživatele. Systém musí sám na pozadí hlídat čas. Jakmile limit vyprší, automaticky uvolní nezaplacená sedadla.

5. Doručování notifikací

S čím seskupit: S ničím. Tento úkol funguje zcela izolovaně na samotném okraji systému.

Od čeho oddělit: Od hlavní logiky kina a ukládání do databáze. Důvod je zásadní: pokud systém nedokáže odeslat email (např. kvůli výpadku cizího serveru), nesmí to smazat nebo rozbít rezervaci, kterou už systém úspěšně uložil.
