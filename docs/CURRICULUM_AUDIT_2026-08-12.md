# Poincaré curriculum audit

Auditdatum: 2026-08-12  
Auditbasis: `docs/GENERIC_DESCRIPTION_CURRICULUM.md`  
Canonieke statische bron: `curriculum-library/public/data/`  
Curriculumcommit: `90d6553`  
Androidcommit: `48303da`

## Samenvatting en oordeel

De nieuwe audit geeft een **voorwaardelijke PASS**.

- **Technische releasepoort: PASS.** De strikte bibliotheekaudit, productiebuild, 43 webtests, 335 Android-unit-/catalogustests en debugbuild slagen zonder fouten.
- **Structuur en bereikbaarheid: PASS.** De kernlessen volgen Theorie → Oefen → Toets, oefenreeksen zijn opnieuw gescheiden, verhalen en puzzels zijn bereikbaar en de mobiele Curriculum Room behoudt de filters.
- **Definitieve inhoudelijke validatie: NOG NIET GESLAAGD.** Alle 218 kernlessen zijn nog `DRAFT_REVIEW_REQUIRED`; 2.233 van 2.266 kern-oefenreeksen en 17.525 van 17.798 vraagblauwdrukken staan nog op `NEEDS_REVIEW`.
- **Pedagogische paginering: NOG NIET VOLDOENDE.** In 173 lessen bevat één woordenschatpagina meerdere begrippen. Over de kerncatalogus staan 764 woordenschattermen tegenover 393 afzonderlijke kindvriendelijke definitieblokken. Eén Engelse woordenschatpagina bevat zelfs 50 termen.
- **Unieke bron in runtime: NOG TE SLUITEN.** De statische bron telt 343 lessen; de actuele Curriculum Room toonde tijdens de audit 344 lessen en twee actieve wijzigingsrecords. De effectieve editorlaag en de geëxporteerde release moeten daarom vóór publicatie aantoonbaar tot éénzelfde snapshot worden samengevoegd.

Er zijn geen P0-defecten gevonden die de build, het starten van de app of het volledig laden van de catalogus blokkeren. Er is in deze audit niets gepubliceerd en niets op een toestel geïnstalleerd.

## Geauditeerde omvang

### Canonieke curriculumdata

- 343 lessen: 218 kernlessen en 125 actieve verhaallessen.
- 2.234 lesfasen.
- 2.318 oefenreeksen in de volledige bibliotheek.
- 25.200 vaste oefeningen.
- 340.599 stabiele unieke ID's.
- 1.266 iconen.
- 19 boeken, 130 hoofdstukken en 2.037 verhaalpagina's.
- 152 verhaalbronrecords en 108 verhaalvragen.
- 657 resterende legacylessen; verwijdering van de legacy-adapter blijft terecht geblokkeerd tot dit aantal nul is.

### Dekking per leerjaar

- Leerjaar 1: 83 kernlessen; 131 lessen inclusief verhalen.
- Leerjaar 2: 127 kernlessen; 154 lessen inclusief verhalen.
- Leerjaar 3: 4 kernlessen.
- Leerjaar 4: 4 kernlessen.
- Leerjaar 5 en 6: geen V2-kernlessen.

### Vertalingen

- Nederlands (`nl-BE`): 343 lessen als standaardbron.
- Frans (`fr-BE`): 125 volledige verhaallessen; 218 kernlessen met expliciete ontbrekende ondersteuning en Nederlandse fallback.
- Engels (`en-US`): 125 volledige verhaallessen; 218 kernlessen met expliciete ontbrekende ondersteuning en Nederlandse fallback.
- 6.315 vertaalbare inhoudsvelden en 12.630 ontbrekende FR/EN-ondersteuningsvertalingen.

De fallback is technisch correct en traceerbaar, maar mag niet worden verward met inhoudelijk voltooide meertaligheid.

## Geslaagde controles

### Curriculumcontract en structuur

- Elke kernles heeft exact Theorie, Oefen en Toets in de vereiste volgorde.
- Elke kernles heeft minstens drie theoriepagina's; bereik 3–14, gemiddelde 6,51.
- Elke kernles heeft 2–8 afzonderlijke oefenreeksen; gemiddelde 7,56.
- Alle reeksindexen zijn doorlopend en iedere Oefen-fase bevat precies één eigen oefenreeks.
- Extra/plus-oefeningen zitten in de gewone lesfasen en verschijnen niet als losse leerlingoptie.
- `Herhalen` is geen losse subcategorie meer; herhaling zit in begeleid oefenen, foutenanalyse en adaptieve vervolgvragen.
- Sequenties, vereisten, beschikbaarheidsregels en toegestane foutenaantallen voldoen aan het gegevenscontract.

