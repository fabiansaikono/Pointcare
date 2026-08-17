import fs from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const grade = Number(process.env.POINCARE_HIERARCHY_GRADE || 1);
const dateStamp = process.env.POINCARE_HIERARCHY_DATE || "20260731";
const sourceDirectory = path.join(root, "outputs", `exercise-hierarchy-${dateStamp}`, "source");
const outputDirectory = path.join(root, "outputs", `exercise-hierarchy-${dateStamp}`);
const outputName = `Poincare_Alle_Oefeningen_Hierarchie_Leerjaar${grade}.json`;
const outputPath = path.join(outputDirectory, outputName);
const convenientOutputPath = path.join(root, outputName);

const lessonCatalogPath = path.join(
  root,
  "app",
  "src",
  "main",
  "assets",
  "poincare",
  "curriculum",
  "lesson-catalog-v2.json"
);
const lessonExercisePath = path.join(sourceDirectory, "grade1-v2-validation-exercises.json");
const questionAuditPath = path.join(sourceDirectory, "poincare-question-audit.json");

const categoryDefinitions = [
  {
    id: "READING",
    title: { nl: "Lezen & luisteren", en: "Reading & listening", fr: "Lecture & écoute" },
    tiles: [
      ["reading", "Lezen"], ["review", "Herhalen"], ["listening", "Luisteren"],
      ["speaking", "Spreken & presenteren"], ["writing", "Schrijven"], ["dictation", "Dictee"],
      ["vocabulary", "Woordenschat"], ["translation_nl_to_en", "Nederlands naar Engels"],
      ["translation_nl_to_fr", "Nederlands naar Frans"], ["translation_en_to_nl", "Engels naar Nederlands"],
      ["translation_en_to_fr", "Engels naar Frans"], ["translation_fr_to_nl", "Frans naar Nederlands"],
      ["translation_fr_to_en", "Frans naar Engels"]
    ]
  },
  {
    id: "WRITING",
    title: { nl: "Spelling & grammatica", en: "Spelling & grammar", fr: "Orthographe & grammaire" },
    tiles: [
      ["letter_tracing", "Letters schrijven"], ["writing", "Schrijven"], ["spelling", "Spelling"],
      ["sentence_order", "Zinsvolgorde"], ["grammar", "Grammatica"], ["question_words", "Vraagwoorden"],
      ["singular_plural", "Enkelvoud en meervoud"], ["conjugations", "Vervoegingen"],
      ["verbs", "Werkwoorden"], ["past_tense", "Verleden tijden"], ["future_tense", "Toekomende tijden"]
    ]
  },
  {
    id: "MATH",
    title: { nl: "Getallen", en: "Numbers", fr: "Nombres" },
    tiles: [
      ["addition", "Optellen"], ["subtraction", "Aftrekken"], ["multiplication", "Vermenigvuldigen"],
      ["division", "Delen"], ["mixed_arithmetic", "Gemengde bewerkingen"],
      ["number_sequences", "Getallenreeksen"], ["statistics", "Statistiek & gegevens"],
      ["fractions", "Breuken"], ["money", "Geld"]
    ]
  },
  {
    id: "GEOMETRY",
    title: { nl: "Meten & meetkunde", en: "Measurement & geometry", fr: "Mesures & géométrie" },
    tiles: [
      ["time", "Tijd"], ["measurement", "Meten"], ["temperature", "Temperatuur"],
      ["compare_size", "Vergelijken, ordenen & schatten"], ["geometry_shapes", "Vormen en meetkunde"]
    ]
  },
  {
    id: "WORLD_KNOWLEDGE",
    title: { nl: "Wereldkennis", en: "World knowledge", fr: "Connaissance du monde" },
    tiles: [
      ["space", "Ruimte"], ["nature", "Natuur & levende wereld"], ["animals", "Dieren"],
      ["plants", "Planten"], ["weather_climate", "Weer en klimaat"], ["science", "Wetenschap & onderzoek"],
      ["technology", "Techniek & ontwerpen"], ["geography", "Aardrijkskunde & ruimte"],
      ["history", "Geschiedenis & tijd"], ["human_body", "Mens, lichaam & gezondheid"],
      ["citizenship_society", "Samenleving & burgerschap"], ["traffic_safety", "Verkeer & veiligheid"],
      ["environment_sustainability", "Milieu & duurzaamheid"], ["everyday_life", "Dagelijks leven"],
      ["arts_culture", "Kunst en cultuur"]
    ]
  },
  {
    id: "PUZZLE",
    title: { nl: "Puzzels", en: "Puzzles", fr: "Puzzles" },
    tiles: [
      ["sudoku", "Sudoku"], ["math-crosswords", "Rekenkruiswoord"], ["word-seeker", "Woordzoeker"],
      ["mothership-dock", "Moederschip"], ["galaxycon", "Galaxycon"], ["calculator-puzzle", "Rekenmachine"],
      ["constellations", "Sterrenbeelden"], ["orbit-builder", "Orbitbouwer"],
      ["pattern_sequences", "Patronen"], ["odd_one_out", "Wat hoort er niet bij?"],
      ["analogies", "Analogieën"], ["grid_logic", "Rasterlogica"], ["mazes", "Doolhoven"],
      ["visual_reasoning", "Visueel redeneren"], ["memory_rules", "Geheugenregels"]
    ]
  }
];

