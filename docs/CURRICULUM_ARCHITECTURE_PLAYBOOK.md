# Poincaré curriculumarchitectuur en uitrolhandleiding

Versie: 2026-08-14  
Status: normatief bouwcontract voor Curriculum Room, exports, Android-runtime en volgende leerjaren  
Eigenaar: curriculumproduct, redactie en engineering gezamenlijk

## 1. Doel en toepassingsgebied

Dit document vertaalt de verzamelde gebruikersfeedback, gerealiseerde correcties en huidige codecontracten naar één uitvoerbare architectuur. Het beschrijft niet alleen hoe het systeem vandaag werkt, maar vooral hoe nieuwe categorieën, leerjaren en lessen voortaan moeten worden gebouwd.

Het contract geldt voor:

- de canonieke curriculumdata en redactionele wijzigingen;
- de Curriculum Room;
- generatie, validatie en publicatie;
- export naar Android;
- Android-navigatie, lesruntime, audio, voortgang en offline gebruik;
- verhalen en puzzels waar hun gespecialiseerde contract expliciet wordt genoemd;
- de uitrol van leerjaar 2 tot en met 6.

Het contract geldt niet als bewijs dat alle huidige inhoud pedagogisch gevalideerd is. Publicatie- en reviewstatus blijven per element traceerbaar.

## 2. Bronhiërarchie en beslissingsregels

### 2.1 Eén curriculumbron

De redactionele bron staat in `curriculum-library` en wordt in de Curriculum Room bewerkt. Android bevat een versieerbare export van één effectieve snapshot.

```text
ingelezen/gecreëerde broninhoud
        ↓
redactionele wijzigingen in Curriculum Room
        ↓
effectieve canonieke snapshot
        ↓
schema- + pedagogische validatie
        ↓
release-ID + checksum + tellingen
        ├── Curriculum Room publicatie
        └── Android export → tests → APK → expliciete installatie
```

Verboden gegevensstromen:

- Android-assets handmatig corrigeren zonder dezelfde wijziging in de canonieke bron;
- een webeditorlaag publiceren die niet in de geaudite snapshot zit;
- bronbestanden, editorwijzigingen en Android-fallbacks als drie gelijkwaardige bronnen behandelen;
- installatie op een toestel gebruiken als vervanging voor bronvalidatie.

### 2.2 Effectieve snapshot

Een release wordt uitsluitend opgebouwd uit één samengevoegde snapshot van basisdata en geldige editorwijzigingen. Die snapshot bevat minimaal:

- schema- en definitieversie;
- release-ID en UTC-tijdstip;
- checksum over de geëxporteerde bron;
- aantallen per elementtype;
- lijst en hash van shards/assets;
- migratiestatus V2 versus legacy;
- auditstatus en blocking bevindingen.

Curriculum Room en Android moeten dezelfde release-ID en checksum kunnen tonen. Een afwijking is een releaseblokker.

### 2.3 Documentgezag

1. Modellen en validators definiëren het technisch afdwingbare contract.
2. Dit document definieert het normatieve product- en bouwcontract.
3. `GENERIC_DESCRIPTION_CURRICULUM.md` definieert brede product- en kwaliteitsprincipes.
4. Goedgekeurde leerjaarsbronnen definiëren zichtbare labels en sequenties.
5. Audits en handoffs bewijzen een toestand op een datum.
6. Legacycode en oude prompts verklaren geschiedenis, maar bepalen geen nieuwe architectuur.

## 3. Domeinmodel en stabiele identiteit

### 3.1 Canonieke hiërarchie

```text
Curriculum
└── Hoofdcategorie
    └── Subcategorie
        └── Thema/unit
            └── Les
                ├── Theorie
                │   └── Theoriepagina 1..n
                │       └── Contentblok 1..n
                ├── Oefenen
                │   └── Oefenreeks 1..n
                │       └── Vraagdefinitie of generatorcontract
                └── Toets
                    └── Toetsvragen of toetsblueprint
```

Verhalen gebruiken Wereld → Reeks → Boek → Hoofdstuk → Pagina → optionele vraag. Puzzels gebruiken Puzzeltype → Renderer → Configuratie → Poging. Deze twee routes worden niet kunstmatig als klassieke lessen gemodelleerd.

### 3.2 Stabiele ID's

Elk navigeerbaar, redactioneel of voortgangsdragend element heeft een stabiele ID. Een titelcorrectie, vertaling, volgordewijziging of rendererverbetering verandert die ID niet. Een wezenlijk nieuw leerobject krijgt een nieuwe ID.

Een migratie bewaart:

- leerlingprofielen;
- geselecteerde leerjaren;
- les- en reeksvoortgang;
- pogingen en resultaten;
- beloningen;
- verhaalvoortgang;
- synchronisatie-identiteiten;
- mappings van legacy-ID naar canonieke ID.

Een ID-rename zonder expliciete migratie is verboden.

