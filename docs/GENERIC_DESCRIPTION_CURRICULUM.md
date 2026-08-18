# Generic Description Curriculum

Versie: 2026-08-18  
Status: levend masterdocument voor ontwerp, redactie, ontwikkeling, audit en release  
Normatief detailcontract: `docs/CURRICULUM_ARCHITECTURE_PLAYBOOK.md`

## 0. Doel en rode draad

Dit document beschrijft hoe de Poincaré-curriculumomgeving en de mobiele app samen een consistente, kindvriendelijke en controleerbare leerervaring moeten vormen. Het is tegelijk:

- de functionele en technische referentie voor de Curriculum Room en Android-app;
- de pedagogische standaard voor alle lessen, oefeningen, toetsen, verhalen en puzzels;
- de checklist voor inhoudelijke review en kindtests;
- het releasecontract dat bepaalt wanneer curriculumdata naar productie en de app mag;
- het uitgangspunt voor toekomstige audits en verbeteringen.

### Documenthiërarchie en interpretatie

Bij tegenstrijdigheid geldt deze volgorde:

1. het actuele, gevalideerde dataschema en de bijbehorende validator;
2. `docs/CURRICULUM_ARCHITECTURE_PLAYBOOK.md` voor het normatieve bouw- en uitrolcontract;
3. `docs/lesson_v2/SECTIONED_LESSON_ARCHITECTURE.md` voor het drieledige lescontract;
4. `docs/MOBILE_APP_ARCHITECTURE.md` en `docs/GALAXY_NAVIGATION_ARCHITECTURE.md` voor de Android-app en Galaxy-integratie;
5. `context/GalaxyMap/poincare_galaxy_navigation_prompt.md` uitsluitend voor de exacte formulegeometrie binnen een klassieke sphere group;
6. dit masterdocument voor productprincipes en kwaliteitsregels;
7. leerjaarspecifieke migratiedocumenten en goedgekeurde bronbestanden voor namen en sequenties;
8. audits en handoffs als gedateerd bewijs, niet als permanente productspecificatie;
9. oudere prompts en implementatienotities als historische context.

Een gedateerd aantal, release-ID of auditresultaat is een **statusmeting**. Een regel met woorden als “moet”, “exact” of “nooit” is een **normatieve eis**. Een legacyveld is alleen een **compatibiliteitsmechanisme** en mag geen zichtbare curriculumstructuur bepalen.

De rode draad bestaat uit twaalf principes:

1. **Het kind begrijpt de inhoud zonder uitleg van een ontwikkelaar of redacteur.** Taal, antwoordopties, interacties en visuele steun passen bij de leeftijd.
2. **Het webcurriculum is de unieke bron van lesdata.** Android ontvangt alleen gecontroleerde exports en bevat geen concurrerende handmatig onderhouden curriculumbron.
3. **De hiërarchie is overal dezelfde.** Hoofdcategorie → subcategorie/tegel → thema → les → Theorie → één of meer Oefenreeksen → Toets.
4. **Elke stap heeft een pedagogische reden.** Lessen en reeksen gaan van eenvoudig, concreet en sterk ondersteund naar moeilijker, abstracter en zelfstandiger.
5. **Eén theoriepagina behandelt één hoofdconcept.** Nieuwe begrippen worden niet samengeperst op één pagina.
6. **Oefenvragen zijn kort, eenduidig en eerlijk.** Er is exact één juist antwoord wanneer het interactietype dat verwacht.
7. **Gegenereerde inhoud is transparant en reproduceerbaar.** Criteria, bronmodus, generatorversie en een voorbeeld zijn zichtbaar in de Curriculum Room.
8. **Data en renderer vormen samen één contract.** Een oefentype mag pas worden gepubliceerd wanneer webeditor, export, Android-renderer, audio en voortgang het ondersteunen.
9. **Navigatie laat een kind nooit vastlopen.** Teruggaan, afronden, resultaten verlaten en doorgaan hebben voorspelbare bestemmingen.
10. **Alle inhoud is bereikbaar en herkenbaar.** Geen verdwenen lessen, onklikbare tegels, verborgen oefenbanken of onverklaarde dubbele groepen.
11. **Een voorbeeldfout leidt tot een algemene correctie.** Bij een fout in één vraag wordt de volledige vraagpool, generatorregel of vergelijkbare inhoud gecontroleerd.
12. **Geen release zonder bewijs.** Audit, regressietests, build, steekproef en handoff zijn vaste onderdelen van elke release.

---

# Document 1 — Architectuur van mobiele app en webapp

## 1.1 Systeemoverzicht

### Curriculum Room / webcurriculum

De Curriculum Room is de redactionele bron voor:

- categorieën, subcategorieën, thema’s, lessen en hun volgorde;
- Theorie, Oefenreeksen en Toets;
- vaste en gegenereerde vragen;
- antwoordopties, feedback, hints en toegelaten fouten;
- leerdoelen, begrippen, kindvriendelijke definities en ouderuitleg;
- publicatie- en validatiestatus;
- beschikbaarheid, vereisten en ontgrendelvoorwaarden;
- verhalen, reeksen, boeken, AVI-niveaus, hoofdstukken en pagina’s;
- vertalingen en expliciete fallbackstatussen;
- visuele assets en generatie-instructies;
- stabiele unieke ID’s en versiehistoriek.

De webapp draait als een private Sites-app. Redactionele wijzigingen en hun revisies worden in D1 bewaard. Beeldbestanden die via de editor worden opgeslagen, worden in R2 bewaard. De gepubliceerde catalogus wordt als een versieerbare, controleerbare release opgebouwd.

### Android-app

De mobiele app is een native Android-app in Kotlin en Jetpack Compose. Ze gebruikt een geëxporteerde, sharded curriculumcatalogus en verpakte assets, zodat de leerinhoud offline beschikbaar blijft.

Android is een uitvoerdoel, geen tweede redactionele bron. Een correctie aan lesinhoud gebeurt eerst in het webcurriculum en wordt daarna opnieuw geëxporteerd.

### Leerlingvoortgang

Leerlingprofielen, lesvoortgang, pogingen, resultaten, story progress en instellingen worden lokaal opgeslagen met Room en DataStore. Pogingen hebben stabiele poging- en synchronisatie-ID’s, zodat een toekomstige idempotente synchronisatie mogelijk blijft.

Online synchronisatie van leerlingvoortgang is momenteel bewust uitgeschakeld. De app mag geen gesimuleerde upload als geslaagde synchronisatie voorstellen. Remote sync wordt pas geactiveerd wanneer er een geauthenticeerd, idempotent en getest servercontract bestaat.

### Android-navigatie en build

De Galaxy is de standaardnavigator na profielselectie of -creatie; de tegelinterface blijft via de gedeelde hoofdheader beschikbaar. Beide navigators lezen hetzelfde manifest, dezelfde leerjaarfilter, dezelfde voortgang en dezelfde category-header. `docs/MOBILE_APP_ARCHITECTURE.md` beschrijft de route- en profielcontracten; `docs/GALAXY_NAVIGATION_ARCHITECTURE.md` beschrijft de dynamische kaart.

De build gebruikt AGP 9 met ingebouwde Kotlin. `kotlin-android` en de oude AGP-opt-outs worden niet opnieuw toegevoegd. Compose Compiler en Kotlin Serialization volgen Kotlin 2.2.10; KSP 2.3.11 verzorgt Room-codegeneratie.

## 1.2 Canonieke gegevensstroom

De verplichte stroom is:

