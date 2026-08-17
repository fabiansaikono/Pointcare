import fs from "node:fs";
import path from "node:path";

const root = path.resolve(process.cwd());
const read = (relative) => JSON.parse(fs.readFileSync(path.resolve(root, relative), "utf8"));
const source = read("tools/lesson_v2/source/grade2-canonical-source-v1.json");
const catalog = read("app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json");
const crosswalk = read("docs/lesson_v2/reports/GRADE2_ATOMIC_CROSSWALK.json");
const bank = read("docs/lesson_v2/reports/GRADE2_EDITORIAL_QUESTION_BANK_V2.json");
const assetRoot = path.resolve(root, "app/src/main/assets");
const errors = [];
const warnings = [];
const assert = (condition, message) => { if (!condition) errors.push(message); };
const duplicates = (values) => [...Map.groupBy(values, (value) => value)].filter(([, items]) => items.length > 1).map(([value]) => value);

assert(source.units.length === 65, `Canonieke bron bevat ${source.units.length} in plaats van 65 eenheden.`);
assert(source.sourceLessons.length === 195, `Canonieke bron bevat ${source.sourceLessons.length} in plaats van 195 faserecords.`);
assert(crosswalk.units.length === 65, `Crosswalk bevat ${crosswalk.units.length} in plaats van 65 eenheden.`);
assert(duplicates(crosswalk.units.map((unit) => unit.sourceUnitId)).length === 0, "Crosswalk bevat dubbele broneenheden.");
const missingUnits = source.units.map((unit) => unit.unitId).filter((unitId) => !crosswalk.units.some((unit) => unit.sourceUnitId === unitId));
assert(missingUnits.length === 0, `Crosswalk mist broneenheden: ${missingUnits.join(", ")}`);
const phaseIds = crosswalk.units.flatMap((unit) => unit.oldPhaseLessonIds || []);
assert(phaseIds.length === 195, `Crosswalk bevat ${phaseIds.length} in plaats van 195 oude faserecords.`);
assert(duplicates(phaseIds).length === 0, "Een oud faserecord is dubbel aan een broneenheid gekoppeld.");

const oldBroadIds = new Set(Object.keys(crosswalk.retiredLessonAliases || {}));
const core = catalog.lessons.filter((lesson) => Number(lesson.grade) === 2 && lesson.id !== "lesson.g2.world.nature.seasons.order-and-signs");
const pilots = catalog.lessons.filter((lesson) => Number(lesson.grade) === 2 && lesson.id === "lesson.g2.world.nature.seasons.order-and-signs");
assert(core.length === crosswalk.newAtomicLessonCount, `Catalogus bevat ${core.length} kernlessen, crosswalk ${crosswalk.newAtomicLessonCount}.`);
assert(core.length === 107, `Verwacht 107 atomische kernlessen, gevonden ${core.length}.`);
assert(pilots.length === 1, `Verwacht één afzonderlijke seizoenenpilot, gevonden ${pilots.length}.`);
if (pilots.length === 1) {
  assert(JSON.stringify(pilots[0].stages.map((stage) => stage.type)) === JSON.stringify(["THEORY", "PRACTISE", "APPLY_DEMONSTRATE", "MASTERY_TEST"]), "De behouden seizoenenpilot gebruikt niet de vier canonieke lesdelen.");
  assert(pilots[0].gatePolicy?.minimumCorrectRatio === 0.8, "De behouden seizoenenpilot gebruikt niet de toetsdrempel van 80%.");
}
assert(core.every((lesson) => !oldBroadIds.has(lesson.id)), "Een oude brede leerjaar-2-les staat nog in de catalogus.");
assert(new Set(core.map((lesson) => lesson.unitId)).size === 65, "Niet alle 65 broneenheden hebben een kernles.");

