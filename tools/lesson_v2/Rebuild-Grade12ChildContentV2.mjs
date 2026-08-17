import fs from "node:fs";
import path from "node:path";
import {
  buildQuestionPool,
  grade1Guide,
  grade1FallbackGuide,
  grade2Guide,
  parentExplanation,
} from "./grade12-child-content-guides.mjs";
import { childVocabularyDefinition } from "./grade12-child-vocabulary.mjs";

const root = process.cwd();
const catalogPath = path.join(root, "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json");
const grade1ProfilesPath = path.join(root, "app/src/main/resources/poincare/curriculum/grade1-unit-knowledge-profiles.json");
const grade2SourcePath = path.join(root, "tools/lesson_v2/source/grade2-canonical-source-v1.json");
const questionBankPath = path.join(root, "docs/lesson_v2/reports/GRADE2_EDITORIAL_QUESTION_BANK_V2.json");
const reportPath = path.join(root, "docs/lesson_v2/reports/GRADE12_CHILD_CONTENT_REWRITE.json");
const fieldMigrationPath = path.join(root, "docs/lesson_v2/reports/GRADE12_CONTENT_FIELD_SCOPE_MIGRATION.json");
const testGeneratorMigrationPath = path.join(root, "docs/lesson_v2/reports/GRADE1_MASTERY_GENERATOR_MIGRATION_V2.json");

const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
const grade1Profiles = JSON.parse(fs.readFileSync(grade1ProfilesPath, "utf8"));
const grade2Source = JSON.parse(fs.readFileSync(grade2SourcePath, "utf8"));
const questionBank = JSON.parse(fs.readFileSync(questionBankPath, "utf8"));
const grade1ByUnit = new Map(grade1Profiles.map((profile) => [profile.unitId, profile]));
const grade2ByUnit = new Map(grade2Source.units.map((unit) => [unit.unitId, unit]));

function collectStableIds(catalogValue, questionBankValue) {
  const result = { lessons: [], visuals: [], stages: [], blocks: [], terms: [], sets: [], blueprints: [], questions: [], answers: [] };
  for (const lesson of catalogValue.lessons) {
    result.lessons.push(lesson.id);
    for (const visual of lesson.visuals || []) result.visuals.push(visual.id);
    for (const stage of lesson.stages || []) {
      result.stages.push(stage.id);
      for (const block of stage.contentBlocks || []) {
        if (block.type !== "UITLEG_OUDERS") result.blocks.push(block.id);
        for (const term of block.terms || []) result.terms.push(term.id);
      }
      for (const set of stage.exerciseSets || []) {
        result.sets.push(set.id);
        for (const blueprint of set.questionBlueprints || []) result.blueprints.push(blueprint.id);
      }
    }
  }
  for (const question of questionBankValue.questions || []) {
    result.questions.push(question.id);
    for (const answer of question.answers || []) result.answers.push(answer.id);
  }
  return Object.fromEntries(Object.entries(result).map(([key, values]) => [key, values.sort()]));
}

const stableIdsBefore = collectStableIds(catalog, questionBank);

const nlText = (value) => ({ visible: { "nl-BE": String(value || "").trim() } });
const stable = (value) => String(value).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
const clean = (value) => String(value || "").replace(/\s+/g, " ").trim();
const localizeHierarchy = (text) => text ? nlText(text.visible?.["nl-BE"] || Object.values(text.visible || {})[0] || "") : text;
const poolRecordSeparator = "\u001e";
const poolFieldSeparator = "\u001f";
const genericGeneratorIds = new Set([
  "generator.g1.math.concepts",
  "generator.g1.language.french_basics",
  "generator.g1.applied_domains",
  "generator.g1.curriculum.catalog",
  "generator.g2.atomic-curriculum",
]);

function exerciseTranslationStatus(ownerId) {
  return {
    "nl-BE": { id: `${ownerId}.nl-be`, status: "DEFAULT", comment: "Brontaal en standaardtaal." },
  };
}

