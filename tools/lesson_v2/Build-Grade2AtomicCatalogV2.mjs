import fs from "node:fs";
import path from "node:path";

const root = path.resolve(process.cwd());
const sourcePath = path.resolve(process.argv[2] || "tools/lesson_v2/source/grade2-canonical-source-v1.json");
const catalogPath = path.resolve(process.argv[3] || "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json");
const crosswalkPath = path.resolve(process.argv[4] || "docs/lesson_v2/reports/GRADE2_ATOMIC_CROSSWALK.json");
const indexPath = path.resolve(process.argv[5] || "docs/lesson_v2/reports/GRADE2_CATALOG_INDEX.json");
const questionBankPath = path.resolve(process.argv[6] || "docs/lesson_v2/reports/GRADE2_EDITORIAL_QUESTION_BANK_V2.json");

const source = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
const oldBroadIds = new Set([
  "lesson.g2.language.technical_reading.fluency",
  "lesson.g2.spelling.rules.punctuation_syllables_plural",
  "lesson.g2.language.writing.short_texts",
  "lesson.g2.language.listening.extended_instructions",
  "lesson.g2.math.operations.expansion",
  "lesson.g2.geometry.measurement_context",
  "lesson.g2.math.statistics.introduction",
  "lesson.g2.world.integrated_foundations",
  "lesson.g2.world.digital.media_safety",
  "lesson.g2.world.citizenship.emotions_conflicts",
]);
const seasonsPilotId = "lesson.g2.world.nature.seasons.order-and-signs";