const focusLabelsNl = new Map([
  ["compare_size", "Groter en kleiner"],
  ["compare_weight", "Zwaarder en lichter"],
  ["compare_height", "Hoger en lager"],
  ["compare_length", "Langer en korter"],
  ["compare_distance", "Verder en dichterbij"],
  ["regular", "Regelmatig enkelvoud en meervoud"],
  ["apostrophe", "Meervoud met apostrof"],
  ["f_v", "F en v"],
  ["s_z", "S en z"],
  ["irregular", "Onregelmatig meervoud"]
]);

const inputTypeLabelsNl = new Map([
  ["audio_choice", "Luister en kies"],
  ["audio_translation_choice", "Luister en vertaal"],
  ["missing_letter_choice", "Vul de ontbrekende letter in"],
  ["multiple_choice", "Kies het juiste antwoord"],
  ["shape_choice", "Herken de vorm"],
  ["visual_word_choice", "Herken het woord bij het beeld"],
  ["open_numeric", "Vul het antwoord in"]
]);

const [lessonCatalog, lessonExerciseBundle, questionAudit] = await Promise.all([
  fs.readFile(lessonCatalogPath, "utf8").then(JSON.parse),
  fs.readFile(lessonExercisePath, "utf8").then(JSON.parse),
  fs.readFile(questionAuditPath, "utf8").then(JSON.parse)
]);

const categoryById = new Map(categoryDefinitions.map((category, index) => [
  category.id,
  {
    ...category,
    sequence: index + 1,
    tileById: new Map(category.tiles.map(([id, titleNl], tileIndex) => [
      id,
      { id, titleNl, sequence: tileIndex + 1 }
    ]))
  }
]));

const lessonSetsByStage = new Map();
for (const set of lessonExerciseBundle.sets) {
  if (!lessonSetsByStage.has(set.stageId)) lessonSetsByStage.set(set.stageId, []);
  lessonSetsByStage.get(set.stageId).push(set);
}

const gradeLessons = lessonCatalog.lessons
  .filter((lesson) => lesson.grade === grade)
  .sort((left, right) => {
    const leftCategory = categoryById.get(left.legacyRouting.domain);
    const rightCategory = categoryById.get(right.legacyRouting.domain);
    return (leftCategory?.sequence ?? 999) - (rightCategory?.sequence ?? 999) ||
      (leftCategory?.tileById.get(left.legacyRouting.subcategoryId)?.sequence ?? 999) -
        (rightCategory?.tileById.get(right.legacyRouting.subcategoryId)?.sequence ?? 999) ||
      left.sequence - right.sequence ||
      left.id.localeCompare(right.id);
  });

const gradeXmlExercises = questionAudit.xmlQuestions
  .filter((exercise) => exercise.grade === grade)
  .sort((left, right) =>
    left.domain.localeCompare(right.domain) ||
    left.subcategoryId.localeCompare(right.subcategoryId) ||
    left.exerciseId.localeCompare(right.exerciseId)
  );

function visibleText(value, locale = "nl-BE") {
  return value?.visible?.[locale] ?? value?.visible?.["nl-BE"] ?? "";
}

function tileReferences(exercise) {
  return String(exercise.tiles || "")
    .split("|")
    .filter(Boolean)
    .map((compositeId) => {
      const separator = compositeId.indexOf(":");
      return {
        compositeId,
        domain: separator < 0 ? exercise.domain : compositeId.slice(0, separator),
        tileId: separator < 0 ? compositeId : compositeId.slice(separator + 1)
      };
    });
}