1. Maak vóór een grote import of migratie een gedateerde, onveranderlijke backup van de curriculumdata buiten de app/repositoryfolder.
2. Voeg nieuwe of verbeterde inhoud toe aan de Curriculum Room.
3. Ken stabiele ID’s, bronmodus, validatiestatus, publicatiestatus en versiegegevens toe.
4. Voer redactionele en automatische validatie uit.
5. Bewaar goedgekeurde wijzigingen als de nieuwe canonieke curriculumstaat.
6. Bouw de publicatiecatalogus en voer de volledige curriculumaudit uit.
7. Publiceer de private Curriculum Room-versie.
8. Exporteer exact die goedgekeurde versie naar Android.
9. Bouw en test de Android-app.
10. Installeer alleen op een toestel na expliciete toestemming.
11. Leg resultaat, versie, controles en resterende risico’s vast in de handoff.

De stroom loopt nooit omgekeerd. Oefeningen die in Android werden aangepast, moeten eerst terug naar het webcurriculum voordat ze als nieuwe bron mogen gelden.

## 1.3 Data-eigenaarschap en versiebeheer

Elk element heeft een stabiele unieke ID: categorie, subcategorie, thema/unit, les, fase, theoriepagina, contentblok, oefenreeks, vraag, antwoord, visual, verhaalwereld, boek, hoofdstuk en verhaalpagina.

Regels:

- bestaande ID’s veranderen niet door titel-, volgorde- of tekstcorrecties;
- een nieuw inhoudelijk element krijgt een nieuwe ID;
- verwijderde of vervangen inhoud blijft traceerbaar in migratie- of versiehistoriek;
- import en export mogen geen onbekende velden stilzwijgend verliezen;
- schemawijzigingen worden tegelijk in webcatalogus, export en Android-deserialisatie ondersteund;
- een release bevat een versienummer, datum, checksum en brontelling.

De Curriculum Room toont de ID naast het paginanummer of elementnummer in wit, kleiner en niet vet, zodat feedback exact aan een element kan worden gekoppeld.

## 1.4 Publicatie- en reviewstatus

Minimaal ondersteunde statussen:

- `DRAFT`: inhoud is zichtbaar voor redactie/test, maar nog niet inhoudelijk goedgekeurd;
- `NEEDS_REVIEW`: technisch volledig, inhoudelijke of pedagogische controle nodig;
- `VALIDATED`: inhoudelijk goedgekeurd;
- `PUBLISHED`: opgenomen in de bedoelde release.

Draftreeksen of -lessen krijgen zichtbaar en cursief `Draft` in de titel. Draft mag nooit onzichtbaar als gevalideerde inhoud worden voorgesteld. Beschikbaarheid voor een leerling is een afzonderlijke instelling en mag niet met publicatiestatus worden verward.

## 1.5 Vaste en gegenereerde oefeningen

Er zijn twee canonieke bronmodi:

- `CURATED`: vaste, redactioneel ingevoerde vragen en antwoorden;
- `GENERATED`: vragen of antwoorden worden volgens een expliciet generatorcontract opgebouwd.

De editor kan technisch onderscheid maken tussen `FIXED`, `MANUAL` en `GENERATED`, maar de leerling ziet geen technische versielabels zoals V1/V2.

Voor gegenereerde inhoud toont de Curriculum Room altijd:

- welke velden vast zijn en welke worden gegenereerd;
- de selectiecriteria en moeilijkheidsgrenzen;
- toegelaten waarden, woordenschat, representaties en antwoordregels;
- generator-ID en generatorversie;
- seed-/reproduceerbaarheidsbeleid;
- één concreet voorbeeld van vraag, antwoord en afleiders;
- de validatie- en publicatiestatus.

De optie “Vraag en antwoorden automatisch genereren bij de lesstart” verschijnt alleen wanneer runtimegeneratie werkelijk ondersteund en bedoeld is. Bij vaste vragen blijft deze uitgeschakeld en wordt ze niet als verwarrende standaardboodschap op elk element getoond.

Bij tekstuele begrips- en leesvragen betekent `GENERATED` uitsluitend dat de runtime deterministisch kiest uit een lesgebonden, redactioneel nagekeken bank. De runtime bedenkt geen definities of afleiders vanuit een algemene woordenlijst. Elke vraag is gekoppeld aan één theoriebegrip of geoefende vaardigheid. Een vaste nagekeken bank bevat minstens `itemCount` unieke, volledige prompts. Alleen een procedurele generator moet bovenop die capaciteit ongeziene equivalente toetsitems kunnen maken; een vaste bank mag een eerder onderwezen vraag bewust opnieuw toetsen.

De generatorfase is technische contractdata, geen vrij tekstlabel. De canonieke waarden zijn `guided`, `independent` en `test`; theorie-opvolgvragen gebruiken `guided`. Een legacygenerator die `checkpoint` vereist, wordt via een expliciete exportadapter bediend. Waarden zoals `theory`, `practice` of `theory_check` mogen niet ongecontroleerd in de Android-catalogus belanden.

Na het dedupliceren van een vraagbank worden `itemCount`, blueprints, toetsuitsluiting en `minimumIndependentItems` opnieuw gevalideerd. Een tekort aan unieke vragen is een releasefout. Het wordt niet verborgen door dezelfde prompt met een andere antwoordvolgorde als nieuw curriculumitem op te slaan.

## 1.6 Visual- en SVG-architectuur

Theorievisuals ondersteunen een concreet leerdoel en zijn geen decoratie. Elke visual heeft:

- een stabiele ID;
- een semantisch type;
- een SVG-bron en, waar de renderer dat nodig heeft, een PNG-fallback;
- transparante achtergrond;
- gelokaliseerde alt-tekst;
- een gecontroleerd kleurenpalet;
- een verwijzing naar het concept of de theoriepagina waarvoor hij werd gemaakt.

De SVG-generator gebruikt standaard de stijl `Colors3Images`: PlanetGold, wit en de goedgekeurde derde Poincaré-kleur op transparantie. De editor gebruikt het veld **Beschrijving nieuwe afbeelding**. Bij generatie telt standaard alleen de gekozen generatiestijl en dit veld mee. Alleen wanneer **Lescontext mee verwerken** is aangevinkt, wordt ook `Learning_intent` aan de prompt toegevoegd.

De generatiestijl van verhaalillustraties staat los van de theorie-SVG-stijl.

## 1.7 TTS- en audioarchitectuur

De afspeelvolgorde is:

1. Gebruik een verpakte oefenaudio wanneer die bestaat.
2. Gebruik anders TTS met de expliciete `speechText` of de gecontroleerde vraagtekst.
3. Voor Belgisch Nederlands gebruikt de app het gebundelde offline model; bij een technische fout is Android System TTS de fallback.
4. Voor Frans en Engels gebruikt de app de geselecteerde, beschikbare Android-stem totdat goedgekeurde offline modellen beschikbaar zijn.

Spraaktekst en zichtbare tekst zijn afzonderlijke velden. Bij een oefening wordt alleen de concrete vraag voorgelezen. De algemene opdracht of lesinstructie wordt niet telkens herhaald. Dicteewoorden en -zinnen moeten automatisch én opnieuw op verzoek kunnen worden afgespeeld.

---

# Document 2 — Analyse van feedback en gerealiseerde verbeteringen

## 2.1 Van losse melding naar algemene kwaliteitsregel

