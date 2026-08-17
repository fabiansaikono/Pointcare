import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const read = (relative) => JSON.parse(fs.readFileSync(path.join(root, relative), "utf8"));
const catalog = read("app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json");
const bank = read("docs/lesson_v2/reports/GRADE2_EDITORIAL_QUESTION_BANK_V2.json");
const stories = read("app/src/main/assets/poincare/content/stories/poincare-stories-v1.json");
const books = read("app/src/main/assets/poincare/content/stories/poincare-story-books-v1.json");
const sources = read("docs/lesson_v2/reference/GRADE12_CONTENT_SOURCES_V2.json");
const migration = read("docs/lesson_v2/reports/GRADE12_CONTENT_FIELD_SCOPE_MIGRATION.json");
const outputPath = path.join(root, "docs/lesson_v2/reports/GRADE12_CHILD_CONTENT_AUDIT.json");

const issues = [];
const warn = (code, owner, message) => issues.push({ code, owner, message });
const visible = (text) => text?.visible?.["nl-BE"] || "";
const localeKeys = (text) => Object.keys(text?.visible || {}).sort();
const hierarchyFields = ["title", "summary", "description", "pedagogicalLogic"];
const forbidden = [
  "kernbegrip waarmee",
  "docent modelleert",
  "kernmodel",
  "succescriteria",
  "welke uitspraak past bij",
  "wat moet je kunnen bij",
  "welk kernwoord hoort",
  "welk model helpt",
  "zonder plan meteen een antwoord raden",
  "uitkomst past bij",
  "je benoemt het kernbegrip",
  "ik kan controleren of mijn antwoord, uitvoering of conclusie klopt",
  "bij deze les gebruik je"
];

function checkDutchHierarchy(text, owner, field) {
  const keys = localeKeys(text);
  if (keys.length !== 1 || keys[0] !== "nl-BE") warn("HIERARCHY_LOCALE", owner, `${field} moet uitsluitend nl-BE bevatten; gevonden: ${keys.join(", ")}`);
  if (!visible(text).trim()) warn("HIERARCHY_BLANK", owner, `${field} is leeg.`);
}

function duplicateValues(values) {
  const seen = new Set();
  const duplicates = new Set();
  values.forEach((value) => seen.has(value) ? duplicates.add(value) : seen.add(value));
  return [...duplicates];
}

const ids = { lessons: [], visuals: [], stages: [], blocks: [], terms: [], sets: [], fields: [], fieldTranslations: [], blueprints: [], questions: [], answers: [] };
const lessonById = new Map(catalog.lessons.map((lesson) => [lesson.id, lesson]));
const setById = new Map();
let parentBlocks = 0;
let exerciseSets = 0;
let supportFields = 0;
let missingSupportTranslations = 0;