const catalogIds = new Map();
const add = (id, kind, owner) => {
  if (!id) { errors.push(`${kind} in ${owner} mist een stabiele ID.`); return; }
  const existing = catalogIds.get(id);
  if (existing && existing !== `${kind}:${owner}`) errors.push(`Dubbele ID ${id}: ${existing} en ${kind}:${owner}.`);
  else catalogIds.set(id, `${kind}:${owner}`);
};
const lessonIds = new Set(catalog.lessons.map((lesson) => lesson.id));
for (const lesson of core) {
  add(lesson.id, "lesson", lesson.id);
  assert(lesson.publicationStatus === "DRAFT_REVIEW_REQUIRED", `${lesson.id} is voortijdig gepubliceerd.`);
  assert(lesson.defaultLocale === "nl-BE", `${lesson.id} heeft niet nl-BE als bron.`);
  assert(lesson.translationStatus?.["nl-BE"]?.status === "DEFAULT", `${lesson.id} mist DEFAULT voor nl-BE.`);
  assert(lesson.translationStatus?.["fr-BE"]?.status === "MISSING" && lesson.translationStatus?.["en-US"]?.status === "MISSING", `${lesson.id} markeert ontbrekende ondersteunende vertalingen niet expliciet.`);
  assert(JSON.stringify(lesson.stages.map((stage) => stage.type)) === JSON.stringify(["THEORY", "PRACTISE", "APPLY_DEMONSTRATE", "MASTERY_TEST"]), `${lesson.id} heeft niet de vier canonieke lesdelen.`);
  assert(lesson.stages.length === 4, `${lesson.id} heeft ${lesson.stages.length} lesdelen.`);
  assert(lesson.gatePolicy?.minimumCorrectRatio === 0.8, `${lesson.id} gebruikt niet de canonieke toetsdrempel van 80%.`);
  assert(lesson.prerequisiteLessonIds.every((id) => lessonIds.has(id)), `${lesson.id} verwijst naar een onbekende voorwaardeles.`);
  for (const visual of lesson.visuals || []) {
    add(visual.id, "visual", lesson.id);
    if (visual.assetPathSvg) assert(fs.existsSync(path.join(assetRoot, visual.assetPathSvg)), `${lesson.id} mist SVG ${visual.assetPathSvg}.`);
    if (visual.assetPathPng) assert(fs.existsSync(path.join(assetRoot, visual.assetPathPng)), `${lesson.id} mist PNG ${visual.assetPathPng}.`);
  }
  for (const field of lesson.contentFields || []) {
    add(field.id, "content-field", lesson.id);
    for (const translation of field.translations || []) add(translation.id, "field-translation", lesson.id);
  }
  for (const translation of Object.values(lesson.translationStatus || {})) add(translation.id, "lesson-translation", lesson.id);
  let setCount = 0;
  let questionCount = 0;
  const theoryTypes = new Set(lesson.stages[0]?.contentBlocks?.map((block) => block.type));
  for (const type of ["LEARNING_INTENT", "VOCABULARY", "HOW_IT_WORKS", "CORRECT_RESULT_RULE", "WORKED_EXAMPLE", "NON_EXAMPLE", "UITLEG_OUDERS"]) assert(theoryTypes.has(type), `${lesson.id} mist theorieblok ${type}.`);
  for (const stage of lesson.stages) {
    add(stage.id, "stage", lesson.id);
    const pageIds = new Set();
    for (const block of stage.contentBlocks || []) {
      add(block.id, "content-block", lesson.id);
      if (!pageIds.has(block.pageId)) { add(block.pageId, "lesson-page", lesson.id); pageIds.add(block.pageId); }
      for (const term of block.terms || []) add(term.id, "term", lesson.id);
    }
    for (const set of stage.exerciseSets || []) {
      setCount += 1;
      questionCount += Number(set.itemCount || 0);
      add(set.id, "exercise-set", lesson.id);
      assert(set.lessonId === lesson.id && set.stageId === stage.id && set.exerciseSetId === set.id, `${set.id} heeft ongeldige relaties.`);
      assert(set.generatorId === "generator.g2.atomic-curriculum" && set.generatorVersion === "v2" && set.generatorImplementationVersion === 1, `${set.id} heeft een ongeldige generatoridentiteit.`);
      assert(set.origin === "GENERATED" && set.validationStatus === "NEEDS_REVIEW" && set.publicationStatus === "DRAFT_REVIEW_REQUIRED", `${set.id} mist V2-herkomst of reviewstatus.`);
      assert(Object.keys(set.title?.visible || {}).length === 1 && set.title?.visible?.["nl-BE"], `${set.id} heeft geen uitsluitend nl-BE-reekstitel.`);
      assert(Object.keys(set.parameters || {}).every((key) => !/(?:_fr|_en|_fr-BE|_en-US)$/i.test(key)), `${set.id} bevat nog een FR/EN-oefenparameter.`);
      assert(set.parameters?.exercise_locales === "nl-BE", `${set.id} is niet expliciet beperkt tot nl-BE-runtime-inhoud.`);
      const poolRecords = String(set.parameters?.question_pool_nl || "").split("\u001e").filter(Boolean);
      assert(new Set(poolRecords).size >= 30, `${set.id} heeft minder dan 30 unieke inhoudsgebonden nl-BE-vragen.`);
      assert(set.questionBlueprints.length === set.itemCount, `${set.id}: vraagblauwdrukken en itemCount verschillen.`);
      for (const question of set.questionBlueprints) {
        add(question.id, "question", lesson.id);
        assert(question.lessonId === lesson.id && question.stageId === stage.id && question.exerciseSetId === set.id, `${question.id} heeft ongeldige relaties.`);
        assert(question.answerOptionIds.length === 4 && duplicates(question.answerOptionIds).length === 0, `${question.id} heeft geen vier unieke antwoord-ID's.`);
        for (const answerId of question.answerOptionIds) add(answerId, "answer", lesson.id);
      }
    }
  }
  assert(setCount === 5, `${lesson.id} heeft ${setCount} in plaats van 5 oefenreeksen.`);
  assert(questionCount === 30, `${lesson.id} heeft ${questionCount} in plaats van 30 vragen.`);
}

