import fs from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const sourcePath = path.join(
  root,
  "outputs",
  "g1-content-audit-20260731",
  "grade1-v2-validation-exercises.json"
);
const catalogPath = path.join(
  root,
  "app",
  "src",
  "main",
  "assets",
  "poincare",
  "curriculum",
  "lesson-catalog-v2.json"
);
const outputPath = path.join(
  root,
  "outputs",
  "g1-content-audit-20260731",
  "Poincare_Alle_Oefeningen_Leerjaar1.csv"
);

const categoryDefinitions = [
  ["READING", "Lezen & luisteren"],
  ["WRITING", "Spelling & grammatica"],
  ["MATH", "Getallen"],
  ["GEOMETRY", "Meten & meetkunde"],
  ["WORLD_KNOWLEDGE", "Wereldkennis"],
  ["PUZZLE", "Puzzels"]
];

const tileDefinitions = {
  READING: [
    ["reading", "Lezen"], ["review", "Herhalen"], ["listening", "Luisteren"],
    ["speaking", "Spreken & presenteren"], ["writing", "Schrijven"], ["dictation", "Dictee"],
    ["vocabulary", "Woordenschat"], ["translation_nl_to_en", "Nederlands naar Engels"],
    ["translation_nl_to_fr", "Nederlands naar Frans"], ["translation_en_to_nl", "Engels naar Nederlands"],
    ["translation_en_to_fr", "Engels naar Frans"], ["translation_fr_to_nl", "Frans naar Nederlands"],
    ["translation_fr_to_en", "Frans naar Engels"]
  ],
  WRITING: [
    ["letter_tracing", "Letters schrijven"], ["writing", "Schrijven"], ["spelling", "Spelling"],
    ["sentence_order", "Zinsvolgorde"], ["grammar", "Grammatica"], ["question_words", "Vraagwoorden"],
    ["singular_plural", "Enkelvoud en meervoud"], ["conjugations", "Vervoegingen"],
    ["verbs", "Werkwoorden"], ["past_tense", "Verleden tijden"], ["future_tense", "Toekomende tijden"]
  ],
  MATH: [
    ["addition", "Optellen"], ["subtraction", "Aftrekken"], ["multiplication", "Vermenigvuldigen"],
    ["division", "Delen"], ["mixed_arithmetic", "Gemengde bewerkingen"],
    ["number_sequences", "Getallenreeksen"], ["statistics", "Statistiek & gegevens"],
    ["fractions", "Breuken"], ["money", "Geld"]
  ],
  GEOMETRY: [
    ["time", "Tijd"], ["measurement", "Meten"], ["temperature", "Temperatuur"],
    ["compare_size", "Vergelijken, ordenen & schatten"], ["geometry_shapes", "Vormen en meetkunde"]
  ],
  WORLD_KNOWLEDGE: [
    ["space", "Ruimte"], ["nature", "Natuur & levende wereld"], ["animals", "Dieren"],
    ["plants", "Planten"], ["weather_climate", "Weer en klimaat"], ["science", "Wetenschap & onderzoek"],
    ["technology", "Techniek & ontwerpen"], ["geography", "Aardrijkskunde & ruimte"],
    ["history", "Geschiedenis & tijd"], ["human_body", "Mens, lichaam & gezondheid"],
    ["citizenship_society", "Samenleving & burgerschap"], ["traffic_safety", "Verkeer & veiligheid"],
    ["environment_sustainability", "Milieu & duurzaamheid"], ["everyday_life", "Dagelijks leven"],
    ["arts_culture", "Kunst en cultuur"]
  ],
  PUZZLE: [
    ["sudoku", "Sudoku"], ["math-crosswords", "Rekenkruiswoord"], ["word-seeker", "Woordzoeker"],
    ["mothership-dock", "Moederschip"], ["galaxycon", "Galaxycon"], ["calculator-puzzle", "Rekenmachine"],
    ["constellations", "Sterrenbeelden"], ["orbit-builder", "Orbitbouwer"],
    ["pattern_sequences", "Patronen"], ["odd_one_out", "Wat hoort er niet bij?"],
    ["analogies", "Analogieën"], ["grid_logic", "Rasterlogica"], ["mazes", "Doolhoven"],
    ["visual_reasoning", "Visueel redeneren"], ["memory_rules", "Geheugenregels"]
  ]
};