### Inhoudelijke regressieregels

De geautomatiseerde tests bewaken onder meer:

- kindgerichte en ondubbelzinnige vraagpools voor leerjaar 1 en 2;
- concrete vraagstelling voor klank-tekenkoppeling en levend/niet-levend;
- unieke antwoordopties en één vereist juist antwoord;
- verwijdering van voorbeeldkaart- en placeholderantwoorden als algemeen patroon;
- afzonderlijke conceptpagina's en visuals voor Wereldkennis;
- vijf sequentiële lessen voor Spreken en presenteren en Schrijven en schriftelijke expressie;
- de schrijflijn van houding, lijnen en vormen naar letters en woorden;
- dictee met TTS, aangepaste invoervormen en ouderuitleg;
- afzonderlijke woordenschatroutes voor Frans en Engels;
- boekreeksen Kleine verhaaltjes, Nova en Robbie, inclusief AVI, boekvolgorde en afzonderlijke Robbie-hoofdstukken;
- actieve verhaal- en puzzelroutes en het grijs maken van werkelijk lege tegels.

### Curriculum Room

Een interactieve productiecontrole op desktop en een mobiele viewport van 390 × 844 bevestigde:

- de pagina laadt zonder browserconsolefouten;
- leerjaar-, status- en bronfilters blijven zichtbaar op mobiel;
- V1/V2 is geen gebruikersfilter meer;
- `Manueel gecodeerd`, `Genereert vragen` en `Technisch` zijn wel zichtbaar als brononderscheid;
- stabiele les-, pagina- en vraag-ID's zijn zichtbaar;
- gegenereerde reeksen tonen generatiecriteria en een voorbeeldgebied;
- `Beschrijving nieuwe afbeelding` en `Lescontext mee verwerken` bestaan in de SVG-pagina-editor;
- verhalen verschijnen als eigen bron/type in de Curriculum Room.

### Android

- `gradlew testDebugUnitTest assembleDebug`: geslaagd.
- 335 tests, 0 fouten, 0 overgeslagen.
- Debug-APK gebouwd op `app/build/outputs/apk/debug/app-debug.apk`.
- APK: 620.236.096 bytes (591,5 MiB).
- SHA-256: `5A82214C939A192001A3073E8EEC165B5DBB2C91D58DEA469DD8DA5E5415F6DC`.

## Geprioriteerde bevindingen

### P1 — Inhoud is technisch beschikbaar maar nog niet pedagogisch gevalideerd

Alle 218 kernlessen hebben publicatiestatus `DRAFT_REVIEW_REQUIRED`. Van de 2.266 kern-oefenreeksen zijn er 33 gevalideerd en 2.233 nog te reviewen. Van de 17.798 vraagblauwdrukken zijn er 273 gevalideerd en 17.525 nog te reviewen; alle 10.192 vaste editorvragen in deze kernsets staan op `NEEDS_REVIEW`.

**Impact:** de inhoud is geschikt voor test- en reviewbuilds, maar niet voor het label “volledig gevalideerd”. De bestaande audit slaagt omdat draftinhoud bewust mag worden meegenomen.

**Aanbevolen actie:** voer een traceerbare kindreview per les en per oefenreeks uit; publiceer een les pas als theorie, oefeningen, toets en antwoordlogica afzonderlijk zijn goedgekeurd.

### P1 — Theorie is nog niet overal atomair genoeg

In 173 van de 218 kernlessen bevat een woordenschatpagina meer dan één term. Er zijn 764 termen maar slechts 393 afzonderlijke `CHILD_FRIENDLY_DEFINITION`-blokken. Er zijn geen pagina's met meerdere definitieblokken, wat positief is, maar de overzichtsblokken tonen de definities nog steeds samen. De grootste uitschieters zijn:

- `lesson.g2.en.vocabulary.basic`: 50 termen op één pagina;
- `lesson.g1.en.vocabulary.basic`: 30 termen op één pagina;
- meerdere Wereldkennis-, reken-, taal-, kunst- en bewegingslessen met 5–6 termen op de overzichtspagina.

