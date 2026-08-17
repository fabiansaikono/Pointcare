#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "../..");
const read = (relative) => JSON.parse(fs.readFileSync(path.join(root, relative), "utf8").replace(/^\uFEFF/, ""));
const catalog = read("app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json");
const bank = read("docs/lesson_v2/reports/GRADE2_EDITORIAL_QUESTION_BANK_V2.json");
const mapping = read("docs/lesson_v2/reports/V1_TO_V2_CURRICULUM_MAPPING_L12.json");
const coverage = read("docs/lesson_v2/reports/FEEDBACK_COVERAGE_MATRIX_L12.json");
const dispositions = read("docs/lesson_v2/reports/FEEDBACK_ROW_DISPOSITIONS_L12.json");
const outputPath = path.join(root, "docs/lesson_v2/reports/FEEDBACK_EXTRAPOLATION_AUDIT_L12.json");
const RS = "\u001e";
const US = "\u001f";

const issues = [];
const fail = (validator, owner, message) => issues.push({ validator, owner, message });
const normalize = (value) => String(value || "").normalize("NFKC").toLocaleLowerCase("nl-BE")
  .replace(/[‘’'"“”.,!?;:()[\]{}]/g, " ").replace(/\s+/g, " ").trim();
const duplicateValues = (values) => {
  const seen = new Set();
  const duplicates = new Set();
  for (const value of values) seen.has(value) ? duplicates.add(value) : seen.add(value);
  return [...duplicates];
};
const decode = (value) => String(value || "").split(RS).filter(Boolean).map((row) => {
  const fields = row.split(US);
  return { prompt: fields[0], answer: fields[1], options: fields.slice(1, 5), explanation: fields[5] };
});
const contentSignature = (record) => normalize(`${record.prompt}|${record.answer}|${record.options.map(normalize).sort().join("|")}`);
const optionSignature = (record) => record.options.map(normalize).sort().join("|");

const forbidden = [
  "Welke zin klopt?",
  "Welke zin zou de juf",
  "Noor legt",
  "Wat kun je juist vertellen",
  "Denk terug aan het voorbeeld",
  "Gokken geeft altijd",
  "Gokken op de prent",
  "Ik hoef",
  "De volgorde van letters en woorden maakt nooit uit",
  "De volgorde van de rekenstappen maakt nooit uit",
  "Ik verander een getal",
  "Ik controleer mijn antwoord niet",
];
const catalogText = JSON.stringify(catalog).toLocaleLowerCase("nl-BE");
const bankText = JSON.stringify(bank).toLocaleLowerCase("nl-BE");
for (const phrase of forbidden) {
  if (catalogText.includes(phrase.toLocaleLowerCase("nl-BE"))) fail("noGenericDistractorSet", "catalog", `Forbidden phrase remains: ${phrase}`);
  if (bankText.includes(phrase.toLocaleLowerCase("nl-BE"))) fail("noGenericDistractorSet", "question-bank", `Forbidden phrase remains: ${phrase}`);
}

const mappedIds = new Set(mapping.items.map((item) => item.exerciseId));
const bankById = new Map(bank.questions.map((question) => [question.id, question]));
const allBlueprintIds = [];
let firstSignatureChecks = 0;
let practiceTestIsolationChecks = 0;
let adjacentSetChecks = 0;
let optionVariationChecks = 0;
let mathLessons = 0;
let frenchThemeLessons = 0;

for (const lesson of catalog.lessons.filter((item) => [1, 2].includes(item.grade))) {
  const theory = lesson.stages.filter((stage) => stage.type === "THEORY");
  const exerciseStages = lesson.stages.filter((stage) => stage.type === "EXERCISES").sort((a, b) => a.seriesIndex - b.seriesIndex);
  const testStages = lesson.stages.filter((stage) => stage.type === "MASTERY_TEST");
  const theorySets = theory.flatMap((stage) => stage.exerciseSets || []);
  const exerciseSets = exerciseStages.flatMap((stage) => stage.exerciseSets || []);
  const testSets = testStages.flatMap((stage) => stage.exerciseSets || []);
  const allSets = [...theorySets, ...exerciseSets, ...testSets];

  if (theory.length !== 1 || exerciseStages.length !== 4 || testStages.length !== 1) {
    fail("completeLessonSeriesModel", lesson.id, `Expected 1 theory, 4 exercise stages and 1 test stage; found ${theory.length}/${exerciseStages.length}/${testStages.length}.`);
  }
  if (theorySets.length !== 1 || theorySets[0]?.itemCount !== 3) fail("completeLessonSeriesModel", lesson.id, "Theory check must contain 3 questions.");
  if (exerciseSets.length !== 8) fail("completeLessonSeriesModel", lesson.id, `Expected 8 exercise sets, found ${exerciseSets.length}.`);
  if (testSets.length !== 2 || testSets.some((set) => set.itemCount !== 10)) fail("completeLessonSeriesModel", lesson.id, "Expected mastery test A and retest B with 10 items each.");
  if (allSets.length !== 11) fail("completeLessonSeriesModel", lesson.id, `Expected 11 total sets, found ${allSets.length}.`);

  const allowedOne = exerciseSets.filter((set) => set.parameters?.["wrong answers allowed"] === "1").length;
  const allowedTwo = exerciseSets.filter((set) => set.parameters?.["wrong answers allowed"] === "2").length;
  if (allowedOne !== 4 || allowedTwo !== 4) fail("wrongAnswersAllowedDistribution", lesson.id, `Expected 4/4 split, found ${allowedOne}/${allowedTwo}.`);
  if (theorySets.some((set) => set.parameters?.["wrong answers allowed"] !== "1")) fail("wrongAnswersAllowedDistribution", lesson.id, "Theory check must allow one wrong answer.");

  const firstRecords = exerciseSets.map((set) => {
    if (set.sourceMode === "CURATED") return { set, signature: normalize(set.curatedExerciseIds[0]), prompt: set.curatedExerciseIds[0], options: set.curatedExerciseIds.slice(0, 4) };
    const record = decode(set.parameters?.question_pool_nl)[0];
    if (!record) {
      const signature = normalize(set.parameters?.first_item_signature || set.id);
      return { set, signature, prompt: signature, options: [set.id, set.parameters?.exercise_type || "exercise"] };
    }
    return { set, signature: contentSignature(record), prompt: normalize(record.prompt), options: record.options };
  });
  firstSignatureChecks += 1;
  for (const duplicate of duplicateValues(firstRecords.map((item) => item.signature).filter(Boolean))) {
    fail("firstExerciseSignatureUniqueWithinLesson", lesson.id, `Repeated first-exercise signature: ${duplicate.slice(0, 160)}`);
  }

  for (let index = 1; index < firstRecords.length; index += 1) {
    adjacentSetChecks += 1;
    if (firstRecords[index - 1].prompt === firstRecords[index].prompt) {
      fail("noRepeatedStimulusAcrossAdjacentSets", lesson.id, `Adjacent sets repeat their first stimulus: ${firstRecords[index].set.id}`);
    }
  }

  const oneOptionSets = new Set(firstRecords.filter((item) => item.set.parameters?.["wrong answers allowed"] === "1").map((item) => item.options.map(normalize).sort().join("|")));
  const twoOptionSets = new Set(firstRecords.filter((item) => item.set.parameters?.["wrong answers allowed"] === "2").map((item) => item.options.map(normalize).sort().join("|")));
  optionVariationChecks += 1;
  for (const signature of oneOptionSets) if (twoOptionSets.has(signature)) fail("answerOptionsVaryWhenRetryPolicyChanges", lesson.id, `An option set is reused across retry policies: ${signature.slice(0, 160)}`);

  const practiceSignatures = new Set();
  for (const set of exerciseSets) {
    if (set.sourceMode === "CURATED") {
      set.curatedExerciseIds.forEach((id) => practiceSignatures.add(`curated:${normalize(id)}`));
    } else {
      const records = decode(set.parameters?.question_pool_nl);
      if (records.length) records.forEach((record) => practiceSignatures.add(contentSignature(record)));
      else practiceSignatures.add(normalize(set.parameters?.first_item_signature || set.id));
    }
  }
  for (const set of testSets) {
    if (set.sourceMode === "CURATED") {
      for (const id of set.curatedExerciseIds) if (practiceSignatures.has(`curated:${normalize(id)}`)) fail("noPracticeTestOverlapNormalized", set.id, `Curated item reused from practice: ${id}`);
    } else {
      const records = decode(set.parameters?.question_pool_nl);
      for (const record of records) {
        if (practiceSignatures.has(contentSignature(record))) fail("noPracticeTestOverlapNormalized", set.id, `Test record overlaps practice: ${record.prompt}`);
      }
      if (!records.length && practiceSignatures.has(normalize(set.parameters?.first_item_signature || set.id))) fail("noPracticeTestOverlapNormalized", set.id, "Test signature overlaps practice.");
    }
  }
  practiceTestIsolationChecks += 1;

  const frenchSets = allSets.filter((set) => set.parameters?.french_theme_tag);
  if (lesson.id.includes(".fr.")) {
    frenchThemeLessons += 1;
    const tags = new Set(frenchSets.map((set) => set.parameters.french_theme_tag));
    if (frenchSets.length !== allSets.length || tags.size !== 1) fail("frenchThemeConsistency", lesson.id, "Every French set must have exactly one common theme tag.");
  }

  if (lesson.grade === 2 && ["MATH", "GEOMETRY"].includes(lesson.legacyRouting.domain)) {
    mathLessons += 1;
    if (allSets.length < 10 || exerciseSets.length < 5 || testSets.length < 2) fail("grade2MathCoverage", lesson.id, "Grade 2 math lesson has insufficient series or tests.");
    if (!lesson.visuals?.length || allSets.some((set) => !(set.representationIds || []).length)) fail("visualModelRequired", lesson.id, "A Grade 2 math set is missing a representation or visual.");
  }

  for (const set of allSets) {
    if (set.questionBlueprints.length !== set.itemCount) fail("blueprintCoverage", set.id, "Blueprint count does not match itemCount.");
    for (const blueprint of set.questionBlueprints) {
      allBlueprintIds.push(blueprint.id);
      if (lesson.grade === 2 && !bankById.has(blueprint.id)) fail("grade2QuestionBankCoverage", blueprint.id, "Editorial question is missing.");
    }
    if (set.sourceMode === "CURATED") {
      for (const id of set.curatedExerciseIds) if (!mappedIds.has(id)) fail("v1Mapping", set.id, `Curated item is absent from V1 mapping: ${id}`);
    }
  }
}

const requiredMappingFields = ["grade", "difficulty", "category", "lessonId", "masteryTags", "exerciseType", "contentSignature"];
for (const item of mapping.items.filter((entry) => entry.curriculumStatus === "MAPPED_CURRICULUM")) {
  for (const field of requiredMappingFields) {
    if (item[field] === null || item[field] === undefined || item[field] === "" || (Array.isArray(item[field]) && item[field].length === 0)) fail("v1Mapping", item.exerciseId, `Missing required mapping field: ${field}`);
  }
}
if (dispositions.actualRows !== 550 || dispositions.implementedRows + dispositions.rejectedRows !== 550) fail("feedbackRowDisposition", "feedback", "All 550 direct feedback rows must be implemented or explicitly rejected.");
if (coverage.rows.length !== catalog.lessons.filter((lesson) => [1, 2].includes(lesson.grade)).length) fail("coverageMatrix", "coverage", "Coverage matrix does not contain every L1/L2 lesson.");
if (duplicateValues(allBlueprintIds).length) fail("blueprintCoverage", "catalog", "Duplicate question blueprint IDs found.");
if (duplicateValues(bank.questions.map((question) => question.id)).length) fail("grade2QuestionBankCoverage", "question-bank", "Duplicate editorial question IDs found.");

const frenchThemeTags = new Set(catalog.lessons.flatMap((lesson) => lesson.stages.flatMap((stage) => stage.exerciseSets || [])).map((set) => set.parameters?.french_theme_tag).filter(Boolean));
for (const theme of ["FR-01", "FR-02", "FR-03", "FR-04", "FR-05", "FR-06", "FR-07", "FR-08"]) {
  if (!frenchThemeTags.has(theme)) fail("frenchThemeCoverage", theme, "Required French theme is missing.");
}
const multiplication = catalog.lessons.find((lesson) => lesson.id.includes("vermenigvuldigen-als-gelijke-groepen"));
if (!multiplication?.masteryTags.includes("g2.math.operations.tables-2-5-10")) fail("grade2MathCoverage", "tables-2-5-10", "Core multiplication tables tag is missing.");
const divisionLessons = catalog.lessons.filter((lesson) => lesson.id.includes("delen-als-verdelen-en-groeperen"));
if (!divisionLessons.length || divisionLessons.some((lesson) => !lesson.masteryTags.includes("g2.math.operations.division-facts-2-5-10"))) fail("grade2MathCoverage", "division-facts-2-5-10", "Core division facts tag is missing.");

const report = {
  schemaVersion: 1,
  generatedAt: new Date().toISOString(),
  result: issues.length ? "FAILED" : "PASSED",
  counts: {
    lessons: catalog.lessons.filter((lesson) => [1, 2].includes(lesson.grade)).length,
    exerciseSets: catalog.lessons.filter((lesson) => [1, 2].includes(lesson.grade)).flatMap((lesson) => lesson.stages.flatMap((stage) => stage.exerciseSets || [])).length,
    grade2EditorialQuestions: bank.questions.length,
    feedbackRows: dispositions.actualRows,
    v1Items: mapping.items.length,
    v1MappedItems: mapping.counts.mappedCurriculum,
    coverageRows: coverage.rows.length,
    mathLessons,
    frenchThemeLessons,
    firstSignatureChecks,
    adjacentSetChecks,
    optionVariationChecks,
    practiceTestIsolationChecks,
    issues: issues.length,
  },
  validators: {
    firstExerciseSignatureUniqueWithinLesson: !issues.some((issue) => issue.validator === "firstExerciseSignatureUniqueWithinLesson"),
    noGenericDistractorSet: !issues.some((issue) => issue.validator === "noGenericDistractorSet"),
    noPracticeTestOverlapNormalized: !issues.some((issue) => issue.validator === "noPracticeTestOverlapNormalized"),
    noRepeatedStimulusAcrossAdjacentSets: !issues.some((issue) => issue.validator === "noRepeatedStimulusAcrossAdjacentSets"),
    answerOptionsVaryWhenRetryPolicyChanges: !issues.some((issue) => issue.validator === "answerOptionsVaryWhenRetryPolicyChanges"),
  },
  issues,
};
fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
console.log(JSON.stringify(report, null, 2));
if (issues.length) process.exitCode = 1;
