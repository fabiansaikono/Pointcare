import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";

const root = process.cwd();
const sourceDir = path.join(root, "deliverables", "lesson-hierarchy-source");
const qaSample = process.env.POINCARE_WORD_QA_SAMPLE === "1";
const requestedOutput = process.env.POINCARE_WORD_OUTPUT;
const outputPath = requestedOutput
  ? path.resolve(root, requestedOutput)
  : path.join(root, "deliverables", qaSample ? "rendered/Poincare_Lessenhierarchie_QA_sample.docx" : "Poincare_Lessenhierarchie_zonder_verhalen.docx");
const v1 = JSON.parse(fs.readFileSync(path.join(sourceDir, "poincare-question-audit.json"), "utf8"));
const v2 = JSON.parse(fs.readFileSync(path.join(sourceDir, "grades-1-2-v2-validation-exercises.json"), "utf8"));

const categoryLabels = {
  READING: "Lezen & luisteren", WRITING: "Spelling & grammatica", MATH: "Getallen",
  GEOMETRY: "Meten & meetkunde", WORLD_KNOWLEDGE: "Wereldkennis", PUZZLE: "Puzzels"
};
const inputLabels = {
  multiple_choice: "Meerkeuze", visual_word_choice: "Beeld-woordkeuze", shape_choice: "Vormkeuze",
  audio_choice: "Luisterkeuze", audio_translation_choice: "Luistervertaling",
  missing_letter_choice: "Ontbrekende letter", open_numeric: "Open getal",
  TAP_CHOICE: "Meerkeuze", LISTEN_AND_SELECT: "Luisterkeuze", CUSTOM_NUMBER_PAD: "Open getal",
  TRACE_LETTER: "Schrijven/overtrekken"
};
const stageLabels = { THEORY: "Theorie", EXERCISES: "Oefeningen", MASTERY_TEST: "Toets" };
const curriculumCategoryLabels = {
  language_skills: "Taalvaardigheid",
  measurement_geometry: "Meten en meetkunde",
  number_sense: "Getallen en bewerkingen",
  spelling_grammar: "Spelling en grammatica",
  world_orientation: "Wereldoriëntatie"
};
const theoryBlockLabels = {
  CHILD_FRIENDLY_DEFINITION: "Uitleg voor het kind",
  CORRECT_RESULT_RULE: "Regel voor het juiste resultaat",
  HOW_IT_WORKS: "Zo werkt het",
  LEARNING_INTENT: "Leerdoel",
  NON_EXAMPLE: "Tegenvoorbeeld",
  RUBRIC: "Beoordelingspunten",
  SUMMARY: "Samenvatting",
  UITLEG_OUDERS: "Uitleg voor ouder of begeleider",
  VOCABULARY: "Woordenschat",
  WORKED_EXAMPLE: "Uitgewerkt voorbeeld"
};
const colors = { blue: "2E74B5", darkBlue: "1F4D78", ink: "0B2545", muted: "667085", green: "187A3B", gold: "9A7200", red: "9B1C1C" };

function esc(value) {
  return String(value ?? "")
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, "")
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;").replaceAll("'", "&apos;");
}
function pretty(value) {
  return String(value ?? "").replace(/^.*?:/, "").replaceAll(/[._-]+/g, " ")
    .replace(/\b\w/g, letter => letter.toUpperCase());
}
function visible(value) {
  return value?.visible?.["nl-BE"] ?? value?.visible?.nl ?? value?.visible?.["en-US"] ?? "";
}
function run(text, { bold = false, italic = false, color, size } = {}) {
  const rPr = [bold ? "<w:b/>" : "", italic ? "<w:i/>" : "", color ? `<w:color w:val="${color}"/>` : "", size ? `<w:sz w:val="${size * 2}"/><w:szCs w:val="${size * 2}"/>` : ""].join("");
  const pieces = String(text ?? "").split("\n").map((part, index) => `${index ? "<w:br/>" : ""}<w:t xml:space="preserve">${esc(part)}</w:t>`).join("");
  return `<w:r>${rPr ? `<w:rPr>${rPr}</w:rPr>` : ""}${pieces}</w:r>`;
}
function para(style, runs, { pageBreakBefore = false, keepNext = false, align } = {}) {
  const properties = [style ? `<w:pStyle w:val="${style}"/>` : "", pageBreakBefore ? "<w:pageBreakBefore/>" : "", keepNext ? "<w:keepNext/>" : "", align ? `<w:jc w:val="${align}"/>` : ""].join("");
  const runXml = Array.isArray(runs) ? runs.join("") : runs;
  return `<w:p>${properties ? `<w:pPr>${properties}</w:pPr>` : ""}${runXml}</w:p>`;
}
function pageBreak() { return "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"; }

