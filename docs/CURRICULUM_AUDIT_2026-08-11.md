# Poincaré curriculum audit

Auditdatum: 2026-08-11

## Eindresultaat

De geprioriteerde vervolgacties zijn uitgevoerd. De volledige strikte V2-bibliotheekaudit staat nu op `PASS` met nul fouten en nul waarschuwingen. De productiebuild, TypeScript-controle, 35 releasetests en alle 334 Android-unit- en catalogustests slagen. De bron is naar Android geëxporteerd en een lokale debug-APK is gebouwd, maar niets is op een toestel geïnstalleerd.

De Curriculum Room/webcurriculum blijft de unieke bron van lesdata. Deze bron bevat 324 lessen, 25.200 vaste oefeningen en 125 actieve verhaallessen. De Android-export bevat de 199 kernlessen, 25.200 oefeningen, 152 verhaalrecords en 19 boeken.

## Geauditeerde omvang

- 324 lessen: 199 kernlessen en 125 verhaallessen.
- 121 lessen in leerjaar 1 en 145 in leerjaar 2, inclusief verhalen.
- 25.200 vaste oefeningen.
- 330.010 stabiele unieke ID's.
- 5.455 vertaalbare inhoudsvelden.
- 10.910 bewust als ontbrekend geregistreerde FR/EN-ondersteuningsvertalingen.
- 125 van 125 actieve verhalen hebben volledige Franse en Engelse contentmetadata.
- Alle 199 kernlessen gebruiken `nl-BE` als bron en hebben expliciete `MISSING`-records met fallback `nl-BE` voor nog niet beoordeelde FR/EN-inhoud.

## Uitgevoerde correcties

### Canonieke bron en publicatiestroom

- Curriculumdata wordt in het webcurriculum gebouwd en gecontroleerd en pas daarna naar Android geëxporteerd.
- Gegenereerde en manueel gecodeerde oefeningen zijn afzonderlijk herkenbaar; gegenereerde vragen tonen criteria en een voorbeeld.
- V1/V2 werd als gebruikersfilter verwijderd; bronsoort, publicatiestatus, versiehistoriek en stabiele ID's blijven zichtbaar.
- De standaard testopdracht voert voortaan eerst de strikte V2-audit uit, zodat een releasebuild bij auditfouten blokkeert.

### Lesstructuur en sequentie

- Iedere kernles heeft exact Theorie, Oefen en Toets, in die volgorde.
- De hiërarchielabels van kernlessen zijn contractueel Nederlands; vertalingen worden via afzonderlijke contentmetadata en expliciete fallbacks beheerd.
- Extra/plus-oefeningen zijn in Oefen geïntegreerd.
- `wrong answers allowed`, lessequentie, beschikbaarheidsregels en vereisten zijn opgenomen.
- Routes zijn doorlopend pedagogisch geordend; dubbele generieke leerjaargroepen bij onder meer optellen en aftrekken zijn vervangen door betekenisvolle stappen.
- Schrijven begint met houding, lijnen en vormen; letters en woorden volgen gradueel. `Alle letters oefenen` volgt op `Hoofdletters N tot Z`.

### Oefeningen, antwoorden en schermindeling

- Leerlingvreemde voorbeeldkaart-/steuntekst en ongeschikte placeholderantwoorden worden tijdens release en runtime verwijderd.
- Antwoordopties worden uniek gemaakt; vragen met één vereist antwoord hebben één ondubbelzinnig correct antwoord.
- De Franse begroetingsvragen en vergelijkbare vraagpools doorlopen dezelfde algemene integriteitscontrole.
- Voorlezen leest alleen de oefenvraag en herhaalt niet de algemene lesopdracht.
- De bovenste Theorie/Oefeningen/Toets-knoppen verdwijnen in de lesinhoud.
- Multiplechoice-antwoorden en toetsenborden staan onderaan met ondermarge; de vraaginhoud wordt in de resterende ruimte gecentreerd.
- Bij letteroefeningen zijn alleen de letters uit de actuele oefening selecteerbaar.

### Dictee en woordenschat

