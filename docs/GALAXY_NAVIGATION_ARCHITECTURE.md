# Poincaré Galaxy navigation architecture

Versie: 2026-08-18  
Status: normatief integratie-, layout-, rendering- en interactiecontract  
Scope: hoofd-Galaxy en sphere-grid per hoofdcategorie

## 1. Doel en gezag

De Galaxy is een dynamische visualisatie van het actuele curriculum, geen tweede curriculumstructuur en geen handmatig getekende kaart. Nieuwe of gewijzigde lessen, prerequisites, leerjaren en verhaalthema's moeten zonder schermspecifieke positietabellen in de kaart verschijnen.

De bronverdeling is bewust gescheiden:

- curriculum-/profielmodellen bepalen zichtbaarheid, volgorde, prerequisites, checkpoint en voortgang;
- `context/GalaxyMap/poincare_galaxy_navigation_prompt.md` bepaalt de exacte formule, `ShapeSpec`s, Point IDs, route en interne krommen van een klassieke sphere group;
- dit document bepaalt hoe die geometrie dynamisch wordt gevuld, geplaatst, verbonden, gerenderd en bediend;
- `GalaxyFormulaGeometry.kt`, `GalaxyLayoutEngine.kt`, `LearningGalaxyModels.kt` en `LearningGalaxyScreen.kt` zijn de actuele technische implementatie;
- gerichte tests bewaken zowel de formule als het integratiecontract.

Er is geen force-directed of grid-layout voor individuele orbs. Collision- en routeoptimalisatie is alleen toegestaan voor de positie en oriëntatie van een complete sphere group; de formulepunten binnen die group bewegen nooit afzonderlijk.

## 2. Navigatieniveaus

### 2.1 Hoofd-Galaxy

De hoofd-Galaxy toont:

- één centrale speler-/startorb zonder tekst of binnenste cirkel;
- één categorie-orb per ingeschakelde hoofdcategorie, met het manifesticoon;
- organische verbindingen die vanaf de rand van de startorb vertrekken;
- categorievoortgang in de orb, behalve voor Logica & puzzels;
- dezelfde hoofdheader en algemene voortgang als de tegelinterface.

Categoriecentra worden responsief rond het beschikbare vlak verdeeld. De buitenste categoriering is slechts iets groter dan de klikbare categorie-orb.

### 2.2 Category focus

Na het openen van een hoofdcategorie verdwijnen profiel-, navigator- en instellingenbediening. De gedeelde category-header blijft staan; daaronder is een pan- en zoombare kaart met:

- één startorb;
- één of meer sphere groups per subcategorie en leerjaar;
- optionele checkpoint-orbs tussen groups;
- een zelfstandige verhaal-sphere group binnen Taalvaardigheid;
- een detailpaneel voor de geselecteerde lesfase.

De kaart toont geen zichtbare legenda, gesture-hint, “kies een orb”-hint, “categorie voltooid”-tekst, titel “Leergalaxy”, titel “Kies een leerwereld” of tekst “Start” in de startorb. De relevante percentages, iconen en toegankelijkheidslabels blijven wel beschikbaar volgens hun eigen contract.

## 3. Dynamische invoer en filtering

`buildLearningGalaxyUiState` bouwt de kaart uitsluitend uit:

- het actuele curriculummanifest;
- de V2-lescatalogus;
- het huidige profiel en de per-categorie toegestane leerjaren;
- de actieve hoofdleerjaarfilter;
- les- en fasevoortgang;
- de beschikbare verhalencatalogus.

Een klassieke les verschijnt alleen als hoofdcategorie, subcategorie en leerjaar voor het profiel zijn ingeschakeld. De hoofdleerjaarfilter kiest daarna één zichtbare jaargang. Verhalen negeren alleen die tijdelijke hoofdleerjaarfilter; hun zichtbaarheid blijft afhankelijk van de ouderinstelling voor Taalvaardigheid/Verhalen.