function optionsV2(exercise) {
  const raw = exercise.data?.["options_nl-BE"] || exercise.data?.options_nl || "";
  return String(raw).split(/\n|\s\|\s/).map(x => x.trim()).filter(Boolean);
}
function typeV2(exercise) {
  const interfaceType = inputLabels[exercise.inputMode] || pretty(exercise.inputMode);
  const skill = exercise.data?.operation || exercise.data?.promptKind || exercise.skill || exercise.data?.subcategoryId;
  return `${interfaceType} · ${pretty(skill)}`;
}
function typeV1(exercise) {
  const interfaceType = inputLabels[exercise.inputType] || pretty(exercise.inputType);
  const skill = exercise.skillRef || exercise.topic || exercise.subcategoryId;
  return `${interfaceType} · ${pretty(skill)}`;
}
function v2CurriculumStatus(lesson) {
  return lesson.publicationStatus === "ACTIVE" ? "Ja — actief in het curriculum" : "Ja — opgenomen, redactionele review vereist";
}
function v1CurriculumStatus(exercise) {
  return String(exercise.lessonReferences || "").trim() || String(exercise.mappingStatus || "").includes("LESSON")
    ? "Ja — gekoppeld aan een curriculumles" : "Nee — extra oefenbank";
}
function exerciseParagraphs(sequence, meta, prompt, options, correctAnswer) {
  const paragraphs = [
    para("ExerciseMeta", run(`Vraag ${sequence} · ${meta}`, { bold: true, color: colors.darkBlue, size: 8.5 }), { keepNext: true }),
    para("ExerciseQuestion", run(prompt || "[vraagtekst ontbreekt]", { size: 9.5 }), { keepNext: options.length > 0 })
  ];
  if (options.length) {
    paragraphs.push(para("AnswerLabel", run("Antwoorden", { bold: true, color: colors.muted, size: 8.5 }), { keepNext: true }));
    options.forEach((option, index) => {
      const isCorrect = String(option).trim() === String(correctAnswer || "").trim();
      paragraphs.push(para("Answer", [
        run(`${String.fromCharCode(65 + index)}. `, { bold: true, color: isCorrect ? colors.green : colors.muted, size: 9 }),
        run(option, { bold: isCorrect, color: isCorrect ? colors.green : colors.ink, size: 9 })
      ]));
    });
  }
  paragraphs.push(para("CorrectAnswer", [
    run("Juist antwoord: ", { bold: true, color: colors.green, size: 9.5 }),
    run(correctAnswer || "[antwoord ontbreekt]", { bold: true, color: colors.green, size: 9.5 })
  ]));
  return paragraphs;
}

const body = [];
body.push(para("TitleCustom", run("Poincaré — volledige lessenhiërarchie", { bold: true, color: colors.ink, size: 28 }), { align: "center" }));
body.push(para("Subtitle", run("Zonder verhalen · tot en met oefening- en antwoordniveau", { color: colors.darkBlue, size: 14 }), { align: "center" }));
body.push(para("Meta", run(`Gegenereerd op 10 augustus 2026 · V2-catalogus definitie ${v2.catalogDefinitionVersion}`, { color: colors.muted }), { align: "center" }));
body.push(para("Normal", run("Doel: dit document is een bewerkbare redactionele export. V2 bevat de gestructureerde lessen voor leerjaar 1 en 2. V1 bevat de vaste oefenbank voor leerjaar 1 tot en met 6. Verhalen, verhaalhoofdstukken en verhaalvragen zijn niet opgenomen.")));
body.push(para("Heading2", run("Leeswijzer", { bold: true, color: colors.blue })));
body.push(para("Normal", run("Bij elke oefening staan de versie, curriculumstatus, het oefeningstype, de vraag, alle beschikbare Nederlandse antwoordopties en het juiste antwoord. Bij V2 staat ook hoeveel foute antwoorden zijn toegestaan; de toets behoudt zijn bestaande gedrag en toont daarom geen retrymetadata.")));
body.push(para("Meta", run(`Omvang: ${v2.lessonCount} V2-lessen · ${v2.exerciseCount.toLocaleString("nl-BE")} V2-oefeningen · ${v1.xmlQuestions.length.toLocaleString("nl-BE")} V1-oefeningen.`, { color: colors.muted })));
body.push(para("Heading2", run("Inhoudsopgave", { bold: true, color: colors.blue })));
body.push('<w:p><w:fldSimple w:instr="TOC \\o &quot;1-5&quot; \\h \\z \\u"><w:r><w:t>Werk de inhoudsopgave bij in Word (rechtsklik → Veld bijwerken).</w:t></w:r></w:fldSimple></w:p>');
body.push(pageBreak());