### 3.3 Kernmetadata per les

Iedere les bevat minimaal:

- stabiele les-ID en definitieversie;
- canonieke categorie- en subcategorie-ID;
- unit/thema-ID;
- leerjaar 1–6;
- lesstap en eventuele prerequisites;
- titel, samenvatting, beschrijving en pedagogische logica;
- bronlocale `nl-BE`;
- expliciete statussen voor `nl-BE`, `fr-BE` en `en-US`;
- leerdoel-ID's en mastery-tags;
- publicatie- en validatiestatus;
- visual- en audiometadata;
- precies de drie zichtbare lesdelen;
- legacyroute alleen waar runtimecompatibiliteit nog nodig is.

## 4. Canonieke taxonomie

De zes hoofdcategorieën zijn:

| ID | Nederlands label | Route |
|---|---|---|
| `language_skills` | Taalvaardigheid | klassieke lessen plus gespecialiseerde verhalenroute |
| `spelling_grammar` | Spelling & grammatica | klassieke lessen |
| `number_sense` | Rekenen & getalbegrip | klassieke lessen |
| `measurement_geometry` | Meten & meetkunde | klassieke lessen |
| `world_orientation` | Wereldoriëntatie | klassieke lessen |
| `logic_puzzles` | Logica & puzzels | gespecialiseerde puzzelroute |

Regels:

- Labels komen uit het manifest, niet uit `legacyRouting`.
- Web en Android renderen dezelfde categorie- en subcategorievolgorde.
- Een tegel zonder lessen voor het geselecteerde leerjaar is zichtbaar grijs en niet klikbaar wanneer productontwerp aanwezigheid vereist; anders wordt hij niet getoond.
- Een klikbare tegel heeft altijd een werkende bestemming.
- Lege klassieke subcategorieën onder Logica & puzzels bestaan niet.
- “Herhalen” en “Extra oefeningen” zijn geen algemene subcategorieën. Herhaling en extra variatie horen in de juiste oefenreeks of gerichte remediëring.
- Taalroutes met andere doeltalen zijn expliciet onderscheiden, bijvoorbeeld Woordenschat Frans en Woordenschat Engels.

## 5. Volgorde, beschikbaarheid en ontgrendeling

### 5.1 Subcategoriesequentie

De subcategoriesequentie ordent subcategorieën binnen één hoofdcategorie en leerjaar. Een hogere stap opent wanneer alle lessen in alle lagere beschikbare stappen van dezelfde hoofdcategorie voltooid zijn.

### 5.2 Lesstap

De lesstap ordent lessen binnen één subcategorie. Gelijke waarden vormen een parallelle stap. De volgende waarde opent wanneer alle lessen van de vorige waarde voltooid zijn.

### 5.3 Leerjaar 1 als referentiemigratie

Voor leerjaar 1 zijn de goedgekeurde bronkolommen leidend:

- B overschrijft de oude hoofdcategorienaam;
- D overschrijft de oude subcategorienaam;
- E bepaalt de subcategoriesequentie;
- H bepaalt de lesstap;
- K overschrijft de Nederlandse samenvatting.

Dezelfde methode wordt voor een volgend leerjaar toegepast, maar nooit door leerjaar-1-keuzes blind te kopiëren. De inhoudelijke leerlijn van het doel-leerjaar blijft leidend.

### 5.4 Progressiebehoud

- Een leerling die al verder geraakt is, wordt na een taxonomie- of volgordemigratie niet opnieuw vergrendeld.
- Een voltooide les blijft voltooid wanneer alleen tekst, visual of volgorde wijzigt.
- Bij samenvoegen of splitsen bestaat een expliciete voortgangsmapping.
- Toetsbewijs blijft gekoppeld aan mastery-tags, niet alleen aan een schermpositie.

## 6. Drieledige kernlesarchitectuur

### 6.1 Zichtbaar contract

Iedere niet-verhaal-kernles toont exact:

1. Theorie
2. Oefenen
3. Toets

De delen worden niet als blijvende tabs boven de lesinhoud herhaald. Het kind navigeert lineair, kan in theorie teruggaan en krijgt bij afronding een voorspelbare bestemming.

### 6.2 Datacontract

- Eerste fase: exact één `THEORY`.
- Midden: één of meer `EXERCISES`-fasen met doorlopende `seriesIndex` vanaf 1.
- Laatste fase: exact één `MASTERY_TEST`.
- Elke `EXERCISES`-fase bevat precies één oefenreeks.
- `MASTERY_TEST` is de enige bron voor het slagen van de lespoort.
- Legacytypes `ACQUIRE_UNDERSTAND`, `PRACTISE` en `APPLY_DEMONSTRATE` zijn alleen leescompatibiliteit.

### 6.3 Theoriecontract

Theorie is redactioneel gecontroleerd en bevat:

- een kort leerdoel in leerlingtaal;
- uitleg van hoe het werkt;
- een controleerbare regel voor een juist resultaat;
- minstens één volledig uitgewerkt voorbeeld;
- een herkenbaar niet-voorbeeld of typische fout wanneer pedagogisch nuttig;
- één hoofdconcept per pagina;
- passende visual en alt-tekst wanneer het begrip visuele ondersteuning vereist;
- een korte zelfcontrole alleen wanneer die veilig en ondubbelzinnig is.

Voor leerjaar 1 en 2 krijgt ieder nieuw begrip in elke kernles een eigen pagina. Een korte doelpagina mag voorafgaan, maar een woordenlijstblok wordt bij publicatie opgesplitst naar afzonderlijke `CHILD_FRIENDLY_DEFINITION`-pagina's. Definitie, werkwijze, uitgewerkt voorbeeld, niet-voorbeeld en controlepunt delen geen leerlingpagina. Wereldoriëntatiebegrippen krijgen bovendien elk een passende eigen visual.

Voor schrijven, rekenen en meetkunde zijn meerdere uitgewerkte voorbeelden nodig wanneer één voorbeeld de variatie niet dekt. In de theorie wordt iedere traceervorm of -letter één keer geoefend; oefeningen vragen vijf correcte traces; de toets één correcte trace.

### 6.4 Oefencontract

Het ene zichtbare deel Oefenen bevat afzonderlijke reeksen met elk:

- stabiele reeks-ID;
- titel, doel en pedagogische functie;
- `seriesIndex`;
- bronmodus `CURATED` of `GENERATED`;
- aantal items;
- mastery-tags en representatie-ID's;
- hint- en feedbackbeleid;
- `wrong answers allowed` met waarde 1 of 2;
- validatie- en publicatiestatus.

Minimaal zijn begeleid en zelfstandig oefenen afgedekt. Waar relevant volgen: afbouwende steun, nabije transfer, verdere transfer en foutgerichte herhaling.

Standaard foutentolerantie:

- eerste helft van de reeksen: 2;
- tweede helft: 1;
- bij oneven aantal reeksen heeft de strengere helft één reeks meer;
- toets: geen oefenhints en feedback pas na de toets.

### 6.5 Toetscontract

De toets:

- is de laatste fase;
- gebruikt nieuwe equivalente items, niet de recente oefenvragen;
- dekt alle vereiste mastery-tags;
- bevat geen hints;
- geeft feedback na afloop;
- heeft een configureerbare, expliciete slaaggrens;
- brengt het kind vanuit het resultaat terug naar de les of het juiste overzicht;
- levert idempotent bewijs en nooit dubbele beloning.

## 7. Vraag-, antwoord- en kindtaalcontract

### 7.1 Leeftijdsperspectief

Redactie beoordeelt leerjaar 1 als een kind van zes en leerjaar 2 als een kind van zeven. Voor volgende leerjaren wordt de persona jaarlijks verhoogd, maar zinscomplexiteit groeit geleidelijk en nooit automatisch met vakjargon.

### 7.2 Heldere vraag

Een vraag:

- bevat één opdracht;
- gebruikt korte, concrete woorden;
- bevat alle noodzakelijke context;
- vraagt om een waarneembare handeling of keuze;
- herhaalt bij TTS alleen de vraag, niet de algemene lesopdracht;
- gebruikt geen editor-, rubric- of voorbeeldkaarttaal;
- gebruikt geen abstracte procesvraag wanneer een concreet voorbeeld mogelijk is.

Voorbeeld: “Hoe zeg je hallo tegen een vriend?” is geschikter dan “Welke informele Franse begroeting gebruik je voor iemand die je goed kent?”

### 7.3 Eenduidig antwoord

- Single-choice heeft exact één inhoudelijk juist antwoord.
- Opties zijn uniek, grammaticaal vergelijkbaar en aantoonbaar verschillend.
- Geen optie start met een nummer of interne code.
- Feedback, voorbeeldkaarttekst en leerkrachtinstructies mogen nooit antwoordopties worden.
- Als meerdere antwoorden geldig zijn, wordt de vraag vernauwd of het interactietype gewijzigd.
- Een afleider is plausibel maar niet misleidend door ontbrekende context.
- De betekenis van een vakwoord komt uit de lescontext, niet uit een algemeen woordenboek. Zo betekent *plakken* bij technisch lezen klanken samenvoegen en nooit papier met lijm vastmaken.
- Een term, definitie of antwoord wordt alleen hergebruikt wanneer dezelfde vakbetekenis en representatie gelden.

### 7.4 Eerste-klik-voorlezen

Voor oefeningen van leerjaar 1 met optieknoppen:

1. eerste tik markeert de optie half ingedrukt en leest haar voor;
2. tweede tik op dezelfde optie bevestigt;
3. tik op een andere optie verplaatst de voorlopige selectie en leest die voor;
4. toegankelijkheidsfocus en visuele status blijven synchroon;
5. toets- en voortgangslogica registreren pas na bevestiging.