| Doorgegeven probleem of wens | Algemene kwaliteitsregel | Aangebrachte verbetering |
|---|---|---|
| Meerdere bronnen bevatten verschillende oefeningen | Eén canonieke bron en éénrichtingsrelease | Curriculum Room is bron; daarna audit, publicatie en Android-export |
| Risico op verlies bij migratie | Voor grote wijzigingen eerst externe backup | Backupstap is verplicht onderdeel van de releaseflow; backups blijven buiten de appfolder |
| Android faalde op onbekend `seriesId` | Schema’s moeten vooruit/achteruit compatibel evolueren | Export- en appmodel werden afgestemd; stabiele IDs en schema-audits bewaken relaties |
| Theorie/Oefen/Toets ontbrak of werd verkeerd getoond | Elke kernles heeft dezelfde zichtbare basisstructuur | Structuur genormaliseerd naar Theorie → afzonderlijke Oefenreeksen → Toets |
| Schrijven toonde maar één pagina | Runtime mag canonieke theoriepagina’s niet samenpersen | Pagina-ID en paginavolgorde sturen nu alle theoriepagina’s |
| Oefenreeksen verdwenen in één lange reeks | Een didactische reeks blijft een afzonderlijke eenheid | Elke exercise set krijgt opnieuw een eigen geordende Oefenfase |
| Extra oefeningen stonden als losse optie | Verrijking hoort in de leerroute | Extra/plus-oefeningen zijn in Oefenreeksen geïntegreerd; alleen incompatibele inhoud wordt een aparte les |
| Dubbele groepen met de titel “Leerjaar 1” | Groepering beschrijft inhoud of leerstap | Willekeurige chunks zijn vervangen door thema-/unitgroepering en betekenisvolle titels |
| Lesvolgorde was niet logisch | Volgorde gaat van eenvoudig en concreet naar complex en zelfstandig | Sequence, vereisten, beschikbaarheid en pedagogische route zijn expliciet gemaakt |
| Letters en vormen traceren verdwenen | Migratie mag oude unieke inhoud niet breed verbergen | Alleen expliciet vervangen lessen worden verborgen; traceerlessen blijven bereikbaar |
| Houding en basislijnen bevatte al letters | Eén les behandelt het beloofde vaardigheidsniveau | Deze startfase beperkt zich tot houding, lijnen en eenvoudige vormen |
| “Alle letters oefenen” ontbrak | Reeksen hebben een aantoonbaar eindpunt | Les toegevoegd na “Hoofdletters N tot Z” |
| Moeilijke, abstracte taal voor zesjarigen | Leeftijd en leerlingtaal zijn releasecriteria | Vragen en antwoorden zijn herschreven vanuit 6-/7-jarig perspectief; generatorpatronen zijn curriculum-breed aangepast |
| Vragen toetsten “Wat doe je daarna?” zonder lescontext | Een vraag toetst het lesdoel, niet een generieke werkwijze | Alleen echte lesgebonden stappen blijven over; het onderwerp staat steeds in de vraag |
| Voorbeeldkaart- en steuntekst verscheen als antwoord | Interne instructietekst mag nooit kandidaatantwoord zijn | Placeholder-/voorbeeldkaartteksten worden bij release en runtime verwijderd |
| Afleiders zeiden “Een andere uitleg uit deze les” | Elk antwoord moet inhoudelijk beoordeelbaar zijn | Opvulantwoorden zijn uit vraagpools en redactionele items verwijderd |
| Meerdere antwoorden konden juist zijn | Single-choice vereist exact één ondubbelzinnig antwoord | Antwoorden worden uniek gemaakt; vraag of interactietype wordt aangepast als meerdere antwoorden geldig zijn |
| Antwoorden begonnen met nummers zoals “1.” | Antwoorden tonen alleen leerlinginhoud | Nummerprefixen en andere editorartefacten worden genormaliseerd |
| Woordkaarten toonden een voorbeeld van een ander begrip | Elk voorbeeld moet aantoonbaar bij zijn begrip horen | Niet-passende koppelingen worden verwijderd; alleen een passend voorbeeld blijft staan |
| Spelfouten waren kunstmatig, zoals `maanxxx` | Een foutoptie blijft leesbaar en bevat een echte, herkenbare letterfout | Afleiders gebruiken verwisselen, weglaten of verdubbelen van letters |
| Vertaalreeksen bevatten vaktaal voor jonge kinderen | Leerjaar 1 en 2 gebruiken concrete basiswoordenschat | De taalbanken zijn beperkt tot dagelijkse woorden over thuis, school, lichaam, kleuren, natuur en handelen |
| Meetvragen waren lang en gebruikten buitensporige waarden | De benodigde context is kort en getallen passen bij het leerjaar | Decoratieve ruimtescènes zijn verwijderd; meetwaarden zijn klein, uniek en direct vergelijkbaar |
| Franse begroetingsvragen waren omslachtig of dubbelzinnig | Eén korte handeling per vraag, in dagelijkse kindtaal | Bijvoorbeeld “Hoe zeg je hallo tegen een vriend?” met één passend antwoord |
| `Woordenschat` was eigenlijk Frans | Tegelnamen benoemen taal en doel | `Woordenschat Frans` en een afzonderlijke route `Woordenschat Engels` toegevoegd |
| Dictee werd niet uitgesproken of miste invoer | Elk dicteetype heeft audio én passende invoer | Ontbrekende letter(s), volledig woord en volledige zin ondersteund met vast AZERTY-klavier en herhaalbare audio |
| Alleen relevante letters moeten klikbaar zijn | De interface beperkt keuzes tot de actuele taak | Letteroefeningen activeren alleen de letters die de oefening nodig heeft |
| Vraaginhoud en toetsenbord/antwoorden stonden onrustig | Interactiepositie is voorspelbaar | Keyboard en multiplechoice onderaan met ondermarge; inhoud gecentreerd in de vrije ruimte |
| Bovenste faseknoppen bleven in de inhoud staan | Navigatiechrome mag de oefening niet verdringen | Theorie/Oefeningen/Toets-knoppen verdwijnen zodra de lesinhoud gestart is |
| Theorie kon niet terug naar vorige pagina | Lineaire inhoud moet herstelbaar zijn | Elke theoriepagina na de eerste heeft een vorige-paginactie |
| Resultatenscherm had geen terugweg | Elke eindstaat heeft een duidelijke bestemming | Resultaten voorzien terugkeer naar de les |
| Story `Afronden` bleef in het verhaal | Afronden sluit de actuele eenheid | Afronden keert terug naar het hoofdstukkenoverzicht |
| Verhalen stonden onder “Lezen” en waren niet klikbaar | Een eigen inhoudstype krijgt een eigen route | Verhalen hebben een actieve, afzonderlijke tegel onder Lezen & luisteren |
| Robbie-hoofdstukken waren samengevoegd | Dezelfde verhaalhiërarchie geldt voor alle werelden | Robbie is opgesplitst in afzonderlijke boeken, hoofdstukken en pagina’s |
| Robbie had boeknummer 999 | Volgorde komt uit canonieke reeksmetadata | Ongeldige sentinelvolgorde wordt genegeerd en vervangen door het canonieke boeknummer |
| Verhaalwerelden en boeken waren niet duidelijk gegroepeerd | Reeks → boek → hoofdstuk → pagina is zichtbaar | Reeksen heten Kleine verhaaltjes, Nova en Robbie; boeken zijn genummerd, gesorteerd en hebben AVI |
| Hoofdstukafbeeldingen namen te veel ruimte in | Overzichtsbeelden gebruiken een compacte vaste verhouding | Covers/hoofdstukbeelden worden 1:1 weergegeven |
| Tekst liep uit het leesframe | Tekst en beeld moeten binnen het leesvlak passen | Tekst wordt over pagina’s verdeeld of beeld wordt verkleind wanneer elke pagina een illustratie heeft |
| Puzzels waren niet actief | Beschikbare inhoud moet klikbaar zijn | Puzzelroutes worden geactiveerd wanneer hun eigen bron bestaat |
| Tegels zonder lessen leken bruikbaar | Beschikbaarheid moet visueel eerlijk zijn | Lege tegels worden grijs en niet klikbaar |
| “Herhalen” was een onduidelijke aparte subcategorie | Herhaling is een leermechanisme, geen afvalbak | Losse tegel verwijderd; herhaling/remediëring hoort in Oefen, foutanalyse en adaptieve vervolgsets |
| Wereldkennis legde veel concepten op één theoriepagina uit | Eén nieuw begrip per pagina met passende illustratie | Theorie is per begrip opgesplitst; 362 conceptvisuals zijn gegenereerd voor leerjaar 1 en 2 |
| `/m/` was visueel storend | Technische fonemenotatie wordt kindvriendelijk gerenderd | Slashes worden verborgen; de letter staat vet en PlanetGold |
| V1/V2-filter zei de gebruiker niets | Filters tonen redactioneel relevante kenmerken | V1/V2 verwijderd; bronmodus, status, ID, categorie, leerjaar en andere betekenisvolle filters blijven |
| Mobiele filters verdwenen | Kernredactie blijft op elk schermformaat bruikbaar | Filters blijven beschikbaar in mobile view |
| Gegenereerde vragen waren niet controleerbaar | Generatorlogica moet inspecteerbaar zijn | Criteria en één voorbeeld worden getoond; vaste en gegenereerde oefeningen zijn herkenbaar |
| SVG-generator gebruikte ongewenste lescontext | Promptinput is expliciet en controleerbaar | Veld hernoemd; lescontext wordt alleen met een afzonderlijk vinkje toegevoegd |
| Feedback op één vraag kwam elders terug | Fouten worden als patroon behandeld | Vergelijkbare vragen, afleiders en generatorregels worden mee geaudit en gecorrigeerd |
| “Plakken” bij lezen werd uitgelegd als lijm gebruiken | Een vakwoord krijgt de betekenis van het lesdomein, nooit toevallig de algemene woordenboekbetekenis | Semantische poolaudit en lesgebonden begrippenregister |
| Dezelfde theorievraag kwam meermaals in één run terug | Runtimevariatie bewaakt uniciteit binnen iedere afspeelwindow | Circulaire-windowtest per theorievraagset |
| Werk duurde lang en status was onduidelijk | Werkactiviteit moet overdraagbaar zijn | Automatisch keep-awake tijdens actieve taken en verplichte handoff na elke taak |

