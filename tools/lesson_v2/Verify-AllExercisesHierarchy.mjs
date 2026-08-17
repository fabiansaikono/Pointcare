import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const outputName = "Poincare_Alle_Oefeningen_Hierarchie_Leerjaar1.json";
const mainPath = path.join(root, outputName);
const datedPath = path.join(
  root,
  "outputs",
  "exercise-hierarchy-20260731",
  outputName
);

const [mainBytes, datedBytes] = await Promise.all([
  fs.readFile(mainPath),
  fs.readFile(datedPath)
]);
const bundle = JSON.parse(mainBytes.toString("utf8"));

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function sha256(bytes) {
  return crypto.createHash("sha256").update(bytes).digest("hex").toUpperCase();
}

const lessonExercises = [];
const extraExercises = [];
const lessons = [];
const tiles = [];
const extraSeries = [];

for (const category of bundle.categories) {
  for (const tile of category.tiles) {
    tiles.push({ category, tile });
    for (const lesson of tile.lessons) {
      lessons.push({ category, tile, lesson });
      for (const part of lesson.parts) {
        for (const series of part.series) {
          for (const exercise of series.exercises) {
            lessonExercises.push({ category, tile, lesson, part, series, exercise });
          }
        }
      }
    }
    for (const type of tile.extraPractice.types) {
      for (const series of type.series) {
        extraSeries.push({ category, tile, type, series });
        for (const exercise of series.exercises) {
          extraExercises.push({ category, tile, type, series, exercise });
        }
      }
    }
  }
}

const expectedSummary = {
  grade: 1,
  categoryCount: 6,
  tileCount: 68,
  lessonCount: 67,
  partCount: 201,
  lessonSeriesCount: 201,
  extraPracticeTypeCount: 42,
  extraPracticeSeriesCount: 420,
  lessonExerciseCount: 2016,
  extraPracticeExerciseCount: 4200,
  uniqueExerciseCount: 6216,
  totalExerciseOccurrences: 6216,
  duplicateExerciseIdCount: 0,
  unmappedExerciseCount: 0
};

for (const [key, value] of Object.entries(expectedSummary)) {
  assert(bundle.summary[key] === value, `summary.${key}: expected ${value}, got ${bundle.summary[key]}`);
}

assert(
  Object.values(bundle.validation.checks).every(Boolean),
  "At least one embedded validation check failed."
);
assert(Buffer.compare(mainBytes, datedBytes) === 0, "Root and dated exports differ.");
assert(lessonExercises.length === 2016, "Unexpected flattened lesson exercise count.");
assert(extraExercises.length === 4200, "Unexpected flattened extra-practice exercise count.");
assert(lessons.every(({ lesson }) => lesson.parts.map((part) => part.type).join("|") ===
  "THEORY|EXERCISES|MASTERY_TEST"), "Not every lesson follows Theory → Exercises → Test.");
assert(extraSeries.every(({ series }) => series.exerciseCount >= 1 && series.exerciseCount <= 10),
  "An extra-practice series contains fewer than 1 or more than 10 exercises.");

for (const item of lessonExercises) {
  const expectedPrefix = [
    `categorie:${item.category.id}`,
    `tegel:${item.tile.id}`,
    `les:${item.lesson.id}`,
    `deel:${item.part.sequence}`,
    `reeks:${item.series.sequence}`
  ];
  assert(
    expectedPrefix.every((segment, index) => item.exercise.hierarchyPath[index] === segment),
    `Incorrect lesson hierarchyPath for ${item.exercise.id}.`
  );
}

for (const item of extraExercises) {
  const expectedPrefix = [
    `categorie:${item.category.id}`,
    `tegel:${item.tile.id}`,
    "extra-oefeningen",
    `type:${item.type.id}`,
    `reeks:${item.series.sequence}`
  ];
  assert(
    expectedPrefix.every((segment, index) => item.exercise.hierarchyPath[index] === segment),
    `Incorrect extra-practice hierarchyPath for ${item.exercise.id}.`
  );
}

const additionTile = tiles.find(({ category, tile }) =>
  category.id === "MATH" && tile.id === "addition"
)?.tile;
assert(additionTile, "The addition tile is missing.");
assert(additionTile.extraPractice.exerciseCount > 0, "Addition has no extra-practice exercises.");

const comparisonTile = tiles.find(({ category, tile }) =>
  category.id === "GEOMETRY" && tile.id === "compare_size"
)?.tile;
assert(comparisonTile, "The comparison tile is missing.");
assert(
  comparisonTile.extraPractice.types.map((type) => type.id).sort().join("|") ===
    ["compare_distance", "compare_height", "compare_length", "compare_size", "compare_weight"].join("|"),
  "Comparison exercises are not split into the five expected types."
);

const traceExercises = lessonExercises.filter(({ exercise }) => exercise.inputMode === "TRACE_LETTER");
const tracePayload = JSON.stringify(traceExercises.map(({ exercise }) => exercise.data));
const missingDigits = [..."0123456789"].filter((digit) => !tracePayload.includes(`"${digit}"`));
assert(traceExercises.length === 66, `Expected 66 trace exercises, got ${traceExercises.length}.`);
assert(missingDigits.length === 0, `Number tracing is incomplete; missing: ${missingDigits.join(", ")}.`);

const allIds = [...lessonExercises, ...extraExercises].map(({ exercise }) => exercise.id);
assert(new Set(allIds).size === allIds.length, "Duplicate exercise IDs found after flattening.");

console.log(JSON.stringify({
  file: mainPath,
  bytes: mainBytes.length,
  sha256: sha256(mainBytes),
  counts: expectedSummary,
  structure: {
    partsPerLesson: ["Theorie", "Oefeningen", "Toets"],
    maximumExtraExercisesPerSeries: 10,
    comparisonExerciseTypes: comparisonTile.extraPractice.types.map((type) => type.id),
    traceExerciseCount: traceExercises.length,
    numberTracingDigitsPresent: missingDigits.length === 0
  },
  status: "OK"
}, null, 2));
