# Bereikbaarheidsaudit curriculum en extra oefeningen

Datum: 24 juli 2026

## Geautomatiseerde controles

- Alle 64 curriculumunits en hun verrijkte lessen zijn bereikbaar via minstens één zichtbare subcategorietegel.
- Alle 25.200 oefeningen uit de vijf canonieke XML-catalogi hebben een zichtbare tegel- of optie-route.
- Alle 384 goedgekeurde generatorbronnen zijn geregistreerd, uitvoerbaar en leveren exact het gevraagde aantal oefeningen.
- Een tegel wordt alleen getoond wanneer minstens één geselecteerd leerjaar lessen of extra oefeningen bevat.
- Een voortgangspercentage wordt alleen getoond wanneer de tegel echte lessen bevat.
- Puzzels tonen XP in plaats van lesvoortgang.
- De lesroutering gebruikt zowel de primaire subcategorie van een unit als alle
  goedgekeurde subcategorieën uit de roadmap. Een gemengde meetunit staat daardoor
  niet langer uitsluitend onder `Vergelijken, ordenen & schatten`.
- Pedagogische routebruggen koppelen `Dictee` aan passende spelling- en
  klank-letterunits en `Letters schrijven` aan de bestaande units voor schriftelijke
  expressie, ook wanneer de oude tegel en de curriculumunit historisch in een ander
  intern taaldomein staan.

De audit ontdekte en herstelde:

- 4.200 verpakte logica-oefeningen zonder zichtbare tegel: patronen, wat hoort er niet bij, analogieën, rasterlogica, doolhoven, visueel redeneren en geheugenregels.
- Negen curriculumunits zonder kindroute: schrijven in leerjaar 1, 2, 3, 5 en 6; leesstrategieën in leerjaar 3; temperatuur in leerjaar 4; en verkeersveiligheid in leerjaar 1.

## Tegels met lessen én extra oefeningen

| Hoofdcategorie | Tegels |
|---|---|
| Lezen & luisteren | Lezen, luisteren, spreken & presenteren, schrijven, dictee |
| Spelling & grammatica | Letters schrijven, schrijven, spelling, zinsvolgorde, grammatica, vraagwoorden, enkelvoud en meervoud |
| Getallen | Optellen, aftrekken, vermenigvuldigen, delen, gemengde bewerkingen, getallenreeksen, statistiek, breuken |
| Meten & meetkunde | Tijd, meten, vergelijken/ordenen/schatten, vormen en meetkunde, temperatuur |
| Wereldkennis | Natuur, wetenschap, techniek, aardrijkskunde, mens & gezondheid, burgerschap & sociale vaardigheden, dagelijks leven |

## Tegels met lessen maar nog zonder aparte extra-oefenbank

| Hoofdcategorie | Tegel |
|---|---|
| Wereldkennis | Verkeer, mobiliteit & veiligheid |

Deze tegels tonen lesvoortgang en blijven zichtbaar. Een volgende inhoudsronde kan er een afzonderlijke extra-oefenbank aan koppelen.

## Tegels met alleen extra oefeningen

| Hoofdcategorie | Tegels |
|---|---|
| Lezen & luisteren | Alle zes vertaalrichtingen |
| Spelling & grammatica | Vervoegingen, werkwoorden, verleden tijden, toekomstige tijden |
| Getallen | Geld |
| Wereldkennis | Geschiedenis, milieu & duurzaamheid, kunst & cultuur |
| Puzzels | Alle puzzeltegels; dit is bewust een XP-gestuurde oefenomgeving zonder lespercentage |

Deze tegels blijven bereikbaar, maar tonen geen misleidende `0%`. In de volgende curriculumuitbreiding moeten de niet-puzzeltegels ofwel aan passende bestaande units worden gekoppeld, ofwel eigen curriculumunits en lessen krijgen.

## Belangrijk onderscheid

`Bereikbaar` betekent dat een verpakte oefening of goedgekeurde generator via de
kindinterface kan worden geopend. Het betekent niet dat elke oude oefentegel al een
volledig eigen, diepgaand lestraject van ongeveer 100 uur per leerjaar bevat.

De huidige correctieronde herstelt de navigatie naar passende bestaande units.
De geplande curriculumuitbreiding blijft nodig voor uitgebreidere instructie,
meer herhaling, adaptieve remediëring, praktische opdrachten en afzonderlijke
toetsen per leerdoel.

## Permanente regressiepoort

`AllContentReachabilityTest` blokkeert voortaan een build wanneer:

1. een curriculumunit geen zichtbare subcategorieroute heeft; of
2. een canonieke XML-oefening niet via een tegel of tegeloptie kan worden geopend.

De bestaande generatoractivatietest blijft daarnaast elke goedgekeurde dynamische oefenbron daadwerkelijk uitvoeren.