function canonicalTileReference(exercise) {
  const references = tileReferences(exercise);
  const exact = references.find((reference) =>
    reference.domain === exercise.domain &&
    reference.tileId === exercise.subcategoryId &&
    categoryById.get(reference.domain)?.tileById.has(reference.tileId)
  );
  return exact ?? references.find((reference) =>
    categoryById.get(reference.domain)?.tileById.has(reference.tileId)
  ) ?? null;
}

function exerciseTypeLabel(key) {
  return focusLabelsNl.get(key) ?? inputTypeLabelsNl.get(key) ??
    key.replaceAll("_", " ").replace(/^\p{L}/u, (letter) => letter.toUpperCase());
}

function lessonExerciseRecord(exercise, set, sequence, hierarchyPath) {
  return {
    sequence,
    id: exercise.id,
    source: {
      kind: "V2_GENERATED_MATERIALIZATION",
      setId: set.setId,
      purpose: set.purpose,
      generatorId: set.generatorId,
      generatorVersion: set.generatorVersion,
      seedFingerprint: set.seedFingerprint
    },
    hierarchyPath,
    domain: exercise.domain,
    skill: exercise.skill,
    section: exercise.section,
    inputMode: exercise.inputMode,
    prompt: exercise.prompt,
    speechText: exercise.speechText,
    answer: exercise.answer,
    hints: exercise.hints,
    data: exercise.data,
    rewardMetadata: exercise.rewardMetadata
  };
}

function buildLesson(lesson) {
  const category = categoryById.get(lesson.legacyRouting.domain);
  const tile = category?.tileById.get(lesson.legacyRouting.subcategoryId);
  const basePath = [
    `categorie:${lesson.legacyRouting.domain}`,
    `tegel:${lesson.legacyRouting.subcategoryId}`,
    `les:${lesson.id}`
  ];
  const theoryStages = lesson.stages.filter((stage) => stage.type === "THEORY").sort((a, b) => a.sequence - b.sequence);
  const exerciseStages = lesson.stages.filter((stage) => stage.type === "EXERCISES").sort((a, b) =>
    (a.seriesIndex ?? 999) - (b.seriesIndex ?? 999) || a.sequence - b.sequence
  );
  const testStages = lesson.stages.filter((stage) => stage.type === "MASTERY_TEST").sort((a, b) => a.sequence - b.sequence);

  const part = (sequence, id, title, type, stages, isTest = false) => {
    const partPath = [...basePath, `deel:${sequence}`];
    const series = type === "THEORY" ? [] : stages.map((stage, stageIndex) => {
      const seriesSequence = isTest ? stageIndex + 1 : (stage.seriesIndex ?? stageIndex + 1);
      const seriesTitle = isTest ? (testStages.length === 1 ? "Toets" : `Toets ${seriesSequence}`) : `Reeks ${seriesSequence}`;
      const seriesPath = [...partPath, `reeks:${seriesSequence}`];
      const sets = (lessonSetsByStage.get(stage.id) ?? []).sort((a, b) =>
        a.setId.localeCompare(b.setId)
      );
      let exerciseSequence = 0;
      const exercises = sets.flatMap((set) => set.exercises.map((exercise) => {
        exerciseSequence += 1;
        return lessonExerciseRecord(
          exercise,
          set,
          exerciseSequence,
          [...seriesPath, `oefening:${exerciseSequence}`]
        );
      }));
      return {
        sequence: seriesSequence,
        id: stage.id,
        title: seriesTitle,
        stageType: stage.type,
        sourceSetCount: sets.length,
        exerciseCount: exercises.length,
        exercises
      };
    });
    return {
      sequence,
      id,
      title,
      type,
      stageIds: stages.map((stage) => stage.id),
      theoryContentBlockCount: type === "THEORY"
        ? stages.reduce((sum, stage) => sum + (stage.contentBlocks?.length ?? 0), 0)
        : 0,
      seriesCount: series.length,
      exerciseCount: series.reduce((sum, item) => sum + item.exerciseCount, 0),
      series
    };
  };

  const parts = [
    part(1, `${lesson.id}.part.theory`, "Theorie", "THEORY", theoryStages),
    part(2, `${lesson.id}.part.exercises`, "Oefeningen", "EXERCISES", exerciseStages),
    part(3, `${lesson.id}.part.test`, "Toets", "MASTERY_TEST", testStages, true)
  ];

  return {
    sequence: lesson.sequence,
    id: lesson.id,
    unitId: lesson.unitId,
    title: {
      nl: visibleText(lesson.title, "nl-BE"),
      en: visibleText(lesson.title, "en-US"),
      fr: visibleText(lesson.title, "fr-BE")
    },
    summary: {
      nl: visibleText(lesson.summary, "nl-BE"),
      en: visibleText(lesson.summary, "en-US"),
      fr: visibleText(lesson.summary, "fr-BE")
    },
    publicationStatus: lesson.publicationStatus,
    categorySequence: category?.sequence ?? null,
    tileSequence: tile?.sequence ?? null,
    partCount: parts.length,
    seriesCount: parts.reduce((sum, item) => sum + item.seriesCount, 0),
    exerciseCount: parts.reduce((sum, item) => sum + item.exerciseCount, 0),
    masteryTags: lesson.masteryTags,
    prerequisiteLessonIds: lesson.prerequisiteLessonIds,
    gatePolicy: lesson.gatePolicy,
    parts
  };
}

