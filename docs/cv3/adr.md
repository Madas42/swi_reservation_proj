## ADR-01 — Kde má být provedeno confirmation decision a vynuceno pravidlo BR-02?

**Kontext:** 
Při souběžném odeslání dvou požadavků na potvrzení rezervace pro stejný zdroj a překrývající se časový interval může dojít k race condition. Současná AS-IS implementace provádí kontrolu dostupnosti a zápis přímo v aplikační službě bez vynucení databázového či doménového zámku, což umožňuje oběma transakcím projít validací.

**Drivery:**
- BR-02: Dvě potvrzené rezervace stejného resource se nesmí překrývat.
- Požadavek na konzistenci stavu a ochranu proti souběžným požadavkům (concurrency).

**Alternativa A:** 
Vynucení konzistence a rozhodnutí o potvrzení (confirmation decision) přímo v aplikační vrstvě pomocí programových zámků (Mutex / synchronized) v paměti aplikace.

**Alternativa B:** 
Provedení rozhodnutí v doménové vrstvě podpořené databázovým omezením (Unique constraint / Pessimistic Locking nebo `@Version` v perzistenční vrstvě) spravovaným komponentou `Reservation Management`.

**Rozhodnutí:** 
Vybíráme **Alternativu B**. Autoritativní rozhodnutí proběhne v doménové vrstvě a bude vynuceno na úrovni databázové transakce a perzistence, za kterou odpovídá prvek `Reservation Management`.

**Důvod:** 
Zajišťuje spolehlivou ochranu proti race conditions i při vícero instancích aplikace. Doménový model zůstane konzistentní a integrita dat bude garantována na úrovni databázové vrstvy.

**Přijaté negativní důsledky:** 
- Mírné zvýšení složitosti perzistenční vrstvy.
- Nutnost správného ošetření výjimek při souběžném selhání transakce (např. konflikty verzí nebo porušení unikátnosti).

**Rozhodnutí znovu otevřeme, když:** 
Budeme přecházet na mikroslužbovou architekturu s event-driven konsistencí (Saga pattern), kde nebude přímá sdílená databázová transakce možná.