## 2.2 Wat deze analyse ons leert

De meeste problemen hadden niet als hoofdoorzaak “één slechte vraag” of “één fout scherm”. Ze ontstonden op één van vijf systeemgrenzen:

1. **Datahiërarchie:** les, fase, reeks of verhaalonderdeel werd bij import/export samengevoegd of verkeerd gerouteerd.
2. **Pedagogische modellering:** een les bevatte te veel concepten, te abstracte taal of een onlogische moeilijkheidsstap.
3. **Generatorcontract:** criteria waren onzichtbaar of een fout patroon werd over veel vragen herhaald.
4. **Renderercontract:** correcte data bestond, maar de app toonde slechts één pagina, één lange reeks of een niet-passende invoer.
5. **Releaseproces:** web, broncode en toestel liepen niet altijd op exact dezelfde curriculumversie.

Elke nieuwe buganalyse moet daarom deze vijf grenzen controleren voordat een lokale tekst- of UI-fix als voldoende wordt beschouwd.

---

# Document 3 — Logica van de curriculumarchitectuur

## 3.1 Hoofdcategorieën

Het curriculummanifest heeft zes stabiele hoofdcategorie-ID's met deze actuele Nederlandse labels:

1. `language_skills` — Taalvaardigheid
2. `spelling_grammar` — Spelling & grammatica
3. `number_sense` — Rekenen & getalbegrip
4. `measurement_geometry` — Meten & meetkunde
5. `world_orientation` — Wereldoriëntatie
6. `logic_puzzles` — Logica & puzzels

Deze labels komen uit de canonieke structuurbron. Legacylabels zoals “Lezen & luisteren”, “Getallen” of “Wereldkennis” mogen niet opnieuw als parallelle zichtbare taxonomie worden geïntroduceerd. Verhalen blijven een gespecialiseerde route binnen Taalvaardigheid. Puzzels volgen een gespecialiseerde, niet-klassieke spelroute en hebben daarom geen lege klassieke lessubcategorieën.

Elke hoofdcategorie bevat subcategorieën/tegels. Een tegel kan leiden naar:

- thematisch gegroepeerde lessen;
- verhalen;
- puzzels;
- een gespecialiseerde oefenroute, alleen wanneer daar een expliciet curriculumcontract voor bestaat.

Een tegel zonder inhoud voor de geselecteerde leerjaren is grijs en niet klikbaar. Een percentage verschijnt alleen als er echte lessen zijn; puzzels tonen XP in plaats van lesvoortgang.

De hoofdleerjaarfilter selecteert één actieve jaargang uit de per-categorie ouderinstellingen. Verhalen zijn de uitzondering: alle beschikbare verhaalthema's blijven zichtbaar ongeacht die tijdelijke hoofdfilter, zolang Verhalen in het ouderdashboard is ingeschakeld.

## 3.2 Verplichte hiërarchie voor lessen

De zichtbare hiërarchie is:

**Hoofdcategorie → Subcategorie → Thema → Les → Theorie → Oefenreeks 1..n → Toets**

Regels:

- een thema bundelt lessen met één herkenbaar inhoudelijk doel;
- een les is één observeerbare en toetsbare vaardigheidscluster;
- elke les heeft een unieke naam; generieke titels zoals “Oefenen”, “Leerjaar 1” of “Extra variatie” zijn zonder inhoudelijke context onvoldoende;
- elke les heeft precies één Theorie-deel, minimaal één afzonderlijke Oefenreeks en één afsluitende Toets;
- Oefenreeksen blijven afzonderlijk zichtbaar en hebben een doorlopende `seriesIndex`;
- fase-, pagina- en reeksvolgorde zijn aaneensluitend en stabiel;
- beschikbaarheid kan worden ingesteld met leerjaar, sequence, vereisten, publicatiestatus en eventuele ouderinstellingen;
- een volgende les wordt alleen ontgrendeld volgens het ingestelde gatebeleid;
- extra oefeningen worden in de passende Oefenreeks verwerkt of worden alleen bij echte inhoudelijke incompatibiliteit een aparte les.

### Drie zichtbare delen, meerdere oefenreeksen

Een kernles heeft voor het kind exact drie zichtbare delen:

1. **Theorie** — één geordende verzameling theoriepagina's;
2. **Oefenen** — één deel met één of meer afzonderlijke, geordende oefenreeksen;
3. **Toets** — exact één afsluitende toets.