const atom = (key, title, focus, mode = "concept", terms = []) => ({ key, title, focus, mode, terms });
const SPLITS = {
  "unit.g2.nl.technical_reading.woordfamilies-voor-en-achtervoegsels": [
    atom("woordfamilies-en-stammen", "Woordfamilies en stammen", "woordfamilies en hun gemeenschappelijke stam herkennen", "language_word_parts", ["stam", "woordfamilie"]),
    atom("voor-en-achtervoegsels", "Voor- en achtervoegsels", "voor- en achtervoegsels herkennen en hun invloed op een woord uitleggen", "language_word_parts", ["voorvoegsel", "achtervoegsel"]),
  ],
  "unit.g2.nl.technical_reading.complexe-klinker-en-medeklinkerpatronen": [
    atom("complexe-klinkerpatronen", "Klinkerpatronen aai, ooi, oei, eeuw en ieuw", "complexe klinkerpatronen volledig lezen", "language_patterns", ["aai", "ooi", "oei", "eeuw", "ieuw"]),
    atom("medeklinkerclusters", "Moeilijke medeklinkerclusters", "medeklinkerclusters van links naar rechts ontsleutelen", "language_patterns", ["cluster"]),
  ],
  "unit.g2.nl.handwriting.verbinden-ritme-en-spatiering": [
    atom("letters-verbinden", "Letters vloeiend verbinden", "letters met een leesbare doorgaande beweging verbinden", "handwriting", ["verbinding", "ritme"]),
    atom("spatiering-en-regelmaat", "Spaties en regelmaat", "gelijke woordspaties en een regelmatig schrift gebruiken", "handwriting", ["spatie", "regelmaat"]),
  ],
  "unit.g2.nl.spelling.eindklanken-ng-nk-en-ch-g": [
    atom("eindklanken-en-verlengproef", "Eindklanken controleren met de verlengproef", "een moeilijk hoorbare eindklank controleren door een woord te verlengen", "spelling", ["eindklank", "verlengproef"]),
    atom("ng-nk-ch-g", "ng, nk, ch en g schrijven", "de klanken ng, nk, ch en g in woorden onderscheiden en schrijven", "spelling", ["ng", "nk", "ch", "g"]),
  ],
  "unit.g2.nl.spelling.meervouden-en-verkleinwoorden": [
    atom("meervouden", "Meervouden met -en en -s", "het passende meervoud met -en of -s vormen", "spelling", ["meervoud", "en", "s"]),
    atom("verkleinwoorden", "Verkleinwoorden met -je en -tje", "een passend verkleinwoord met -je of -tje vormen", "spelling", ["verkleinwoord", "je", "tje"]),
  ],
  "unit.g2.nl.composition.zinnen-uitbreiden-en-verbinden": [
    atom("zinnen-uitbreiden", "Zinnen uitbreiden", "een kernzin uitbreiden met wie-, waar-, wanneer- en hoe-informatie", "language_composition", ["kernzin", "uitbreiding"]),
    atom("zinnen-verbinden", "Zinnen verbinden", "zinnen logisch verbinden met een passend verbindingswoord", "language_composition", ["verbindingswoord"]),
  ],
  "unit.g2.nl.oral.gericht-luisteren-en-mondeling-ordenen": [
    atom("gericht-luisteren", "Gericht luisteren", "met een luisterdoel hoofdzaak en details uit een boodschap halen", "language_listening", ["luisterdoel", "hoofdzaak", "detail"]),
    atom("mondeling-ordenen", "Mondeling geordend vertellen", "informatie in een duidelijke volgorde mondeling samenvatten", "language_speaking", ["verslag", "hoofdzaak", "detail"]),
  ],
  "unit.g2.nl.literature.personages-sfeer-en-taalspel": [
    atom("personages-en-sfeer", "Personages en sfeer", "uit tekst en beeld afleiden wie een personage is en welke sfeer een verhaal heeft", "literature", ["personage", "sfeer"]),
    atom("rijm-herhaling-humor", "Rijm, herhaling en humor", "taalspel in verhalen en gedichten herkennen en bespreken", "literature", ["rijm", "herhaling", "humor"]),
  ],
  "unit.g2.math.numbers.getalbegrip-en-plaatswaarde-tot-100": [
    atom("tientallen-en-eenheden", "Tientallen en eenheden tot 100", "getallen tot 100 opbouwen met tientallen en eenheden", "math_place_value", ["tiental", "eenheid"]),
    atom("plaatswaarde-en-cijferwaarde", "Plaatswaarde en cijferwaarde", "de waarde van elk cijfer in een getal tot 100 bepalen", "math_digit_value", ["plaatswaarde", "cijferwaarde"]),
  ],
  "unit.g2.math.numbers.tellen-in-sprongen-en-getalpatronen": [
    atom("tellen-in-sprongen", "Tellen in sprongen van 2, 5 en 10", "vooruit en achteruit tellen in vaste sprongen", "math_skip_count", ["sprong", "veelvoud"]),
    atom("getalpatronen", "Getalpatronen aanvullen", "de regel van een getallenrij herkennen en voortzetten", "math_number_pattern", ["patroon", "rij"]),
  ],
  "unit.g2.math.operations.optellen-en-aftrekken-tot-100-zonder-en-met-brug": [
    atom("optellen-zonder-brug", "Optellen tot 100 zonder brug", "optellen zonder een tiental te overschrijden", "math_add_no_bridge", ["rijgen", "splitsen"]),
    atom("aftrekken-zonder-brug", "Aftrekken tot 100 zonder brug", "aftrekken zonder een tiental te overschrijden", "math_sub_no_bridge", ["rijgen", "splitsen"]),
    atom("optellen-met-brug", "Optellen tot 100 met brug", "optellen via het volgende volle tiental", "math_add_bridge", ["brug", "splitsen"]),
    atom("aftrekken-met-brug", "Aftrekken tot 100 met brug", "aftrekken via het vorige volle tiental", "math_sub_bridge", ["brug", "compenseren"]),
  ],
  "unit.g2.math.operations.delen-als-verdelen-en-groeperen": [
    atom("eerlijk-verdelen", "Delen als eerlijk verdelen", "een hoeveelheid eerlijk over een aantal groepen verdelen", "math_divide_share", ["delen", "deler", "quotiënt"]),
    atom("groepjes-maken", "Delen door groepjes te maken", "bepalen hoeveel gelijke groepjes in een hoeveelheid passen", "math_divide_group", ["delen", "rest"]),
  ],
  "unit.g2.math.measurement.standaardmaten-voor-lengte-massa-en-inhoud": [
    atom("lengte-cm-m", "Lengte meten in centimeter en meter", "een geschikte lengtemaat kiezen, meten en vergelijken", "math_length", ["centimeter", "meter"]),
    atom("massa-g-kg", "Massa meten in gram en kilogram", "een geschikte massamaat kiezen, meten en vergelijken", "math_mass", ["gram", "kilogram"]),
    atom("inhoud-liter", "Inhoud meten in liter", "inhoud met de liter schatten, meten en vergelijken", "math_capacity", ["liter"]),
  ],
  "unit.g2.math.measurement.klok-duur-en-kalender": [
    atom("kloklezen", "Kloklezen tot op een kwartier", "hele uren, halve uren en kwartieren lezen", "math_clock", ["kwartier", "minuut"]),
    atom("tijdsduur", "Begintijd, eindtijd en duur", "een eenvoudige tijdsduur bepalen", "math_duration", ["begintijd", "eindtijd", "duur"]),
    atom("kalender", "Rekenen met de kalender", "dagen en data in een kalender opzoeken en ordenen", "math_calendar", ["duur"]),
  ],
  "unit.g2.math.measurement.geld-rekenen-tot-100-euro": [
    atom("bedragen-samenstellen", "Bedragen tot 100 euro samenstellen", "euro- en centstukken combineren tot een gevraagd bedrag", "math_money_total", ["prijs", "euro", "cent"]),
    atom("betalen-en-wisselgeld", "Betalen en wisselgeld", "bepalen hoeveel je betaalt en terugkrijgt", "math_money_change", ["prijs", "wisselgeld", "euro"]),
  ],
  "unit.g2.math.geometry.eigenschappen-van-figuren-en-lichamen": [
    atom("vlakke-figuren", "Eigenschappen van vlakke figuren", "zijden en hoekpunten van vlakke figuren onderzoeken", "math_shapes", ["zijde", "hoekpunt"]),
    atom("ruimtefiguren", "Eigenschappen van ruimtefiguren", "vlakken en ribben van ruimtefiguren onderzoeken", "math_solids", ["ribbe", "vlak"]),
  ],
  "unit.g2.math.geometry.routes-rooster-en-spiegeling": [
    atom("routes-op-een-rooster", "Routes op een rooster", "een route met richting en afstand lezen en beschrijven", "math_routes", ["route", "rooster", "afstand"]),
    atom("spiegeling", "Spiegelen rond een spiegelas", "een figuur ten opzichte van een spiegelas herkennen en aanvullen", "math_mirror", ["spiegelas", "afstand"]),
  ],
  "unit.g2.math.data.tabellen-en-staafdiagrammen": [
    atom("tabellen-lezen", "Tabellen lezen", "categorieën en frequenties in een tabel opzoeken en vergelijken", "math_table", ["categorie", "frequentie"]),
    atom("staafdiagrammen-lezen", "Staafdiagrammen lezen", "assen en staven gebruiken om gegevens af te lezen en te vergelijken", "math_bar_chart", ["staaf", "as", "frequentie"]),
  ],
  "unit.g2.math.problem_solving.een-en-tweestapsproblemen": [
    atom("eenstapsproblemen", "Rekenverhalen in één stap", "een passende bewerking kiezen en het antwoord controleren", "math_word_one", ["plan", "bewerking", "redelijk"]),
    atom("tweestapsproblemen", "Rekenverhalen in twee stappen", "een tussenstap plannen en twee bewerkingen juist uitvoeren", "math_word_two", ["tussenstap", "plan", "bewerking"]),
  ],
  "unit.g2.science_tech.organisms.levenscycli-van-plant-en-dier": [
    atom("levenscyclus-plant", "De levenscyclus van een plant", "de stadia van zaad tot volwassen plant ordenen", "science_cycle", ["levenscyclus", "jong", "volwassen"]),
    atom("levenscyclus-dier", "De levenscyclus van een dier", "de stadia van ei of jong dier tot volwassen dier ordenen", "science_cycle", ["ei", "larve", "jong", "volwassen"]),
  ],
  "unit.g2.science_tech.human_body.gezond-groeien-en-bewegen": [
    atom("gezonde-routines", "Gezond groeien met goede routines", "hygiëne, slaap, voeding en rust als gezonde routines herkennen", "health", ["gezond", "hygiëne", "slaap", "rust"]),
    atom("bewegen-en-herstellen", "Bewegen, inspannen en herstellen", "bewegen afwisselen met passende rust en herstel", "health", ["bewegen", "rust"]),
  ],
  "unit.g2.science_tech.forces_energy.licht-geluid-en-eenvoudige-beweging": [
    atom("licht-en-schaduw", "Licht en schaduw", "lichtbronnen herkennen en voorspellen hoe een schaduw ontstaat", "science_light", ["lichtbron", "schaduw"]),
    atom("geluid-en-trilling", "Geluid en trilling", "geluidsbronnen en trillingen onderzoeken", "science_sound", ["trilling", "geluidsbron"]),
    atom("beweging-en-wrijving", "Beweging en wrijving", "onderzoeken hoe wrijving een beweging beïnvloedt", "science_motion", ["wrijving"]),
  ],
  "unit.g2.science_tech.inquiry_design.onderzoeken-en-ontwerpen-met-een-eerlijke-vergelijking": [
    atom("eerlijk-onderzoek", "Een eerlijke vergelijking onderzoeken", "één factor veranderen, resultaten noteren en een conclusie trekken", "science_inquiry", ["onderzoeksvraag", "voorspelling", "test", "resultaat", "conclusie"]),
    atom("ontwerpen-en-verbeteren", "Ontwerpen, testen en verbeteren", "een oplossing maken, testen en doelgericht verbeteren", "science_design", ["test", "resultaat", "conclusie"]),
  ],
  "unit.g2.geo.human.verkeer-en-voorzieningen": [
    atom("verkeer-en-veilige-route", "Verkeer en een veilige route", "een veilige route en passend vervoer kiezen", "geography", ["vervoer", "route", "veilig"]),
    atom("voorzieningen-in-de-buurt", "Voorzieningen in de buurt", "voorzieningen en hun bereikbaarheid op een kaart herkennen", "geography", ["voorziening", "halte"]),
  ],
  "unit.g2.history.chronology.generaties-en-eeuwen": [
    atom("generaties", "Generaties ordenen", "personen en gebeurtenissen bij een generatie plaatsen", "history", ["generatie", "oud", "recent"]),
    atom("jaartallen-en-eeuwen", "Jaartallen en eeuwen", "een jaartal bij de juiste eeuw plaatsen", "history", ["jaartal", "eeuw"]),
  ],
  "unit.g2.arts.visual.kleurmenging-patroon-en-ruimte": [
    atom("kleurmenging", "Kleuren mengen", "primaire kleuren mengen en het resultaat voorspellen", "arts", ["primair", "mengen"]),
    atom("patronen-maken", "Visuele patronen maken", "een patroon herkennen, voortzetten en ontwerpen", "arts", ["patroon"]),
    atom("ruimte-en-overlap", "Ruimte tonen met overlap", "voorgrond en overlap gebruiken om ruimte te tonen", "arts", ["overlap", "voorgrond"]),
  ],
  "unit.g2.arts.music.maat-ritme-en-melodische-richting": [
    atom("maat-en-ritme", "Maat en ritme", "een regelmatige maat en accenten horen en uitvoeren", "music", ["maat", "accent"]),
    atom("melodische-richting", "Melodieën die stijgen en dalen", "de richting van een melodie horen en weergeven", "music", ["melodie", "stijgen", "dalen"]),
  ],
  "unit.g2.pe.movement.combinaties-ritme-en-evenwicht": [
    atom("bewegingscombinaties", "Bewegingscombinaties", "huppelen, draaien en overgangen veilig combineren", "physical", ["huppelen", "draaien", "overgang"]),
    atom("evenwicht-op-niveaus", "Evenwicht op verschillende niveaus", "evenwicht bewaren op lage en hogere niveaus", "physical", ["niveau", "evenwicht"]),
  ],
  "unit.g2.pe.gymnastics.klimmen-zwaaien-en-omkeren": [
    atom("veilig-klimmen", "Veilig klimmen", "met steunpunten en afspraken veilig klimmen", "physical", ["klimmen", "steun", "hulp"]),
    atom("zwaaien", "Gecontroleerd zwaaien", "veilig starten, zwaaien en stoppen", "physical", ["zwaaien", "hulp"]),
    atom("omkeren", "Veilig omkeren", "met steun en hulp een eenvoudige omkering uitvoeren", "physical", ["omkeren", "steun", "hulp"]),
  ],
  "unit.g2.pe.ball_games.dribbelen-passen-en-ontvangen": [
    atom("dribbelen", "Een bal dribbelen", "een bal dicht bij het lichaam gecontroleerd dribbelen", "physical", ["dribbelen", "controleren"]),
    atom("passen-en-ontvangen", "Passen, vrijlopen en ontvangen", "gericht passen, vrijlopen en een bal gecontroleerd ontvangen", "physical", ["passen", "ontvangen", "vrijlopen"]),
  ],
  "unit.g2.pe.swimming.watergewenning-en-zelfredzaamheid": [
    atom("watergewenning-en-ademen", "Watergewenning en ademen", "veilig in water bewegen en rustig uitademen", "physical", ["ademen", "waterveilig", "instappen"]),
    atom("drijven-en-veilig-uitstappen", "Drijven en veilig uitstappen", "kort drijven en veilig uit het water komen", "physical", ["drijven", "waterveilig", "uitstappen"]),
  ],
  "unit.g2.ict.devices_data.bestanden-mappen-en-eenvoudige-data": [
    atom("bestanden-en-mappen", "Bestanden benoemen, bewaren en terugvinden", "bestanden met een duidelijke naam in een passende map bewaren", "digital", ["bestand", "map", "naam", "terugvinden"]),
    atom("eenvoudige-data", "Eenvoudige gegevens ordenen", "gegevens in een eenvoudige lijst of tabel invoeren en ordenen", "digital", ["gegevens"]),
  ],
  "unit.g2.ict.coding.patronen-herhaling-en-debuggen": [
    atom("herhaling-en-lussen", "Herhaling en lussen in code", "een herhaald patroon als lus beschrijven", "coding", ["herhalen", "lus", "code", "uitvoer"]),
    atom("debuggen", "Fouten in code opsporen", "code stap voor stap controleren en een fout verbeteren", "coding", ["debuggen", "code", "uitvoer"]),
  ],
  "unit.g2.attitudes.learning.werkgeheugen-en-handige-routines": [
    atom("werkgeheugen-ontlasten", "Je werkgeheugen helpen", "informatie ordenen en in kleine stappen vasthouden", "learning", ["onthouden", "stap", "ordenen"]),
    atom("focus-en-herhalen", "Focus en handige herhaalroutines", "met een vaste routine gericht werken en belangrijke informatie herhalen", "learning", ["herhalen", "focus"]),
  ],
  "unit.g2.fr.speaking.naam-gevoel-en-voorkeur": [
    atom("naam-en-gevoel", "Je naam en gevoel zeggen in het Frans", "je naam noemen en eenvoudig zeggen hoe het gaat", "french", ["je m'appelle", "ça va", "oui", "non"]),
    atom("voorkeur-zeggen", "Een voorkeur zeggen in het Frans", "met j'aime eenvoudig zeggen wat je graag hebt", "french", ["j'aime", "oui", "non"]),
  ],
};