const setsByStage = new Map();
for (const set of v2.sets) {
  const list = setsByStage.get(set.stageId) || [];
  list.push(set);
  setsByStage.set(set.stageId, list);
}
const v2LessonsAll = [...v2.lessons].filter(lesson =>
  lesson.editorialKind !== "story" && !lesson.sourceStoryId && !(lesson.stages || []).some(stage => stage.type === "STORY")
).sort((a, b) => a.grade - b.grade || a.curriculumCategoryId.localeCompare(b.curriculumCategoryId) || a.curriculumSubcategoryId.localeCompare(b.curriculumSubcategoryId) || a.sequence - b.sequence || a.id.localeCompare(b.id));
const v2Lessons = qaSample ? v2LessonsAll.slice(0, 1) : v2LessonsAll;

body.push(para("Heading1", run("A. Gestructureerde lessen — V2", { bold: true, color: colors.blue })));
let lastGrade = null, lastCategory = "", lastSubcategory = "";
for (const lesson of v2Lessons) {
  if (lesson.grade !== lastGrade) {
    body.push(para("Heading2", run(`Leerjaar ${lesson.grade}`, { bold: true, color: colors.blue }), { pageBreakBefore: lastGrade !== null }));
    lastGrade = lesson.grade; lastCategory = ""; lastSubcategory = "";
  }
  if (lesson.curriculumCategoryId !== lastCategory) {
    body.push(para("Heading3", run(curriculumCategoryLabels[lesson.curriculumCategoryId] || pretty(lesson.curriculumCategoryId), { bold: true, color: colors.darkBlue })));
    lastCategory = lesson.curriculumCategoryId; lastSubcategory = "";
  }
  if (lesson.curriculumSubcategoryId !== lastSubcategory) {
    body.push(para("Heading4", run(pretty(lesson.curriculumSubcategoryId), { bold: true, color: colors.darkBlue })));
    lastSubcategory = lesson.curriculumSubcategoryId;
  }
  body.push(para("Heading5", run(visible(lesson.title), { bold: true, color: colors.ink }), { keepNext: true }));
  body.push(para("Meta", [
    run("Versie: V2", { bold: true, color: colors.blue }),
    run(` · Curriculum: ${v2CurriculumStatus(lesson)} · Les-ID: ${lesson.id} · Unit: ${lesson.unitId}`, { color: colors.muted })
  ]));
  const orderedStages = [...(lesson.stages || [])].sort((a, b) => a.sequence - b.sequence);
  for (const stage of orderedStages) {
    const partNumber = stage.type === "THEORY" ? 1 : stage.type === "MASTERY_TEST" ? 3 : 2;
    const seriesLabel = stage.type === "EXERCISES" ? ` · reeks ${stage.seriesIndex || 1}` : "";
    body.push(para("Heading6", run(`Deel ${partNumber} — ${stageLabels[stage.type] || pretty(stage.type)}${seriesLabel}`, { bold: true, color: colors.gold }), { keepNext: true }));
    if (stage.type === "THEORY") {
      const visualPaths = (lesson.visuals || []).flatMap(visual => [visual.assetPathSvg, visual.assetPathPng].filter(Boolean));
      if (visualPaths.length) body.push(para("Meta", run(`SVG/visuals: ${visualPaths.join(" · ")}`, { color: colors.muted })));
      for (const block of stage.contentBlocks || []) {
        const terms = (block.terms || []).map(term => `${visible(term.term)}: ${visible(term.definition)}`).filter(Boolean);
        const text = [visible(block.title), visible(block.body), ...terms].filter(Boolean).join(" — ");
        if (text) body.push(para("TheoryBody", [run(`${theoryBlockLabels[block.type] || pretty(block.type)}: `, { bold: true, color: colors.darkBlue }), run(text)]));
      }
    }
    const sets = (setsByStage.get(stage.id) || []).sort((a, b) => a.setId.localeCompare(b.setId));
    for (const set of sets) {
      const allowance = set.wrongAnswersAllowed == null ? "niet van toepassing" : String(set.wrongAnswersAllowed);
      body.push(para("SetLabel", [
        run(`${visible(stage.title) || stageLabels[stage.type] || pretty(stage.type)} · ${pretty(set.purpose)}`, { bold: true, color: colors.darkBlue })
      ], { keepNext: true }));
      body.push(para("Meta", run(`Reeks-ID: ${set.setId} · wrong answers allowed: ${allowance} · oefeningstype: ${pretty(set.parameters?.exercise_type || "multiple_choice")}`, { color: colors.muted, size: 8.5 }), { keepNext: true }));
      (qaSample ? set.exercises.slice(0, 1) : set.exercises).forEach((exercise, index) => {
        const sourceVersion = exercise.data?.sourceVersion === "V1" ? "V1-oefening in V2-les" : "V2";
        body.push(...exerciseParagraphs(
          index + 1,
          `${sourceVersion} · Curriculum: ${v2CurriculumStatus(lesson)} · Type: ${typeV2(exercise)} · ID: ${exercise.id}`,
          exercise.prompt?.nl || exercise.prompt?.["nl-BE"] || "",
          optionsV2(exercise),
          exercise.data?.["answer_nl-BE"] || exercise.answer
        ));
      });
    }
  }
}