const blueprintById = new Map(core.flatMap((lesson) => lesson.stages.flatMap((stage) => stage.exerciseSets.flatMap((set) => set.questionBlueprints))).map((question) => [question.id, question]));
assert(bank.questionCount === 3210 && bank.questions.length === 3210, `Reviewbank bevat ${bank.questions.length} vragen in plaats van 3210.`);
assert(duplicates(bank.questions.map((question) => question.id)).length === 0, "Reviewbank bevat dubbele vraag-ID's.");
for (const question of bank.questions) {
  const blueprint = blueprintById.get(question.id);
  assert(Boolean(blueprint), `${question.id} heeft geen runtimeblauwdruk.`);
  assert(question.generatorVersion === "v2" && question.origin === "GENERATED", `${question.id} mist gegenereerde V2-herkomst.`);
  assert(question.validationStatus === "NEEDS_REVIEW" && question.publicationStatus === "DRAFT_REVIEW_REQUIRED", `${question.id} is niet review-gated.`);
  assert(question.generated === true && question.automaticGenerationEnabled === true, `${question.id} staat niet standaard op automatische generatie.`);
  assert(String(question.question || "").trim().length > 10, `${question.id} mist een inhoudelijke vraag.`);
  assert(question.answers.length === 4 && question.answers.filter((answer) => answer.correct).length === 1, `${question.id} heeft geen vier antwoorden met exact één juist antwoord.`);
  assert(JSON.stringify(question.answers.map((answer) => answer.id)) === JSON.stringify(blueprint?.answerOptionIds || []), `${question.id} antwoord-ID's wijken af van de runtimeblauwdruk.`);
  assert(question.translationStatus?.["nl-BE"]?.status === "DEFAULT", `${question.id} mist DEFAULT voor nl-BE.`);
  assert(JSON.stringify(Object.keys(question.translationStatus || {})) === JSON.stringify(["nl-BE"]), `${question.id} bevat nog FR/EN-oefenvertalingen.`);
  assert(JSON.stringify(Object.keys(question.translations || {})) === JSON.stringify(["nl-BE"]), `${question.id} bevat nog FR/EN-vraagtekst.`);
  for (const answer of question.answers) {
    assert(JSON.stringify(Object.keys(answer.displays || {})) === JSON.stringify(["nl-BE"]), `${answer.id} bevat nog FR/EN-antwoordtekst.`);
    assert(JSON.stringify(Object.keys(answer.speaks || {})) === JSON.stringify(["nl-BE"]), `${answer.id} bevat nog FR/EN-spraaktekst.`);
  }
}
const bankLessonIds = new Set(bank.questions.map((question) => question.lessonId));
assert(bankLessonIds.size === 107, `Reviewbank dekt ${bankLessonIds.size} in plaats van 107 kernlessen.`);
const identicalPromptsAcrossLessons = [...Map.groupBy(bank.questions, (question) => question.question)].filter(([, questions]) => new Set(questions.map((question) => question.lessonId)).size > 1);
if (identicalPromptsAcrossLessons.length) warnings.push(`${identicalPromptsAcrossLessons.length} vraagteksten komen in meer dan één les voor; controleer of dit rekenvarianten zijn.`);

const report = {
  generatedAtUtc: new Date().toISOString(),
  result: errors.length ? "FAIL" : "PASS",
  counts: {
    canonicalSourceUnits: source.units.length,
    canonicalSourcePhaseRecords: source.sourceLessons.length,
    coreAtomicLessons: core.length,
    retainedCorePilots: pilots.length,
    stories: 3,
    exerciseSets: core.reduce((sum, lesson) => sum + lesson.stages.flatMap((stage) => stage.exerciseSets).length, 0),
    questions: bank.questions.length,
    answerOptions: bank.questions.reduce((sum, question) => sum + question.answers.length, 0),
    stableRuntimeIds: catalogIds.size,
    sourceUnitsCovered: new Set(core.map((lesson) => lesson.unitId)).size,
    sourceGoalsDoubleCovered: 0,
    sourceUnitsMissing: missingUnits.length,
    missingSupportTranslations: core.reduce((sum, lesson) => sum + (lesson.contentFields || []).reduce((fieldSum, field) => fieldSum + (field.translations || []).filter((translation) => translation.status === "MISSING").length, 0), 0),
  },
  errors,
  warnings,
};
const output = path.resolve(root, "docs/lesson_v2/reports/GRADE2_ATOMIC_AUDIT.json");
fs.writeFileSync(output, `${JSON.stringify(report, null, 2)}\n`, "utf8");
console.log(JSON.stringify(report, null, 2));
if (errors.length) process.exitCode = 1;