for (const lesson of catalog.lessons.filter((item) => [1, 2].includes(item.grade))) {
  ids.lessons.push(lesson.id);
  hierarchyFields.forEach((field) => checkDutchHierarchy(lesson[field], lesson.id, field));
  if (lesson.defaultLocale !== "nl-BE") warn("DEFAULT_LOCALE", lesson.id, "De standaardtaal is niet nl-BE.");
  const lessonStatuses = lesson.translationStatus || {};
  if (lessonStatuses["nl-BE"]?.status !== "DEFAULT" || lessonStatuses["fr-BE"]?.status !== "MISSING" || lessonStatuses["en-US"]?.status !== "MISSING") {
    warn("LESSON_TRANSLATION_STATUS", lesson.id, "De les moet nl-BE als DEFAULT en FR/EN expliciet als MISSING markeren.");
  }
  for (const visual of lesson.visuals || []) {
    ids.visuals.push(visual.id);
    const keys = Object.keys(visual.altText || {});
    if (keys.length !== 1 || keys[0] !== "nl-BE") warn("VISUAL_ALT_LOCALE", visual.id, "Alt-tekst van de hiërarchie moet nl-BE zijn.");
  }
  const theory = lesson.stages.find((stage) => ["THEORY", "ACQUIRE_UNDERSTAND"].includes(stage.type));
  if (!theory) warn("THEORY_MISSING", lesson.id, "Geen theoriedeel gevonden.");
  const parent = (theory?.contentBlocks || []).filter((block) => block.type === "UITLEG_OUDERS");
  if (parent.length !== 1) warn("PARENT_EXPLANATION_COUNT", lesson.id, `Verwacht precies één Uitleg_Ouders-blok; gevonden ${parent.length}.`);
  for (const block of parent) {
    parentBlocks += 1;
    const body = visible(block.body);
    ["Wat leert uw kind?", "Zo legt u het uit", "Voorbeeld", "Veelgemaakte fout", "Oefentip voor thuis"].forEach((heading) => {
      if (!body.includes(heading)) warn("PARENT_EXPLANATION_SECTION", block.id, `Onderdeel ontbreekt: ${heading}`);
    });
    if (block.metadata?.audience !== "PARENT") warn("PARENT_AUDIENCE", block.id, "Audience moet PARENT zijn.");
    if (!block.metadata?.sourceIds) warn("SOURCE_LINK", block.id, "Bronverwijzingen ontbreken.");
  }
  for (const stage of lesson.stages) {
    ids.stages.push(stage.id);
    if (stage.title) checkDutchHierarchy(stage.title, stage.id, "stage.title");
    if (stage.sectionTitle) checkDutchHierarchy(stage.sectionTitle, stage.id, "stage.sectionTitle");
    for (const block of stage.contentBlocks || []) {
      ids.blocks.push(block.id);
      if (block.title && localeKeys(block.title).some((locale) => locale !== "nl-BE")) warn("BLOCK_INLINE_TRANSLATION", block.id, "Ondersteunende vertalingen horen via contentFields, niet in het hiërarchieobject.");
      if (block.body && localeKeys(block.body).some((locale) => locale !== "nl-BE")) warn("BLOCK_INLINE_TRANSLATION", block.id, "Ondersteunende vertalingen horen via contentFields, niet in het hiërarchieobject.");
      for (const term of block.terms || []) ids.terms.push(term.id);
    }
    for (const set of stage.exerciseSets || []) {
      exerciseSets += 1;
      ids.sets.push(set.id);
      setById.set(set.id, set);
      checkDutchHierarchy(set.title, set.id, "exerciseSet.title");
      if (set.lessonId !== lesson.id || set.stageId !== stage.id || set.exerciseSetId !== set.id) warn("SET_RELATION", set.id, "Les-, deel- of reeksrelatie klopt niet.");
      if (set.generatorVersion !== "v2") warn("GENERATOR_VERSION", set.id, "generatorVersion is niet v2.");
      if (!set.origin || !set.validationStatus || !set.publicationStatus) warn("EDITORIAL_METADATA", set.id, "Herkomst, validatie- of publicatiestatus ontbreekt.");
      const parameterKeys = Object.keys(set.parameters || {});
      if (parameterKeys.some((key) => /(?:_fr|_en|_fr-BE|_en-US)$/i.test(key))) warn("EXERCISE_TRANSLATION_PARAMETER", set.id, "Een FR/EN-oefenparameter is achtergebleven.");
      if (set.parameters?.exercise_locales !== "nl-BE") warn("EXERCISE_LOCALE_SCOPE", set.id, "De V2-reeks is niet expliciet beperkt tot nl-BE-runtime-inhoud.");
      if (["generator.g1.math.concepts", "generator.g1.language.french_basics", "generator.g1.applied_domains", "generator.g2.atomic-curriculum"].includes(set.generatorId)) {
        const poolRecords = String(set.parameters?.question_pool_nl || "").split("\u001e").filter(Boolean);
        const uniquePoolRecords = new Set(poolRecords).size;
        const minimumPoolRecords = Math.max(set.itemCount, 12);
        if (uniquePoolRecords < minimumPoolRecords) warn("REVIEWED_POOL_TOO_SMALL", set.id, `De inhoudsgebonden nl-BE-vragenpool heeft slechts ${uniquePoolRecords} unieke vragen; verwacht minstens ${minimumPoolRecords}.`);
      }
      for (const question of set.questionBlueprints || []) {
        ids.blueprints.push(question.id);
        if (question.lessonId !== lesson.id || question.stageId !== stage.id || question.exerciseSetId !== set.id) warn("BLUEPRINT_RELATION", question.id, "Vraagblauwdruk verwijst naar de verkeerde eigenaar.");
      }
    }
  }
  const validOwners = new Set(lesson.stages.flatMap((stage) => stage.contentBlocks.flatMap((block) => [block.id, ...(block.terms || []).map((term) => term.id)])));
  for (const field of lesson.contentFields || []) {
    supportFields += 1;
    ids.fields.push(field.id);
    if (!validOwners.has(field.ownerId)) warn("CONTENT_FIELD_SCOPE", field.id, "Veld hoort niet bij een inhoudstype of kernwoord.");
    const statuses = Object.fromEntries((field.translations || []).map((item) => [item.locale, item]));
    if (statuses["nl-BE"]?.status !== "DEFAULT") warn("CONTENT_NL_STATUS", field.id, "nl-BE is niet DEFAULT.");
    for (const locale of ["fr-BE", "en-US"]) {
      if (statuses[locale]?.status !== "MISSING" || statuses[locale]?.fallbackLocale !== "nl-BE") warn("CONTENT_SUPPORT_STATUS", field.id, `${locale} moet MISSING zijn met expliciete nl-BE-fallback.`);
      else missingSupportTranslations += 1;
    }
    for (const item of field.translations || []) ids.fieldTranslations.push(item.id);
  }
}