Een oefenreeks is dus geen vierde lesdeel. De data mag meerdere `EXERCISES`-fasen bevatten, maar de navigatie groepeert die onder het ene zichtbare deel Oefenen. De oude fasen `ACQUIRE_UNDERSTAND`, `PRACTISE` en `APPLY_DEMONSTRATE` mogen alleen worden ingelezen om bestaande data en voortgang te behouden. Nieuwe lessen en migraties schrijven uitsluitend `THEORY`, `EXERCISES` en `MASTERY_TEST`.

### Twee niveaus van sequentie

- De **subcategoriesequentie** bepaalt welke subcategorie binnen dezelfde hoofdcategorie eerst voltooid moet zijn. Voor leerjaar 1 is dit de goedgekeurde E-waarde uit `Poincare_Lesvolgorde.xlsx`.
- De **lesstap** bepaalt de volgorde binnen één subcategorie. Voor leerjaar 1 is dit de goedgekeurde H-waarde.
- Gelijke lesstappen zijn parallel: alle lessen met dezelfde stap worden samen beschikbaar en moeten samen voltooid zijn voordat de volgende stap opent.
- Een hogere subcategoriesequentie opent pas wanneer alle lessen uit alle lagere, voor dat leerjaar beschikbare subcategoriesequenties binnen dezelfde hoofdcategorie voltooid zijn.
- Reeds geopende of voltooide inhoud wordt door een migratie niet opnieuw vergrendeld.
- Verhalen en puzzels volgen hun eigen vastgelegde progressiemodel en worden niet stilzwijgend in deze lespoortlogica gedwongen.

## 3.3 Theorie

Theorie is vaste, redactioneel gecontroleerde uitleg. Ze legt uit wat het kind leert, hoe iets werkt en hoe het kind zichzelf kan controleren.

### Inhoudstypes

| Inhoudstype | Doel en gebruik |
|---|---|
| `LEARNING_INTENT` | Zegt in eenvoudige leerlingtaal wat het kind na de les kan. Stuurt redactie, audit en optioneel de beeldgenerator. |
| `VOCABULARY` | Lijst alleen de begrippen die nodig zijn voor deze les. Elk nieuw begrip krijgt term, definitie en waar nuttig voorbeeld/niet-voorbeeld. |
| `CHILD_FRIENDLY_DEFINITION` | Legt precies één begrip uit met korte, concrete woorden die passen bij het leerjaar. |
| `HOW_IT_WORKS` | Beschrijft de handeling of redenering stap voor stap. |
| `CORRECT_RESULT_RULE` | Geeft een eenvoudige controleerbare regel voor een juist resultaat. |
| `WORKED_EXAMPLE` | Toont een volledig correct voorbeeld met dezelfde representatie als in de oefeningen. |
| `NON_EXAMPLE` | Toont een herkenbare fout of iets dat niet bij het begrip hoort en legt kort uit waarom. |
| `VISUAL_MODEL` | Koppelt de uitleg aan een diagram, schrijfvorm, getallenbeeld, fonetisch beeld of wereldkennisillustratie. |
| `SELF_CHECK` | Geeft het kind een korte vraag of controlehandeling vóór het oefenen. |
| `SUMMARY` | Herhaalt de kern in maximaal enkele korte zinnen. |
| `PRACTICAL_INSTRUCTION` | Beschrijft een concrete handeling, materiaalopstelling of beweging. |
| `RUBRIC` | Maakt zichtbaar waarop een productieve of praktische opdracht wordt beoordeeld. |
| `UITLEG_OUDERS` | Geeft precies één beknopte ouderuitleg per les, buiten de herhaalde kindvraag. |

### Pagina-opbouw

- Eén theoriepagina behandelt één hoofdconcept, term, strategie of duidelijke stap.
- `Learning intent` en een zeer korte begrippenintroductie mogen op de overzichtspagina staan.
- Voor leerjaar 1 en 2 wordt ieder `VOCABULARY`-begrip bij publicatie een afzonderlijke `CHILD_FRIENDLY_DEFINITION`-pagina, ongeacht het vak.
- Definitie, werkwijze, uitgewerkt voorbeeld, niet-voorbeeld en controlepunt krijgen elk een eigen leerlingpagina; een pagina bevat maximaal één primair leerlingblok.
- Nieuwe begrippen binnen Wereldoriëntatie krijgen elk een afzonderlijke pagina en eigen visual.
- De tekst blijft binnen het beschikbare frame; scrollen of pagineren is toegestaan, afsnijden niet.
- Elke pagina na de eerste heeft een actie **Vorige**.
- **Verder** gaat naar de volgende theoriepagina of de eerste Oefenreeks.
- De bovenste Theorie/Oefen/Toets-tabs worden niet in de actieve inhoud herhaald.

### Leeftijdstaal

Voor leerjaar 1 wordt elk zichtbaar element gelezen alsof de gebruiker zes jaar is; voor leerjaar 2 alsof de gebruiker zeven jaar is.

- Gebruik korte zinnen en één opdracht per zin.
- Vermijd abstracte metataal zoals “welke stap voer je nu uit?” wanneer een concrete vraag mogelijk is.
- Vermijd onnodige bijzinnen, schooladministratieve woorden en redactieterminologie.
- Introduceer één nieuw begrip tegelijk.
- Gebruik concrete situaties uit de leefwereld van het kind.
- Een moeilijk woord wordt eerst kindvriendelijk uitgelegd en pas daarna gebruikt.
- Een zichtbare letterreferentie zoals `/m/` wordt getoond als een vetgedrukte PlanetGold-letter zonder slashes.

## 3.4 Pedagogische sequentie

### Algemene opbouw

Binnen elke subcategorie en elk thema geldt:

1. waarnemen of motorisch verkennen;
2. herkennen;
3. benoemen;
4. uitvoeren met sterke steun;
5. uitvoeren met afnemende steun;
6. zelfstandig oefenen;
7. toepassen in een nieuwe maar herkenbare context;
8. toetsen met een nieuwe equivalente opgave;
9. later herhalen op conceptniveau.

### Schrijven

De aanbevolen startvolgorde is:

1. zithouding, pengreep en bladligging;
2. basislijnen en eenvoudige vormen, zonder letters;
3. cijfers en letters traceren;
4. kleine lettergroepen met vergelijkbare schrijfbewegingen;
5. hoofdletters A–M;
6. hoofdletters N–Z;
7. alle letters oefenen;
8. één of meer ontbrekende letters in korte woorden;
9. volledige korte woorden;
10. eenvoudige zinnen;
11. schriftelijke expressie met voorbeeld, steun en daarna zelfstandigheid.

### Lezen en luisteren

De aanbevolen opbouw is:

1. boek- en luisterhouding;
2. klanken herkennen;
3. klank-tekenkoppeling in eenvoudige kindtaal;
4. letters en korte woorddelen;
5. korte woorden;
6. eenvoudige zinnen;
7. vlotheid en begrip;
8. verhalen volgens passend AVI-niveau.

Voor technisch lezen is de expliciete startvolgorde:

- leerjaar 1: beginklank → basisletters en klanken → woordjes hakken en plakken → lange klinkers en tweetekenklanken → medeklinkerclusters in eenlettergrepige woorden → frequente woorden automatiseren;
- leerjaar 2: meerlettergrepige woorden → woordfamilies → voor- en achtervoegsels → moeilijke medeklinkerclusters → complexe klinkerpatronen → vlot lezen met tempo en intonatie.

Didactische codes zoals `MKM` mogen intern in metadata blijven, maar de leerling ziet een concrete titel zoals **Woordjes hakken en plakken**.

### Rekenen