const lessonsByTile = new Map();
for (const lesson of gradeLessons) {
  const key = `${lesson.legacyRouting.domain}:${lesson.legacyRouting.subcategoryId}`;
  if (!lessonsByTile.has(key)) lessonsByTile.set(key, []);
  lessonsByTile.get(key).push(buildLesson(lesson));
}

const xmlByCanonicalTile = new Map();
const unmappedXmlExercises = [];
for (const exercise of gradeXmlExercises) {
  const canonical = canonicalTileReference(exercise);
  if (!canonical) {
    unmappedXmlExercises.push(exercise.exerciseId);
    continue;
  }
  const key = `${canonical.domain}:${canonical.tileId}`;
  if (!xmlByCanonicalTile.has(key)) xmlByCanonicalTile.set(key, []);
  xmlByCanonicalTile.get(key).push({
    ...exercise,
    canonical,
    allTileReferences: tileReferences(exercise)
  });
}

function buildExtraPractice(domain, tileId, tileTitleNl) {
  const key = `${domain}:${tileId}`;
  const sourceExercises = xmlByCanonicalTile.get(key) ?? [];
  const actualSubcategories = [...new Set(sourceExercises.map((exercise) => exercise.subcategoryId))];
  const splitBySubject = actualSubcategories.length > 1;
  const typeGroups = new Map();

  for (const exercise of sourceExercises) {
    const typeKey = splitBySubject ? exercise.subcategoryId : exercise.inputType || "exercises";
    if (!typeGroups.has(typeKey)) typeGroups.set(typeKey, []);
    typeGroups.get(typeKey).push(exercise);
  }

  const types = [...typeGroups.entries()]
    .sort((left, right) =>
      left[1][0].exerciseId.localeCompare(right[1][0].exerciseId) ||
      left[0].localeCompare(right[0])
    )
    .map(([typeKey, exercises], typeIndex) => {
      const ordered = exercises.sort((left, right) => left.exerciseId.localeCompare(right.exerciseId));
      const series = [];
      for (let start = 0; start < ordered.length; start += 10) {
        const seriesSequence = series.length + 1;
        const hierarchyBase = [
          `categorie:${domain}`,
          `tegel:${tileId}`,
          "extra-oefeningen",
          `type:${typeKey}`,
          `reeks:${seriesSequence}`
        ];
        const seriesExercises = ordered.slice(start, start + 10).map((exercise, index) => ({
          sequence: index + 1,
          id: exercise.exerciseId,
          source: {
            kind: "STORED_XML",
            file: exercise.sourceFile,
            packId: exercise.packId,
            progressKey: exercise.exerciseId
          },
          hierarchyPath: [...hierarchyBase, `oefening:${index + 1}`],
          domain: exercise.domain,
          canonicalTileId: tileId,
          sourceSubcategoryId: exercise.subcategoryId,
          alsoVisibleInTiles: exercise.allTileReferences
            .filter((reference) => reference.compositeId !== exercise.canonical.compositeId)
            .map((reference) => reference.compositeId),
          difficultyStars: exercise.difficultyStars,
          topic: exercise.topic,
          skillRef: exercise.skillRef,
          inputType: exercise.inputType,
          contentType: exercise.contentType,
          mappingStatus: exercise.mappingStatus,
          lessonReferences: String(exercise.lessonReferences || "").split("|").filter(Boolean),
          prompt: {
            nl: exercise.promptNl,
            en: exercise.promptEn,
            fr: exercise.promptFr
          },
          answer: {
            nl: exercise.correctAnswerNl,
            en: exercise.correctAnswerEn,
            fr: exercise.correctAnswerFr
          },
          options: {
            nl: exercise.optionsNl,
            en: exercise.optionsEn,
            fr: exercise.optionsFr
          },
          tags: String(exercise.tags || "").split("|").filter(Boolean)
        }));
        series.push({
          sequence: seriesSequence,
          id: `${domain}.${tileId}.extra.${typeKey}.series.${seriesSequence}`,
          title: `Reeks ${seriesSequence}`,
          exerciseCount: seriesExercises.length,
          exercises: seriesExercises
        });
      }
      return {
        sequence: typeIndex + 1,
        id: typeKey,
        title: exerciseTypeLabel(typeKey),
        groupingBasis: splitBySubject ? "SOURCE_SUBCATEGORY" : "INPUT_TYPE",
        sourceSubcategoryIds: [...new Set(exercises.map((exercise) => exercise.subcategoryId))],
        inputTypes: [...new Set(exercises.map((exercise) => exercise.inputType))],
        seriesCount: series.length,
        exerciseCount: exercises.length,
        series
      };
    });

  return {
    id: `${domain}.${tileId}.extra-practice`,
    title: "Extra oefeningen",
    seriesSize: 10,
    typeCount: types.length,
    seriesCount: types.reduce((sum, type) => sum + type.seriesCount, 0),
    exerciseCount: types.reduce((sum, type) => sum + type.exerciseCount, 0),
    types,
    emptyReason: types.length === 0
      ? `Geen opgeslagen XML-oefeningen voor ${tileTitleNl} in leerjaar ${grade}.`
      : null
  };
}

