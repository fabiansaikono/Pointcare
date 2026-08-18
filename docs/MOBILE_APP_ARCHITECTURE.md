# Poincaré mobile-app architecture

Versie: 2026-08-18  
Status: normatief Android-integratie- en navigatiecontract  
Scope: app-shell, profiel- en ouderstromen, navigatiemodi, lokale data en buildstack

## 1. Doel en documentgezag

Dit document beschrijft hoe de Android-app de canonieke curriculumdata, leerlingvoortgang en gespecialiseerde routes samenbrengt. Het vervangt geen inhoudelijk curriculumcontract en bevat geen momentopname van les- of assetaantallen.

Bij tegenstrijdigheid geldt:

1. actuele modellen, validators en regressietests bepalen wat technisch afdwingbaar is;
2. `docs/CURRICULUM_ARCHITECTURE_PLAYBOOK.md` bepaalt curriculum, publicatie en ontgrendeling;
3. `docs/lesson_v2/SECTIONED_LESSON_ARCHITECTURE.md` bepaalt de drieledige kernles;
4. `docs/GALAXY_NAVIGATION_ARCHITECTURE.md` bepaalt de Galaxy-integratie en interactie;
5. `context/GalaxyMap/poincare_galaxy_navigation_prompt.md` bepaalt de exacte formulegeometrie binnen een niet-verhaal-sphere group;
6. dit document bepaalt app-shell, route-eigenaarschap en gedeelde Android-UI-contracten;
7. audits en handoffs zijn alleen gedateerd bewijs van een uitvoering.

Een implementatieprompt is geen tweede curriculumbron. De Galaxy-prompt bezit uitsluitend de exact beschreven geometrie; zichtbaarheid, status, voortgang en navigatie komen uit de actuele app- en curriculumcontracten.

## 2. Systeemgrens en lagen

```text
canonieke Curriculum Room-snapshot
        ↓ export + release-ID/checksum
Android assets en manifest
        ↓ repositories
curriculum-, verhaal- en takenmodellen
        ↓ ViewModels / StateFlow
Compose-schermen en Navigation Compose
        ↕
Room + DataStore voor profiel, voortgang en instellingen
```

Verantwoordelijkheden:

- De Curriculum Room blijft eigenaar van categorieën, subcategorieën, lessen, verhalen, volgorde, prerequisites en assets.
- Android leest één geëxporteerde, sharded snapshot en corrigeert geen lesinhoud als concurrerende bron.
- Repositories verzorgen assetparsing en lokale opslag; ViewModels bouwen schermtoestand; Composables renderen en sturen alleen gebruikersacties terug.
- Profielen, geselecteerde leerjaren, les-/reeksvoortgang, verhaalvoortgang en instellingen blijven lokaal. Online voortgangssynchronisatie blijft uit totdat er een geauthenticeerd en idempotent servercontract bestaat.

## 3. Hoofdnavigatie

De standaardbestemming na profielselectie of profielcreatie is `LearningGalaxy`. De tegelinterface (`SpaceMap`) blijft een volwaardige alternatieve navigator. De knop in de hoofdheader wisselt tussen beide routes en vervangt de vorige navigator op de back stack, zodat wisselen geen stapel identieke hoofdoverzichten maakt.

```text
ProfileSelection
├── CreateProfile → LearningGalaxy
└── bestaand profiel → LearningGalaxy

LearningGalaxy ⇄ SpaceMap
├── ParentDashboard
├── Tasks
├── hoofdcategorie
│   ├── Galaxy-category focus → orb → LessonIntro/LessonPlayer
│   └── tegelroute → SubCategorySelection → LessonHub → LessonIntro/LessonPlayer
├── Verhalen → StoryLibrary → StoryBook → StoryReader
└── Logica & puzzels → gespecialiseerde puzzelroute
```

De route waaruit een les wordt geopend blijft onder `LessonIntro`/`LessonPlayer` op de back stack. Terugkeren na een deel, een resultaat of de systeemterugknop brengt de leerling daardoor terug naar dezelfde navigatorcontext: de geselecteerde Galaxy-les of het juiste tegel-/lesoverzicht. Een nieuwe route mag die herkomst niet vervangen door een hard gecodeerde algemene bestemming.

## 4. Profielcreatie en oudertoegang

Een nieuw profiel doorloopt exact deze stappen:

1. app-taal kiezen;
2. ouderpincode invoeren via hetzelfde `ParentPinGate`-component als het ouderdashboard;
3. profielgegevens en leerjaarinstellingen invullen.

Wanneer nog geen ouderpincode bestaat, is `1234` de standaard en wordt dit alleen in de eerste-profieltekst vermeld. Naam moet niet leeg zijn. Geboortejaar accepteert maximaal vier cijfers en moet binnen de gevalideerde jaarrange vallen voordat opslaan mogelijk is.

Het ouderdashboard werkt met een lokale draft:

- wijzigingen aan profiel, talen, categorieën, leerjaren, leraar-ID en instellingen worden niet direct opgeslagen;
- de knop Opslaan is alleen actief bij een geldige gewijzigde draft;
- verlaten met niet-opgeslagen wijzigingen vraagt expliciet om opslaan of wijzigingen verwerpen;
- iedere hoofdcategorie heeft minstens één geselecteerd leerjaar;
- zichtbare categorienamen komen uit hetzelfde curriculummanifest als de leerlinginterface;
- `teacherId` wordt getrimd opgeslagen en koppelt het profiel aan de lokale huiswerk-/takencatalogus.

## 5. Gedeelde hoofdheader en leerjaarfilter