**Impact:** het kind ziet op de eerste theoriepagina vaak meerdere nieuwe begrippen en voorbeelden tegelijk. Dat botst met de afgesproken regel “één hoofdconcept per pagina”, ook wanneer later afzonderlijke pagina's bestaan.

**Aanbevolen actie:** laat de openingspagina alleen het leerdoel en eventueel een korte woordenlijst zonder definities tonen; geef iedere nieuwe term een eigen pagina met één definitie, één concreet voorbeeld en één passende visual. Splits de Engelse woordenschat bovendien in meerdere pedagogisch geordende lessen.

### P1 — De effectieve Curriculum Room-snapshot en statische release zijn niet aantoonbaar identiek

De lokale statische release telt 343 lessen. De productie-interface toonde tijdens deze audit 344 lessen en twee actieve wijzigingsrecords, waaronder wijzigingen bij `Helft, dubbel en kwart in concrete situaties` en `rob is los!`.

**Impact:** de Curriculum Room is conceptueel de unieke bron, maar de strikte release-audit controleert momenteel vooral de gebouwde statische snapshot. Een nog niet geëxporteerde editorlaag kan daardoor afwijken zonder dat de lokale release-audit dit als fout ziet.

**Aanbevolen actie:** voeg een reproduceerbare “effective snapshot”-stap toe die basisdata en editorwijzigingen samenvoegt, dezelfde snapshot audit, exporteert en van een release-ID/hash voorziet. Laat Curriculum Room, productie-export en Android daarna exact die hash tonen.

### P1 — Transparantie van gegenereerde vragen is aanwezig maar semantisch misleidend

De Curriculum Room toont criteria en een voorbeeld, maar de gecontroleerde gegenereerde reeks gebruikte als voorbeeld een redactionele kernzin (`Kern: Een helft is één van twee gelijke delen.`) in plaats van een echte leerlingvraag met antwoordopties. De ruwe `question_pool_nl` wordt als één technisch, door controlekarakters gescheiden veld getoond. Tegelijk stond `editorial_status` op `approved`, terwijl de reeks in de UI 0 van 6 gevalideerde vragen meldde en de les op `Nakijken` stond.

**Impact:** een reviewer kan ten onrechte denken dat de getoonde voorbeeldvraag representatief of inhoudelijk goedgekeurd is.

**Aanbevolen actie:** parseer de pool in afzonderlijke, leesbare voorbeeldkaarten; toon één deterministisch werkelijk genereerbaar voorbeeld met vraag, opties, juist antwoord en feedback; gebruik één eenduidige validatiestatus als bron voor alle labels.

### P2 — Latere leerjaren en ondersteunende vertalingen blijven onvolledig

Leerjaar 3 en 4 bevatten elk alleen vier V2-kernlessen; leerjaar 5 en 6 hebben er geen. Voor de 218 kernlessen ontbreken 12.630 beoordeelde Franse en Engelse ondersteuningsvertalingen.

**Impact:** dit blokkeert geen Nederlandstalige L1/L2-testrelease, maar wel de claim dat de volledige curriculum- en taaldekking gereed is.

**Aanbevolen actie:** volg het bestaande gefaseerde G3–G6- en vertaalplan, met dezelfde kindreview- en releasepoorten als voor L1/L2.

### P2 — APK- en assetomvang zijn sterk gegroeid

- Huidige APK: 620.236.096 bytes, tegenover 537.958.463 bytes in het vorige buildrapport: +82.277.633 bytes (+15,29%).
- Huidige assets: 616.614.489 bytes, tegenover 424.969.809 bytes: +191.644.680 bytes (+45,10%).
- De TTS-map alleen is 301.646.722 bytes; `assets/poincare` is 314.940.133 bytes.

**Impact:** langere bouw-, download- en installatietijd, meer opslagverbruik en een groter risico op distributielimieten.

**Aanbevolen actie:** maak een duplicatie- en compressierapport per assettype, controleer of TTS/content meerdere keren wordt verpakt en definieer een maximale APK-grootte als releasepoort.

### P2 — Reviewerlabels zijn niet volledig Nederlandstalig

In de Nederlandse Curriculum Room verschijnen onder andere `FAR TRANSFER` en `ERROR REVIEW` nog als technische Engelse labels.

