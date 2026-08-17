#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "../..");
const catalogPath = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.join(repoRoot, "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json");
const WRONG_ANSWERS_ALLOWED = "wrong answers allowed";

const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
let changed = false;

function localized(nl) {
  return { visible: { "nl-BE": nl } };
}

function cloneFollowUpSet(lesson, theory, sourceSet) {
  const id = `${theory.id}.set.follow-up`;
  const questions = sourceSet.questionBlueprints.slice(0, 3).map((question, index) => {
    const questionId = `${id}.question.${index + 1}`;
    return {
      ...question,
      id: questionId,
      lessonId: lesson.id,
      stageId: theory.id,
      exerciseSetId: id,
      answerOptionIds: question.answerOptionIds.map((_, answerIndex) =>
        `${questionId}.answer.${answerIndex + 1}`
      )
    };
  });
  if (questions.length < 3) {
    throw new Error(`Cannot create three theory questions for ${lesson.id}: source set is too small.`);
  }
  return {
    ...sourceSet,
    id,
    exerciseSetId: id,
    lessonId: lesson.id,
    stageId: theory.id,
    title: localized("Controlevragen"),
    sequence: 1,
    purpose: "GUIDED",
    itemCount: 3,
    parameters: {
      ...sourceSet.parameters,
      [WRONG_ANSWERS_ALLOWED]: "1",
      theory_follow_up: "true"
    },
    hintPolicy: "NONE",
    feedbackPolicy: "IMMEDIATE_EXPLANATORY",
    questionBlueprints: questions,
    editorialItems: []
  };
}

for (const lesson of catalog.lessons) {
  let lessonChanged = false;
  const ordered = [...lesson.stages].sort((a, b) => a.sequence - b.sequence);
  const theory = ordered.find(stage => stage.type === "THEORY" || stage.type === "ACQUIRE_UNDERSTAND");
  const practice = ordered.find(stage => stage.type === "PRACTISE")
    ?? ordered.find(stage => stage.type === "EXERCISES" && stage.seriesIndex === 1);
  const apply = ordered.find(stage => stage.type === "APPLY_DEMONSTRATE")
    ?? ordered.find(stage => stage.type === "EXERCISES" && stage.seriesIndex === 2);

  if (!theory || !practice || !apply) {
    throw new Error(`Lesson ${lesson.id} does not have the expected theory and two exercise series.`);
  }

  if (theory.type !== "THEORY") { theory.type = "THEORY"; changed = lessonChanged = true; }
  if (practice.type !== "EXERCISES") { practice.type = "EXERCISES"; changed = lessonChanged = true; }
  if (apply.type !== "EXERCISES") { apply.type = "EXERCISES"; changed = lessonChanged = true; }
  if (practice.seriesIndex !== 1) { practice.seriesIndex = 1; changed = lessonChanged = true; }
  if (apply.seriesIndex !== 2) { apply.seriesIndex = 2; changed = lessonChanged = true; }

  const exerciseTitle = localized("Oefeningen");
  for (const stage of [practice, apply]) {
    if (JSON.stringify(stage.title) !== JSON.stringify(exerciseTitle)) {
      stage.title = exerciseTitle;
      changed = lessonChanged = true;
    }
  }

  for (const [stage, allowed] of [[practice, "2"], [apply, "1"]]) {
    for (const set of stage.exerciseSets) {
      if (set.parameters?.[WRONG_ANSWERS_ALLOWED] !== allowed) {
        set.parameters = { ...(set.parameters ?? {}), [WRONG_ANSWERS_ALLOWED]: allowed };
        changed = lessonChanged = true;
      }
    }
  }

  const existingFollowUp = theory.exerciseSets.find(set => set.parameters?.theory_follow_up === "true");
  if (!existingFollowUp) {
    const sourceSet = practice.exerciseSets.find(set => set.questionBlueprints?.length >= 3);
    if (!sourceSet) throw new Error(`No suitable follow-up source set found for ${lesson.id}.`);
    theory.exerciseSets.push(cloneFollowUpSet(lesson, theory, sourceSet));
    changed = lessonChanged = true;
  } else if (existingFollowUp.parameters?.[WRONG_ANSWERS_ALLOWED] !== "1") {
    existingFollowUp.parameters = { ...existingFollowUp.parameters, [WRONG_ANSWERS_ALLOWED]: "1" };
    changed = lessonChanged = true;
  }
  const followUpTitle = localized("Controlevragen");
  const followUp = theory.exerciseSets.find(set => set.parameters?.theory_follow_up === "true");
  if (followUp && JSON.stringify(followUp.title) !== JSON.stringify(followUpTitle)) {
    followUp.title = followUpTitle;
    changed = lessonChanged = true;
  }

  theory.required = true;
  theory.completionPolicy = "COMPLETED_REQUIRED_SETS";
  if (lessonChanged) lesson.definitionVersion += 1;
}

if (changed) {
  catalog.definitionVersion += 1;
  fs.writeFileSync(catalogPath, JSON.stringify(catalog));
}

console.log(changed
  ? `Migrated ${catalog.lessons.length} lessons to the three-part contract: ${catalogPath}`
  : `Catalog already satisfies the three-part migration: ${catalogPath}`);