## 8. Invoer-, layout- en navigatiecontract

### 8.1 Oefenscherm

- Oefeninhoud staat verticaal gecentreerd in de ruimte boven de antwoorden of het toetsenbord.
- Een aangepast toetsenbord staat onderaan met de vaste bottom margin.
- Multiplechoice-opties staan onderaan met dezelfde layoutlogica.
- Inhoud mag niet overlopen; tekst schaalt niet onbeperkt kleiner maar wordt gepagineerd of scrollbaar.
- De vraag kan worden voorgelezen waar dat nodig is.
- Feedback staat in de oefenkaart en verschuift geen essentiële bediening buiten beeld.

### 8.2 Dictee

Ondersteunde progressie:

1. één ontbrekende letter;
2. meerdere ontbrekende letters;
3. volledig woord;
4. korte zin;
5. langere zin op passend niveau.

Iedere dicteevraag bevat uitspreekbare inhoud, locale en inputtype. Het aangepaste toetsenbord verschijnt bij schrijfinput. De leerling hoort het doelwoord of de doelzin, niet het antwoord in visuele vorm. Ontbrekende letters kunnen via selectie/drag-and-drop worden ingevuld wanneer de renderer dit expliciet ondersteunt.

### 8.3 Navigatie

- Theorie heeft vanaf pagina 2 een knop Vorige.
- Verder gaat naar de volgende pagina, de volgende oefenreeks of de toets.
- “Theorie afgerond” biedt een PlanetGold-knop terug naar de lessen.
- Een resultaatsscherm heeft onderaan een knop terug naar de les.
- “Afronden” in een hoofdstuk gaat naar het hoofdstukoverzicht.
- “Afronden” in een boekhoofdstuk mag niet naar het algemene boekenmenu springen.
- Een kernles kan worden verlaten zonder voortgangscorruptie en hervat op de laatste geldige positie.

## 9. Gegenereerde en vaste inhoud

### 9.1 Bronmodi

- `CURATED`: vaste redactionele items.
- `GENERATED`: runtime-items volgens een deterministisch generatorcontract.

`FIXED`, `MANUAL` en `GENERATED` mogen redactionele herkomst verduidelijken, maar V1/V2 is geen gebruikersfilter.

### 9.2 Generatorcontract

Een gegenereerde set bevat:

- generator-ID, semantische versie en implementatieversie;
- seedbeleid;
- selectiecriteria en moeilijkheidsgrenzen;
- woordenschat en toegelaten waarden;
- mastery-tags en representaties;
- antwoord- en afleiderregels;
- uitsluiting van recente exacte items voor een toets;
- één deterministisch, werkelijk genereerbaar voorbeeld met vraag, opties, juist antwoord en feedback.

Voor tekstuele begrips- en leesvragen is “gegenereerd” beperkt tot deterministische selectie uit een lesgebonden, redactioneel nagekeken bank. De runtime mag geen definities, juiste antwoorden of afleiders uit een algemene woordenlijst samenstellen. Elke record verwijst naar precies één theoriebegrip of geoefende vaardigheid. Een vaste nagekeken bank bevat minstens `itemCount` unieke, volledige records; een procedurele generator moet bovendien de door zijn eigen contract vereiste variatie en ongeziene equivalente toetsitems kunnen leveren. Een theorievraagset wordt niet gepubliceerd wanneer deze semantische binding, capaciteit en uniciteit niet aantoonbaar zijn.

Generatorfasen gebruiken een klein canoniek vocabularium. `guided` is bestemd voor begeleid oefenen en theorie-opvolgvragen, `independent` voor zelfstandig oefenen en `test` voor de eindtoets. Labels zoals `theory`, `practice` en `theory_check` zijn UI- of redactielabels en mogen niet rechtstreeks als runtime-`phase` worden geëxporteerd. Een legacygenerator die `checkpoint` verwacht, krijgt die waarde uitsluitend via een expliciete, geteste exportadapter.

Deduplicatie gebeurt vóór de releasepoort. Zij mag het aantal werkelijk genereerbare items nooit stil onder het vereiste minimum brengen. Na deduplicatie gelden tegelijk:

- `itemCount` is niet groter dan het aantal unieke, geldige records;
- een procedurele eindtoets kan voldoende nog niet gebruikte equivalente items leveren; een vaste nagekeken bank mag bewust eerder onderwezen vragen opnieuw toetsen;
- `gatePolicy.minimumIndependentItems` is niet groter dan het werkelijk oplosbare onafhankelijke toetsaanbod;
- `questionBlueprints`, redactionele items en generatorparameters beschrijven hetzelfde aantal;
- een tekort blokkeert de release en wordt niet opgelost door prompts of antwoordpermutaties als schijnbaar nieuwe vragen te dupliceren.