body.push(pageBreak());
body.push(para("Heading1", run("B. Vaste oefenbank — V1", { bold: true, color: colors.blue })));
body.push(para("Normal", run("Deze sectie bevat alle vaste XML-oefeningen. Een curriculumkoppeling betekent dat de oefening expliciet aan minstens één les is gekoppeld; andere vragen blijven beschikbaar als extra oefening.")));
const v1QuestionsAll = [...v1.xmlQuestions].filter(exercise => !exercise.sourceStoryId && exercise.contentType !== "STORY").sort((a, b) =>
  a.grade - b.grade || a.domain.localeCompare(b.domain) || a.subcategoryId.localeCompare(b.subcategoryId) || a.inputType.localeCompare(b.inputType) || a.exerciseId.localeCompare(b.exerciseId)
);
const v1Questions = qaSample ? v1QuestionsAll.slice(0, 3) : v1QuestionsAll;
lastGrade = null; lastCategory = ""; lastSubcategory = ""; let lastType = "", series = 0, itemInSeries = 0;
for (const exercise of v1Questions) {
  if (exercise.grade !== lastGrade) {
    body.push(para("Heading2", run(`Leerjaar ${exercise.grade}`, { bold: true, color: colors.blue }), { pageBreakBefore: lastGrade !== null }));
    lastGrade = exercise.grade; lastCategory = ""; lastSubcategory = ""; lastType = "";
  }
  if (exercise.domain !== lastCategory) {
    body.push(para("Heading3", run(categoryLabels[exercise.domain] || pretty(exercise.domain), { bold: true, color: colors.darkBlue })));
    lastCategory = exercise.domain; lastSubcategory = ""; lastType = "";
  }
  if (exercise.subcategoryId !== lastSubcategory) {
    body.push(para("Heading4", run(exercise.tileTitlesNl || pretty(exercise.subcategoryId), { bold: true, color: colors.darkBlue })));
    lastSubcategory = exercise.subcategoryId; lastType = "";
  }
  const type = typeV1(exercise);
  if (type !== lastType) {
    body.push(para("Heading5", run(type, { bold: true, color: colors.ink }), { keepNext: true }));
    lastType = type; series = 0; itemInSeries = 0;
  }
  if (itemInSeries % 10 === 0) {
    series += 1;
    body.push(para("Heading6", run(`Reeks ${series}`, { bold: true, color: colors.gold }), { keepNext: true }));
  }
  itemInSeries += 1;
  const options = String(exercise.optionsNl || "").split(/\s\|\s/).map(x => x.trim()).filter(Boolean);
  body.push(...exerciseParagraphs(
    ((itemInSeries - 1) % 10) + 1,
    `V1 · Curriculum: ${v1CurriculumStatus(exercise)} · Type: ${type} · ID: ${exercise.exerciseId}`,
    exercise.promptNl,
    options,
    exercise.correctAnswerNl
  ));
}