Galaxy en tegels gebruiken hetzelfde hoofdheadercontract:

- profielavatar opent profielwissel; profielnaam en helpertekst worden niet herhaald;
- het actieve leerjaar verschijnt als tekst `L1` ... `L6`;
- alleen bij meerdere beschikbare leerjaren is die tekst een dropdown;
- de takenknop staat naast het leerjaar;
- navigatorwissel en instellingen staan rechts;
- de hoofdvoortgang gebruikt in beide navigatiemodi dezelfde `LevelProgressSummary`.

Het gekozen hoofdleerjaar is een kijkfilter. De ouderinstelling blijft de bron voor welke leerjaren per categorie werkelijk toegestaan zijn. Bij één toegestaan leerjaar wordt geen redundant leerjaarlabel naast elke les getoond.

Verhalen vormen de expliciete uitzondering: de hoofdleerjaarfilter verbergt geen beschikbare verhaalthema's. Alleen de categorie-/subcategorie-instelling in het ouderdashboard bepaalt of Verhalen zichtbaar is.

## 6. Hoofdcategorie- en lesheaders

In de hoofdtegelnavigator vormen icoon en titel één bewegend tile-element. Er staat geen decoratieve cirkel achter het icoon; de volledige inhoud krijgt dezelfde rustige hoverbeweging. Portret gebruikt extra verticale tussenruimte, landschap extra horizontale tussenruimte. Een gespecialiseerde tegel zoals Verhalen behoudt de standaardtegelhoogte. Subcategorietegels tonen geen redundante tekst “0 van de 1 lessen voltooid”.

Galaxy-category focus en de tegelroute delen `MainCategoryHeader`:

- terugknop en categorietitel links;
- `Volgende les` en voortgang rechts op dezelfde rij, ook in portret;
- titel is één headermaat compacter en gebruikt de beschikbare breedte met ellipsis;
- de pill `Volgende les` heeft een compacte vaste hoogte;
- `Volgende les` toont alle beschikbare, aanbevolen of lopende kernlessen met hun lesstatus en sluit verhaalportalen uit.

Subcategorie- en lesoverzichten tonen een lesnaam eenmaal. Technische samenvattingen zoals “3 delen” of “n reeksen” zijn geen leerlinglabels. Het lesoverzicht gebruikt de zichtbare labels Theorie, Oefeningen en Toetsen; elke sectie toont haar reeksindicatoren, ook wanneer er één reeks bestaat. In landschap gebruiken theorie, oefeningen en toetsinhoud twee kolommen waar het schermtype dit ondersteunt.

## 7. Taken

`Tasks` is een afzonderlijke route vanuit beide hoofdnavigators. De repository leest een versieerbare verpakte catalogus. De ViewModel:

- normaliseert en trimt de leraar-ID;
- filtert case-insensitive op die ID;
- lokaliseert titels en beschrijvingen met gecontroleerde fallback;
- sorteert deterministisch volgens taakvolgorde en ID;
- onderscheidt laden, geen leraar-ID, geen taken en laadfout.

De huidige takenroute is read-only. Een toekomstig servercontract mag de lokale repository vervangen zonder dat profiel- of schermmodellen externe credentials hoeven te kennen.

## 8. Specialized content

- Verhalen behouden hun eigen wereld/boek/hoofdstuk/pagina-model en eigen voortgang. De Galaxy toont thema-portalen, geen boek- of lesfasen.
- Puzzels behouden eigen renderer, moeilijkheid, XP en hervatgedrag. Ze worden niet tot Theorie–Oefeningen–Toetslessen omgevormd.
- De hoofd-Galaxy toont voor Logica & puzzels geen percentage, voortgangsboog of percentage in accessibility-semantics.

## 9. Android-buildarchitectuur

De actuele buildbasis is:

| Onderdeel | Contract |
|---|---|
| Android Gradle Plugin | 9.2.1 |
| Gradle wrapper | 9.4.1 |
| Kotlin | ingebouwd in AGP, taal-/compilerplugins 2.2.10 |
| KSP | 2.3.11 |
| Java/Kotlin JVM-doel | 11 |
| Android SDK | min 29, compile/target 35 |
| UI | Jetpack Compose + Navigation Compose |
| lokale opslag | Room via KSP + DataStore |
| serialisatie | kotlinx.serialization |
| offline Nederlandse TTS | sherpa-onnx, met gecontroleerde systeemfallback |

`org.jetbrains.kotlin.android`/`kotlin-android` wordt niet toegepast. De oude AGP-opt-outs voor ingebouwde Kotlin en de nieuwe DSL zijn verwijderd. Compose Compiler en Kotlin Serialization gebruiken dezelfde Kotlin-versie; Room-codegeneratie loopt uitsluitend via KSP.

## 10. Verificatie-ankers

Architectuurwijzigingen krijgen minimaal gerichte dekking voor:

- profielcreatie en ouder-pincode;
- parent-dashboard-draft, validatie en geselecteerde leerjaren;
- takenfilter en catalogusparsing;
- hoofd-/subcategorynavigatie en terugbestemming;
- gedeelde headers, systeeminsets en portret/landschap;
- Galaxy-model, formulegeometrie, layout en interactie;
- lesfasevolgorde en voortgangsmigratie;
- catalogus-/assetpariteit.

Een documentwijziging bewijst geen runtimegedrag. Bij wijzigingen aan navigatie, layout of buildstack volgen eerst gerichte unittests/compilatie en daarna, wanneer relevant en toegestaan, één incrementele debugbuild en een korte toesteltest.