De Curriculum Room toont deze gegevens als leesbare velden/kaarten. Een redactionele kernzin is geen geldig leerlingvoorbeeld. De runtimegenerator blijft gezaghebbend; een editorvoorbeeld is inspectiebewijs.

### 9.3 Pattern fix

Bij een fout voorbeeld wordt niet alleen dat item gecorrigeerd. Controleer:

- alle items uit dezelfde pool;
- dezelfde generatorregel in andere lessen/leerjaren;
- vertaalde varianten;
- redactionele voorbeelden;
- toetsblueprints;
- rendererfallbacks.

Voeg een regressietest toe die het patroon blokkeert.

## 10. Visuals, iconen en audio

### 10.1 Visualcontract

Iedere visual heeft:

- stabiele visual-ID;
- doel en relatie tot precies één concept of stap;
- locale/onafhankelijkheid;
- alt-tekst;
- bron- en licentiegegevens;
- palette- en renderercompatibiliteit;
- bestandscontrole en hash in de release.

De SVG-generator gebruikt standaard alleen de generatiestijl en “Beschrijving nieuwe afbeelding”. Alleen wanneer “Lescontext mee verwerken” is aangevinkt, wordt `learningIntent` toegevoegd.

Het thema Spacetiles genereert categorie- en subcategorie-iconen als SVG met transparante achtergrond, uitsluitend witte, dikke lijnen en een inhoudelijk herkenbare voorstelling. Het is een icoonthema, geen lesillustratiestijl.

De canonieke Spacetiles-pijplijn is `curriculum-library/scripts/generate-spacetiles-icons.mjs`. De generator:

- vereist een expliciete semantische scène voor iedere categorie en subcategorie in het curriculummanifest;
- schrijft de SVG's naar `curriculum-library/public/icons/spacetiles/`;
- registreert per manifestitem `iconUrl`, `iconAssetPath` en `imageGenerationTheme: SPACETILES`;
- genereert daarnaast de iconen van de zelfstandige puzzelervaringen, zonder ze opnieuw als klassieke lessubcategorieën te modelleren;
- verwijdert verouderde gegenereerde SVG's en faalt wanneer het scènesregister en het manifest uiteenlopen.

De Android-export kopieert exact deze bestanden naar `app/src/main/assets/poincare/curriculum/icons/spacetiles/` en neemt ieder icoon op in de releasehash. Web en Android mogen dus geen afzonderlijke handmatig onderhouden categorie-iconensets gebruiken. De Android-weergave leest het `iconAssetPath` uit dezelfde manifestmetadata; een legacy drawable is uitsluitend een technische fallback voor een ontbrekend of onleesbaar asset.

### 10.2 Spraakcontract

- Zichtbare tekst en gesproken tekst zijn afzonderlijke velden.
- Uitspraakcorrecties gebeuren in de spraaknormalizer, nooit door zichtbare spelling te vervormen.
- Dicteeaudio is offline beschikbaar of heeft een expliciete veilige fallback.
- Locale en uitspraakregels zijn testbaar.
- Een letter die zichtbaar als `/m/` werd aangeleverd, verschijnt zonder slashes, vet en PlanetGold; gesproken tekst noemt alleen de letter/klank passend bij de context.

## 11. Verhalencontract

Verhaalwerelden:

- Kleine verhaaltjes — warme aquarel;
- Nova — Space travellers;
- Robbie — videogame.

Per boek zijn reeks-ID, boeknummer, titel, status en AVI-niveau verplicht. Boeken zijn per wereld gegroepeerd en numeriek gesorteerd; tijdelijke waarden zoals 999 zijn ongeldig.

Hoofdstukken hebben eigen titel, volgorde, pagina's en waar beschikbaar een passende unieke afbeelding. Robbie-hoofdstukken worden niet samengevoegd en gebruiken niet overal dezelfde afbeelding.

Weergave:

- hoofdstukthumbnail is 1:1;
- bovenkant afbeelding en hoofdstuktitel zijn uitgelijnd;
- lange tekst wordt over twee pagina's verdeeld wanneer de volgende pagina geen afbeelding heeft;
- wanneer iedere pagina een afbeelding heeft, wordt de afbeelding proportioneel kleiner zodat tekst niet uit het frame loopt;
- Afronden gaat terug naar het hoofdstukoverzicht.

Verhalen worden alleen gewijzigd wanneer de opdracht ze omvat; curriculumexports met `--skip-stories` moeten verhaalhashes behouden.

## 12. Puzzelcontract

Puzzels volgen geen Theorie–Oefenen–Toets-contract. Alleen puzzeltypes met een werkende renderer, configuratie, voortgang en test zijn klikbaar. Verouderde of lege puzzeltypes en klassieke lessubcategorieën worden verwijderd. Puzzels tonen XP, geen lespercentage.

Een nieuwe puzzel wordt pas actief na:

- renderer-capability;
- geldige configuratie;
- toegankelijk inputpad;
- fout- en hervatgedrag;
- voortgangs- en beloningscontract;
- unit- en device-smoketest.

## 13. Vertalingen en fallback

`nl-BE` is bron- en standaardlocale. `fr-BE` en `en-US` hebben per veld expliciet `COMPLETE`, `NEEDS_REVIEW` of `MISSING` met eventuele fallback.

- Een fallback is traceerbaar en geen bewijs van voltooide vertaling.
- Taalinhoud wordt pedagogisch aangepast, niet blind letterlijk vertaald.
- Titel, instructie, vraag, opties, feedback, hints, alt-tekst en gesproken tekst blijven per locale coherent.
- Een vertaling mag geen extra correct antwoord introduceren.

## 14. Redactionele status en publicatie

Technisch gebruikt het V2-model minimaal `ACTIVE` en `DRAFT_REVIEW_REQUIRED`; de Curriculum Room kan fijnmaziger workflowlabels tonen zolang één bronstatusmapping bestaat.

- Draft is zichtbaar en cursief.
- Beschikbaarheid, validatie en publicatie zijn verschillende dimensies.
- Een draft mag in een reviewbuild, maar niet als pedagogisch gevalideerd worden voorgesteld.
- Een les is pas actief wanneer alle actieve oefensets gevalideerd zijn volgens het releasebeleid.
- Alle statuslabels in de UI komen uit één semantische bron.

## 15. Web- en Android-pariteit

Pariteit betekent:

- dezelfde categorie- en subcategorie-ID's, labels en volgorde;
- dezelfde les-ID's, lesstappen en prerequisites;
- hetzelfde aantal theoriepagina's, oefenreeksen en toetsdefinities;
- dezelfde publicatie- en validatiestatus;
- dezelfde renderervereisten;
- dezelfde release-ID en checksum;
- geen onbekende velden die bij export stil verdwijnen.

De Curriculum Room blijft op mobiel bruikbaar: filters verdwijnen niet, ID's zijn zichtbaar, bronmodus is inspecteerbaar en reeksen blijven afzonderlijk bewerkbaar.

## 16. Legacy- en migratiebeleid

De huidige snapshot bevat V2-kernlessen en een legacycompatibiliteitslaag. Dit is tijdelijk.

Regels:

- nieuwe inhoud is uitsluitend V2;
- legacyroute bepaalt nooit zichtbare taxonomie;
- migratie gebeurt per samenhangende batch;
- iedere batch heeft ID-mapping, voortgangsmapping en regressietests;
- de adapter wordt pas verwijderd wanneer `remainingLegacyLessons == 0` en de V2-runtimesuite slaagt;
- een onbekende legacyvorm blokkeert migratie in plaats van data stil te verliezen.

## 17. Uitrolhandleiding voor een volgend leerjaar

### Fase A — bron en leerlijn

1. Verzamel de goedgekeurde leerdoelen en bronverwijzingen.
2. Maak één rij per doel, subcategorie, les en bewijssoort.
3. Definieer categorie/subcategorie, subcategoriesequentie en lesstap.
4. Markeer parallelle lessen expliciet.
5. Detecteer duplicaten op stabiele ID, leerjaar en inhoud, niet alleen titel.
6. Laat namen en samenvattingen inhoudelijk goedkeuren.

### Fase B — verticale proef

Kies minstens één representatieve les per hoofdcategorie en bouw die volledig:

- theoriepagina's;
- meerdere oefenreeksen;
- toets;
- visuals/audio;
- drie locale-statussen;
- progressie en navigatie;
- webeditor en Android-renderer.

Valideer deze verticale proef voordat massageneratie start.

### Fase C — gecontroleerde batch

1. Genereer of redigeer een kleine samenhangende batch.
2. Voer schema- en kindtaalvalidatie uit.
3. Review ieder nieuw concept, iedere antwoordpool en ieder generatorcontract.
4. Controleer eenvoudige → moeilijke sequentie.
5. Herstel patronen catalogusbreed.
6. Markeer niet-gevalideerde inhoud als draft.

### Fase D — pariteit en release

1. Bouw de effectieve snapshot.
2. Draai structurele, pedagogische en assetaudits.
3. Publiceer de Curriculum Room-snapshot wanneer toegestaan.
4. Exporteer exact dezelfde snapshot naar Android.
5. Draai Android-catalogus- en runtimetests.
6. Bouw APK en noteer hash/grootte.
7. Installeer alleen na expliciete toestemming.
8. Voer device-smoketest uit en leg bewijs vast.

### Fase E — acceptatie

Een leerjaar is niet klaar omdat alle titels bestaan. Het is klaar wanneer alle verplichte doelen expliciete instructie, passende oefening, voldoende variatie, toetsbewijs, retentieplanning en waar nodig menselijke observatie hebben.

## 18. Verplichte kwaliteitscontroles

### 18.1 Automatisch blokkerend