const categories = categoryDefinitions.map((definition, categoryIndex) => {
  const category = categoryById.get(definition.id);
  const tiles = definition.tiles.map(([tileId, titleNl], tileIndex) => {
    const key = `${definition.id}:${tileId}`;
    const lessons = (lessonsByTile.get(key) ?? []).sort((a, b) =>
      a.sequence - b.sequence || a.id.localeCompare(b.id)
    );
    const extraPractice = buildExtraPractice(definition.id, tileId, titleNl);
    return {
      sequence: tileIndex + 1,
      id: tileId,
      title: { nl: titleNl },
      lessonCount: lessons.length,
      lessonExerciseCount: lessons.reduce((sum, lesson) => sum + lesson.exerciseCount, 0),
      extraPracticeExerciseCount: extraPractice.exerciseCount,
      totalExerciseOccurrences: lessons.reduce((sum, lesson) => sum + lesson.exerciseCount, 0) +
        extraPractice.exerciseCount,
      hasContent: lessons.length > 0 || extraPractice.exerciseCount > 0,
      lessons,
      extraPractice
    };
  });
  return {
    sequence: categoryIndex + 1,
    id: definition.id,
    title: definition.title,
    tileCount: tiles.length,
    lessonCount: tiles.reduce((sum, tile) => sum + tile.lessonCount, 0),
    lessonExerciseCount: tiles.reduce((sum, tile) => sum + tile.lessonExerciseCount, 0),
    extraPracticeExerciseCount: tiles.reduce((sum, tile) => sum + tile.extraPracticeExerciseCount, 0),
    totalExerciseOccurrences: tiles.reduce((sum, tile) => sum + tile.totalExerciseOccurrences, 0),
    tiles
  };
});

const allLessonExerciseIds = [];
const allExtraExerciseIds = [];
let lessonSeriesCount = 0;
let extraSeriesCount = 0;
let partCount = 0;
for (const category of categories) {
  for (const tile of category.tiles) {
    for (const lesson of tile.lessons) {
      partCount += lesson.parts.length;
      for (const part of lesson.parts) {
        lessonSeriesCount += part.series.length;
        for (const series of part.series) {
          allLessonExerciseIds.push(...series.exercises.map((exercise) => exercise.id));
        }
      }
    }
    for (const type of tile.extraPractice.types) {
      extraSeriesCount += type.series.length;
      for (const series of type.series) {
        allExtraExerciseIds.push(...series.exercises.map((exercise) => exercise.id));
      }
    }
  }
}