const [catalog, audit] = await Promise.all([
  fs.readFile(catalogPath, "utf8").then(JSON.parse),
  fs.readFile(sourcePath, "utf8").then(JSON.parse)
]);

const categoryById = new Map(categoryDefinitions.map(([id, title], index) => [
  id,
  { sequence: index + 1, title }
]));
const tileByCategoryAndId = new Map();
for (const [categoryId, tiles] of Object.entries(tileDefinitions)) {
  tiles.forEach(([id, title], index) => {
    tileByCategoryAndId.set(`${categoryId}:${id}`, { sequence: index + 1, title });
  });
}

const lessons = catalog.lessons.filter((lesson) => lesson.grade === 1);
const lessonById = new Map(lessons.map((lesson) => [lesson.id, lesson]));
const stageById = new Map();
for (const lesson of lessons) {
  for (const stage of lesson.stages) stageById.set(stage.id, { lesson, stage });
}

const lessonsByTile = new Map();
for (const lesson of lessons) {
  const key = `${lesson.legacyRouting.domain}:${lesson.legacyRouting.subcategoryId}`;
  if (!lessonsByTile.has(key)) lessonsByTile.set(key, []);
  lessonsByTile.get(key).push(lesson);
}
const lessonSequenceInTile = new Map();
for (const tileLessons of lessonsByTile.values()) {
  tileLessons
    .sort((left, right) => left.sequence - right.sequence || left.id.localeCompare(right.id))
    .forEach((lesson, index) => lessonSequenceInTile.set(lesson.id, index + 1));
}

const purposeOrder = new Map([
  ["GUIDED", 1],
  ["FADING_SUPPORT", 2],
  ["INDEPENDENT_PRACTICE", 3],
  ["ERROR_REVIEW", 4],
  ["NEAR_TRANSFER", 5],
  ["FAR_TRANSFER", 6],
  ["MASTERY_TEST", 7]
]);

function visible(value, locale = "nl-BE") {
  return value?.visible?.[locale] ?? value?.visible?.["nl-BE"] ?? "";
}

function normalized(value) {
  if (value === null || value === undefined) return "";
  if (Array.isArray(value)) return value.map(normalized).filter(Boolean).join(" | ");
  if (typeof value === "object") return JSON.stringify(value);
  return String(value).replace(/\r?\n/g, " | ");
}

function csvCell(value) {
  const text = normalized(value).replaceAll('"', '""');
  return `"${text}"`;
}

const orderedSets = [...audit.sets].sort((left, right) => {
  const leftLesson = lessonById.get(left.lessonId);
  const rightLesson = lessonById.get(right.lessonId);
  const leftCategory = categoryById.get(leftLesson.legacyRouting.domain)?.sequence ?? 999;
  const rightCategory = categoryById.get(rightLesson.legacyRouting.domain)?.sequence ?? 999;
  const leftTile = tileByCategoryAndId.get(`${leftLesson.legacyRouting.domain}:${leftLesson.legacyRouting.subcategoryId}`)?.sequence ?? 999;
  const rightTile = tileByCategoryAndId.get(`${rightLesson.legacyRouting.domain}:${rightLesson.legacyRouting.subcategoryId}`)?.sequence ?? 999;
  const leftStage = stageById.get(left.stageId)?.stage;
  const rightStage = stageById.get(right.stageId)?.stage;
  return leftCategory - rightCategory ||
    leftTile - rightTile ||
    (lessonSequenceInTile.get(left.lessonId) ?? 999) - (lessonSequenceInTile.get(right.lessonId) ?? 999) ||
    (leftStage?.sequence ?? 999) - (rightStage?.sequence ?? 999) ||
    (purposeOrder.get(left.purpose) ?? 999) - (purposeOrder.get(right.purpose) ?? 999) ||
    left.setId.localeCompare(right.setId);
});