- schema en verplichte velden;
- unieke/stabiele ID's;
- exacte fasevolgorde;
- doorlopende reeksindex;
- één finaletest;
- antwoorduniciteit;
- verboden placeholder-/editor-/nummertekst;
- generatorversie, seed en werkelijk voorbeeld;
- generatorfase is ondersteund door de Android-generator of expliciet geadapteerd;
- iedere gegenereerde bank voldoet na deduplicatie aan minimumgrootte en circulaire uniciteit;
- `itemCount`, blueprints, uniek toetsaanbod en `minimumIndependentItems` zijn onderling consistent;
- renderer-capability;
- locale-status en fallback;
- visual/audio-bestaan;
- sequentie- en prerequisitecycli;
- web/Android releasehash;
- onverwachte catalogus-, asset- of APK-groei.

### 18.2 Pedagogische review

Per pagina/vraag:

1. Begrijpt de leerling ieder woord?
2. Is onmiddellijk duidelijk wat te doen?
3. Is één hoofdconcept actief?
4. Is alle context aanwezig?
5. Is het antwoord eenduidig?
6. Is dezelfde representatie eerst uitgelegd?
7. Is de moeilijkheidstap klein genoeg?
8. Is feedback corrigerend zonder antwoord weg te geven?

### 18.3 Handmatige smoke

Minimaal één les per renderer en bronmodus, plus trace, dictee, multiplechoice, open input, Wereldoriëntatieconcept, doeltaalwoordenschat, verhaalwereld en actieve puzzel. Test portret/landschap, telefoon/tablet, offline herstart, vorige/volgende, afronden en hervatten.

## 19. Definition of Done

Een curriculumwijziging is klaar wanneer:

- bronwijziging en eventuele backup aantoonbaar zijn;
- vergelijkbare inhoud is gecontroleerd;
- ID's en voortgang behouden zijn;
- webeditor, snapshot, export en runtime hetzelfde contract lezen;
- alle blocking audits en tests slagen;
- niet-uitgevoerde handtests expliciet als risico staan;
- release-ID/checksum/tellingen zijn vastgelegd;
- publicatie en installatie binnen de expliciet toegestane scope zijn uitgevoerd;
- de handoff uitkomst, artifacts, verificatie, risico's en exacte volgende stap bevat.

## 20. Actuele architectuurstatus

Snapshot 2026-08-14:

- 6 categorieën en 56 subcategorieën;
- 348 lesrecords in de webbibliotheek;
- 223 V2-kernlessen;
- 125 verhaallessen, 19 boeken, 130 hoofdstukken en 2.037 verhaalpagina's;
- 2.334 oefensets en 21.000 vaste oefeningen;
- 657 resterende legacylessen;
- lokaal gesynchroniseerde Android-snapshot `2026-08-14-a0830c3b166f` met 28 les-shards;
- releasechecksum `a0830c3b166f415534b8fa145def9aaaee9a76f0d6ca9c37cb71937fd9cc55b0`;
- webstructuurtest 29/29, leerjaar-1-leerlingaudit 92/92, child-contentaudit PASS, V2-audit PASS en web/Android-structuuraudit PASS;
- Android-unitstatus 341/341. De eerdere fase-, vraagpool-, poort- en verouderde assertionconflicten zijn opgelost. De snapshot is lokaal technisch valide, maar is in deze taak niet gepubliceerd of geïnstalleerd.

Deze cijfers zijn bewijs van één lokale snapshot en worden bij iedere release vervangen. Een gelijke snapshot en checksum bewijzen dat de data gelijk zijn, maar niet dat de runtime ze correct kan uitvoeren. Pariteit én een volledig groene Android-runtimesuite zijn afzonderlijke releasepoorten.

## 21. Open architectuurbeslissingen en risico's

- Leg één technische mapping vast tussen workflowlabels in de Curriculum Room en de V2-publicatiestatus.
- Maak de effectieve snapshot de enige invoer voor audit én export; editorwijzigingen mogen niet buiten de checksum vallen.
- Bewaak het onderscheid tussen vaste nagekeken vraagbanken en procedurele generators: alleen procedurele generators beloven ongeziene equivalente toetsitems; beide moeten voldoende unieke records voor hun eigen `itemCount` leveren.
- Houd Android-tests gekoppeld aan de canonieke Excelstructuur, de introductie-eerst-regel en inhoudelijke capaciteitsgrenzen, niet aan historische vaste aantallen reeksen of vragen.
- Plan de afbouw van 657 legacylessen per leerjaar/batch.
- Scheid “inhoud bestaat”, “technisch valide”, “pedagogisch gereviewd” en “gepubliceerd” in rapportage.
- Houd APK- en assetgrootte als releasepoort in het bijzonder door offline TTS en verhaalbeelden.
- Bouw leerjaar 2 met dezelfde migratiediscipline als leerjaar 1 en gebruik daarna pas de methode voor leerjaar 3–6.