`GalaxyNodeState` kent `LOCKED`, `AVAILABLE`, `RECOMMENDED`, `IN_PROGRESS`, `COMPLETED` en `MASTERED`. Prerequisites worden beoordeeld met hetzelfde gatecontract als de lesruntime. De eerste beschikbare kernles per categorie wordt `RECOMMENDED`.

## 4. Les- en verhaalprojectie

### 4.1 Eén overzichtsorb per zichtbaar lesdeel

Een klassieke les levert in vaste volgorde maximaal drie overview-orbs:

1. `THEORY`;
2. `PRACTICE`;
3. `TEST`.

Meerdere theoriepagina's, oefenreeksen of toetsrecords blijven in het lesoverzicht en de runtime afzonderlijk bereikbaar, maar worden per soort tot één Galaxy-orb samengevat. Status en voortgang van zo'n orb worden uit alle onderliggende fasen afgeleid. Node-identiteit is deterministisch: `<lessonId>:<orbKind>`.

De drie orbs blijven onderdeel van één doorlopende sphere-route. Een smalle, themakleurige corridor volgt theorie → oefenen → toets zonder van elke les een los cluster te maken. De corridor is 1,15 maal de orbdiameter breed, gebruikt alpha 0,10 en alpha 0,20 voor de geselecteerde les. Eén aaneengesloten draw-operatie voorkomt dat de middelste oefenorb dubbel wordt gemarkeerd.

### 4.2 Verhalen

Alle verhaalthema's staan in één afzonderlijke sphere group. Een thema wordt bepaald door de stabiele verhaalreeks-ID en verschijnt als één portal-orb; individuele boeken verschijnen niet in de Galaxy. Thema's worden deterministisch volgens reeksvolgorde en ID gesorteerd en op de hoeken van een symbolische regelmatige veelhoek gelegd.

De verhaalgroup:

- staat verder van de klassieke lesgroups;
- heeft geen verbinding met de startorb of lesroutes;
- opent de gespecialiseerde Story Library;
- wordt niet opgenomen in `Volgende les`.

## 5. Sphere-groupvorming

Voor klassieke tracks geldt:

1. groepeer per subcategorie en zichtbaar leerjaar;
2. orden lessen topologisch volgens hun live prerequisite-DAG;
3. gebruik `sequence` en stabiele ID uitsluitend als deterministische tie-breaker;
4. projecteer iedere les naar haar complete set overview-orbs;
5. splits pas tussen volledige lessen wanneer meer dan 60 orbs nodig zijn;
6. kies de exacte `ShapeSpec` voor het uiteindelijke orbaantal.

Omdat een klassieke les drie orbs levert, zijn de ondersteunde aantallen 3, 6, ... 60. Een toekomstige cataloguscyclus mag geen inhoud verbergen: de resterende lessen blijven deterministisch zichtbaar, terwijl catalogusvalidatie de cyclus als fout rapporteert.

De formule-engine gebruikt het canonieke vlak 1100 × 1100 met centrum (550,550), de 20 `ShapeSpec`s van 3 tot en met 60 punten, stabiele Point IDs, vaste laagvolgorde en het logische exit-sentinel `453`. Uniforme schaal, rotatie en spiegeling behouden Point IDs, routevolgorde en interne verbindingen.

## 6. Plaatsing en oriëntatie van groups

Sphere groups worden eerst als knopen in een gereduceerde dependency-DAG behandeld. Daarna:

- roots staan organisch rond de startorb;
- opvolgers worden buiten hun voorgangers geplaatst;
- parallelle opvolgers krijgen gespreide hoeken;
- een enkel vervolgpad krijgt afwisselende organische bochten;
- minimale afstand voorkomt overlap tussen volledige groups en met de startorb;
- kandidaatposities worden deterministisch gescoord op obstructies, lijnkruisingen, hoekafwijking en afstand;
- de verhaalgroup krijgt extra afstand;
- de complete formulevorm wordt geroteerd en eventueel horizontaal gespiegeld zodat entry- en exitpunten naar de aangrenzende groups wijzen.