const headers = [
  "Globale sequentie", "Categorie #", "Categorie-ID", "Categorie", "Tegel #", "Tegel-ID", "Tegel",
  "Les #", "Les-ID", "Unit-ID", "Les", "Publicatiestatus", "Deel #", "Deel", "Reeks #", "Reeks",
  "Set #", "Set-ID", "Setdoel", "Generator-ID", "Generatorversie", "Oefening #", "Vraag-ID",
  "Sectietype", "Domein", "Vaardigheid", "Invoermodus", "Vraag NL", "Vraag FR", "Vraag EN",
  "Spraak NL", "Spraak FR", "Spraak EN", "Antwoord", "Antwoord NL", "Antwoord FR", "Antwoord EN",
  "Opties NL", "Opties FR", "Opties EN", "Mastery-tags", "Fouttags", "Feedback NL", "Feedback FR",
  "Feedback EN", "Hint NL", "Hint FR", "Hint EN", "Visual-ID's", "Representatie-ID's", "Signature", "Data JSON"
];

const rows = [];
let globalSequence = 0;
for (const set of orderedSets) {
  const lesson = lessonById.get(set.lessonId);
  const stage = stageById.get(set.stageId)?.stage;
  if (!lesson || !stage) throw new Error(`Onbekende les of fase voor set ${set.setId}`);
  const categoryId = lesson.legacyRouting.domain;
  const tileId = lesson.legacyRouting.subcategoryId;
  const category = categoryById.get(categoryId) ?? { sequence: 999, title: categoryId };
  const tile = tileByCategoryAndId.get(`${categoryId}:${tileId}`) ?? { sequence: 999, title: tileId };
  const siblingSets = orderedSets.filter((candidate) => candidate.stageId === set.stageId);
  const setSequence = siblingSets.findIndex((candidate) => candidate.setId === set.setId) + 1;
  const partSequence = stage.sequence;
  const partTitle = ({ PRACTISE: "Oefenen", APPLY_DEMONSTRATE: "Pas toe", MASTERY_TEST: "Toets" })[stage.type] ?? stage.type;
  const seriesSequence = setSequence;
  const seriesTitle = stage.type === "MASTERY_TEST" ? "Toets" : `Reeks ${seriesSequence}`;

  set.exercises.forEach((exercise, exerciseIndex) => {
    globalSequence += 1;
    const data = exercise.data ?? {};
    const signature = set.exerciseSignatures?.[exerciseIndex] ?? "";
    rows.push([
      globalSequence,
      category.sequence,
      categoryId,
      category.title,
      tile.sequence,
      tileId,
      tile.title,
      lessonSequenceInTile.get(lesson.id),
      lesson.id,
      lesson.unitId,
      visible(lesson.title),
      lesson.publicationStatus,
      partSequence,
      partTitle,
      seriesSequence,
      seriesTitle,
      setSequence,
      set.setId,
      set.purpose,
      set.generatorId,
      set.generatorVersion,
      exerciseIndex + 1,
      exercise.id,
      exercise.section,
      exercise.domain,
      exercise.skill,
      exercise.inputMode,
      exercise.prompt?.nl,
      exercise.prompt?.fr,
      exercise.prompt?.en,
      exercise.speechText?.nl,
      exercise.speechText?.fr,
      exercise.speechText?.en,
      exercise.answer,
      data["answer_nl-BE"] ?? exercise.answer,
      data["answer_fr-BE"] ?? exercise.answer,
      data["answer_en-US"] ?? exercise.answer,
      data["options_nl-BE"],
      data["options_fr-BE"],
      data["options_en-US"],
      [set.setMasteryTags, data.masteryTag].flat().filter(Boolean),
      data.errorTags,
      data["feedback_nl-BE"],
      data["feedback_fr-BE"],
      data["feedback_en-US"],
      data["hint_nl-BE"],
      data["hint_fr-BE"],
      data["hint_en-US"],
      set.visualAssetIds,
      set.representationIds,
      signature,
      data
    ]);
  });
}

if (globalSequence !== audit.exerciseCount) {
  throw new Error(`CSV bevat ${globalSequence} oefeningen; verwacht ${audit.exerciseCount}.`);
}

const csv = "\uFEFF" + [headers, ...rows]
  .map((row) => row.map(csvCell).join(","))
  .join("\r\n") + "\r\n";

await fs.mkdir(path.dirname(outputPath), { recursive: true });
await fs.writeFile(outputPath, csv, "utf8");

console.log(JSON.stringify({
  outputPath,
  rows: rows.length,
  columns: headers.length,
  bytes: Buffer.byteLength(csv, "utf8")
}, null, 2));