Start met concrete aantallen en handelingen, ga daarna naar visuele modellen en pas dan naar symbolen en strategieën. Een oefengenerator mag nooit ongemerkt een ander rekenmodel gebruiken dan de theorie. Groepeer bijvoorbeeld niet tweemaal op “Leerjaar 1”, maar op betekenisvolle stappen zoals concrete samenvoegingen, optellen tot 5, tot 10, tot 20 en strategiegebruik.

### Wereldoriëntatie

Start met kijken en herkennen, behandel vervolgens één term of concept per pagina, laat daarna vergelijken/classificeren en pas pas dan toe in een nieuwe situatie. Abstracte classificaties worden altijd ondersteund door concrete voorbeelden en passende SVG’s.

## 3.5 Oefen

Een les heeft meerdere afzonderlijk zichtbare Oefenreeksen. Elke reeks bevat meerdere vragen en heeft een eigen titel, volgorde, doel en voortgang.

### Bronscenario’s

- vaste vraag + vaste antwoorden;
- vaste vraag + gegenereerde antwoorden;
- gegenereerde vraag + gegenereerde antwoorden;
- open vraag met vaste antwoordvalidatie;
- productieve/praktische opdracht met rubric of observatie.

Elk scenario blijft technisch herleidbaar tot `CURATED` of `GENERATED`. Een gemengde fase bestaat uit meerdere expliciete sets, niet uit een onduidelijke hybride bronmodus.

### Oefendoelen

Oefenreeksen kunnen deze doelen hebben:

- begeleid oefenen;
- afbouwende steun;
- zelfstandig oefenen;
- nabije transfer;
- verdere transfer;
- foutgerichte herhaling.

“Herhalen” is daarom geen zelfstandige algemene subcategorie. Herhaling wordt gericht ingevoegd bij het concept waar een fout of verval werd vastgesteld.

### Toegelaten fouten

Per reeks kan `wrong answers allowed` worden ingesteld. Standaard:

- de eerste helft van de oefenreeksen laat twee fouten per vraag toe;
- de tweede helft laat één fout per vraag toe;
- bij een oneven aantal reeksen bevat de strengere tweede helft één reeks meer.

Deze instelling beïnvloedt feedback en herkansing, maar mag geen dubbelzinnige vraag vergoelijken.

### Vraag- en antwoordkwaliteit

- De vraag bevat één duidelijke opdracht.
- Een kind begrijpt wat het moet doen zonder de algemene lesopdracht opnieuw te horen.
- Single-choice heeft exact één juist antwoord.
- Wanneer meerdere antwoorden inhoudelijk juist kunnen zijn, wordt de vraag versmald of wordt bewust een multi-select/open interactie gebruikt.
- Antwoordopties zijn uniek, vergelijkbaar van vorm en grammaticaal passend bij de vraag.
- Afleiders zijn plausibel maar aantoonbaar fout.
- Geen optie bevat nummers, technische codes, voorbeeldkaarttekst, feedbacktekst of redactienotities.
- De correctheid hangt niet af van een ontbrekende afbeelding of impliciete context.
- De vraag en antwoorden passen bij het taal- en abstractieniveau van het kind.
- De betekenis van elk vakwoord past bij het actuele lesdomein; homoniemen uit een algemeen woordenboek zijn geen geldige vervanging.
- Een theorievraag toetst uitsluitend inhoud die op een voorafgaande theoriepagina zichtbaar of hoorbaar is gemaakt.
- Binnen één runtime-reeks wordt dezelfde prompt niet herhaald, ongeacht het deterministische startpunt.
- Bij een gevonden fout wordt de volledige vergelijkbare set of generatorregel gecontroleerd.

## 3.6 Toets

De Toets meet de vaardigheid zonder dezelfde recente items letterlijk te herhalen.

Regels:

- de toets gebruikt nieuwe equivalente varianten;
- er zijn geen hints;
- feedback volgt na de toets, niet tijdens elke vraag;
- alle vereiste mastery-tags worden afgedekt;
- alleen toetsbewijs kan de lespoort laten slagen;
- de minimumscore is instelbaar door de ouder binnen de ondersteunde grenzen;
- het uitslagenscherm toont score en begrijpelijke vervolgstap;
- onderaan staat een knop om terug naar de les te gaan;
- fouten worden als concepttag bewaard voor latere gevarieerde herhaling, niet als onmiddellijke kopie van exact dezelfde vraag.

## 3.7 Ondersteunde interactie- en oefentypes

De Curriculum Room benoemt het inhoudstype; Android koppelt dit aan een ondersteunde invoermodus/renderer.

### Taal en algemeen

- meerkeuze;
- beeld-en-woordkeuze;
- luisterkeuze;
- luistervertaling;
- juist/onjuist;
- volgorde;
- ontbrekende letter;
- invulvraag;
- vrije tekst;
- woord bouwen;
- aangepast AZERTY-letterklavier;
- letters slepen en neerzetten;
- letter of vorm traceren.

### Dictee

- één ontbrekende letter slepen of invoeren;
- meerdere ontbrekende letters op de juiste plaats zetten;
- een volledig woord schrijven;
- een volledige zin schrijven.

Elke dicteevraag gebruikt “Luister naar het gesproken woord/de gesproken zin en schrijf wat je hoort”, maar TTS leest tijdens de oefening alleen het dicteewoord of de dicteezin, niet de algemene opdracht.

### Rekenen en meetkunde

- aangepast cijferklavier;
- cijfers slepen en neerzetten;
- open numeriek antwoord;
- optellen;
- aftrekken;
- vermenigvuldigen;
- delen en staartdelingen;
- geschreven bewerkingen;
- vormkeuze en meetkundediagram;
- volgorde, vergelijking en getallenreeksen;
- getallenmuur en andere expliciet ondersteunde rekenmodellen.

### Puzzelrenderers

- rekenkruiswoord;
- sudoku-raster;
- woordzoeker;
- calculatorpuzzel;
- getallenmuur;
- andere afzonderlijk geprogrammeerde puzzels met een eigen capabilitystatus.

Een oefentype kan `ACTIVE`, `EXPERIMENTAL` of `UNSUPPORTED` zijn. Alleen actieve renderers mogen zonder expliciete testwaarschuwing in een productie-les worden gebruikt.

## 3.8 Scherm- en interactiecontract

- Galaxy is de standaardnavigator; de tegelnavigator blijft met één headeractie bereikbaar.
- Beide navigators gebruiken dezelfde category-header met categorietitel links en `Volgende les` plus voortgang rechts.
- `Volgende les` bevat geen verhaalportalen.
- Een Galaxy-selectie verandert zoom of pan niet; een tik op lege kaartinhoud sluit alleen het detailpaneel.
- Na een lesdeel of resultaat keert de leerling terug naar de navigatorcontext van waaruit de les werd geopend.
- In een lesintroductie ziet de gebruiker thema, lesnaam, doel, duur, delen en beschikbaarheid.
- In de inhoud verdwijnen overbodige bovenste faseknoppen.
- Theorie ondersteunt **Vorige** en **Verder**.
- Vraaginhoud wordt verticaal gecentreerd in het beschikbare vlak.
- Multiplechoice en aangepast toetsenbord staan voorspelbaar onderaan met veilige bottom margin.
- Het aangepaste klavier blijft zichtbaar bij elke oefening die het vereist.
- Alleen relevante toetsen zijn actief wanneer de taak een beperkte letterset vereist.
- Voorlezen spreekt alleen de vraag of het dictee-item uit.
- **Afronden** verlaat de voltooide eenheid naar het juiste overzicht.
- Een resultaatoverzicht biedt een duidelijke terugkeer naar de les.
- Filters en essentiële editoracties blijven beschikbaar op mobiel.
- De interface voorkomt overflow bij grote tekst, kleine schermen en vergrote letterinstellingen.