Deze stap mag een hele group verplaatsen of transformeren. Ze mag nooit een individueel formulepunt herschikken, naar een raster snappen of door collision-resolution van zijn berekende plaats halen.

## 7. Verbindingscontract

Verbindingen worden afgeleid, niet handmatig opgeslagen:

- binnen één les: Theorie → Oefeningen → Toets;
- tussen lessen in dezelfde group: laatste Toets van de directe prerequisite → eerste orb van de vervolglesson;
- tussen sphere groups: laatste Toets van de bron-group → eerste Theorie-orb van de doel-group;
- ancestor-shortcuts worden met transitieve reductie verwijderd, directe vertakkingen blijven;
- de startorb verbindt alleen met echte rootlessen zonder prerequisite, checkpoint of portal;
- een checkpoint ontvangt lijnen van de laatste Toets-orbs van zijn directe vereisten en verbindt daarna met de eerste Theorie-orb van de doelles;
- lijnen beginnen en eindigen op orbgrenzen, niet in het middelpunt;
- het einde van een group wordt nooit terug met het begin van dezelfde group verbonden.

Een les met `checkpoint=true` vereist de volledige transitieve prerequisiteketen. Het checkpoint is een afzonderlijke orb. Leerjaar 1 mag volgens de catalogusvalidator nooit `checkpoint=true` hebben.

## 8. Rendering en lijnlagen

De renderer gebruikt deze functionele laagvolgorde:

1. sterrenachtergrond;
2. delen van cross-group-lijnen die achter niet-gekoppelde sphere groups lopen;
3. transparante groupvulling;
4. lescorridors;
5. dunne groupomtrek;
6. interne lijnen en de normale delen van cross-group-lijnen;
7. start-, checkpoint- en lesorbs;
8. subcategorie-iconen, subcategorietitels en lestitels.

De gewone sphere-groupvulling gebruikt slechts een lage themakleuropacity; selectie gebeurt via de duidelijkere lescorridor en geselecteerde orb, niet door de volledige group massief te vullen. De groupomtrek blijft dun zodat groups herkenbaar zijn zonder een tegel- of tabelgevoel te creëren.

Voor iedere cross-group-lijn worden de sphere groups van beide endpoints uitgesloten van de blocker-mask. Alleen het deel dat een overige group kruist wordt eronder getekend. Op gewone achtergrond en in beide gekoppelde groups behoudt dezelfde lijn haar normale opacity. Een lijn wordt nooit volledig verzwakt omdat ze ergens een group kruist.

Wanneer een vergrendelde orb geselecteerd is, worden alle prerequisite- en afhankelijke lijnen in de relevante dependencyketen wit en vet. De geselecteerde orb en corridor blijven herkenbaar, terwijl de doorgaanactie vergrendeld blijft.

## 9. Orbstatus en iconen

De statusstijl is overal dezelfde:

| Status | Orbweergave |
|---|---|
| Vergrendeld | dunne grijze rand |
| Beschikbaar/aanbevolen | dunne PlanetBlue-rand |
| Bezig | dunne PlanetBlue-rand plus duidelijke PlanetBlue-voortgangsrand |
| Voltooid/beheerst | krachtige PlanetGold-weergave |

Orbsoort wordt met universele iconen aangeduid: boek voor Theorie, potlood voor Oefeningen en vink/toetskader voor Toets. Verhaalthema's gebruiken het verhaal-/portalicoon. Lesillustraties vervangen deze universele fase-iconen niet.

## 10. Labels en zoom

De initiële schaal past de volledige kaart gecentreerd in het beschikbare viewport. De gebruiker kan uitzoomen tot de kleinste van de fitted scale en 0,42, en inzoomen tot 2,15. Pan blijft begrensd met een marge zodat links of rechts gelegen groups terug in beeld kunnen worden gebracht.

Op het eerste zoomniveau:

- het subcategorie-icoon blijft exact in het midden van iedere sphere group;
- de subcategorietitel verschijnt vanaf `minimumScale + 0,06` en gebruikt een leesbare, inverse schaal;
- de lestitel verschijnt vanaf `minimumScale + 0,12`, bij de eerste orb van de les;
- labels worden na lijnen en orbs gerenderd en blijven bovenaan zichtbaar;
- de verborgen resetfunctie blijft in code beschikbaar, maar heeft geen zichtbare knop.

## 11. Selectie en detailpaneel

Iedere orb is selecteerbaar, ook wanneer ze vergrendeld is. Selectie:

- kiest exact de getapte Theorie-, Oefeningen-, Toets- of portalorb;
- markeert orb en volledige lescorridor;
- synchroniseert de geselecteerde kaart in het detailpaneel;
- laat toe tussen Theorie, Oefeningen en Toets te wisselen via de drie even brede detailkaarten;
- toont lesnaam, lespercentage, prerequisiteboodschap en reeksdots, zonder hoofdcategory, dubbele subcategory, orbtype of “n reeksen”;
- schakelt Start/Verder uit zolang de gekozen orb vergrendeld is.

Een tik op lege kaartruimte verbergt alleen het detailpaneel. `scale`, `panOffsetX` en `panOffsetY` zijn per categorie bewaard en veranderen niet door orbselectie, detailpaneel-resize of het sluiten van details. Alleen een expliciete zoom-/panbeweging, verborgen resetactie of het verlaten van de category focus mag de viewportcontext veranderen.

## 12. Header en volgende les

Category focus gebruikt hetzelfde `MainCategoryHeader` als de tegelroute. Titel staat links; `Volgende les` en percentage staan rechts op dezelfde rij in portret en landschap. Het detailpaneel neemt in landschap een vaste zijbreedte en in portret de onderzijde in zonder de kaarttransformatie te resetten.

`Volgende les` bevat alleen niet-portal-kernlessen in status `RECOMMENDED`, `IN_PROGRESS` of `AVAILABLE`, gesorteerd op statusprioriteit, sequence en ID. Het overzicht toont de les en haar Theorie/Oefeningen/Toetsstructuur en selecteert bij openen de best passende beschikbare orb.

## 13. Uitbreidbaarheid en verboden shortcuts

Toegestaan:

- een nieuw curriculumitem toevoegen en de bestaande projectie/layout opnieuw uitvoeren;
- een nieuwe `ShapeSpec` toevoegen met formule- en invarianttests;
- renderingoptimalisatie die node-ID, geometry en hit targets intact laat;
- volledige groups anders roteren/plaatsen om nieuwe dependencies leesbaar te maken.

Verboden:

- handmatige x/y-posities per les, subcategorie of leerjaar;
- orbs verplaatsen om een lokaal lijnprobleem visueel te verbergen;
- routes tekenen vanaf een groupcontainer in plaats van een individuele orb;
- iedere oefenreeks als een extra Galaxy-orb tonen;
- verhalen als boekenorb of als volgende les behandelen;
- de zichtbare volgorde uit legacylabels afleiden;
- selectie gebruiken als impliciete zoom/resetactie.

## 14. Verificatiecontract

Gerichte tests moeten minimaal bewaken:

- alle formule-invarianten, shape-aantallen, Point IDs, route en transformaties;
- topologische lesvolgorde en deterministische tie-breakers;
- splitsing op volledige lesgrenzen en maximale shape-capaciteit;
- interne, intergroup-, checkpoint- en startverbindingen;
- transitive reduction en afwezigheid van verhaalverbindingen;
- story-themegroepering en uitzondering op de hoofdleerjaarfilter;
- orbstatus, checkpointregel en kandidaten voor Volgende les;
- geen overlap van sphere groups;
- viewportbegrenzing, labeldrempels en selectie zonder zoomreset;
- blocker-masks die alleen niet-endpoint-groups bedekken;
- verbergen van het Logica & puzzels-percentage op de hoofd-Galaxy.

Na layout- of renderingwijzigingen blijft een korte visuele toesteltest nodig voor portret en landschap: fit, pan/zoom, orbselectie, locked dependencyhighlight, lijnlagen, labels en terugkeer vanuit een les.