const broadAliasTargets = {
  "lesson.g2.language.technical_reading.fluency": "unit.g2.nl.technical_reading.meerlettergrepige-woorden-ontsleutelen",
  "lesson.g2.spelling.rules.punctuation_syllables_plural": "unit.g2.nl.spelling.open-en-gesloten-lettergrepen",
  "lesson.g2.language.writing.short_texts": "unit.g2.nl.composition.zinnen-uitbreiden-en-verbinden",
  "lesson.g2.language.listening.extended_instructions": "unit.g2.nl.oral.gericht-luisteren-en-mondeling-ordenen",
  "lesson.g2.math.operations.expansion": "unit.g2.math.numbers.getalbegrip-en-plaatswaarde-tot-100",
  "lesson.g2.geometry.measurement_context": "unit.g2.math.measurement.standaardmaten-voor-lengte-massa-en-inhoud",
  "lesson.g2.math.statistics.introduction": "unit.g2.math.data.tabellen-en-staafdiagrammen",
  "lesson.g2.world.integrated_foundations": "unit.g2.science_tech.organisms.levenscycli-van-plant-en-dier",
  "lesson.g2.world.digital.media_safety": "unit.g2.ict.devices_data.bestanden-mappen-en-eenvoudige-data",
  "lesson.g2.world.citizenship.emotions_conflicts": "unit.g2.attitudes.social_emotional.impuls-keuze-en-gevolg",
};