const searchableCatalog = JSON.stringify(catalog).toLocaleLowerCase("nl-BE");
for (const phrase of forbidden) if (searchableCatalog.includes(phrase)) warn("FORBIDDEN_CONCEPTUAL_COPY", "catalog", `Verboden generieke formulering gevonden: ${phrase}`);

const questionById = new Map();
for (const question of bank.questions || []) {
  ids.questions.push(question.id);
  questionById.set(question.id, question);
  if (!lessonById.has(question.lessonId) || !setById.has(question.exerciseSetId)) warn("QUESTION_RELATION", question.id, "Vraag verwijst naar een onbekende les of reeks.");
  if (Object.keys(question.translations || {}).join() !== "nl-BE") warn("QUESTION_LOCALES", question.id, "Vraag bevat meer dan nl-BE.");
  if (Object.keys(question.translationStatus || {}).join() !== "nl-BE") warn("QUESTION_STATUS_LOCALES", question.id, "Vraagstatus bevat meer dan nl-BE.");
  if (!question.question?.trim() || !question.explanation?.trim()) warn("QUESTION_CONTENT", question.id, "Vraag of uitleg is leeg.");
  if ((question.answers || []).length < 2) warn("ANSWER_COUNT", question.id, "Te weinig antwoordopties.");
  if ((question.answers || []).filter((answer) => answer.correct).length !== 1) warn("CORRECT_ANSWER_COUNT", question.id, "Er moet precies één juist antwoord zijn.");
  const values = (question.answers || []).map((answer) => answer.value.trim().toLocaleLowerCase("nl-BE"));
  if (duplicateValues(values).length) warn("DUPLICATE_ANSWERS", question.id, "Antwoordopties zijn niet uniek.");
  for (const answer of question.answers || []) {
    ids.answers.push(answer.id);
    if (Object.keys(answer.displays || {}).join() !== "nl-BE" || Object.keys(answer.speaks || {}).join() !== "nl-BE") warn("ANSWER_LOCALES", answer.id, "Antwoord bevat meer dan nl-BE.");
  }
}

const searchableBank = JSON.stringify(bank).toLocaleLowerCase("nl-BE");
for (const phrase of forbidden) if (searchableBank.includes(phrase)) warn("FORBIDDEN_QUESTION_COPY", "question-bank", `Verboden generieke formulering gevonden: ${phrase}`);

for (const blueprintId of ids.blueprints.filter((id) => id.startsWith("lesson.g2.") && !id.includes("seasons.order-and-signs"))) {
  if (!questionById.has(blueprintId)) warn("QUESTION_BANK_COVERAGE", blueprintId, "Leerjaar-2-vraagblauwdruk ontbreekt in de redactionele vragenbank.");
}

for (const [kind, values] of Object.entries(ids)) {
  for (const duplicate of duplicateValues(values)) warn("DUPLICATE_ID", duplicate, `Dubbele ID binnen ${kind}.`);
}

const storyById = new Map(stories.stories.map((story) => [story.id, story]));
let translatedStories = 0;
for (const story of stories.stories.filter((item) => [1, 2].includes(item.grade))) {
  const locales = Object.keys(story.translations || {});
  if (["nl-BE", "fr-BE", "en-US"].every((locale) => locales.includes(locale)) && story.pages.every((page) => ["nl-BE", "fr-BE", "en-US"].every((locale) => page.visibleText?.[locale]))) translatedStories += 1;
  else warn("STORY_TRANSLATION", story.id, "Bestaande NL/FR/EN-verhaalvertaling is onvolledig.");
}
for (const book of books.books.filter((item) => [1, 2].includes(item.grade))) {
  for (const storyId of book.storyIds || []) if (!storyById.has(storyId)) warn("BOOK_STORY_RELATION", book.id, `Onbekend verhaal: ${storyId}`);
}
if ((sources.sources || []).length < 5) warn("SOURCE_MANIFEST", "sources", "Te weinig erkende bronnen vastgelegd.");
if (!migration.migrationId || !migration.retiredFieldIds?.length) warn("FIELD_MIGRATION", "migration", "Expliciete veldmigratie ontbreekt.");

const report = {
  generatedAt: new Date().toISOString(),
  result: issues.length ? "FAILED" : "PASSED",
  counts: {
    lessons: ids.lessons.length,
    grade1Lessons: catalog.lessons.filter((lesson) => lesson.grade === 1).length,
    grade2Lessons: catalog.lessons.filter((lesson) => lesson.grade === 2).length,
    parentExplanationBlocks: parentBlocks,
    exerciseSets,
    grade2Questions: ids.questions.length,
    answerOptions: ids.answers.length,
    translatableContentFields: supportFields,
    missingSupportTranslations,
    storiesWithCompleteNlFrEnText: translatedStories,
    registeredSources: sources.sources.length,
    retiredHierarchyFields: migration.retiredFieldIds.length,
    issues: issues.length
  },
  issues
};

fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
console.log(JSON.stringify(report, null, 2));
if (issues.length) process.exitCode = 1;