## 22. Exacte volgende stap voor leerjaar 2

1. Maak een goedgekeurde leerjaar-2-bron met dezelfde overschrijf- en sequentiekolommen als leerjaar 1.
2. Draai de structuurmigratie eerst als dry-run en controleer alle records, parallelle stappen en vermoedelijke duplicaten.
3. Bouw per hoofdcategorie één volledige verticale lesproef.
4. Voer een zevenjarigenreview uit op theorie, oefeningen en toets.
5. Migreer vervolgens per subcategorie, met pariteits- en voortgangstests na iedere batch.
6. Publiceer pas wanneer de effectieve snapshot, Curriculum Room en Android-export dezelfde releasehash tonen.

## 23. Traceerbaarheid van feedback naar architectuurregel

| Terugkerende feedback of vastgestelde fout | Permanente architectuurregel | Verplicht bewijs |
|---|---|---|
| App en web tonen andere categorieën | Manifestlabels en -ID's zijn canoniek; legacyrouting is onzichtbaar | pariteitsaudit + gelijke releasehash |
| Theorie/Oefen/Toets ontbrak of werd versnipperd | exact drie zichtbare delen; meerdere reeksen onder Oefenen | fasevalidator + runtimetest |
| Eén zeer lange oefenbank | iedere reeks heeft eigen ID, doel, index en voortgang | doorlopende `seriesIndex` + UI-steekproef |
| Eerste theoriepagina was te complex | één hoofdconcept per pagina; overzicht bevat geen samengeperste definities | kindreview + paginacontrole |
| Woordenboekbetekenis verving de vakbetekenis, zoals lijm bij lees-“plakken” | betekenis is les- en domeingebonden; tekstvragen komen uit een nagekeken vaardigheidsbank | semantische poolaudit + regressietest |
| Dezelfde theorievraag kwam tijdens één reeks terug | elke runtime-window bevat unieke prompts | circulaire-windowtest per vraagset |
| Vragen/antwoorden waren te abstract of dubbelzinnig | leeftijdspersona, één concrete opdracht en één correct single-choice-antwoord | volledige pool-/generatortest |
| Voorbeeldkaart- of nummertekst belandde in antwoorden | editor- en placeholdertekst zijn verboden inhoud | contentlint over alle locales |
| Dictee had geen audio of toetsenbord | audio-, locale- en inputtype zijn verplichte rendererdata | dicteeregressietest + device smoke |
| Toetsenbord en opties zweefden in de layout | invoer onderaan; inhoud gecentreerd in resterend vlak | telefoon/tablet-UI-test |
| Verhalen waren niet gegroepeerd of eindigden verkeerd | eigen verhaalhiërarchie en expliciete afrondbestemming | verhaalvalidator + navigatietest |
| Robbie-hoofdstukken/beelden waren samengevoegd | hoofdstuk is zelfstandig object met eigen volgorde en passende visual | boek-/hoofdstukaudit |
| Lege of onwerkende puzzeltegels | alleen renderer-backed puzzels; geen klassieke lege subcategorieën | capability-audit |
| Generator was ondoorzichtig | criteria, versie, seed en echt leerlingvoorbeeld zijn zichtbaar | Curriculum Room-test |
| Een lokale voorbeeldfix liet gelijkaardige fouten staan | herstel de volledige pool, regel, locale en toetsvariant | regressietest op het patroon |
| Structuur liep na export uiteen | één effectieve snapshot voedt audit, web en Android | release-ID/checksum op beide doelen |
| Volgorde was pedagogisch onlogisch | E ordent subcategorieën; H ordent/paralleliseert lessen | bronmapping- en ontgrendelingstest |
| Legacybenamingen keerden terug | zichtbare taxonomie komt nooit uit legacyvelden | manifestvalidator |
| Installaties gaven onverwacht andere inhoud | bouwen, publiceren en installeren zijn afzonderlijke toegestane stappen | handoff met exacte artifacthash |

## 24. Bronnen van deze consolidatie

Deze architectuur is geconsolideerd uit:

- de gebruikersfeedback en correctierondes in de Poincaré-taken en eerdere projectgesprekken;
- `docs/GENERIC_DESCRIPTION_CURRICULUM.md`;
- `docs/CURRICULUM_STRUCTURE_GRADE1_MIGRATION.md`;
- de gedateerde curriculumaudits en taakhandovers;
- `LessonV2Models.kt` en `LessonCatalogV2Validator.kt` als actueel technisch contract;
- `curriculum-manifest-v1.json`, `lesson-catalog-v2.json`, `library-meta.json` en Android `release-metadata-v1.json` als actuele snapshotbewijzen.

Wanneer een toekomstig gesprek een nieuwe algemene regel oplevert, wordt niet alleen de lokale fix vastgelegd: deze traceerbaarheidstabel, de validator en de uitrolchecklist worden samen bijgewerkt.