function lessonTranslationStatus(ownerId) {
  return {
    "nl-BE": { id: `${ownerId}.nl-be`, status: "DEFAULT", comment: "Brontaal en standaardtaal." },
    "fr-BE": { id: `${ownerId}.fr-be`, status: "MISSING", comment: "Ondersteunende vertaling ontbreekt; nl-BE blijft zichtbaar als fallback." },
    "en-US": { id: `${ownerId}.en-us`, status: "MISSING", comment: "Ondersteunende vertaling ontbreekt; nl-BE blijft zichtbaar als fallback." },
  };
}

function contentField(id, ownerId, fieldPath) {
  return {
    id,
    ownerId,
    path: fieldPath,
    translations: [
      { id: `${id}.translation.nl-be`, locale: "nl-BE", status: "DEFAULT", comment: "Brontaal nl-BE.", fallbackLocale: null },
      { id: `${id}.translation.fr-be`, locale: "fr-BE", status: "MISSING", comment: "Ondersteunende vertaling nog te schrijven en na te kijken.", fallbackLocale: "nl-BE" },
      { id: `${id}.translation.en-us`, locale: "en-US", status: "MISSING", comment: "Ondersteunende vertaling nog te schrijven en na te kijken.", fallbackLocale: "nl-BE" },
    ],
  };
}

function theoryStage(lesson) {
  return lesson.stages.find((stage) => stage.type === "THEORY" || stage.type === "ACQUIRE_UNDERSTAND");
}

function blockBy(theory, type) {
  return theory.contentBlocks.find((block) => block.type === type);
}

function setBody(block, value, title) {
  if (!block) return;
  block.body = nlText(value);
  if (title) block.title = nlText(title);
}

function definitionFor(term, profile) {
  const word = clean(term).toLocaleLowerCase("nl-BE");
  const lessonDefinition = profile.facts.find((fact) => fact.toLocaleLowerCase("nl-BE").includes(word));
  if (lessonDefinition) return lessonDefinition;
  const glossaryDefinition = childVocabularyDefinition(term);
  if (glossaryDefinition) return glossaryDefinition;
  throw new Error(`Geen kindvriendelijke definitie voor kernwoord: ${term}`);
}

function rewriteVocabulary(block, profile) {
  if (!block) return;
  block.title = nlText("Woorden die je nodig hebt");
  const sourceTerms = block.terms || [];
  const words = sourceTerms.map((term) => term.term?.visible?.["nl-BE"]).filter(Boolean);
  block.terms = words.map((word, index) => {
    const old = sourceTerms[index];
    const id = old?.id || `${block.id}.term.${stable(word)}.${index + 1}`;
    return {
      id,
      term: nlText(word),
      definition: nlText(definitionFor(word, profile)),
      example: nlText(profile.examples[index % profile.examples.length]),
      contentFieldIds: {
        term: old?.contentFieldIds?.term || `${id}.field.term`,
        definition: old?.contentFieldIds?.definition || `${id}.field.definition`,
        example: old?.contentFieldIds?.example || `${id}.field.example`,
      },
    };
  });
}

function makeParentBlock(lesson, theory, profile) {
  const id = `${lesson.id}.parent-explanation`;
  return {
    id,
    pageId: `${theory.id}.page.3`,
    pageSequence: 3,
    type: "UITLEG_OUDERS",
    title: nlText("Uitleg voor ouders"),
    body: nlText(parentExplanation(profile)),
    terms: [],
    visualIds: [],
    contentFieldIds: { title: `${id}.field.title`, body: `${id}.field.body` },
    metadata: { audience: "PARENT", sourceIds: profile.sources.join("|") },
  };
}