## 3.9 Vertalingen

`nl-BE` is de canonieke brontaal voor kernlessen. De vraag en het antwoord blijven in `nl-BE`; vertalingen ondersteunen kinderen en ouders die onvoldoende Nederlands begrijpen.

Vertaalbare velden omvatten minimaal:

- titels en samenvattingen;
- theorie en zichtbare instructies;
- vragen en antwoordopties;
- feedback en hints;
- alt-tekst;
- ouderuitleg;
- relevante gesproken tekst.

Vertaalstatus is expliciet. Niet-beoordeelde vertaling wordt als `MISSING` met goedgekeurde Nederlandse fallback bewaard en nooit stilzwijgend als voltooide machinevertaling voorgesteld. Verhalen kunnen volledige taalversies hebben en blijven per taal als samenhangend geheel gecontroleerd.

## 3.10 Verhalen

### Werelden en beeldstijl

1. **Kleine verhaaltjes** — generatieprompt `Warm aquarelle`
2. **Nova** — generatieprompt `Space travellers`
3. **Robbie** — generatieprompt `Videogame`

### Hiërarchie

**Wereld → Boek → Hoofdstuk → Pagina**

- Elk boek heeft een geldig volgnummer binnen de reeks.
- Boeken worden per reeks op dit nummer gerangschikt.
- Elk boek toont het passende AVI-niveau.
- Nova en Robbie vormen doorlopende verhalen; boek- en hoofdstukvolgorde mogen niet veranderen.
- Robbie-hoofdstukken blijven afzonderlijke hoofdstukken, net zoals bij Nova en Kleine verhaaltjes.
- Draftboeken/-reeksen tonen cursief `Draft`.

### Presentatie en navigatie

- Verhalen hebben een afzonderlijke gespecialiseerde tegel binnen Taalvaardigheid.
- In de Galaxy staan alle verhaalthema's samen in één zelfstandige sphere group, met één portal-orb per thema en zonder start- of lesverbindingen. Boeken verschijnen daar niet afzonderlijk.
- Verhaalportalen worden niet opgenomen in `Volgende les` en worden niet door de tijdelijke hoofdleerjaarfilter verborgen.
- Het wereldoverzicht bevat een submenu per wereld.
- Hoofdstukafbeeldingen worden 1:1 getoond.
- Wanneer tekst te groot is en de volgende pagina geen afbeelding heeft, wordt de tekst logisch over twee pagina’s verdeeld.
- Wanneer elke pagina een afbeelding heeft, wordt de afbeelding voldoende verkleind zodat de tekst binnen het frame blijft.
- **Afronden** keert terug naar het hoofdstukkenoverzicht.
- Een verhaal kan in Nederlands, Frans en Engels worden gelezen/voorgelezen wanneer die taalversie aanwezig en gevalideerd is.

## 3.11 Puzzels

Elke puzzel is een afzonderlijk geprogrammeerde, herhaalbare spelvorm voor logisch denken, automatisatie, geheugen of reflexen.

- Een puzzel genereert nieuwe spelinstanties binnen gecontroleerde grenzen.
- Niveau en inhoud sluiten aan bij de geselecteerde leerjaren en het bekende reken-/woordenschatniveau van het profiel.
- De gebruiker kiest moeilijkheid 1, 2 of 3 sterren.
- Puzzels tonen XP en geen misleidend lespercentage.
- Een puzzeltegel is alleen actief wanneer renderer én inhoudsbron beschikbaar zijn.
- Puzzelvoortgang mag de formele Toets van een les niet vervangen.

---

# 4. Kwaliteits- en teststrategie

## 4.1 Pedagogische kindreview

Voor elke nieuwe of gewijzigde batch wordt de inhoud opnieuw doorlopen vanuit twee persona’s:

- een kind van zes voor leerjaar 1;
- een kind van zeven voor leerjaar 2.

Per theoriepagina, oefenvraag en toetsvraag wordt gecontroleerd:

1. Begrijp ik alle woorden?
2. Weet ik onmiddellijk wat ik moet doen?
3. Is alle noodzakelijke informatie zichtbaar of hoorbaar?
4. Kan maar één antwoord juist zijn als ik één antwoord mag kiezen?
5. Is geen antwoord duidelijk afkomstig uit een editor, voorbeeldkaart of leerkrachtinstructie?
6. Heb ik dit begrip of deze representatie al in de theorie gezien?
7. Wordt de taak merkbaar moeilijker zonder plotselinge sprong?
8. Kan ik teruggaan of de oefening veilig verlaten?

Een negatieve uitkomst leidt eerst tot analyse van het patroon en daarna tot een catalogusbrede correctie.

## 4.2 Automatische curriculumcontroles

Een release blokkeert minimaal op:

- ontbrekende verplichte velden;
- dubbele of instabiele ID’s;
- verbroken relaties tussen les, pagina, reeks, vraag, antwoord of asset;
- verkeerde fase- of reeksvolgorde;
- lessen zonder Theorie, Oefen of Toets;
- samengevoegde oefenbanken waar afzonderlijke reeksen verwacht worden;
- onbereikbare lessen/oefeningen of actieve tegels zonder bron;
- dubbele antwoorden of meer dan één correct antwoord in single-choice;
- verboden placeholder-, voorbeeldkaart- of nummerprefixtekst;
- gegenereerde sets zonder criteria, versie, seedbeleid of voorbeeld;
- ontbrekende renderer-capability;
- ontbrekende dicteeaudio of verkeerd inputtype;
- ontbrekende theorievisual waar die verplicht is;
- ontbrekende assetbestanden, alt-tekst of niet-toegelaten kleuren;
- verhaalboeken zonder geldige reeks, volgorde of AVI;
- verdwenen verhaalvertalingen;
- impliciete, niet-geregistreerde vertaalfallback;
- relocking of verlies van bestaande leerlingvoortgang;
- onverklaarde groei van catalogus, assets of APK.

## 4.3 Handmatige Curriculum Room-test

Controleer op desktop en mobiel:

- filters blijven zichtbaar en bruikbaar;
- V1/V2 wordt niet als betekenisloze filter getoond;
- uniek ID staat klein en leesbaar bij elk element;
- Draft is cursief en status is ondubbelzinnig;
- bronmodus curated/generated is zichtbaar;
- gegenereerde criteria en voorbeeld zijn inspecteerbaar;
- Oefenreeksen zijn afzonderlijk en juist genummerd;
- sequence en beschikbaarheid zijn instelbaar;
- visualprompt gebruikt alleen de bedoelde velden;
- alle zes hoofdcategorieën, relevante tegels, verhalen en puzzels zijn bereikbaar.

## 4.4 Handmatige Android-smoketest

Test per release minimaal:

- één traceerles;
- één dicteeles van elk inputtype;
- één multiplechoice-les;
- één gegenereerde en één vaste oefenreeks;
- één les met meerdere theoriepagina’s en meerdere Oefenreeksen;
- één rekenles met aangepast cijferklavier;
- één les Wereldoriëntatie met meerdere conceptpagina’s/SVG’s;
- één Franse en één Engelse woordenschatles;
- één boek uit elke verhaalwereld;
- story `Afronden` en quizafronding;
- één puzzel per actieve renderer;
- Vorige/Verder, terug naar les en alle ontgrendelregels;
- offline herstart met behoud van voortgang.

