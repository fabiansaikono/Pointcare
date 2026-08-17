# Plan voor V2-dekking leerjaar 3–6 en ondersteunende vertalingen

Datum: 2026-08-11

## Doel en uitgangspunten

Het webcurriculum blijft de enige bron van lesdata. Nieuwe of gemigreerde lessen worden eerst daar opgenomen, doorlopen de V2-audit en worden pas daarna naar productie en Android geëxporteerd. Bestaande vaste oefenbanken blijven behouden; dit plan voegt er een canonieke leslaag met Theorie, Oefen en Toets rond.

Onbeoordeelde machinevertalingen gelden niet als voltooid. Een ontbrekende Franse of Engelse vertaling blijft expliciet op `MISSING` staan met `nl-BE` als fallback totdat een inhoudelijk beoordelaar ze goedkeurt.

## Nulmeting

- 324 lessen in de bibliotheek: 199 kernlessen en 125 actieve verhaallessen.
- 25.200 vaste oefeningen blijven gepubliceerd en bereikbaar.
- V2-kernlesdekking: 73 lessen in leerjaar 1, 118 in leerjaar 2, vier dicteelessen in leerjaar 3 en vier in leerjaar 4.
- Leerjaar 5 en 6 hebben nog geen volledige canonieke V2-kernleslaag.
- Alle 125 verhalen hebben volledige Franse en Engelse contentmetadata.
- De 199 kernlessen hebben 10.910 ontbrekende ondersteunende veldvertalingen; die gebruiken gecontroleerd de Nederlandse fallback.

## Uitvoeringsvolgorde

### Fase 1 — inventariseren en koppelen

1. Maak per leerjaar en subcategorie een lijst van de bestaande vaste oefenreeksen.
2. Koppel elke reeks aan één beoogde canonieke les en leg prerequisite-, beschikbaarheids- en sequentieregels vast.
3. Markeer dubbels, verweesde oefenreeksen en oefenvormen die niet in de huidige leerlingrenderer passen.
4. Start met leerjaar 3 en 4; verwerk daarna leerjaar 5 en 6.

### Fase 2 — canonieke V2-lessen bouwen

1. Bouw per gekoppelde eenheid exact Theorie, Oefen en Toets.
2. Integreer extra/plus-oefeningen in Oefen of maak alleen bij inhoudelijke incompatibiliteit een afzonderlijke les.
3. Voeg les-specifieke uitleg, één ouderuitleg waar begeleiding nodig is, beheersingscriteria en `wrong answers allowed` toe.
4. Orden binnen elke subcategorie van eenvoudig naar moeilijk en geef elke les een duidelijke, unieke naam.
5. Publiceer pas wanneer alle vaste oefeningen bereikbaar zijn vanuit hun canonieke lesroute.

### Fase 3 — technische en pedagogische validatie

Per nieuwe les moet vóór publicatie gelden:

- stabiele en unieke ID's voor les, lesdelen, pagina's, reeksen, vragen en antwoorden;
- exact één correcte antwoordoptie waar dat oefentype dit vereist;
- geen redactionele placeholders, voorbeeldkaartlekken of dubbele zichtbare opties;
- leerlingrenderer ondersteunt het oefentype, inclusief audio en aangepast toetsenbord waar nodig;
- doorlopende sequentie en geldige vereisten binnen de subcategorie;
- `npm run audit:v2`, de webtests en de Android-catalogustests slagen.

### Fase 4 — Franse en Engelse ondersteuning

1. Bevries eerst de gevalideerde Nederlandse lesinhoud.
2. Vertaal per volledige les, niet los per willekeurig veld, in deze volgorde: zichtbare instructies, oefenvragen, antwoordopties, feedback en ouderuitleg.
3. Laat terminologie en pedagogische betekenis inhoudelijk nakijken.
4. Zet veldstatus pas na controle van `MISSING` naar `COMPLETE`; gebruik `NEEDS_REVIEW` wanneer nog een tweede controle nodig is.
5. Voer na elke vertaalbatch de volledige audit en visuele steekproef in alle drie talen uit.

## Publicatiepoorten

Een batch mag naar productie en Android wanneer:

- de V2-audit nul fouten en nul waarschuwingen rapporteert;
- alle nieuwe lessen via een leerlingtegel of tegeloptie bereikbaar zijn;
- elke les exact Theorie, Oefen en Toets heeft en de sequentie doorlopend is;
- vaste oefeningen, verhalen en eerder gevalideerde ID's niet zijn verdwenen;
- vertaalstatussen de werkelijke reviewstatus weergeven;
- webbuild, releasetests en Android-unit/catalogustests slagen.

## Aanbevolen eerstvolgende batch

Begin met alle niet-dictee-oefenbanken van leerjaar 3. Maak eerst de route- en leskoppeling compleet, migreer vervolgens één subcategorie end-to-end als referentiebatch, en herhaal pas daarna hetzelfde patroon voor de overige subcategorieën en leerjaren.