- Alle 16 dicteelessen ondersteunen de bedoelde vormen: één of meerdere ontbrekende letters, een volledig woord en een volledige zin.
- Elke dicteeles heeft exact één `UITLEG_OUDERS`-blok en contractconforme lesdeel-/reekstitels en fallbackmetadata.
- Het vaste AZERTY-toetsenbord wordt gebruikt; dicteetekst wordt uitgesproken en kan opnieuw worden afgespeeld.
- `Woordenschat Frans` en de afzonderlijke route `Woordenschat Engels` voor leerjaar 1 en 2 zijn opgenomen.

### Verhalen

- Verhalen hebben een afzonderlijke, actieve tegel onder Lezen & luisteren.
- Reeksen heten `Kleine verhaaltjes`, `Nova` en `Robbie`; boeken zijn per reeks genummerd, gesorteerd en voorzien van AVI.
- Een ongeldig opgeslagen boeknummer zoals 999 wordt genegeerd ten voordele van het canonieke Robbie-boeknummer.
- Robbie bevat acht boeken; elk boek heeft 12 afzonderlijke hoofdstukken en 150 pagina's.
- Lange leestekst wordt verdeeld of de afbeelding wordt verkleind om overflow te vermijden.
- De metadatafix voor kernlessen tast de bestaande Franse en Engelse verhaalinhoud niet aan; een regressietest bewaakt alle 125 verhalen.

### Navigatie en puzzels

- Verhalen- en puzzeltegels zijn actief wanneer hun eigen bron beschikbaar is.
- Tegels zonder onderliggende inhoud worden grijs weergegeven.
- Canonieke lessen en vaste oefeningen zijn via een leerlingroute bereikbaar.

## Resultaat van de geprioriteerde vervolgacties

1. **Engelse woordenschat — voltooid.** De twee lessen volgen het Nederlandse hiërarchiecontract, hebben expliciete FR/EN-fallbackmetadata en behouden een eigen leerlingroute.
2. **Dictee — voltooid.** Alle 16 lessen hebben exact één ouderuitleg en geldige hiërarchie-, reeks- en vertaalmetadata.
3. **Vertaalcontract — beslist en toegepast.** Kernhiërarchie is Nederlands; nog niet beoordeelde FR/EN-velden blijven expliciet `MISSING` met Nederlandse fallback. Verhalen behouden hun volledige vertalingen.
4. **V2-dekking leerjaar 3–6 — gepland.** De gefaseerde migratie- en acceptatiepoorten staan in `docs/CURRICULUM_G3_G6_TRANSLATION_PLAN_2026-08-11.md`.
5. **Ondersteunende vertalingen — veilig gefaseerd.** Er zijn geen onbeoordeelde machinevertalingen als voltooid gepubliceerd; 10.910 velden blijven traceerbaar in de backlog met fallback.

## Verificatie

### Geslaagd

- `npm run audit:v2`: `PASS`, 0 fouten, 0 waarschuwingen.
- `npm test`: audit, productiebuild en 35/35 releasetests geslaagd.
- `npm run typecheck`: geslaagd.
- `npm run data:export-android`: geslaagd; release `2026-08-11-b9cf5a991928`.
- Geforceerde Android-suite: 334 tests, 0 fouten, 0 overgeslagen.
- `gradlew assembleDebug`: geslaagd.
- Debug-APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `602DCF33AC561DF295F2D74EE623D1CE0A7AEDCC4E1AE9E58FE5DABD18A8A83B`.

### Niet uitgevoerd

- Geen installatie op smartphone of ander toestel.
- Geen Windows-afsluiting; daarvoor is nog een afzonderlijke expliciete toestemming nodig.

## Resterende inhoudelijke backlog

- Buiten dictee is de canonieke V2-kernleslaag voor leerjaar 3–6 nog niet volledig uitgebouwd. De bestaande vaste oefenbanken blijven behouden en bereikbaar.
- De 10.910 Franse/Engelse ondersteuningsvelden van kernlessen vereisen inhoudelijke vertaling en review; de huidige fallbackstatus is technisch correct en auditconform.

## Bronnen

- Machineleesbaar auditrapport: `curriculum-library/public/data/v2-library-audit.json`.
- Dekking- en vertaalplan: `docs/CURRICULUM_G3_G6_TRANSLATION_PLAN_2026-08-11.md`.
- Releasecontract: `curriculum-library/tests/*.test.mjs`.
- Android-catalogusvalidatie: `app/build/test-results/testDebugUnitTest/`.