function rewriteTheory(lesson, profile) {
  const theory = theoryStage(lesson);
  if (!theory) throw new Error(`${lesson.id} heeft geen theoriedeel.`);
  theory.title = nlText("Theorie");
  const content = theory.contentBlocks.filter((block) => block.type !== "UITLEG_OUDERS");
  const intent = blockBy(theory, "LEARNING_INTENT");
  setBody(intent, profile.goal, "Dit leer je");
  rewriteVocabulary(blockBy(theory, "VOCABULARY"), profile);
  const meaning = blockBy(theory, "CHILD_FRIENDLY_DEFINITION");
  setBody(meaning, profile.facts.join("\n\n"), "Zo zit het in elkaar");
  const how = blockBy(theory, "HOW_IT_WORKS");
  setBody(how, profile.steps.map((step, index) => `${index + 1}. ${step}`).join("\n"), "Zo doe je het");
  setBody(blockBy(theory, "CORRECT_RESULT_RULE"), profile.check, "Zo controleer je");
  const examples = content.filter((block) => block.type === "WORKED_EXAMPLE");
  examples.forEach((block, index) => setBody(block, profile.examples[index % profile.examples.length], `Voorbeeld ${index + 1}`));
  setBody(blockBy(theory, "NON_EXAMPLE"), profile.caution, "Let op");
  theory.contentBlocks = [...content, makeParentBlock(lesson, theory, profile)];
}

function encodePool(questions) {
  return questions.map((question) => [
    question.prompt,
    question.answer,
    ...question.options.filter((option) => option !== question.answer).slice(0, 3),
    question.explanation,
  ].map((value) => clean(value).replace(/[\u001e\u001f]/g, " ")).join(poolFieldSeparator)).join(poolRecordSeparator);
}

function scrubExerciseLocalization(set, pool, profile, lesson) {
  set.title = localizeHierarchy(set.title);
  const parameters = {};
  for (const [key, value] of Object.entries(set.parameters || {})) {
    if (/(?:_fr|_en|_fr-BE|_en-US)$/i.test(key)) continue;
    parameters[key] = value;
  }
  if (genericGeneratorIds.has(set.generatorId)) parameters.question_pool_nl = encodePool(pool);
  if (set.generatorId.startsWith("generator.g1.") && genericGeneratorIds.has(set.generatorId)) {
    for (const key of Object.keys(parameters).filter((key) => key.startsWith("distractor_"))) delete parameters[key];
    parameters.title_nl = lesson.title?.visible?.["nl-BE"] || "";
    parameters.focus_nl = profile.facts[0];
    parameters.target_nl = profile.goal;
    parameters.how_nl = profile.steps.join(" Daarna: ");
    parameters.check_nl = profile.check;
    parameters.example_nl = profile.examples[0];
    parameters.non_example_nl = profile.caution;
  }
  set.parameters = parameters;
}

function hierarchyToDutch(lesson, profile) {
  lesson.title = localizeHierarchy(lesson.title);
  lesson.summary = nlText(profile.goal);
  lesson.description = nlText(`${profile.goal} De les gebruikt korte uitleg, concrete voorbeelden, gerichte oefening en een toets.`);
  lesson.pedagogicalLogic = nlText(`Eerst ziet het kind wat de leerstof betekent. Daarna volgt begeleid oefenen, zelfstandig toepassen en controleren.`);
  lesson.defaultLocale = "nl-BE";
  lesson.translationStatus = lessonTranslationStatus(`${lesson.id}.translation-status`);
  lesson.visuals = (lesson.visuals || []).map((visual) => ({ ...visual, altText: { "nl-BE": visual.altText?.["nl-BE"] || profile.examples[0] } }));
  for (const stage of lesson.stages) {
    stage.title = localizeHierarchy(stage.title);
    stage.sectionTitle = localizeHierarchy(stage.sectionTitle);
    for (const block of stage.contentBlocks || []) {
      block.title = localizeHierarchy(block.title);
      if (block.type !== "UITLEG_OUDERS" && block.body) block.body = nlText(block.body.visible?.["nl-BE"] || "");
    }
  }
}