const styles = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/><w:color w:val="000000"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="300" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:before="0" w:after="120" w:line="300" w:lineRule="auto"/></w:pPr><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="TitleCustom"><w:name w:val="Title Custom"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="2400" w:after="160"/><w:jc w:val="center"/></w:pPr><w:rPr><w:b/><w:color w:val="0B2545"/><w:sz w:val="56"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="360"/><w:jc w:val="center"/></w:pPr><w:rPr><w:color w:val="1F4D78"/><w:sz w:val="28"/></w:rPr></w:style>
${[
  ["Heading1","heading 1",32,"2E74B5",360,200,0], ["Heading2","heading 2",26,"2E74B5",280,140,1], ["Heading3","heading 3",24,"1F4D78",200,100,2],
  ["Heading4","heading 4",22,"1F4D78",160,80,3], ["Heading5","heading 5",20,"0B2545",120,60,4], ["Heading6","heading 6",18,"9A7200",100,40,5]
].map(([id,name,size,color,before,after,level]) => `<w:style w:type="paragraph" w:styleId="${id}"><w:name w:val="${name}"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="${before}" w:after="${after}"/><w:outlineLvl w:val="${level}"/></w:pPr><w:rPr><w:b/><w:color w:val="${color}"/><w:sz w:val="${size}"/></w:rPr></w:style>`).join("")}
<w:style w:type="paragraph" w:styleId="Meta"><w:name w:val="Metadata"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="80" w:line="240" w:lineRule="auto"/></w:pPr><w:rPr><w:color w:val="667085"/><w:sz w:val="17"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="TheoryBody"><w:name w:val="Theory body"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="80" w:line="280" w:lineRule="auto"/></w:pPr><w:rPr><w:sz w:val="20"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="SetLabel"><w:name w:val="Exercise set label"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="80" w:after="40" w:line="240" w:lineRule="auto"/></w:pPr><w:rPr><w:sz w:val="18"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ExerciseMeta"><w:name w:val="Exercise metadata"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="80" w:after="20" w:line="240" w:lineRule="auto"/></w:pPr><w:rPr><w:sz w:val="17"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ExerciseQuestion"><w:name w:val="Exercise question"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="0" w:after="50" w:line="280" w:lineRule="auto"/></w:pPr><w:rPr><w:sz w:val="19"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="AnswerLabel"><w:name w:val="Answer label"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="0" w:after="20" w:line="240" w:lineRule="auto"/></w:pPr><w:rPr><w:sz w:val="17"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Answer"><w:name w:val="One answer per paragraph"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="0" w:after="40" w:line="260" w:lineRule="auto"/><w:ind w:left="240" w:hanging="240"/></w:pPr><w:rPr><w:sz w:val="18"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="CorrectAnswer"><w:name w:val="Correct answer"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="20" w:after="160" w:line="260" w:lineRule="auto"/></w:pPr><w:rPr><w:b/><w:color w:val="187A3B"/><w:sz w:val="19"/></w:rPr></w:style>
</w:styles>`;

const documentXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><w:body>${body.join("")}<w:sectPr><w:headerReference w:type="default" r:id="rId2"/><w:footerReference w:type="default" r:id="rId3"/><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/><w:cols w:space="708"/><w:docGrid w:linePitch="360"/></w:sectPr></w:body></w:document>`;
const visibleTextPayloads = [...documentXml.matchAll(/<w:t(?:\s[^>]*)?>([\s\S]*?)<\/w:t>/g)].map(match => match[1]);
const visibleXmlLeaks = visibleTextPayloads.filter(text => /&lt;\/?w:|w:val=&quot;|xml:space=&quot;/.test(text));
if (visibleXmlLeaks.length) {
  throw new Error(`Refusing to write DOCX: ${visibleXmlLeaks.length} visible text nodes contain escaped Word XML.`);
}
const files = {
  "[Content_Types].xml": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/><Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/><Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/><Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>`,
  "_rels/.rels": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/></Relationships>`,
  "word/document.xml": documentXml,
  "word/styles.xml": styles,
  "word/settings.xml": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:updateFields w:val="false"/><w:compat><w:compatSetting w:name="compatibilityMode" w:uri="http://schemas.microsoft.com/office/word" w:val="15"/></w:compat></w:settings>`,
  "word/_rels/document.xml.rels": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/><Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/></Relationships>`,
  "word/header1.xml": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:jc w:val="right"/><w:spacing w:after="0"/></w:pPr>${run("Poincaré · lessenhiërarchie zonder verhalen", { color: colors.muted, size: 8 })}</w:p></w:hdr>`,
  "word/footer1.xml": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:jc w:val="right"/><w:spacing w:after="0"/></w:pPr><w:r><w:rPr><w:color w:val="667085"/><w:sz w:val="16"/></w:rPr><w:t>Pagina </w:t></w:r><w:fldSimple w:instr="PAGE"><w:r><w:rPr><w:color w:val="667085"/><w:sz w:val="16"/></w:rPr><w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>`,
  "docProps/core.xml": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>Poincaré — volledige lessenhiërarchie zonder verhalen</dc:title><dc:subject>V1- en V2-oefeningen tot antwoordniveau</dc:subject><dc:creator>Codex</dc:creator><cp:lastModifiedBy>Codex</cp:lastModifiedBy><dcterms:created xsi:type="dcterms:W3CDTF">2026-08-08T00:00:00Z</dcterms:created><dcterms:modified xsi:type="dcterms:W3CDTF">2026-08-08T00:00:00Z</dcterms:modified></cp:coreProperties>`,
  "docProps/app.xml": `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"><Application>Microsoft Office Word</Application><AppVersion>16.0000</AppVersion></Properties>`
};

