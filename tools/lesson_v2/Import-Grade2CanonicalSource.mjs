import fs from "node:fs";
import path from "node:path";

const [packageRoot, outputPath = "tools/lesson_v2/source/grade2-canonical-source-v1.json"] = process.argv.slice(2);
if (!packageRoot) {
  throw new Error("Usage: node Import-Grade2CanonicalSource.mjs <work-package-root> [output-path]");
}

const curriculumRoot = path.join(
  path.resolve(packageRoot),
  "curriculum_reference",
  "Poincare_curriculum_vlaanderen",
);
const readJson = (name) => JSON.parse(fs.readFileSync(path.join(curriculumRoot, "data", name), "utf8"));
const allUnits = readJson("units.json");
const allLessons = readJson("lessons.json");
const units = allUnits.filter((unit) => unit.grade === 2);
const unitIds = new Set(units.map((unit) => unit.unit_id));
const lessons = allLessons.filter((lesson) => unitIds.has(lesson.unit_id));

const compactUnit = (unit) => ({
  unitId: unit.unit_id,
  version: unit.version,
  grade: unit.grade,
  subjectId: unit.subject_id,
  subject: unit.subject,
  domainId: unit.domain_id,
  sequenceInSubject: unit.sequence_in_grade_subject,
  title: unit.title,
  status: unit.status,
  focus: unit.focus,
  contribution: unit.contribution,
  coreKnowledge: unit.core_knowledge,
  coreSkill: unit.core_skill,
  models: unit.models ?? [],
  vocabulary: unit.vocabulary ?? [],
  sourceLessonIds: unit.lesson_ids ?? [],
  prerequisiteUnitIds: unit.prerequisite_unit_ids ?? [],
  coveredGoals: unit.source_crosswalk_tags ?? [],
  coveredGoalType: unit.source_crosswalk_tag_type,
});

const compactLesson = (lesson) => ({
  lessonId: lesson.lesson_id,
  unitId: lesson.unit_id,
  sequenceInUnit: lesson.sequence_in_unit,
  phase: lesson.phase,
  title: lesson.title,
  contains: lesson.what_it_contains,
  learningObjectives: lesson.learning_objectives ?? [],
  successCriteria: lesson.success_criteria_student_language ?? [],
  proceduralKnowledge: lesson.theory?.procedural_knowledge ?? [],
  models: lesson.theory?.models ?? [],
  workedExample: lesson.theory?.worked_example ?? null,
  lessonFlow: lesson.lesson_flow ?? [],
  blueprintIds: lesson.exercise_blueprint_ids ?? [],
  misconceptions: lesson.common_misconceptions ?? [],
  masteryCriteria: lesson.assessment?.mastery_criteria ?? [],
  humanObservationRequired: lesson.assessment?.human_observation_required === true,
});

const source = {
  schemaVersion: 1,
  source: {
    curriculumId: "poincare-vlaanderen-primary-g1-g6-v1",
    packageFolder: "Poincare_Codex_Work_Package_Lessonarchitecture_v2",
    unitsFile: "curriculum_reference/Poincare_curriculum_vlaanderen/data/units.json",
    lessonsFile: "curriculum_reference/Poincare_curriculum_vlaanderen/data/lessons.json",
    gradeGuide: "curriculum_reference/Poincare_curriculum_vlaanderen/lessen/LEERJAAR_2.md",
  },
  expected: { grade: 2, units: 65, sourceLessonPhaseRecords: 195 },
  units: units.map(compactUnit),
  sourceLessons: lessons.map(compactLesson),
};

if (source.units.length !== source.expected.units) {
  throw new Error(`Expected ${source.expected.units} Grade 2 units, found ${source.units.length}`);
}
if (source.sourceLessons.length !== source.expected.sourceLessonPhaseRecords) {
  throw new Error(
    `Expected ${source.expected.sourceLessonPhaseRecords} Grade 2 lesson phase records, found ${source.sourceLessons.length}`,
  );
}
for (const unit of source.units) {
  const phases = source.sourceLessons.filter((lesson) => lesson.unitId === unit.unitId);
  if (phases.length !== 3 || phases.map((phase) => phase.phase).join(",") !== "acquire,practice,transfer") {
    throw new Error(`Unexpected source phase structure for ${unit.unitId}`);
  }
}

const resolvedOutput = path.resolve(outputPath);
fs.mkdirSync(path.dirname(resolvedOutput), { recursive: true });
fs.writeFileSync(resolvedOutput, `${JSON.stringify(source, null, 2)}\n`, "utf8");
console.log(`Imported ${source.units.length} Grade 2 units and ${source.sourceLessons.length} source phase records.`);
console.log(resolvedOutput);