const stable = (value) => String(value || "field").normalize("NFKD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "field";
const text = (nl, fr = nl, en = nl) => ({ visible: { "nl-BE": nl, "fr-BE": fr, "en-US": en } });
const localeStatus = (base, locale) => ({
  id: `${base}.${stable(locale)}`,
  status: locale === "nl-BE" ? "DEFAULT" : "NEEDS_REVIEW",
  comment: locale === "nl-BE" ? "Brontaal nl-BE." : "Tijdelijke nl-BE-fallback; vertaling inhoudelijk en didactisch nakijken vóór publicatie.",
});
const statuses = (base) => Object.fromEntries(["nl-BE", "fr-BE", "en-US"].map((locale) => [locale, localeStatus(base, locale)]));
const sourceLessonsByUnit = Map.groupBy(source.sourceLessons, (lesson) => lesson.unitId);

function atomsFor(unit) {
  return SPLITS[unit.unitId] || [atom(stable(unit.title), unit.title, unit.coreSkill, modeFor(unit), unit.vocabulary)];
}

function modeFor(unit) {
  const id = unit.unitId;
  if (id.includes("rekenfeiten")) return "math_facts20";
  if (id.includes("vermenigvuldigen")) return "math_multiply";
  if (id.includes("fractions")) return "math_fractions";
  if (id.includes("water-en-toestands")) return "science_water";
  if (unit.subjectId === "math") return "math_concept";
  if (unit.subjectId === "nl") return unit.domainId === "spelling" ? "spelling" : "language";
  if (unit.subjectId === "fr") return "french";
  if (unit.subjectId === "pe") return "physical";
  if (unit.subjectId === "arts") return "arts";
  if (unit.subjectId === "history") return "history";
  if (unit.subjectId === "geo") return "geography";
  if (unit.subjectId === "ict") return "digital";
  if (unit.subjectId === "science_tech") return "science";
  return "concept";
}

function location(unit, atomic) {
  const id = unit.unitId;
  if (unit.subjectId === "math") {
    if (id.includes(".numbers.")) return ["number_sense", "math.number_place_value", "MATH", "number_sequences"];
    if (id.includes(".operations.")) return ["number_sense", id.includes("vermenigvuldigen") || id.includes("delen-") ? "math.multiplication_division" : "math.addition_subtraction", "MATH", "mixed_arithmetic"];
    if (id.includes(".fractions.")) return ["number_sense", "math.fractions", "MATH", "fractions"];
    if (id.includes(".measurement.geld")) return ["number_sense", "math.money_financial_literacy", "MATH", "money"];
    if (id.includes(".measurement.klok")) return ["measurement_geometry", "geometry.time_calendar", "GEOMETRY", "time"];
    if (atomic.mode === "math_mass") return ["measurement_geometry", "geometry.mass_weight", "GEOMETRY", "measurement"];
    if (atomic.mode === "math_capacity") return ["measurement_geometry", "geometry.capacity_volume", "GEOMETRY", "measurement"];
    if (id.includes(".measurement.")) return ["measurement_geometry", "geometry.length_perimeter_scale", "GEOMETRY", "measurement"];
    if (atomic.mode === "math_mirror") return ["measurement_geometry", "geometry.symmetry_patterns_transformations", "GEOMETRY", "geometry_shapes"];
    if (atomic.mode === "math_routes") return ["measurement_geometry", "geometry.spatial_orientation_coordinates", "GEOMETRY", "geometry_shapes"];
    if (id.includes(".geometry.")) return ["measurement_geometry", "geometry.shapes_lines_angles", "GEOMETRY", "geometry_shapes"];
    if (id.includes(".data.")) return ["number_sense", "math.tables_graphs_statistics", "MATH", "statistics"];
    return ["number_sense", "math.problem_solving", "MATH", "mixed_arithmetic"];
  }
  if (unit.subjectId === "nl") {
    if (unit.domainId === "technical_reading") return ["language_skills", "language.technical_reading", "READING", "reading"];
    if (unit.domainId === "comprehension") return ["language_skills", "language.reading_comprehension", "READING", "reading"];
    if (unit.domainId === "handwriting") return ["language_skills", "language.writing_expression", "WRITING", "letter_tracing"];
    if (unit.domainId === "spelling") return ["spelling_grammar", "spelling.rules_word_patterns", "WRITING", "spelling"];
    if (unit.domainId === "composition") return ["language_skills", "language.writing_expression", "WRITING", "sentence_order"];
    if (unit.domainId === "oral") return ["language_skills", atomic.mode === "language_speaking" ? "language.speaking_interaction" : "language.listening_comprehension", "READING", atomic.mode === "language_speaking" ? "speaking" : "listening"];
    return ["language_skills", "language.literature_experience", "READING", "reading"];
  }
  if (unit.subjectId === "fr") return ["language_skills", unit.domainId === "speaking" ? "language.speaking_interaction" : "language.listening_comprehension", "READING", unit.domainId === "speaking" ? "speaking" : "listening"];
  if (unit.subjectId === "science_tech") {
    if (unit.domainId === "organisms") return ["world_orientation", "world.nature_living_world", "WORLD_KNOWLEDGE", "nature"];
    if (unit.domainId === "human_body") return ["world_orientation", "world.body_health", "WORLD_KNOWLEDGE", "human_body"];
    return ["world_orientation", "world.science_inquiry", "WORLD_KNOWLEDGE", "science"];
  }
  if (unit.subjectId === "geo") return ["world_orientation", unit.domainId === "environment" ? "world.environment_sustainability" : unit.domainId === "human" ? "world.traffic_mobility_safety" : "world.geography_space", "WORLD_KNOWLEDGE", unit.domainId === "environment" ? "environment_sustainability" : unit.domainId === "human" ? "traffic_safety" : "geography"];
  if (unit.subjectId === "history") return ["world_orientation", "world.history_time", "WORLD_KNOWLEDGE", "history"];
  if (unit.subjectId === "ict") return ["world_orientation", "world.digital_media_literacy", "WORLD_KNOWLEDGE", "technology"];
  if (unit.subjectId === "attitudes") return ["world_orientation", "world.citizenship_social", "WORLD_KNOWLEDGE", "citizenship_society"];
  if (unit.subjectId === "pe") return ["world_orientation", "world.body_health", "WORLD_KNOWLEDGE", "human_body"];
  return ["world_orientation", "world.daily_life_self_reliance", "WORLD_KNOWLEDGE", "arts_culture"];
}

function concreteExample(mode, index, atomic) {
  const examples = {
    math_place_value: ["64 bestaat uit 6 tientallen en 4 eenheden.", "8 tientallen en 3 eenheden vormen samen 83."],
    math_digit_value: ["In 47 is de 4 vier tientallen waard: 40.", "In 72 is de 2 twee eenheden waard: 2."],
    math_skip_count: ["Tel per 5: 20, 25, 30, 35.", "Tel achteruit per 2: 18, 16, 14, 12."],
    math_number_pattern: ["12, 15, 18, 21: de regel is telkens 3 erbij.", "40, 35, 30, 25: de regel is telkens 5 eraf."],
    math_add_no_bridge: ["32 + 6 = 38: de eenheden blijven onder 10.", "41 + 25 = 66: 40 + 20 en 1 + 5."],
    math_sub_no_bridge: ["48 - 5 = 43: er is geen brug over het tiental.", "76 - 24 = 52: 70 - 20 en 6 - 4."],
    math_add_bridge: ["38 + 7 = 38 + 2 + 5 = 45.", "56 + 28 = 56 + 20 + 4 + 4 = 84."],
    math_sub_bridge: ["42 - 7 = 42 - 2 - 5 = 35.", "73 - 28 = 73 - 20 - 3 - 5 = 45."],
    math_facts20: ["8 + 7 = 15, dus 15 - 7 = 8.", "9 + 6 = 15; het omkeerfeit is 6 + 9 = 15."],
    math_multiply: ["4 groepjes van 3 zijn 4 × 3 = 12.", "5 rijen met 2 stippen zijn samen 10 stippen."],
    math_divide_share: ["12 fiches over 3 kinderen: elk kind krijgt 4.", "15 blokken over 5 dozen: 3 per doos."],
    math_divide_group: ["In 12 passen 3 groepjes van 4.", "14 voorwerpen in groepjes van 3 geeft 4 groepjes en 2 rest."],
    math_fractions: ["Verdeel een pizza in 4 gelijke delen: elk deel is een kwart.", "Drie kinderen delen eerlijk: elk krijgt een derde."],
    math_length: ["Een potlood meet je in centimeter; een kamer in meter.", "Leg de nul van de meetlat aan het begin van het voorwerp."],
    math_mass: ["Een appel meet je in gram; een kind in kilogram.", "1 kilogram is zwaarder dan 600 gram."],
    math_capacity: ["Een fles kan 1 liter water bevatten.", "Vergelijk inhoud met dezelfde maatbeker."],
    math_clock: ["Kwart over drie is 3:15.", "Kwart voor zes is 5:45."],
    math_duration: ["Van 14:00 tot 14:30 duurt 30 minuten.", "Start 9:15, duur 45 minuten: einde 10:00."],
    math_calendar: ["Drie dagen na maandag is donderdag.", "Op een kalender komt 16 één week na 9."],
    math_money_total: ["€ 4,50 kan met € 2 + € 2 + 50 cent.", "€ 12 + € 7 = € 19."],
    math_money_change: ["Je betaalt € 20 voor € 13: je krijgt € 7 terug.", "Controle: prijs + wisselgeld = betaald bedrag."],
    math_shapes: ["Een driehoek heeft 3 zijden en 3 hoekpunten.", "Een rechthoek heeft 4 zijden en 4 hoekpunten."],
    math_solids: ["Een kubus heeft 6 vierkante vlakken.", "Een bol heeft geen ribben en kan rollen."],
    math_routes: ["Ga 2 vakken noord en 1 vak oost.", "Tel de roostervakken om de afstand te vergelijken."],
    math_mirror: ["Elk punt ligt even ver van de spiegelas als zijn spiegelbeeld.", "Een punt 2 vakken links komt 2 vakken rechts van de as."],
    math_table: ["In de tabel staat bij appels de frequentie 6: er zijn 6 appels.", "Vergelijk twee rijen om te zien welke categorie het grootst is."],
    math_bar_chart: ["Een staaf tot 8 betekent frequentie 8.", "Het verschil tussen een staaf van 9 en 5 is 4."],
    math_word_one: ["Er zijn 14 vogels en 5 vliegen weg: 14 - 5 = 9.", "Controleer of het antwoord past bij het verhaal."],
    math_word_two: ["24 kinderen, 6 gaan weg en 3 komen erbij: 24 - 6 + 3 = 21.", "Schrijf eerst de tussenstap en daarna de tweede bewerking."],
  };
  const choices = examples[mode];
  if (choices) return choices[index % choices.length];
  return index === 0
    ? `Bij ${atomic.title.toLowerCase()} gebruik je eerst het kernmodel en benoem je wat je waarneemt.`
    : `Daarna pas je de aanpak toe in een nieuw voorbeeld en controleer je het resultaat met de succescriteria.`;
}

function wrongStatements(unit, atomic, subjectPool) {
  const clean = (value) => String(value).replace(/[.!?]+$/, "");
  const other = subjectPool.filter((value) => value !== unit.coreKnowledge).slice(0, 3);
  return [
    `Je hoeft bij ${atomic.title.toLowerCase()} niets te controleren.`,
    `Alleen snel werken is belangrijk; de gekozen aanpak doet er niet toe.`,
    other[0] ? clean(other[0]) : `Je gebruikt altijd dezelfde gok, ongeacht de informatie.`,
  ];
}

function mathQuestion(mode, ordinal) {
  const n = ordinal + 1;
  const a = 10 + ((n * 7) % 35);
  const b = 2 + ((n * 3) % 8);
  const options = (answer) => [answer, answer + 1, Math.max(0, answer - 1), answer + 10].map(String);
  switch (mode) {
    case "math_place_value": { const tens = 2 + (n % 7); const ones = n % 10; return [`Welk getal bestaat uit ${tens} tientallen en ${ones} eenheden?`, String(tens * 10 + ones), options(tens * 10 + ones)]; }
    case "math_digit_value": { const tens = 2 + (n % 7); const ones = (n * 3) % 10; return [`Wat is de waarde van het cijfer ${tens} in ${tens}${ones}?`, String(tens * 10), [String(tens * 10), String(tens), String(ones), String(tens * 10 + ones)]]; }
    case "math_skip_count": { const step = [2, 5, 10][n % 3]; const start = step * (1 + (n % 5)); return [`Vul aan: ${start}, ${start + step}, ${start + 2 * step}, …`, String(start + 3 * step), options(start + 3 * step)]; }
    case "math_number_pattern": { const step = 2 + (n % 5); const start = 5 + n; return [`Welk getal volgt? ${start}, ${start + step}, ${start + 2 * step}, …`, String(start + 3 * step), options(start + 3 * step)]; }
    case "math_add_no_bridge": { const left = 20 + (n % 5) * 10 + (n % 3); const right = 2 + (n % 5); return [`Bereken ${left} + ${right}.`, String(left + right), options(left + right)]; }
    case "math_sub_no_bridge": { const left = 30 + (n % 5) * 10 + 8; const right = 2 + (n % 6); return [`Bereken ${left} - ${right}.`, String(left - right), options(left - right)]; }
    case "math_add_bridge": { const digit = 2 + ((n * 3) % 7); const left = (2 + (n % 6)) * 10 + digit; const gap = 10 - digit; const right = gap + 1 + (n % Math.max(1, digit - 1)); return [`Bereken ${left} + ${right} via het volgende tiental.`, String(left + right), options(left + right)]; }
    case "math_sub_bridge": { const digit = 1 + ((n * 5) % 8); const left = (3 + (n % 6)) * 10 + digit; const right = digit + 1 + (n % Math.max(1, 9 - digit)); return [`Bereken ${left} - ${right} via het vorige tiental.`, String(left - right), options(left - right)]; }
    case "math_facts20": { const left = 3 + (n % 7); const right = 11 - left + (n % 5); return [`Bereken ${left} + ${right}.`, String(left + right), options(left + right)]; }
    case "math_multiply": { const groups = 2 + (n % 5); const per = 2 + ((n * 3) % 5); return [`Er zijn ${groups} gelijke groepjes van ${per}. Hoeveel zijn er samen?`, String(groups * per), options(groups * per)]; }
    case "math_divide_share": { const children = 2 + (n % 4); const each = 2 + ((n * 2) % 5); const total = children * each; return [`Verdeel ${total} fiches eerlijk over ${children} kinderen. Hoeveel krijgt elk kind?`, String(each), options(each)]; }
    case "math_divide_group": { const size = 2 + (n % 4); const groups = 2 + ((n * 2) % 5); const total = size * groups; return [`Hoeveel groepjes van ${size} passen precies in ${total}?`, String(groups), options(groups)]; }
    case "math_clock": { const hour = 1 + (n % 10); const minutes = [0, 15, 30, 45][n % 4]; return [`Welke digitale tijd hoort bij ${minutes === 0 ? `${hour} uur` : minutes === 15 ? `kwart over ${hour}` : minutes === 30 ? `half ${hour + 1}` : `kwart voor ${hour + 1}`}?`, `${hour}:${String(minutes).padStart(2, "0")}`, [`${hour}:${String(minutes).padStart(2, "0")}`, `${hour}:10`, `${hour}:30`, `${hour + 1}:${String(minutes).padStart(2, "0")}`]]; }
    case "math_duration": { const start = 8 + (n % 8); const duration = [15, 30, 45, 60][n % 4]; const endMinutes = start * 60 + duration; const answer = `${Math.floor(endMinutes / 60)}:${String(endMinutes % 60).padStart(2, "0")}`; return [`Een activiteit start om ${start}:00 en duurt ${duration} minuten. Wanneer eindigt ze?`, answer, [answer, `${start}:15`, `${start}:30`, `${start + 2}:00`]]; }
    case "math_money_total": { const euros = 2 + (n % 15); const extra = 1 + ((n * 3) % 9); return [`Je koopt iets van € ${euros} en iets van € ${extra}. Hoeveel betaal je samen?`, `€ ${euros + extra}`, [`€ ${euros + extra}`, `€ ${euros + extra + 1}`, `€ ${Math.abs(euros - extra)}`, `€ ${euros * extra}`]]; }
    case "math_money_change": { const price = 5 + (n % 15); const paid = price <= 10 ? 20 : 50; return [`Je betaalt € ${paid} voor een prijs van € ${price}. Hoeveel wisselgeld krijg je?`, `€ ${paid - price}`, [`€ ${paid - price}`, `€ ${price}`, `€ ${paid + price}`, `€ ${paid - price - 1}`]]; }
    case "math_word_one": { const total = 12 + (n % 18); const away = 2 + (n % 7); return [`Er zijn ${total} knikkers. ${away} knikkers worden weggegeven. Hoeveel blijven er?`, String(total - away), options(total - away)]; }
    case "math_word_two": { const total = 20 + (n % 20); const away = 2 + (n % 6); const added = 1 + (n % 5); const answer = total - away + added; return [`Er zijn ${total} kinderen. ${away} gaan weg en ${added} komen erbij. Hoeveel zijn er nu?`, String(answer), options(answer)]; }
    default: return null;
  }
}

function editorialQuestion({ lessonId, stageId, setId, unit, atomic, index, subjectPool }) {
  const id = `${setId}.question.${index + 1}`;
  const math = mathQuestion(atomic.mode, index);
  const terms = atomic.terms.length ? atomic.terms : unit.vocabulary;
  const model = unit.models[index % Math.max(1, unit.models.length)] || "stappenplan";
  let prompt;
  let correct;
  let choices;
  let explanation;
  if (math) {
    [prompt, correct, choices] = math;
    explanation = concreteExample(atomic.mode, index, atomic);
  } else {
    const variant = index % 6;
    if (variant === 0) {
      prompt = `Welke uitspraak past bij ${atomic.title.toLowerCase()}?`;
      correct = unit.coreKnowledge;
      choices = [correct, ...wrongStatements(unit, atomic, subjectPool)];
    } else if (variant === 1) {
      prompt = `Wat moet je kunnen bij ${atomic.title.toLowerCase()}?`;
      correct = atomic.focus;
      choices = [correct, `alleen het woord ${terms[0] || atomic.title} overschrijven`, `zonder plan meteen een antwoord raden`, `een andere taak uitvoeren zonder verband met het doel`];
    } else if (variant === 2) {
      const term = terms[index % Math.max(1, terms.length)] || atomic.title;
      prompt = `Welk kernwoord hoort bij deze les: ${atomic.title}?`;
      correct = term;
      choices = [term, ...unit.vocabulary.filter((value) => value !== term), ...["gok", "versiering", "toeval"]].slice(0, 4);
    } else if (variant === 3) {
      prompt = `Welk model helpt je het best bij ${atomic.title.toLowerCase()}?`;
      correct = model;
      choices = [model, ...unit.models.filter((value) => value !== model), "willekeurige gok"].slice(0, 4);
    } else if (variant === 4) {
      prompt = `Welke aanpak vermijdt een veelgemaakte fout bij ${atomic.title.toLowerCase()}?`;
      correct = atomic.focus;
      choices = [correct, "alle informatie negeren", "alleen snelheid controleren", "stoppen vóór je het resultaat controleert"];
    } else {
      prompt = `Je krijgt een nieuw voorbeeld over ${atomic.title.toLowerCase()}. Wat doe je?`;
      correct = `Ik gebruik ${model}, voer de stappen uit en controleer mijn resultaat.`;
      choices = [correct, "Ik neem het eerste antwoord zonder bewijs.", "Ik verander het doel van de opdracht.", "Ik sla uitleg en controle over."];
    }
    explanation = `${unit.coreKnowledge} Daarom leer je ${atomic.focus}.`;
  }
  while (choices.length < 4) choices.push(`Niet passend bij ${atomic.title.toLowerCase()}`);
  choices = [...new Set(choices)].slice(0, 4);
  while (choices.length < 4) choices.push(`Andere onjuiste keuze ${choices.length}`);
  const rotation = index % 4;
  choices = [...choices.slice(rotation), ...choices.slice(0, rotation)];
  const answerObjects = choices.map((value, answerIndex) => {
    const answerId = `${id}.answer.${answerIndex + 1}`;
    const fieldId = `${answerId}.field.value`;
    return {
      id: answerId,
      value,
      correct: value === correct,
      displays: { "nl-BE": value, "fr-BE": value, "en-US": value },
      speaks: { "nl-BE": value, "fr-BE": value, "en-US": value },
      translationStatus: statuses(`${answerId}.translation-status`),
      contentFieldId: fieldId,
    };
  });
  return {
    id, lessonId, stageId, exerciseSetId: setId,
    type: "multiple_choice",
    question: prompt,
    translations: Object.fromEntries(["nl-BE", "fr-BE", "en-US"].map((locale) => [locale, { question: prompt, explanation }])),
    generated: true,
    automaticGenerationEnabled: true,
    generatorVersion: "v2",
    origin: "GENERATED",
    validationStatus: "NEEDS_REVIEW",
    publicationStatus: "DRAFT_REVIEW_REQUIRED",
    translationStatus: statuses(`${id}.translation-status`),
    editorComments: { "nl-BE": "", "fr-BE": "Vertaling na te kijken.", "en-US": "Vertaling na te kijken." },
    answers: answerObjects,
    acceptedAnswers: [],
    explanation,
    contentFieldIds: { question: `${id}.field.question`, explanation: `${id}.field.explanation` },
  };
}

function contentField(id, ownerId, pathValue) {
  return {
    id, ownerId, path: pathValue,
    translations: ["nl-BE", "fr-BE", "en-US"].map((locale) => ({
      id: `${id}.translation.${stable(locale)}`,
      locale,
      status: locale === "nl-BE" ? "DEFAULT" : "NEEDS_REVIEW",
      comment: locale === "nl-BE" ? "Brontaal nl-BE." : "nl-BE-fallback; vertaling nakijken.",
      ...(locale === "nl-BE" ? {} : { fallbackLocale: "nl-BE" }),
    })),
  };
}

function buildSet({ lessonId, stageId, setKey, titleNl, sequence, purpose, count, atomic, unit, subjectPool, hints, feedback, inject }) {
  const id = `${lessonId}.set.${setKey}`;
  const phase = purpose === "GUIDED" || purpose === "FADING_SUPPORT" ? "guided" : "checkpoint";
  const offsetBySet = { "practise-1": 0, "practise-2": 10, "practise-3": 20, "application-1": 30, "test-1": 40 };
  const questionOffset = offsetBySet[setKey] || 0;
  const editorialItems = Array.from({ length: count }, (_, index) => editorialQuestion({ lessonId, stageId, setId: id, unit, atomic, index: questionOffset + index, subjectPool }))
    .map((question, localIndex) => ({ ...question, id: `${id}.question.${localIndex + 1}` }))
    .map((question) => ({
      ...question,
      answers: question.answers.map((answer, answerIndex) => ({ ...answer, id: `${question.id}.answer.${answerIndex + 1}`, contentFieldId: `${question.id}.answer.${answerIndex + 1}.field.value` })),
      contentFieldIds: { question: `${question.id}.field.question`, explanation: `${question.id}.field.explanation` },
    }));
  for (const question of editorialItems) {
    question.lessonId = lessonId;
    question.stageId = stageId;
    question.exerciseSetId = id;
    question.translationStatus = statuses(`${question.id}.translation-status`);
    question.answers = question.answers.map((answer) => ({ ...answer, translationStatus: statuses(`${answer.id}.translation-status`) }));
  }
  const questionBlueprints = editorialItems.map((question) => ({
    id: question.id,
    lessonId,
    stageId,
    exerciseSetId: id,
    generatorVersion: "v2",
    origin: "GENERATED",
    validationStatus: "NEEDS_REVIEW",
    publicationStatus: "DRAFT_REVIEW_REQUIRED",
    answerOptionIds: question.answers.map((answer) => answer.id),
  }));
  return {
    id, exerciseSetId: id, lessonId, stageId,
    title: text(titleNl), sequence,
    sourceMode: "GENERATED", origin: "GENERATED",
    validationStatus: "NEEDS_REVIEW", publicationStatus: "DRAFT_REVIEW_REQUIRED",
    technicalExecution: true, purpose, itemCount: count,
    generatorId: "generator.g2.atomic-curriculum", generatorVersion: "v2", generatorImplementationVersion: 1,
    seedPolicy: "STABLE_PER_PROFILE_ATTEMPT",
    parameters: {
      grade: "2",
      roadmap_entry: `roadmap.${lessonId.slice("lesson.".length)}`,
      phase,
      editorial_status: "approved",
      atom_key: atomic.key,
      mode: atomic.mode,
      title_nl: atomic.title,
      knowledge_nl: unit.coreKnowledge,
      skill_nl: atomic.focus,
      vocabulary_nl: (atomic.terms.length ? atomic.terms : unit.vocabulary).join("|"),
      models_nl: unit.models.join("|"),
      distractors_nl: wrongStatements(unit, atomic, subjectPool).join("|"),
      ...(purpose === "MASTERY_TEST" ? { exclude_recent_exact_items: "true" } : {}),
    },
    masteryTags: [`g2.${unit.subjectId}.${unit.domainId}.${atomic.key}`],
    representationIds: [`representation.g2.${unit.subjectId}.${unit.domainId}.${atomic.key}.v1`],
    hintPolicy: hints, feedbackPolicy: feedback, injectErrorMemory: inject,
    questionBlueprints, editorialItems,
  };
}

function block(id, page, type, titleNl, bodyNl, visualIds = [], terms = []) {
  return {
    id, pageId: `${id.substring(0, id.lastIndexOf("."))}.theory.page.${page}`, pageSequence: page,
    type, ...(titleNl ? { title: text(titleNl) } : {}), ...(bodyNl ? { body: text(bodyNl) } : {}),
    ...(terms.length ? { terms } : {}), ...(visualIds.length ? { visualIds } : {}), contentFieldIds: {},
  };
}

function buildLesson(unit, atomic, atomIndex, globalSequence, subjectPool, atomicByUnit) {
  const unitToken = unit.unitId.slice("unit.g2.".length).replaceAll("_", "-");
  const lessonId = `lesson.g2.${unitToken}.${atomic.key}`;
  const [category, subcategory, domain, legacySubcategory] = location(unit, atomic);
  const visualId = `visual.${lessonId.slice("lesson.".length)}`;
  const representationId = `representation.g2.${unit.subjectId}.${unit.domainId}.${atomic.key}.v1`;
  const terms = (atomic.terms.length ? atomic.terms : unit.vocabulary).map((termValue, index) => ({
    id: `${lessonId}.words.term.${stable(termValue)}.${index + 1}`,
    term: text(termValue),
    definition: text(`${termValue} is een kernbegrip waarmee je leert ${atomic.focus}.`),
    example: text(`Gebruik ‘${termValue}’ wanneer je werkt aan ${atomic.title.toLowerCase()}.`),
    nonExample: text(`Het begrip ‘${termValue}’ zonder verband of uitleg noemen toont nog niet dat je het begrijpt.`),
    contentFieldIds: {},
  }));
  const sourcePhases = sourceLessonsByUnit.get(unit.unitId) || [];
  const acquire = sourcePhases.find((lesson) => lesson.phase === "acquire");
  const misconception = acquire?.misconceptions?.[0] || "Een antwoord geven zonder het kernmodel en de controle te gebruiken.";
  const procedure = (acquire?.proceduralKnowledge || []).slice(0, 5).join(" Eerst ") || `Kies een passend model, voer ${atomic.focus} stap voor stap uit en controleer het resultaat.`;
  const example1 = concreteExample(atomic.mode, 0, atomic);
  const example2 = concreteExample(atomic.mode, 1, atomic);
  const theoryId = `${lessonId}.theory`;
  const blocks = [
    block(`${lessonId}.intent`, 1, "LEARNING_INTENT", "Dit leer je", `Je leert ${atomic.focus}.`),
    block(`${lessonId}.words`, 1, "VOCABULARY", "Kernwoorden", null, [], terms),
    block(`${lessonId}.how`, 1, "HOW_IT_WORKS", "Zo werkt het", procedure, [visualId]),
    block(`${lessonId}.correct`, 2, "CORRECT_RESULT_RULE", "Controleer", `Je resultaat klopt wanneer je ${atomic.focus}, je keuze kunt uitleggen en de uitkomst past bij: ${unit.coreKnowledge}`),
    block(`${lessonId}.example-1`, 2, "WORKED_EXAMPLE", "Voorbeeld 1", example1, [visualId]),
    block(`${lessonId}.example-2`, 2, "WORKED_EXAMPLE", "Voorbeeld 2", example2, [visualId]),
    block(`${lessonId}.non-example`, 2, "NON_EXAMPLE", "Let op", misconception),
  ];
  if (["physical", "arts", "music", "language_speaking"].includes(atomic.mode)) {
    blocks.push(block(`${lessonId}.rubric`, 2, "RUBRIC", "Zo toon je het", `Toon de vaardigheid veilig en doelgericht. Benoem je keuze, voer ze uit en vergelijk je resultaat met het afgesproken criterium.`));
  }
  const practiseId = `${lessonId}.practise`;
  const applyId = `${lessonId}.apply`;
  const testId = `${lessonId}.test`;
  const sets = [
    buildSet({ lessonId, stageId: practiseId, setKey: "practise-1", titleNl: "Oefenreeks 1", sequence: 1, purpose: "GUIDED", count: 5, atomic, unit, subjectPool, hints: "FULL", feedback: "IMMEDIATE_EXPLANATORY", inject: false }),
    buildSet({ lessonId, stageId: practiseId, setKey: "practise-2", titleNl: "Oefenreeks 2", sequence: 2, purpose: "FADING_SUPPORT", count: 5, atomic, unit, subjectPool, hints: "FADING", feedback: "IMMEDIATE_EXPLANATORY", inject: true }),
    buildSet({ lessonId, stageId: practiseId, setKey: "practise-3", titleNl: "Oefenreeks 3", sequence: 3, purpose: "INDEPENDENT_PRACTICE", count: 6, atomic, unit, subjectPool, hints: "NONE", feedback: "IMMEDIATE_MINIMAL", inject: true }),
    buildSet({ lessonId, stageId: applyId, setKey: "application-1", titleNl: "Toepassing 1", sequence: 1, purpose: "NEAR_TRANSFER", count: 4, atomic, unit, subjectPool, hints: "NONE", feedback: "AFTER_SET", inject: true }),
    buildSet({ lessonId, stageId: testId, setKey: "test-1", titleNl: "Toets 1", sequence: 1, purpose: "MASTERY_TEST", count: 10, atomic, unit, subjectPool, hints: "NONE", feedback: "AFTER_TEST", inject: false }),
  ];
  const broadAliases = Object.entries(broadAliasTargets).filter(([, unitId]) => unitId === unit.unitId && atomIndex === 0).map(([id]) => id);
  const previousInUnit = atomIndex > 0 ? `lesson.g2.${unitToken}.${atomsFor(unit)[atomIndex - 1].key}` : null;
  const prerequisiteAtomic = (unit.prerequisiteUnitIds || []).flatMap((unitId) => atomicByUnit.get(unitId) || []).at(-1)?.lessonId;
  const lesson = {
    id: lessonId, unitId: unit.unitId, curriculumCategoryId: category, curriculumSubcategoryId: subcategory,
    grade: 2, sequence: globalSequence,
    title: text(atomic.title),
    summary: text(`Leer ${atomic.focus} met uitleg, gerichte oefening, toepassing en een afsluitende toets.`),
    description: text(`${unit.focus} Deze les focust afzonderlijk op ${atomic.focus}.`),
    pedagogicalLogic: text(`De les activeert voorkennis, modelleert ${atomic.focus}, bouwt steun af over drie oefenreeksen en verzamelt daarna onafhankelijk bewijs in een toepassing en toets.`),
    defaultLocale: "nl-BE", translationStatus: statuses(`${lessonId}.translation-status`),
    contentFields: [],
    legacyRouting: { domain, subcategoryId: legacySubcategory, legacyLessonIds: atomIndex === 0 ? [...unit.sourceLessonIds, ...broadAliases] : [] },
    learningGoalIds: unit.coveredGoals.length ? unit.coveredGoals.map((goal) => `goal.${stable(goal)}`) : [`goal.g2.${unit.subjectId}.${unit.domainId}.${atomic.key}`],
    masteryTags: [`g2.${unit.subjectId}.${unit.domainId}.${atomic.key}`],
    prerequisiteLessonIds: [...new Set([previousInUnit, prerequisiteAtomic].filter(Boolean))],
    publicationStatus: "DRAFT_REVIEW_REQUIRED",
    visuals: [{
      id: visualId,
      kind: domain === "MATH" ? "MATH_REPRESENTATION" : domain === "GEOMETRY" ? "MEASUREMENT_DIAGRAM" : domain === "READING" && unit.domainId === "technical_reading" ? "PHONICS_DIAGRAM" : domain === "WORLD_KNOWLEDGE" ? "WORLD_BICOLOR_ILLUSTRATION" : "THEORY_ICON",
      representationId,
      assetPathSvg: `poincare/visuals/grade2/${lessonId.slice("lesson.g2.".length)}.svg`,
      assetPathPng: `poincare/visuals/grade2/${lessonId.slice("lesson.g2.".length)}.png`,
      palettePolicy: domain === "WORLD_KNOWLEDGE" ? "PLANET_GOLD_WHITE_ONLY" : "POINCARE_TRICOLOUR",
      transparentBackground: true,
      altText: { "nl-BE": `Lesmodel voor ${atomic.title}: ${atomic.focus}.`, "fr-BE": `Lesmodel voor ${atomic.title}: ${atomic.focus}.`, "en-US": `Lesmodel voor ${atomic.title}: ${atomic.focus}.` },
      licenseId: "poincare-original",
    }],
    stages: [
      { id: theoryId, type: "THEORY", sequence: 1, title: text("Theorie"), required: true, estimatedMinutes: 15, completionPolicy: "VIEWED_REQUIRED_BLOCKS", contentBlocks: blocks, exerciseSets: [] },
      { id: practiseId, type: "PRACTISE", sequence: 2, title: text("Oefenen"), required: true, estimatedMinutes: 25, completionPolicy: "COMPLETED_REQUIRED_SETS", contentBlocks: [], exerciseSets: sets.slice(0, 3) },
      { id: applyId, type: "APPLY_DEMONSTRATE", sequence: 3, title: text("Toepassen"), required: true, estimatedMinutes: 10, completionPolicy: "COMPLETED_REQUIRED_SETS", contentBlocks: [], exerciseSets: [sets[3]] },
      { id: testId, type: "MASTERY_TEST", sequence: 4, title: text("Toets"), required: true, estimatedMinutes: 15, completionPolicy: "ASSESSMENT_RESULT", contentBlocks: [], exerciseSets: [sets[4]] },
    ],
    gatePolicy: { evidenceSource: "MASTERY_TEST_ONLY", minimumCorrectRatio: 0.8, minimumIndependentItems: 10, maximumHints: 0, requiredMasteryTags: [`g2.${unit.subjectId}.${unit.domainId}.${atomic.key}`], criticalTagMinimumCorrect: 1, unlockOnPass: true, retentionCheckRequiredForMastered: true },
    errorRepetitionPolicy: { enabled: true, repeatConceptNotExactItem: true, maxInjectedShare: 0.25, reviewIntervalsDays: [0, 1, 3, 7], resolveAfterConsecutiveCorrect: 2 },
    definitionVersion: 2,
  };
  addEditorialFields(lesson);
  return lesson;
}

function addEditorialFields(lesson) {
  const fields = [];
  const add = (id, owner, pathValue) => fields.push(contentField(id, owner, pathValue));
  for (const key of ["title", "summary", "description", "pedagogicalLogic"]) add(`${lesson.id}.field.${stable(key)}`, lesson.id, key);
  for (const visual of lesson.visuals) add(`${visual.id}.field.alt-text`, visual.id, "altText");
  for (const stage of lesson.stages) {
    add(`${stage.id}.field.title`, stage.id, "title");
    for (const blockValue of stage.contentBlocks) {
      if (blockValue.title) { blockValue.contentFieldIds.title = `${blockValue.id}.field.title`; add(blockValue.contentFieldIds.title, blockValue.id, "title"); }
      if (blockValue.body) { blockValue.contentFieldIds.body = `${blockValue.id}.field.body`; add(blockValue.contentFieldIds.body, blockValue.id, "body"); }
      for (const termValue of blockValue.terms || []) {
        for (const key of ["term", "definition", "example", "nonExample"]) {
          if (!termValue[key]) continue;
          termValue.contentFieldIds[key] = `${termValue.id}.field.${stable(key)}`;
          add(termValue.contentFieldIds[key], termValue.id, key);
        }
      }
    }
    for (const set of stage.exerciseSets) {
      add(`${set.id}.field.title`, set.id, "title");
      for (const question of set.editorialItems) {
        add(question.contentFieldIds.question, question.id, "question");
        add(question.contentFieldIds.explanation, question.id, "explanation");
        for (const answer of question.answers) add(answer.contentFieldId, answer.id, "value");
      }
    }
  }
  lesson.contentFields = fields;
}

function migrateSeasonsPilot(sourceLesson) {
  const lesson = structuredClone(sourceLesson);
  const ordered = [...lesson.stages].sort((left, right) => left.sequence - right.sequence);
  if (ordered.length !== 4) throw new Error("The retained Grade 2 seasons pilot no longer has four stages.");
  const practise = ordered[1];
  const apply = ordered[2];
  const independent = (apply.exerciseSets || []).find((set) => set.purpose === "INDEPENDENT_PRACTICE")
    || (practise.exerciseSets || []).find((set) => set.purpose === "INDEPENDENT_PRACTICE");
  const transfer = (apply.exerciseSets || []).find((set) => set.purpose === "NEAR_TRANSFER");
  if (!independent || !transfer) throw new Error("The retained Grade 2 seasons pilot misses its independent or transfer set.");
  practise.exerciseSets = [...practise.exerciseSets.filter((set) => set.id !== independent.id), independent];
  apply.exerciseSets = [transfer];
  apply.type = "APPLY_DEMONSTRATE";
  apply.title = text("Toepassen", "Appliquer", "Apply");
  apply.completionPolicy = "COMPLETED_REQUIRED_SETS";
  for (const set of practise.exerciseSets) {
    set.stageId = practise.id;
    for (const question of set.questionBlueprints || []) question.stageId = practise.id;
  }
  transfer.stageId = apply.id;
  transfer.title = text("Toepassing 1", "Application 1", "Application 1");
  for (const question of transfer.questionBlueprints || []) question.stageId = apply.id;
  lesson.stages = ordered;
  lesson.gatePolicy.minimumCorrectRatio = 0.8;
  lesson.definitionVersion = Math.max(Number(lesson.definitionVersion || 1), 2);
  return lesson;
}

const unitsBySubject = Map.groupBy(source.units, (unit) => unit.subjectId);
const subjectKnowledge = Object.fromEntries([...unitsBySubject].map(([subject, units]) => [subject, units.map((unit) => unit.coreKnowledge)]));
const atomicByUnit = new Map();
for (const unit of source.units) {
  const atoms = atomsFor(unit).map((atomic) => ({ ...atomic }));
  atomicByUnit.set(unit.unitId, atoms);
}
const generated = [];
let sequence = 0;
for (const unit of source.units) {
  const atoms = atomicByUnit.get(unit.unitId);
  for (const [atomIndex, atomic] of atoms.entries()) {
    sequence += 1;
    const previewId = `lesson.g2.${unit.unitId.slice("unit.g2.".length).replaceAll("_", "-")}.${atomic.key}`;
    atomic.lessonId = previewId;
    generated.push(buildLesson(unit, atomic, atomIndex, sequence, subjectKnowledge[unit.subjectId], atomicByUnit));
  }
}

const reviewSets = generated.flatMap((lesson) => lesson.stages.flatMap((stage) => stage.exerciseSets));
const reviewQuestions = reviewSets.flatMap((set) => set.editorialItems);
const questionBank = {
  schemaVersion: 2,
  generatorVersion: "v2",
  grade: 2,
  sourceLocale: "nl-BE",
  publicationStatus: "DRAFT_REVIEW_REQUIRED",
  questionCount: reviewQuestions.length,
  questions: reviewQuestions,
};

// The mobile runtime needs stable blueprints and generator parameters, not the
// much larger editorial preview payload. Curriculum Room merges this bank by
// matching the same immutable question IDs.
for (const lesson of generated) {
  lesson.contentFields = lesson.contentFields.filter((field) => !field.ownerId.includes(".question."));
  for (const set of lesson.stages.flatMap((stage) => stage.exerciseSets)) set.editorialItems = [];
}

const retained = catalog.lessons
  .filter((lesson) => lesson.grade !== 2 || lesson.id === seasonsPilotId)
  .filter((lesson) => !oldBroadIds.has(lesson.id))
  .map((lesson) => lesson.id === seasonsPilotId ? migrateSeasonsPilot(lesson) : lesson);
catalog.schemaVersion = 2;
catalog.definitionVersion = Math.max(Number(catalog.definitionVersion || 1), 2);
catalog.lessons = [...retained, ...generated];

const crosswalkUnits = source.units.map((unit) => {
  const lessons = generated.filter((lesson) => lesson.unitId === unit.unitId);
  return {
    sourceUnitId: unit.unitId,
    subjectId: unit.subjectId,
    domainId: unit.domainId,
    sourceSequence: unit.sequenceInSubject,
    sourceTitle: unit.title,
    oldPhaseLessonIds: unit.sourceLessonIds,
    newAtomicLessons: lessons.map((lesson) => ({
      lessonId: lesson.id,
      title: lesson.title.visible["nl-BE"],
      categoryId: lesson.curriculumCategoryId,
      subcategoryId: lesson.curriculumSubcategoryId,
      grade: 2,
      sequence: lesson.sequence,
      coveredGoals: unit.coveredGoals,
      legacyAliases: lesson.legacyRouting.legacyLessonIds,
      prerequisiteLessonIds: lesson.prerequisiteLessonIds,
    })),
    coveredGoals: unit.coveredGoals,
  };
});
const retiredLessonAliases = Object.fromEntries(Object.entries(broadAliasTargets).map(([oldId, unitId]) => [oldId, generated.filter((lesson) => lesson.unitId === unitId).map((lesson) => lesson.id)]));
const crosswalk = {
  schemaVersion: 1,
  grade: 2,
  sourceUnitCount: source.units.length,
  sourcePhaseRecordCount: source.sourceLessons.length,
  newAtomicLessonCount: generated.length,
  policy: "One canonical source unit maps to one or more atomic V2 lessons; acquire/practice/transfer are source phases, not separate V2 lessons.",
  publicationStatus: "DRAFT_REVIEW_REQUIRED",
  retiredLessonAliases,
  retainedPilotMigration: {
    lessonId: seasonsPilotId,
    preservedStageIds: true,
    preservedExerciseSetIds: true,
    change: "Moved the independent set into PRACTISE and converted the third stage to APPLY_DEMONSTRATE; gate ratio is 0.8.",
  },
  units: crosswalkUnits,
};
const lessonsBySubject = Map.groupBy(generated, (lesson) => source.units.find((unit) => unit.unitId === lesson.unitId)?.subjectId || "unknown");
const bySubject = Object.fromEntries([...lessonsBySubject].map(([subject, lessons]) => [subject, lessons.length]));
const allSets = generated.flatMap((lesson) => lesson.stages.flatMap((stage) => stage.exerciseSets));
const allQuestions = reviewQuestions;
const index = {
  generatedAtUtc: new Date().toISOString(),
  sourceCurriculum: source.source.curriculumId,
  grade: 2,
  sourceUnits: source.units.length,
  sourcePhaseRecords: source.sourceLessons.length,
  generatedLessons: generated.length,
  retainedCorePilots: retained.filter((lesson) => lesson.grade === 2).length,
  exerciseSets: allSets.length,
  questions: allQuestions.length,
  answerOptions: allQuestions.reduce((sum, question) => sum + question.answers.length + question.acceptedAnswers.length, 0),
  lessonsBySubject: bySubject,
  publicationStatus: "DRAFT_REVIEW_REQUIRED",
  translationReview: { "nl-BE": "DEFAULT", "fr-BE": "NEEDS_REVIEW", "en-US": "NEEDS_REVIEW" },
  generator: { id: "generator.g2.atomic-curriculum", implementationVersion: 1, contractVersion: "v2" },
  lessons: generated.map((lesson) => ({ lessonId: lesson.id, unitId: lesson.unitId, categoryId: lesson.curriculumCategoryId, subcategoryId: lesson.curriculumSubcategoryId, stageCount: lesson.stages.length, setCount: lesson.stages.flatMap((stage) => stage.exerciseSets).length, questionCount: lesson.stages.flatMap((stage) => stage.exerciseSets).reduce((sum, set) => sum + set.itemCount, 0) })),
};

for (const [target, value] of [[catalogPath, catalog], [crosswalkPath, crosswalk], [indexPath, index], [questionBankPath, questionBank]]) {
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}
console.log(`Generated ${generated.length} atomic Grade 2 V2 lessons from ${source.units.length} canonical units.`);
console.log(`Generated ${allSets.length} exercise sets, ${allQuestions.length} reviewable questions and ${index.answerOptions} answer options.`);
console.log(catalogPath);
console.log(crosswalkPath);
console.log(questionBankPath);