## 4.5 Releasepoort / Definition of Done

Een curriculumtaak is pas klaar wanneer:

1. de correctie in de canonieke Curriculum Room-bron staat;
2. een backup bestaat wanneer het om een import/migratie gaat;
3. de wijziging generiek is toegepast op alle vergelijkbare inhoud;
4. stabiele ID’s en bestaande voortgang behouden zijn;
5. audit, webtests en Android-catalogustests slagen;
6. alle verplichte assets bestaan;
7. de productiebuild slaagt;
8. de leerlinggerichte audit voor leerjaar 1 en 2 nul overtredingen meldt voor vraaglengte, context, antwoorduniciteit, opvultekst, begripsvoorbeelden, vertaalwoorden, spelling en meetwaarden;
9. een relevante pedagogische en visuele steekproef is uitgevoerd of expliciet als resterend risico is genoteerd;
10. de private webversie is gepubliceerd wanneer publicatie binnen de opdracht valt;
11. Android is pas geëxporteerd en gebouwd nadat de webbron gevalideerd is;
12. installatie alleen gebeurt na expliciete toestemming;
13. de handoff outcome, gewijzigde artifacts, geslaagde/mislukte/niet-uitgevoerde controles, risico’s en exacte volgende stap bevat.

---

# 5. Huidige gerealiseerde basis en resterende backlog

## 5.1 Gerealiseerde basis per 2026-08-14

- Curriculum Room en de reproduceerbare bronbestanden in `curriculum-library` vormen samen de canonieke lesbron; Android-assets zijn uitsluitend release-uitvoer.
- De webcatalogus-snapshot bevat 348 lesrecords, waaronder 223 V2-kernlessen en 125 verhaallessen, 2.334 oefensets, 21.000 vaste oefeningen, 56 subcategorieën, 1.266 iconen en 19 boeken.
- De lokaal gesynchroniseerde Android-snapshot `2026-08-14-a0830c3b166f` bevat 223 V2-kernlessen in 28 shards en 21.000 vaste oefeningen. De checksum is `a0830c3b166f415534b8fa145def9aaaee9a76f0d6ca9c37cb71937fd9cc55b0`.
- Er bestaan nog 657 legacylesrecords achter een compatibiliteitsadapter. Die adapter mag pas weg wanneer dit aantal nul is en de volledige V2-runtimevalidatie slaagt.
- De lesruntime bewaart de zichtbare Theorie/Oefenen/Toets-structuur; afzonderlijke oefenreeksen blijven binnen Oefenen herkenbaar.
- Traceerlessen blijven bereikbaar.
- Wereldoriëntatie voor leerjaar 1 en 2 heeft conceptpagina’s en 362 gegenereerde conceptvisuals.
- Verhalen zijn per wereld, boek, hoofdstuk en pagina gestructureerd; boeknummer en AVI worden bewaakt.
- Dictee, Frans/Engels woordenschat, puzzelroutes, tile availability, audio en navigatie hebben gerichte regressiecontroles.
- De webstructuurtest (29/29), leerjaar-1-leerlingaudit (92/92), child-contentaudit, V2-audit, typecheck en web/Android-pariteitsaudit slagen.
- De Android-unit-/runtimestatus is 341/341. De generatorfasen, vraagpoolcapaciteit, toets-poortconsistentie en assertions volgen nu hetzelfde actuele contract. De snapshot is in deze taak niet gepubliceerd en niet op een toestel geïnstalleerd.

Deze aantallen zijn een gedateerde statusmeting, geen vast productmaximum. Nieuwe releases actualiseren de aantallen en checksums.

### Android-architectuuruitbreiding per 2026-08-18

- Galaxy is de standaardhoofdnavigatie, naast de behouden tegelroute.
- Hoofd- en category-headers, leerjaarfilter, takenroute en ouderdashboard gebruiken gedeelde contracten.
- De Galaxy projecteert iedere klassieke les naar Theorie-, Oefeningen- en Toets-orbs, bouwt sphere groups en verbindingen uit live prerequisites en toont verhalen als thema-portalen.
- De deterministische formulegeometrie, groupplaatsing, lijnlagen, pan/zoom en selectie zijn als afzonderlijk contract vastgelegd in `docs/GALAXY_NAVIGATION_ARCHITECTURE.md`.
- De Android-build is gemigreerd naar AGP 9.2.1, Gradle 9.4.1, ingebouwde Kotlin 2.2.10 en KSP 2.3.11; de verouderde Compose-aanroepen die daarbij waarschuwden zijn opgeschoond.

## 5.2 Bekende resterende aandachtspunten

- De volledige V2-kernleslaag voor leerjaar 3–6 is buiten de reeds gemigreerde delen nog niet compleet.
- De combinatie van 223 V2-lessen en 657 legacylessen is overgangsarchitectuur, geen gewenste eindsituatie. Iedere volgende leerjaarbewerking moet het aantal gemigreerde records aantoonbaar verhogen zonder nieuwe parallelle bronnen te maken.
- Nog niet beoordeelde Franse en Engelse ondersteuningsvelden blijven expliciet als backlog met Nederlandse fallback geregistreerd.
- De nieuwe visualisaties voor Wereldoriëntatie vereisen blijvende pedagogische en visuele steekproeven; automatisch geldig betekent niet automatisch didactisch optimaal.
- Online synchronisatie van leerlingvoortgang blijft uitgeschakeld totdat een echt veilig servercontract bestaat.
- Vaste nagekeken vraagbanken en procedurele generators moeten als verschillende contracttypen herkenbaar blijven. Een toekomstige wijziging mag de toetsuitsluiting van procedurele generators niet stil toepassen op vaste banken, of omgekeerd.
- Een succesvolle audit vervangt geen test door kinderen, ouders en leerkrachten op echte toestellen.

---

# 6. Standaard werkwijze voor elke volgende verbetering

Gebruik voor iedere nieuwe melding dit vaste sjabloon:

1. **Observeer:** noteer scherm, leerjaar, categorie, thema, les, fase/reeks, vraag- of pagina-ID en verwacht gedrag.
2. **Classificeer:** datahiërarchie, pedagogiek, generator, renderer, navigatie, audio, vertaling, asset of releaseflow.
3. **Zoek het patroon:** bepaal welke vergelijkbare lessen, vraagsets, talen en renderers dezelfde regel gebruiken.
4. **Formuleer de curriculumregel:** schrijf eerst wat overal waar moet zijn.
5. **Corrigeer de bron:** pas de Curriculum Room/canonieke buildregel aan, niet alleen de Android-kopie.
6. **Werk renderer/export bij indien nodig:** behoud schema- en voortgangscompatibiliteit.
7. **Voeg regressietest toe:** laat dezelfde fout voortaan automatisch blokkeren waar dat mogelijk is.
8. **Voer kindreview uit:** zes-/zevenjarig perspectief voor leerjaar 1/2.
9. **Bouw en test:** webaudit, webbuild, Android-tests en relevante handmatige steekproef.
10. **Publiceer gecontroleerd:** web eerst; Android daarna; toestelinstallatie alleen op verzoek.
11. **Documenteer:** update dit masterdocument wanneer de algemene regel verandert en schrijf een volledige handoff.

Dit proces zorgt ervoor dat iedere feedbackronde de kwaliteit van het volledige curriculum verhoogt en niet alleen het zichtbare voorbeeld repareert.