function rebuildContentFields(lesson) {
  const fields = [];
  const seen = new Set();
  const add = (id, ownerId, fieldPath) => {
    if (!id || seen.has(id)) return;
    seen.add(id);
    fields.push(contentField(id, ownerId, fieldPath));
  };
  for (const stage of lesson.stages) {
    for (const block of stage.contentBlocks || []) {
      for (const fieldPath of ["title", "body"]) {
        if (!block[fieldPath]) continue;
        block.contentFieldIds ||= {};
        block.contentFieldIds[fieldPath] ||= `${block.id}.field.${stable(fieldPath)}`;
        add(block.contentFieldIds[fieldPath], block.id, fieldPath);
      }
      for (const term of block.terms || []) {
        for (const fieldPath of ["term", "definition", "example", "nonExample"]) {
          if (!term[fieldPath]) continue;
          term.contentFieldIds ||= {};
          term.contentFieldIds[fieldPath] ||= `${term.id}.field.${stable(fieldPath)}`;
          add(term.contentFieldIds[fieldPath], term.id, fieldPath);
        }
      }
    }
  }
  lesson.contentFields = fields;
}

function profileFor(lesson) {
  if (lesson.grade === 1) {
    const source = grade1ByUnit.get(lesson.unitId);
    return source ? grade1Guide(lesson, source) : grade1FallbackGuide(lesson);
  }
  return grade2Guide(lesson, grade2ByUnit.get(lesson.unitId));
}

const profilesByLesson = new Map();
let setCount = 0;
let parentBlockCount = 0;
const testGeneratorMigrations = [];
for (const lesson of catalog.lessons.filter((item) => item.grade === 1 || item.grade === 2)) {
  const profile = profileFor(lesson);
  profilesByLesson.set(lesson.id, profile);
  rewriteTheory(lesson, profile);
  hierarchyToDutch(lesson, profile);
  const pool = buildQuestionPool(profile, lesson.grade);
  for (const stage of lesson.stages) {
    for (const set of stage.exerciseSets || []) {
      if (lesson.grade === 1 && stage.type === "MASTERY_TEST") {
        const previousGeneratorId = set.generatorId;
        set.generatorId = "generator.g1.curriculum.catalog";
        set.generatorImplementationVersion = 1;
        set.parameters = {
          grade: "1",
          unit_id: lesson.unitId,
          domain: lesson.legacyRouting.domain,
          skill: lesson.masteryTags?.[0] || lesson.id,
          phase: "test",
          editorial_status: "approved",
          exclude_recent_exact_items: "true",
        };
        testGeneratorMigrations.push({
          lessonId: lesson.id,
          exerciseSetId: set.id,
          previousGeneratorId,
          generatorId: set.generatorId,
          stableIdsPreserved: true,
        });
      }
      scrubExerciseLocalization(set, pool, profile, lesson);
      set.parameters = { ...(set.parameters || {}), exercise_locales: "nl-BE" };
      setCount += 1;
    }
  }
  rebuildContentFields(lesson);
  parentBlockCount += 1;
  lesson.definitionVersion = Math.max(Number(lesson.definitionVersion || 1), 3);
}

function poolQuestionFor(question, localIndex) {
  const profile = profilesByLesson.get(question.lessonId);
  if (!profile) throw new Error(`Geen profiel voor vraag ${question.id}`);
  const pool = buildQuestionPool(profile, 2);
  return pool[localIndex % pool.length];
}

const questionIndexByLesson = new Map();
for (const question of questionBank.questions) {
  const localIndex = questionIndexByLesson.get(question.lessonId) || 0;
  questionIndexByLesson.set(question.lessonId, localIndex + 1);
  const replacement = poolQuestionFor(question, localIndex);
  question.question = replacement.prompt;
  question.explanation = replacement.explanation;
  question.translations = { "nl-BE": { question: replacement.prompt, explanation: replacement.explanation } };
  question.translationStatus = exerciseTranslationStatus(`${question.id}.translation-status`);
  question.editorComments = { "nl-BE": "" };
  const rotated = [...replacement.options.slice(localIndex % 4), ...replacement.options.slice(0, localIndex % 4)];
  question.answers = question.answers.map((answer, answerIndex) => {
    const value = rotated[answerIndex];
    return {
      ...answer,
      value,
      correct: value === replacement.answer,
      displays: { "nl-BE": value },
      speaks: { "nl-BE": value },
      translationStatus: exerciseTranslationStatus(`${answer.id}.translation-status`),
    };
  });
}
questionBank.sourceLocale = "nl-BE";
questionBank.exerciseLocales = ["nl-BE"];
questionBank.translationPolicy = "Questions and answers are nl-BE only; support translations belong to lesson content fields.";

