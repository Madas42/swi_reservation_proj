## OP-01 — Create Reservation

Cíl / hodnota pro uživatele: udelat reyervace pro uzivatele. hodnota pro uživatelenavrch uspesna rezervace
Spouštěcí událost: pozadavek pro vytvoreni rezervace.
Pozorovatelný požadavek / požadavky:
REQ-01
system vytvori DRAFT normalni rezervace ro existujici sedadla, kdyz pozadovany interval sedadel je volny

Předpoklady: 
- zdroj existuje (misto, sal, film, promitani)
- rezervace musi byt vytvorena pred zacatkem filmu
- uzivatel nema na dane promitani jiz vytvorenou rezervaci 

Stav po úspěšném provedení: 
- existuje nova rezervace
- rezervace ve stavu DRAFT
- zdroje (sedadla) jeste nejsou zapsane do databaze 

Změna stavu:
- (none) -> DRAFT
  
Odkaz na doménová pravidla / invarianty: 
BR-01 (none) 

Hlavní úspěšný scénář:
1. uzivatel zada pocet mist na danem promitani a vybere interval sedadel
2. system validuje uzivatelem zadana data
3. system vytvari DRAFT rezervace
4. system vrati ID rezervace a jeho aktualni stav
...

Alternativní / chybové výsledky:
1. Uzivatel zada pocet sedadel mimo povolene rozsahy -> reject ; no Reservation created
2. Chybejici/chybne kontaktni udaje -> reject ; no Reservation created

Příklady ověření:
1. Validni udaje plus validni zdroje -> DRAFT created
2. start rovna se end -> rejected
3. Nevalidni kontaktni udaje -> rejected

Zdůvodnění / zdroj: Vytvorit zaznam uzivatelskeho zameru bez alokace zdroju
Předpoklad / neznámá / TBD: 
jen pokud je něco skutečně nerozhodnuté

## OP-02 — Check Availability Adela

Cíl / hodnota pro uživatele:
Spouštěcí událost:
Pozorovatelný požadavek / požadavky:
Předpoklady:
Stav po úspěšném provedení:
Změna stavu:
Odkaz na doménová pravidla / invarianty:

Hlavní úspěšný scénář:
1.
2.
3.
...

Alternativní / chybové výsledky:
...

Příklady ověření:
...

Zdůvodnění / zdroj:
Předpoklad / neznámá / TBD:
jen pokud je něco skutečně nerozhodnuté

## OP-03 — Confirm Reservation Karolina

Cíl / hodnota pro uživatele:
Spouštěcí událost:
Pozorovatelný požadavek / požadavky:
Předpoklady:
Stav po úspěšném provedení:
Změna stavu:
Odkaz na doménová pravidla / invarianty:

Hlavní úspěšný scénář:
1.
2.
3.
...

Alternativní / chybové výsledky:
...

Příklady ověření:
...

Zdůvodnění / zdroj:
Předpoklad / neznámá / TBD:
jen pokud je něco skutečně nerozhodnuté

## OP-04 — Cancel Reservation Kuba

Cíl / hodnota pro uživatele:
Spouštěcí událost:
Pozorovatelný požadavek / požadavky:
Předpoklady:
Stav po úspěšném provedení:
Změna stavu:
Odkaz na doménová pravidla / invarianty:

Hlavní úspěšný scénář:
1.
2.
3.
...

Alternativní / chybové výsledky:
...

Příklady ověření:
...

Zdůvodnění / zdroj:
Předpoklad / neznámá / TBD:
jen pokud je něco skutečně nerozhodnuté