const allIds = [...allLessonExerciseIds, ...allExtraExerciseIds];
const duplicateIds = [...new Set(allIds.filter((id, index) => allIds.indexOf(id) !== index))];
const summary = {
  grade,
  categoryCount: categories.length,
  tileCount: categories.reduce((sum, category) => sum + category.tileCount, 0),
  lessonCount: gradeLessons.length,
  partCount,
  lessonSeriesCount,
  extraPracticeTypeCount: categories.reduce(
    (sum, category) => sum + category.tiles.reduce(
      (tileSum, tile) => tileSum + tile.extraPractice.typeCount,
      0
    ),
    0
  ),
  extraPracticeSeriesCount: extraSeriesCount,
  lessonExerciseCount: allLessonExerciseIds.length,
  extraPracticeExerciseCount: allExtraExerciseIds.length,
  uniqueExerciseCount: new Set(allIds).size,
  totalExerciseOccurrences: allIds.length,
  duplicateExerciseIdCount: duplicateIds.length,
  unmappedExerciseCount: unmappedXmlExercises.length
};

const bundle = {
  schema: {
    id: "poincare.exercise-hierarchy",
    version: 1,
    hierarchy: [
      "category",
      "tile/subcategory",
      "lesson or extra practice",
      "part or exercise type",
      "series",
      "exercise"
    ]
  },
  metadata: {
    generatedAtUtc: new Date().toISOString(),
    grade,
    localeForTitles: "nl-BE",
    sourceFiles: [
      path.relative(root, lessonCatalogPath).replaceAll("\\", "/"),
      path.relative(root, lessonExercisePath).replaceAll("\\", "/"),
      path.relative(root, questionAuditPath).replaceAll("\\", "/")
    ],
    coverage: {
      v2Lessons: "Alle v2-lessen van het geselecteerde leerjaar.",
      lessonGenerators: "Eén vaste, reproduceerbare materialisatie per v2-oefenset.",
      extraPractice: "Alle opgeslagen XML-oefeningen van het geselecteerde leerjaar.",
      dynamicVariantCaveat:
        "Dynamische generators kunnen bijkomende deterministische varianten maken; die oneindige variantenset kan niet volledig als eindige JSON worden opgeslagen.",
      runtimeGeneratorImplementations: [
        "GeneratedMathExerciseGenerator",
        "WrittenCalculationGenerator",
        "LanguageCurriculumExerciseFactory",
        "DictationExerciseFactory",
        "TranslationExerciseFactory",
        "WorldKnowledgeExtraExerciseFactory",
        "Letter tracing runtime bank",
        "Generated puzzle engines"
      ]
    }
  },
  summary,
  validation: {
    sourceMappingIssueCount: questionAudit.mappingIssues.length,
    unmappedExerciseIds: unmappedXmlExercises,
    duplicateExerciseIds: duplicateIds,
    checks: {
      expectedGradeOneLessonCount: grade !== 1 || gradeLessons.length === 67,
      expectedGradeOneLessonExercises: grade !== 1 || allLessonExerciseIds.length === 2016,
      expectedGradeOneStoredExtraExercises: grade !== 1 || allExtraExerciseIds.length === 4200,
      allStoredExercisesMappedToTiles: unmappedXmlExercises.length === 0,
      allExerciseIdsUniqueAcrossIncludedRecords: duplicateIds.length === 0,
      everyExtraSeriesHasAtMostTenExercises: categories.every((category) =>
        category.tiles.every((tile) =>
          tile.extraPractice.types.every((type) =>
            type.series.every((series) => series.exerciseCount >= 1 && series.exerciseCount <= 10)
          )
        )
      ),
      everyLessonHasThreeParts: categories.every((category) =>
        category.tiles.every((tile) => tile.lessons.every((lesson) => lesson.partCount === 3))
      )
    }
  },
  categories
};

const failedChecks = Object.entries(bundle.validation.checks).filter(([, ok]) => !ok);
if (failedChecks.length > 0) {
  throw new Error(`Hierarchy validation failed: ${failedChecks.map(([name]) => name).join(", ")}`);
}

await fs.mkdir(outputDirectory, { recursive: true });
const serialized = JSON.stringify(bundle, null, 2);
await Promise.all([
  fs.writeFile(outputPath, serialized, "utf8"),
  fs.writeFile(convenientOutputPath, serialized, "utf8")
]);

console.log(JSON.stringify({
  outputPath,
  convenientOutputPath,
  bytes: Buffer.byteLength(serialized, "utf8"),
  summary,
  checks: bundle.validation.checks
}, null, 2));