const stableIdsAfter = collectStableIds(catalog, questionBank);
const stableIdsPreserved = JSON.stringify(stableIdsBefore) === JSON.stringify(stableIdsAfter);
if (!stableIdsPreserved) throw new Error("Een bestaande stabiele ID werd tijdens de inhoudelijke herwerking gewijzigd.");

const retiredHierarchyFieldIds = catalog.lessons.flatMap((lesson) => [
  ...["title", "summary", "description", "pedagogicalLogic"].map((field) => `${lesson.id}.field.${stable(field)}`),
  ...(lesson.visuals || []).map((visual) => `${visual.id}.field.alt-text`),
  ...lesson.stages.flatMap((stage) => [
    `${stage.id}.field.title`,
    ...(stage.exerciseSets || []).map((set) => `${set.id}.field.title`),
  ]),
]);
const fieldMigration = {
  schemaVersion: 1,
  migrationId: "migration.grade12.v2.content-fields.content-types-only.2026-08-03",
  reason: "Categorieën, subcategorieën, lessen en onderliggende hiërarchielabels blijven nl-BE. Alleen velden van inhoudstypes zijn ondersteunend vertaalbaar.",
  sourceLocale: "nl-BE",
  retiredFieldStatus: "RETIRED_NON_TRANSLATABLE_HIERARCHY",
  retiredFieldIds: [...new Set(retiredHierarchyFieldIds)].sort(),
  addedParentContentFieldIds: catalog.lessons.flatMap((lesson) => [
    `${lesson.id}.parent-explanation.field.title`,
    `${lesson.id}.parent-explanation.field.body`,
  ]).sort(),
};

const report = {
  generatedAt: new Date().toISOString(),
  scope: { grades: [1, 2], ageRanges: { "1": "6-7", "2": "7-8" } },
  lessonsRewritten: profilesByLesson.size,
  grade1Lessons: catalog.lessons.filter((lesson) => lesson.grade === 1).length,
  grade2Lessons: catalog.lessons.filter((lesson) => lesson.grade === 2).length,
  parentExplanationBlocks: parentBlockCount,
  exerciseSetsDutchOnly: setCount,
  grade2EditorialQuestionsRewritten: questionBank.questions.length,
  hierarchyLocales: ["nl-BE"],
  exerciseLocales: ["nl-BE"],
  supportContentLocales: ["nl-BE", "fr-BE", "en-US"],
  supportTranslationsStatus: { "nl-BE": "DEFAULT", "fr-BE": "MISSING", "en-US": "MISSING" },
  stableIdsPreserved,
  explicitlyRetiredHierarchyContentFields: fieldMigration.retiredFieldIds.length,
  grade1MasteryGeneratorMigrations: testGeneratorMigrations.length,
};

const testGeneratorMigration = {
  schemaVersion: 1,
  migrationId: "migration.grade1.v2.mastery-reviewed-pool.2026-08-03",
  reason: "De toets gebruikt een lesgebonden, redactioneel controleerbare nl-BE-vragenbank met voldoende ongeziene items; oefen- en toepassingsdelen behouden hun gespecialiseerde runtime.",
  stableIdsChanged: false,
  migrations: testGeneratorMigrations,
};

fs.writeFileSync(catalogPath, `${JSON.stringify(catalog, null, 2)}\n`, "utf8");
fs.writeFileSync(questionBankPath, `${JSON.stringify(questionBank, null, 2)}\n`, "utf8");
fs.writeFileSync(fieldMigrationPath, `${JSON.stringify(fieldMigration, null, 2)}\n`, "utf8");
fs.writeFileSync(testGeneratorMigrationPath, `${JSON.stringify(testGeneratorMigration, null, 2)}\n`, "utf8");
fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
console.log(JSON.stringify(report, null, 2));