**Impact:** de data is correct, maar het redacteurscherm is minder begrijpelijk en inconsistent.

**Aanbevolen actie:** toon gelokaliseerde labels (`Verre transfer`, `Fouten herbekijken`) en behoud de technische enum alleen intern of naast het stabiele ID.

### P2 — De Android-werkmap is niet schoon

Bij de start van deze audit bevatte de Android-hoofdwerkmap 178 gewijzigde of nieuwe paden. De geneste curriculumrepository was schoon vóór de audit; `npm test` heeft alleen het gegenereerde tijdstip in `public/data/v2-library-audit.json` bijgewerkt.

**Impact:** een geslaagde lokale build is minder eenvoudig exact te reproduceren of aan één gecontroleerde wijziging te koppelen.

**Aanbevolen actie:** inventariseer en groepeer de bestaande wijzigingen, leg de bedoelde releasebestanden vast in een commit of releasebundle en herhaal daarna de audit vanaf een bekende bronstatus.

## Cijfermatige theorie- en oefenaudit

| Controle | Resultaat |
|---|---:|
| Kernlessen | 218 |
| Theoriepagina's | 1.420 |
| Lessen met meerdere woordenschattermen op één pagina | 173 |
| Pagina's met meer dan vier contentblokken | 29 |
| Pagina's met meerdere afzonderlijke definitieblokken | 0 |
| Woordenschattermen | 764 |
| Afzonderlijke kindvriendelijke definitieblokken | 393 |
| Kern-oefenreeksen | 2.266 |
| Gevalideerde kern-oefenreeksen | 33 |
| Te reviewen kern-oefenreeksen | 2.233 |
| Vraagblauwdrukken | 17.798 |
| Gevalideerde vraagblauwdrukken | 273 |
| Te reviewen vraagblauwdrukken | 17.525 |
| Vaste editorvragen met `NEEDS_REVIEW` | 10.192 |

## Verificatie

### Geslaagd

- `npm run audit:v2`: `PASS`, 0 fouten, 0 waarschuwingen.
- `npm test`: productiebuild en 43/43 tests geslaagd.
- `gradlew testDebugUnitTest assembleDebug`: build geslaagd.
- Android: 335/335 tests geslaagd.
- Interactieve Curriculum Room-controle op desktop en mobiel: laadt, filters blijven zichtbaar, ID's en brononderscheid aanwezig, geen browserconsolefouten.
- Catalogusbrede aanvullende controles op theoriepaginering, reeksindexen, publicatie- en validatiestatus uitgevoerd.

### Niet uitgevoerd

- Geen volledige visuele handtest van ieder van de 1.420 theoriepagina's en 25.200 vaste oefeningen; de audit combineert volledige statische controles met gerichte UI-steekproeven.
- Geen installatie of rooktest op een fysiek toestel.
- Geen productiepublicatie, gegevenspush of Windows-afsluiting.

## Beslissing voor de volgende release

De huidige versie kan als **technische reviewbuild** worden gebruikt. Voor een inhoudelijk gevalideerde release zijn minimaal de vier P1-acties nodig:

1. reviewstatus per kernles en oefenreeks daadwerkelijk afronden;
2. drukke woordenschat-/theoriepagina's atomair splitsen;
3. de effectieve Curriculum Room-snapshot als enige audit- en exportbron afdwingen;
4. het generatorvoorbeeld en de validatiestatus in de Curriculum Room betrouwbaar en leesbaar maken.

Daarna moeten dezelfde web- en Android-tests opnieuw worden uitgevoerd, gevolgd door een gerichte kindreview op een toestel. Pas na die controle hoort installatie of productiepublicatie plaats te vinden.

## Auditbronnen

- Kwaliteitskader: `docs/GENERIC_DESCRIPTION_CURRICULUM.md`.
- Machineleesbaar rapport: `curriculum-library/public/data/v2-library-audit.json`.
- Bibliotheekmetadata: `curriculum-library/public/data/library-meta.json`.
- Releasecontracten: `curriculum-library/tests/*.test.mjs`.
- Android-testresultaten: `app/build/test-results/testDebugUnitTest/`.
- Vorige omvangsbaseline: `docs/lesson_v2/reports/BUILD_AND_SIZE_REPORT.md`.