function crc32(buffer) {
  let crc = 0xFFFFFFFF;
  for (const byte of buffer) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ (0xEDB88320 & -(crc & 1));
  }
  return (crc ^ 0xFFFFFFFF) >>> 0;
}
function dosDateTime() {
  const date = new Date(2026, 7, 8, 12, 0, 0);
  return { time: (date.getHours() << 11) | (date.getMinutes() << 5) | (date.getSeconds() >> 1), date: ((date.getFullYear() - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate() };
}
function zip(entries) {
  const local = [], central = []; let offset = 0; const stamp = dosDateTime();
  for (const [name, value] of Object.entries(entries)) {
    const nameBytes = Buffer.from(name); const data = Buffer.from(value, "utf8"); const compressed = zlib.deflateRawSync(data, { level: 9 }); const crc = crc32(data);
    const header = Buffer.alloc(30); header.writeUInt32LE(0x04034b50, 0); header.writeUInt16LE(20, 4); header.writeUInt16LE(0, 6); header.writeUInt16LE(8, 8); header.writeUInt16LE(stamp.time, 10); header.writeUInt16LE(stamp.date, 12); header.writeUInt32LE(crc, 14); header.writeUInt32LE(compressed.length, 18); header.writeUInt32LE(data.length, 22); header.writeUInt16LE(nameBytes.length, 26);
    local.push(header, nameBytes, compressed);
    const directory = Buffer.alloc(46); directory.writeUInt32LE(0x02014b50, 0); directory.writeUInt16LE(20, 4); directory.writeUInt16LE(20, 6); directory.writeUInt16LE(0, 8); directory.writeUInt16LE(8, 10); directory.writeUInt16LE(stamp.time, 12); directory.writeUInt16LE(stamp.date, 14); directory.writeUInt32LE(crc, 16); directory.writeUInt32LE(compressed.length, 20); directory.writeUInt32LE(data.length, 24); directory.writeUInt16LE(nameBytes.length, 28); directory.writeUInt32LE(offset, 42); central.push(directory, nameBytes);
    offset += header.length + nameBytes.length + compressed.length;
  }
  const centralSize = central.reduce((sum, item) => sum + item.length, 0); const count = Object.keys(entries).length;
  const end = Buffer.alloc(22); end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(count, 8); end.writeUInt16LE(count, 10); end.writeUInt32LE(centralSize, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, ...central, end]);
}

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, zip(files));
console.log(JSON.stringify({ outputPath, bytes: fs.statSync(outputPath).size, paragraphs: body.length, v2Lessons: v2Lessons.length, v2Exercises: v2.exerciseCount, v1Exercises: v1Questions.length, storiesIncluded: 0 }, null, 2));
