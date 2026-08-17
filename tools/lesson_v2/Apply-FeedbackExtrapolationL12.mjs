#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "../..");
const catalogPath = path.join(root, "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json");
const grade1CrosswalkPath = path.join(root, "app/src/main/assets/poincare/curriculum/grade1-coverage-crosswalk-v1.json");
const bankPath = path.join(root, "docs/lesson_v2/reports/GRADE2_EDITORIAL_QUESTION_BANK_V2.json");
const v1AuditPath = path.join(root, "deliverables/lesson-hierarchy-source/poincare-question-audit.json");
const reportDir = path.join(root, "docs/lesson_v2/reports");
const mappingPath = path.join(reportDir, "V1_TO_V2_CURRICULUM_MAPPING_L12.json");
const coverageJsonPath = path.join(reportDir, "FEEDBACK_COVERAGE_MATRIX_L12.json");
const coverageCsvPath = path.join(reportDir, "FEEDBACK_COVERAGE_MATRIX_L12.csv");
const deltaJsonPath = path.join(reportDir, "FEEDBACK_EXTRAPOLATION_DELTA_L12.json");
const deltaMdPath = path.join(reportDir, "FEEDBACK_EXTRAPOLATION_DELTA_L12.md");

const RS = "\u001e";
const US = "\u001f";
const WRONG = "wrong answers allowed";
const CATALOG_VERSION = 5;
const REVIEWED_POOL_GENERATOR_ID = "generator.reviewed.question-pool";
const REVIEWED_POOL_GENERATORS = new Set([
  "generator.g1.curriculum.catalog",
  "generator.g1.math.concepts",
  "generator.g1.language.french_basics",
  "generator.g1.applied_domains",
  "generator.g2.atomic-curriculum",
  REVIEWED_POOL_GENERATOR_ID,
]);
const deepClone = (value) => JSON.parse(JSON.stringify(value));
const nl = (value) => ({ visible: { "nl-BE": value } });
const visible = (value) => value?.visible?.["nl-BE"] || "";
const hash = (value) => parseInt(crypto.createHash("sha256").update(value).digest("hex").slice(0, 8), 16);
const slug = (value) => value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase()
  .replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
const normalize = (value) => String(value || "").normalize("NFKC").toLocaleLowerCase("nl-BE")
  .replace(/[‘’'"“”.,!?;:()[\]{}]/g, " ").replace(/\s+/g, " ").trim();
const unique = (items) => [...new Set(items.filter(Boolean))];

function stableIds(catalog) {
  return unique(catalog.lessons.flatMap((lesson) => [
    lesson.id,
    lesson.unitId,
    ...(lesson.visuals || []).map((item) => item.id),
    ...lesson.stages.flatMap((stage) => [
      stage.id,
      ...(stage.contentBlocks || []).flatMap((block) => [
        block.id,
        ...(block.terms || []).map((term) => term.id),
      ]),
      ...(stage.exerciseSets || []).flatMap((set) => [
        set.id,
        ...(set.curatedExerciseIds || []),
        ...(set.questionBlueprints || []).flatMap((question) => [question.id, ...(question.answerOptionIds || [])]),
      ]),
    ]),
  ])).sort();
}

function encodePool(records) {
  return records.map((record) => [
    record.prompt,
    record.answer,
    ...record.options.filter((option) => option !== record.answer).slice(0, 3),
    record.explanation,
  ].join(US)).join(RS);
}

function decodePool(value) {
  return String(value || "").split(RS).filter(Boolean).map((row) => {
    const fields = row.split(US);
    return { prompt: fields[0], answer: fields[1], options: fields.slice(1, 5), explanation: fields[5] };
  });
}

function optionSet(answer, candidates) {
  const options = [];
  const normalized = new Set();
  for (const candidate of [answer, ...candidates]) {
    const signature = normalize(candidate);
    if (!signature || normalized.has(signature)) continue;
    normalized.add(signature);
    options.push(candidate);
    if (options.length === 4) break;
  }
  let cursor = 1;
  while (options.length < 4) {
    const candidate = `${answer} — alternatief ${cursor++}`;
    const signature = normalize(candidate);
    if (!normalized.has(signature)) {
      normalized.add(signature);
      options.push(candidate);
    }
  }
  return options;
}

function numericRecord(prompt, answer, explanation, deltas = [1, -1, 10]) {
  const correct = Number(answer);
  return {
    prompt,
    answer: String(answer),
    options: optionSet(String(answer), deltas.map((delta) => String(Math.max(0, correct + delta)))),
    explanation,
  };
}

function mathRecord(mode, index, seriesOrdinal) {
  const n = index + seriesOrdinal * 7;
  const a = 2 + (n * 3) % 8;
  const b = 1 + (n * 5) % 7;
  const tens = 2 + (n * 7) % 8;
  const ones = (n * 3) % 10;
  switch (mode) {
    case "math_place_value":
      return numericRecord(`Leg ${tens} bundels van tien en ${ones} losse blokjes. Welk getal bouw je?`, tens * 10 + ones, "Elke bundel telt voor tien; tel daarna de losse blokjes.");
    case "math_digit_value": {
      const value = tens * 10 + ones;
      return numericRecord(`Bekijk ${value}. Hoeveel is het cijfer ${tens} op de tientallenplaats waard?`, tens * 10, "Een tiental is tien eenheden waard.", [10, -10, ones - tens * 10]);
    }
    case "math_skip_count": {
      const step = [2, 5, 10][n % 3];
      const start = (n % 5) * step;
      return numericRecord(`Spring telkens ${step} verder: ${start}, ${start + step}, ${start + 2 * step}, … Welk getal volgt?`, start + 3 * step, `Tel nog één sprong van ${step} erbij.`, [step, -step, step * 2]);
    }
    case "math_number_pattern": {
      const step = 2 + n % 6;
      const start = 3 + n % 11;
      return numericRecord(`Vul het lege vak in: ${start}, ${start + step}, □, ${start + 3 * step}.`, start + 2 * step, `Het verschil is telkens ${step}.`, [step, -step, 1]);
    }
    case "math_add_no_bridge": {
      const left = tens * 10 + (n % 4);
      const right = 1 + n % (9 - left % 10);
      return numericRecord(`Reken uit: ${left} + ${right}.`, left + right, "Tel de eenheden erbij; je hoeft geen nieuw tiental te maken.");
    }
    case "math_sub_no_bridge": {
      const left = tens * 10 + 6 + n % 4;
      const right = 1 + n % 5;
      return numericRecord(`Reken uit: ${left} − ${right}.`, left - right, "Trek de eenheden af binnen hetzelfde tiental.");
    }
    case "math_add_bridge": {
      const left = 20 + (n * 7) % 68;
      const gap = 10 - left % 10;
      const right = gap + 1 + n % 5;
      return numericRecord(`Vul eerst aan tot het volgende tiental en reken uit: ${left} + ${right}.`, left + right, `Van ${left} ga je eerst ${gap} verder tot het tiental.`);
    }
    case "math_sub_bridge": {
      const left = 31 + (n * 7) % 65;
      const gap = left % 10;
      const right = gap + 1 + n % 5;
      return numericRecord(`Ga eerst terug tot het vorige tiental en reken uit: ${left} − ${right}.`, left - right, `Van ${left} ga je eerst ${gap} terug tot het tiental.`);
    }
    case "math_facts20": {
      const left = 2 + n % 10;
      const right = 2 + (n * 3) % (19 - left);
      return numericRecord(`Welke uitkomst hoort bij ${left} + ${right}?`, left + right, "Gebruik een gekend rekenfeit en controleer met de omgekeerde bewerking.");
    }
    case "math_multiply": {
      const factor = [2, 5, 10][n % 3];
      const groups = 1 + n % 10;
      return numericRecord(`Leg ${groups} gelijke groepjes van ${factor}. Hoeveel fiches liggen er samen?`, groups * factor, `Dit is ${groups} × ${factor}.`, [factor, -factor, factor * 2]);
    }
    case "math_divide_share": {
      const divisor = [2, 5, 10][n % 3];
      const each = 1 + n % 10;
      const total = divisor * each;
      return numericRecord(`Verdeel ${total} fiches eerlijk over ${divisor} kinderen. Hoeveel krijgt elk kind?`, each, `Controleer: ${divisor} × ${each} = ${total}.`, [1, -1, divisor]);
    }
    case "math_divide_group": {
      const per = [2, 5, 10][n % 3];
      const groups = 1 + n % 10;
      const total = per * groups;
      return numericRecord(`Maak groepjes van ${per} met ${total} blokjes. Hoeveel groepjes kun je maken?`, groups, `Controleer: ${groups} × ${per} = ${total}.`, [1, -1, per]);
    }
    case "math_fractions": {
      const parts = [2, 3, 4][n % 3];
      const labels = { 2: "helften", 3: "derden", 4: "kwarten" };
      return numericRecord(`Verdeel één koek eerlijk in ${labels[parts]}. Hoeveel even grote stukken maak je?`, parts, "De noemer zegt in hoeveel gelijke delen het geheel is verdeeld.", [1, -1, 2]);
    }
    case "math_length": {
      const length = 10 + n % 80;
      return { prompt: `Een potlood is ${length} centimeter lang. Welke maat schrijf je op?`, answer: `${length} cm`, options: optionSet(`${length} cm`, [`${length} m`, `${length + 1} cm`, `${Math.max(1, length - 1)} cm`]), explanation: "Voor een potlood gebruik je centimeter." };
    }
    case "math_mass": {
      const grams = 100 + (n % 8) * 100;
      return { prompt: `De weegschaal wijst ${grams} gram aan. Welke massa noteer je?`, answer: `${grams} g`, options: optionSet(`${grams} g`, [`${grams} kg`, `${grams + 100} g`, `${Math.max(0, grams - 100)} g`]), explanation: "Lees het getal én de eenheid op de weegschaal." };
    }
    case "math_capacity": {
      const litres = 1 + n % 9;
      return { prompt: `In de maatbeker staat ${litres} liter. Welke inhoud noteer je?`, answer: `${litres} l`, options: optionSet(`${litres} l`, [`${litres} ml`, `${litres + 1} l`, `${Math.max(0, litres - 1)} l`]), explanation: "Lees de schaal en schrijf liter als eenheid." };
    }
    case "math_clock": {
      const hour = 1 + n % 11;
      const minutes = [0, 15, 30, 45][n % 4];
      const answer = `${hour}:${String(minutes).padStart(2, "0")}`;
      const words = minutes === 0 ? `${hour} uur` : minutes === 15 ? `kwart over ${hour}` : minutes === 30 ? `half ${hour + 1}` : `kwart voor ${hour + 1}`;
      return { prompt: `Zet de klok op ${words}. Welke digitale tijd hoort erbij?`, answer, options: optionSet(answer, [`${hour}:15`, `${hour}:30`, `${hour + 1}:${String(minutes).padStart(2, "0")}`]), explanation: "Een kwartier is 15 minuten en een halfuur is 30 minuten." };
    }
    case "math_duration": {
      const start = 8 + n % 8;
      const duration = [15, 30, 45, 60][n % 4];
      const end = start * 60 + duration;
      const answer = `${Math.floor(end / 60)}:${String(end % 60).padStart(2, "0")}`;
      return { prompt: `De les start om ${start}:00 en duurt ${duration} minuten. Wanneer eindigt ze?`, answer, options: optionSet(answer, [`${start}:15`, `${start}:30`, `${start + 2}:00`]), explanation: "Tel de duur bij de begintijd." };
    }
    case "math_calendar": {
      const day = 2 + n % 20;
      const later = 1 + n % 7;
      return numericRecord(`Vandaag is het de ${day}e. Welke datum is het ${later} dagen later?`, day + later, "Ga op de kalender het gevraagde aantal vakjes vooruit.", [1, -1, later + 1]);
    }
    case "math_money_total": {
      const first = 2 + n % 28;
      const second = 1 + (n * 3) % 20;
      return { prompt: `Een boek kost € ${first} en een pen € ${second}. Hoeveel betaal je samen?`, answer: `€ ${first + second}`, options: optionSet(`€ ${first + second}`, [`€ ${Math.abs(first - second)}`, `€ ${first + second + 1}`, `€ ${first + second - 1}`]), explanation: "Tel de twee prijzen op." };
    }
    case "math_money_change": {
      const price = 5 + n % 35;
      const paid = price <= 20 ? 20 : 50;
      return { prompt: `Je betaalt € ${paid} voor iets van € ${price}. Hoeveel wisselgeld krijg je?`, answer: `€ ${paid - price}`, options: optionSet(`€ ${paid - price}`, [`€ ${price}`, `€ ${paid + price}`, `€ ${Math.max(0, paid - price - 1)}`]), explanation: "Trek de prijs af van het betaalde bedrag." };
    }
    case "math_shapes": {
      const shapes = [
        ["driehoek", "3 zijden", "4 zijden", "geen hoeken", "6 zijden"],
        ["vierkant", "4 even lange zijden", "3 zijden", "één ronde rand", "5 hoeken"],
        ["rechthoek", "4 rechte hoeken", "3 hoeken", "geen zijden", "alle zijden rond"],
      ][n % 3];
      return { prompt: `Bekijk een ${shapes[0]}. Welke eigenschap kun je aanwijzen?`, answer: shapes[1], options: shapes.slice(1), explanation: `Tel de zijden en hoeken van de ${shapes[0]}.` };
    }
    case "math_solids": {
      const solids = [["kubus", "6 vierkante vlakken"], ["bol", "één gebogen oppervlak"], ["cilinder", "2 ronde vlakken"]];
      const [solid, answer] = solids[n % solids.length];
      return { prompt: `Neem een ${solid}. Welke eigenschap voel of zie je?`, answer, options: optionSet(answer, ["3 driehoekige vlakken", "alleen rechte lijnen", "geen oppervlak"]), explanation: `Draai de ${solid} en bekijk elk vlak.` };
    }
    case "math_routes": {
      const right = 1 + n % 4;
      const up = 1 + (n * 3) % 4;
      return { prompt: `Ga op het rooster ${right} vakjes naar rechts en ${up} vakjes omhoog. Welke route noteer je?`, answer: `→ ${right}, ↑ ${up}`, options: optionSet(`→ ${right}, ↑ ${up}`, [`← ${right}, ↑ ${up}`, `→ ${up}, ↑ ${right}`, `→ ${right}, ↓ ${up}`]), explanation: "Lees eerst de richting en tel daarna de vakjes." };
    }
    case "math_mirror": {
      const distance = 1 + n % 5;
      return numericRecord(`Een punt ligt ${distance} vakjes links van de spiegelas. Hoeveel vakjes rechts van de as ligt het spiegelpunt?`, distance, "Een spiegelpunt ligt even ver van de spiegelas.", [1, -1, 2]);
    }
    case "math_table": {
      const red = 2 + n % 8;
      const blue = 1 + (n * 3) % 7;
      return numericRecord(`In een tabel staan ${red} rode en ${blue} blauwe ballen. Hoeveel ballen staan er samen in de tabel?`, red + blue, "Lees beide vakken en tel de aantallen op.");
    }
    case "math_bar_chart": {
      const high = 6 + n % 9;
      const low = 1 + n % 5;
      return numericRecord(`Een staaf voor appels komt tot ${high}; die voor peren tot ${low}. Hoeveel appels zijn er meer?`, high - low, "Lees de hoogtes af en trek het kleinste aantal af.");
    }
    case "math_word_one": {
      const total = 12 + n % 40;
      const away = 2 + n % 9;
      return numericRecord(`Er liggen ${total} kaartjes. ${away} kaartjes worden weggenomen. Hoeveel blijven er?`, total - away, "Eén handeling neemt iets weg, dus je trekt af.");
    }
    case "math_word_two": {
      const start = 20 + n % 30;
      const away = 2 + n % 8;
      const add = 1 + (n * 3) % 7;
      return numericRecord(`Er zijn ${start} kinderen. ${away} gaan weg en ${add} komen erbij. Hoeveel zijn er nu?`, start - away + add, "Trek eerst af en tel daarna erbij.");
    }
    default:
      return numericRecord(`Tel ${a} voorwerpen en leg er ${b} bij. Hoeveel liggen er samen?`, a + b, "Tel de twee hoeveelheden samen.");
  }
}

const frenchThemes = {
  "FR-01": {
    title: "Begroeten en afscheid nemen",
    pairs: [["bonjour", "goedendag"], ["bonsoir", "goedenavond"], ["salut", "hallo"], ["au revoir", "tot ziens"], ["à bientôt", "tot binnenkort"]],
    situations: [["Je komt de klas binnen.", "bonjour"], ["Je vertrekt naar huis.", "au revoir"], ["Je groet een vriend.", "salut"]],
  },
  "FR-02": {
    title: "Beleefd vragen en bedanken",
    pairs: [["s'il vous plaît", "alstublieft"], ["merci", "dank je"], ["pardon", "sorry"], ["je voudrais", "ik zou graag willen"]],
    situations: [["Je vraagt beleefd om water.", "De l'eau, s'il vous plaît."], ["Iemand helpt je.", "Merci."], ["Je botst per ongeluk tegen iemand.", "Pardon."]],
  },
  "FR-03": {
    title: "Kleuren",
    pairs: [["rouge", "rood"], ["bleu", "blauw"], ["jaune", "geel"], ["vert", "groen"], ["noir", "zwart"], ["blanc", "wit"]],
    situations: [["Je wijst een rode bal aan.", "rouge"], ["Je kiest een blauwe jas.", "bleu"], ["Je kleurt de zon geel.", "jaune"]],
  },
  "FR-04": {
    title: "Tellen tot twintig",
    pairs: [["un", "één"], ["deux", "twee"], ["trois", "drie"], ["quatre", "vier"], ["cinq", "vijf"], ["dix", "tien"]],
    situations: [["Je telt drie ijsjes.", "trois"], ["Je telt vijf potloden.", "cinq"], ["Je telt twee boeken.", "deux"]],
  },
  "FR-05": {
    title: "Eten en drinken",
    pairs: [["l'eau", "water"], ["le lait", "melk"], ["le pain", "brood"], ["une pomme", "een appel"], ["une glace", "een ijsje"], ["le chocolat", "chocolade"]],
    situations: [["Je wilt water drinken.", "l'eau"], ["Je eet een appel.", "une pomme"], ["Je kiest brood.", "le pain"]],
  },
  "FR-06": {
    title: "Een ijsje bestellen",
    pairs: [["vanille", "vanille"], ["chocolat", "chocolade"], ["fraise", "aardbei"], ["une glace", "een ijsje"]],
    situations: [["Je bestelt beleefd een vanille-ijsje.", "Je voudrais une glace à la vanille, s'il vous plaît."], ["De verkoper geeft je het ijsje.", "Merci."], ["Je kiest aardbei.", "fraise"]],
  },
  "FR-07": {
    title: "Tegenstellingen",
    pairs: [["grand", "groot"], ["petit", "klein"], ["rapide", "snel"], ["lent", "traag"], ["haut", "hoog"], ["bas", "laag"]],
    situations: [["Je wijst het grote dier aan.", "grand"], ["De slak beweegt traag.", "lent"], ["De plank hangt hoog.", "haut"]],
  },
  "FR-08": {
    title: "Klasinstructies",
    pairs: [["écoute", "luister"], ["répète", "zeg na"], ["regarde", "kijk"], ["montre", "wijs aan"]],
    situations: [["De juf zegt: écoute.", "Je luistert."], ["De juf zegt: répète.", "Je zegt het na."], ["De juf zegt: montre.", "Je wijst het aan."]],
  },
};

function frenchRecord(themeId, index, seriesOrdinal) {
  const theme = frenchThemes[themeId] || frenchThemes["FR-01"];
  const pair = theme.pairs[(index + seriesOrdinal) % theme.pairs.length];
  const otherFr = theme.pairs.filter((item) => item[0] !== pair[0]).map((item) => item[0]);
  const otherNl = theme.pairs.filter((item) => item[1] !== pair[1]).map((item) => item[1]);
  const situation = theme.situations[(index + seriesOrdinal * 2) % theme.situations.length];
  switch (index % 5) {
    case 0:
      return { prompt: `Luister en tik de betekenis van ‘${pair[0]}’ aan.`, answer: pair[1], options: optionSet(pair[1], otherNl), explanation: `‘${pair[0]}’ betekent ‘${pair[1]}’.` };
    case 1:
      return { prompt: `Hoe zeg je ‘${pair[1]}’ in het Frans?`, answer: pair[0], options: optionSet(pair[0], otherFr), explanation: `Je zegt ‘${pair[0]}’.` };
    case 2:
      return { prompt: `${situation[0]} Wat zeg of doe je?`, answer: situation[1], options: optionSet(situation[1], theme.situations.filter((item) => item[1] !== situation[1]).map((item) => item[1]).concat(otherFr)), explanation: `In deze situatie past ‘${situation[1]}’.` };
    case 3:
      return { prompt: `Zeg ‘${pair[0]}’ rustig na. Welke betekenis kies je daarna?`, answer: pair[1], options: optionSet(pair[1], otherNl), explanation: `Luister naar het hele Franse woord en koppel het aan ‘${pair[1]}’.` };
    default:
      return { prompt: `Maak het korte gesprek af over ${theme.title.toLowerCase()}: “${situation[0]}”`, answer: situation[1], options: optionSet(situation[1], otherFr.concat(otherNl)), explanation: `De passende reactie is ‘${situation[1]}’.` };
  }
}

function lessonFacts(lesson) {
  const theory = lesson.stages.find((stage) => stage.type === "THEORY" || stage.type === "ACQUIRE_UNDERSTAND");
  const blocks = theory?.contentBlocks || [];
  const terms = blocks.flatMap((block) => block.terms || []).map((term) => ({
    term: visible(term.term),
    definition: visible(term.definition),
    example: visible(term.example),
    nonExample: visible(term.nonExample),
  })).filter((item) => item.term && item.definition);
  const bodies = Object.fromEntries(blocks.map((block) => [block.type, visible(block.body)]));
  const firstParameters = lesson.stages.flatMap((stage) => stage.exerciseSets || []).find((set) => set.parameters)?.parameters || {};
  const steps = String(firstParameters.how_nl || bodies.HOW_IT_WORKS || "").split(/(?:Daarna:|\n|•|—)/).map((item) => item.trim()).filter(Boolean);
  return {
    title: visible(lesson.title),
    goal: firstParameters.target_nl || bodies.LEARNING_INTENT || visible(lesson.summary),
    focus: firstParameters.focus_nl || bodies.CORRECT_RESULT_RULE || visible(lesson.description),
    example: firstParameters.example_nl || bodies.WORKED_EXAMPLE || visible(lesson.description),
    nonExample: firstParameters.non_example_nl || bodies.NON_EXAMPLE || "Een stap wordt overgeslagen, waardoor het resultaat niet klopt.",
    check: firstParameters.check_nl || bodies.SELF_CHECK || "Controleer met het voorbeeld of je keuze past.",
    terms: terms.length ? terms : [{ term: visible(lesson.title), definition: visible(lesson.summary), example: visible(lesson.description), nonExample: "Dit hoort bij een ander leerdoel." }],
    steps: steps.length ? steps : ["Bekijk de opdracht.", "Voer de gevraagde handeling uit.", "Controleer het resultaat."],
  };
}

function concreteRecord(lesson, index, seriesOrdinal) {
  const facts = lessonFacts(lesson);
  const term = facts.terms[(index + seriesOrdinal) % facts.terms.length];
  const otherTerms = facts.terms.filter((item) => item.term !== term.term);
  const step = facts.steps[(index + seriesOrdinal) % facts.steps.length];
  const context = ["in de klas", "aan je tafel", "bij een nieuw voorbeeld", "tijdens het oefenen", "bij de controle"][(index + seriesOrdinal) % 5];
  const wrongActions = unique([
    term.nonExample,
    facts.nonExample,
    `Je gebruikt ${otherTerms[0]?.term || "een ander begrip"} terwijl de opdracht om ${term.term} vraagt.`,
    `Je stopt ${context} vóór de gevraagde stap klaar is.`,
    `Je verwisselt ${term.term} met ${otherTerms[1]?.term || "een ander onderdeel"}.`,
  ]);
  switch (index % 5) {
    case 0:
      return { prompt: `Lees: “${term.definition}” Tik het begrip aan dat erbij hoort.`, answer: term.term, options: optionSet(term.term, otherTerms.map((item) => item.term).concat([`niet ${term.term}`, `${term.term} overslaan`])), explanation: `${term.term}: ${term.definition}` };
    case 1:
      return { prompt: `Je werkt ${context} aan ${facts.title.toLowerCase()}. Welke concrete stap voer je nu uit?`, answer: step, options: optionSet(step, wrongActions), explanation: `Voer deze stap uit: ${step}` };
    case 2:
      return { prompt: `Tik het bruikbare voorbeeld voor ${facts.title.toLowerCase()} aan.`, answer: term.example || facts.example, options: optionSet(term.example || facts.example, wrongActions), explanation: term.example || facts.example };
    case 3:
      return { prompt: `Je oefent met ‘${term.term}’. Welke uitleg gebruik je om je keuze te maken?`, answer: term.definition, options: optionSet(term.definition, otherTerms.map((item) => item.definition).concat(wrongActions)), explanation: term.definition };
    default:
      return { prompt: `Controleer je werk over ${facts.title.toLowerCase()}. Welke controle voer je uit?`, answer: facts.check, options: optionSet(facts.check, wrongActions), explanation: facts.check };
  }
}

function poolFor(lesson, set, seriesOrdinal, frenchThemeId) {
  const mode = set.parameters?.mode || "";
  const actors = ["Mila", "Noah", "Lina", "Adam", "Yara", "Finn", "Sara", "Lou", "Amir", "Zoë"];
  const places = ["aan de oefentafel", "bij het bord", "in de leeshoek", "bij het materiaalrek"];
  const seriesTools = [
    "een voorbeeldkaart met zichtbare steun",
    "een stappenstrook met twee ingevulde stappen",
    "een kaart waarop alleen de eerste stap staat",
    "nieuwe voorwerpen zonder uitgewerkt voorbeeld",
    "een tweede reeks met andere woorden en waarden",
    "een V1-opdrachtkaart met een concrete stimulus",
    "een herhaalkaart met een eerder leerdoel",
    "uitdagender materiaal voor een nieuwe toepassing",
    "een korte theoriekaart",
    "een ongeziene toetskaart A zonder hulp",
    "een ongeziene hertoetskaart B zonder hulp",
  ];
  const seriesTool = seriesTools[(seriesOrdinal - 1) % seriesTools.length];
  return Array.from({ length: 12 }, (_, index) => {
    const contentIndex = index + seriesOrdinal * 11;
    const record = frenchThemeId
      ? frenchRecord(frenchThemeId, contentIndex, seriesOrdinal)
      : lesson.grade === 2 && mode.startsWith("math_")
        ? mathRecord(mode, contentIndex, seriesOrdinal)
        : concreteRecord(lesson, contentIndex, seriesOrdinal);
    const actor = actors[index % actors.length];
    const place = places[Math.floor(index / actors.length) % places.length];
    const contextualWrong = `${actor} gebruikt ${seriesTool} voor een andere opdracht dan de vraag.`;
    return {
      ...record,
      prompt: `${actor} gebruikt ${place} ${seriesTool}. ${record.prompt}`,
      options: optionSet(record.answer, [...record.options.filter((option) => normalize(option) !== normalize(record.answer)).slice(0, 2), contextualWrong]),
    };
  });
}

function exerciseTypeFor(lesson, set) {
  const mode = set.parameters?.mode || "";
  if (mode.includes("add")) return "addition";
  if (mode.includes("sub")) return "subtraction";
  if (mode.includes("multiply")) return "multiplication";
  if (mode.includes("divide")) return "division";
  if (mode.includes("money")) return "money";
  if (mode.includes("clock") || mode.includes("duration") || mode.includes("calendar")) return "time";
  if (mode.startsWith("math_")) return "multiple_choice_math";
  if (lesson.id.includes(".fr.")) return "listen_repeat_translate_multiple_choice";
  if (lesson.legacyRouting.domain === "WRITING") return "writing_or_multiple_choice";
  if (lesson.legacyRouting.domain === "READING") return "reading_or_listening_multiple_choice";
  if (lesson.legacyRouting.domain === "WORLD_KNOWLEDGE") return "observation_or_multiple_choice";
  return "multiple_choice";
}

function blueprints(set, count, origin = set.origin || "GENERATED") {
  return Array.from({ length: count }, (_, index) => {
    const id = `${set.id}.question.${index + 1}`;
    return {
      id,
      lessonId: set.lessonId,
      stageId: set.stageId,
      exerciseSetId: set.id,
      generatorVersion: "v2",
      origin,
      validationStatus: set.validationStatus,
      publicationStatus: set.publicationStatus,
      answerOptionIds: Array.from({ length: 4 }, (_, answerIndex) => `${id}.answer.${answerIndex + 1}`),
    };
  });
}

function retargetSet(set, lesson, stage, id, title, purpose, itemCount, sequence, allowed) {
  const copy = deepClone(set);
  copy.id = id;
  copy.exerciseSetId = id;
  copy.lessonId = lesson.id;
  copy.stageId = stage.id;
  copy.title = nl(title);
  copy.sequence = sequence;
  copy.purpose = purpose;
  copy.itemCount = itemCount;
  copy.validationStatus = lesson.publicationStatus === "ACTIVE" ? "VALIDATED" : "NEEDS_REVIEW";
  copy.publicationStatus = lesson.publicationStatus;
  copy.parameters = { ...(copy.parameters || {}), [WRONG]: String(allowed), exercise_locales: "nl-BE" };
  copy.curatedExerciseIds = [];
  copy.questionBlueprints = blueprints(copy, itemCount, copy.origin || "GENERATED");
  copy.editorialItems = [];
  return copy;
}

function replacePrefix(value, oldId, newId) {
  if (typeof value === "string") return value.split(oldId).join(newId);
  if (Array.isArray(value)) return value.map((item) => replacePrefix(item, oldId, newId));
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([key, item]) => [replacePrefix(key, oldId, newId), replacePrefix(item, oldId, newId)]));
  return value;
}

function addFrenchLesson(catalog, baseId, suffix, title, themeId, grade) {
  const id = `${catalog.lessons.find((item) => item.id === baseId).id.substring(0, baseId.lastIndexOf("."))}.${suffix}`;
  if (catalog.lessons.some((item) => item.id === id)) return catalog.lessons.find((item) => item.id === id);
  const base = catalog.lessons.find((item) => item.id === baseId);
  if (!base) throw new Error(`French base lesson missing: ${baseId}`);
  const lesson = replacePrefix(deepClone(base), base.id, id);
  lesson.id = id;
  lesson.grade = grade;
  lesson.sequence = Math.max(...catalog.lessons.map((item) => item.sequence || 0)) + 1;
  lesson.title = nl(title);
  lesson.summary = nl(`Je begrijpt en gebruikt Franse woorden en korte zinnen over ${title.toLowerCase()}.`);
  lesson.description = nl(`Luister, zeg na, koppel de betekenis en gebruik de woorden in een kort gesprek over ${title.toLowerCase()}.`);
  lesson.pedagogicalLogic = nl("Van luisteren en nazeggen naar betekenis, vertalen, een mini-dialoog en een ongeziene toets.");
  lesson.learningGoalIds = [`goal.${id.substring("lesson.".length)}.functional`];
  lesson.masteryTags = [`${id.substring("lesson.".length)}.${themeId.toLowerCase()}`];
  lesson.prerequisiteLessonIds = [base.id];
  lesson.gatePolicy.requiredMasteryTags = [...lesson.masteryTags];
  lesson.legacyRouting.legacyLessonIds = [];
  lesson.publicationStatus = "DRAFT_REVIEW_REQUIRED";
  lesson.definitionVersion = CATALOG_VERSION;
  const visualIdMap = new Map();
  lesson.visuals = (lesson.visuals || []).map((visual, index) => {
    const visualId = `${id}.visual.${index + 1}`;
    visualIdMap.set(visual.id, visualId);
    return { ...visual, id: visualId };
  });
  for (const stage of lesson.stages) {
    for (const block of stage.contentBlocks || []) {
      block.visualIds = (block.visualIds || []).map((visualId) => visualIdMap.get(visualId) || visualId);
    }
  }
  lesson._feedbackThemeId = themeId;
  catalog.lessons.push(lesson);
  return lesson;
}

function themeForLesson(lesson) {
  if (lesson._feedbackThemeId) return lesson._feedbackThemeId;
  if (lesson.id === "lesson.g1.fr.listening.franse-klanken-en-begroetingen") return "FR-01";
  if (lesson.id.endsWith(".beleefd-vragen-en-bedanken")) return "FR-02";
  if (lesson.id.endsWith(".kleuren")) return "FR-03";
  if (lesson.id.endsWith(".tellen-tot-twintig")) return "FR-04";
  if (lesson.id.endsWith(".eten-en-drinken")) return "FR-05";
  if (lesson.id.endsWith(".een-ijsje-bestellen")) return "FR-06";
  if (lesson.id.endsWith(".tegenstellingen")) return "FR-07";
  if (lesson.id.includes("liedjes-tellen-en-kleuren")) return "FR-04";
  if (lesson.id.endsWith(".klaswoorden-en-eenvoudige-opdrachten")) return "FR-08";
  if (lesson.id.includes("naam-en-gevoel")) return "FR-02";
  if (lesson.id.includes("voorkeur-zeggen")) return "FR-05";
  return null;
}

function rewriteFrenchTheory(lesson, themeId) {
  const theme = frenchThemes[themeId];
  if (!theme) return;
  lesson.title = nl(theme.title);
  lesson.summary = nl(`Je begrijpt en gebruikt Franse woorden over ${theme.title.toLowerCase()}.`);
  lesson.description = nl(`Luister, zeg na, kies de betekenis en gebruik de woorden in een kort gesprek over ${theme.title.toLowerCase()}.`);
  lesson.pedagogicalLogic = nl("Eerst luisteren, daarna nazeggen, betekenis kiezen, vertalen en toepassen in een mini-dialoog.");
  const theory = lesson.stages.find((stage) => stage.type === "THEORY" || stage.type === "ACQUIRE_UNDERSTAND");
  const pairs = theme.pairs;
  const bodyByType = {
    LEARNING_INTENT: `Je leert Franse woorden en korte zinnen gebruiken voor ${theme.title.toLowerCase()}.`,
    HOW_IT_WORKS: "1. Luister naar het hele Franse woord. 2. Zeg het rustig na. 3. Koppel het aan de Nederlandse betekenis of een beeld. 4. Gebruik het in een korte situatie.",
    CORRECT_RESULT_RULE: "Je keuze klopt wanneer het Franse woord, de betekenis en de situatie bij elkaar passen.",
    WORKED_EXAMPLE: `Je hoort ‘${pairs[0][0]}’. Je zegt het na en kiest ‘${pairs[0][1]}’.`,
    NON_EXAMPLE: `Je hoort ‘${pairs[0][0]}’, maar kiest een betekenis die bij een ander woord hoort.`,
    SELF_CHECK: "Luister nog één keer en controleer daarna de betekenis.",
    SUMMARY: `Luister, zeg na en gebruik de woorden over ${theme.title.toLowerCase()}.`,
  };
  for (const block of theory?.contentBlocks || []) {
    if (bodyByType[block.type]) block.body = nl(bodyByType[block.type]);
    if (block.type === "VOCABULARY" && block.terms?.length) {
      block.terms.forEach((term, index) => {
        const pair = pairs[index % pairs.length];
        term.term = nl(pair[0]);
        term.definition = nl(pair[1]);
        if (term.example) term.example = nl(`Luister en zeg na: ${pair[0]}.`);
        if (term.nonExample) term.nonExample = nl(`Een ander Frans woord betekent niet automatisch ${pair[1]}.`);
      });
    }
    if (block.type === "UITLEG_OUDERS") {
      block.body = nl(`Wat leert uw kind?\nFranse woorden en korte zinnen over ${theme.title.toLowerCase()}.\n\nZo legt u het uit\nLaat het kind eerst luisteren, dan nazeggen en pas daarna de betekenis kiezen.\n\nVoorbeeld\n‘${pairs[0][0]}’ betekent ‘${pairs[0][1]}’.\n\nVeelgemaakte fout\nEen bekend klinkend woord te snel aan de verkeerde betekenis koppelen.\n\nOefentip voor thuis\nGebruik twee woorden in een kort vraag-en-antwoordspel.`);
    }
  }
}

function mapV1(xmlQuestions, lessons) {
  const byGrade = new Map([1, 2].map((grade) => [grade, lessons.filter((lesson) => lesson.grade === grade)]));
  const patterns = [
    [/addition|optellen/, /optell|addition/],
    [/subtraction|aftrekken/, /aftrek|subtraction/],
    [/multiplication|vermenigvuldigen|maal/, /vermenig|maal|sprongen/],
    [/division|delen/, /delen|deel|verdeel|groepjes/],
    [/mixed.?arithmetic|gemengd/, /rekenfeiten|problem-solving|strategie/],
    [/money|geld/, /geld|money/],
    [/fraction|breuk/, /fraction|breuk|helft|kwart|derden/],
    [/number.?sequence|getallenreeks|sequence/, /getalpatron|sprongen|getallenlijn/],
    [/time|clock|calendar|tijd|klok/, /tijd|klok|kalender|duration/],
    [/length|lengte/, /length|lengte|measurement/],
    [/mass|weight|massa|gewicht/, /mass|massa|gewicht/],
    [/capacity|volume|inhoud|liter/, /capacity|volume|inhoud|liter/],
    [/shape|geometry|vorm|figuur/, /geometry|figuur|vorm|spiegel|route/],
    [/spelling|dictee/, /spelling|lettergreep|klank|meervoud|verklein/],
    [/french|frans/, /\.fr\./],
    [/reading|lezen/, /reading|technical-reading|comprehension/],
    [/listening|luisteren/, /listening|oral/],
    [/science|nature|natuur|plant|dier|materi|kracht/, /science_tech|world\.nature|world\.environment|nature\./],
    [/history|geschiedenis|vroeger|bron/, /history|chronology|sources|society|eras/],
    [/geography|kaart|ruimte|space|plattegrond/, /geo\.|maps|space|routes/],
    [/citizenship|burger|samenwerk|sociaal|veilig/, /citizenship|social|attitudes|traffic/],
    [/music|art|kunst|teken|drama/, /arts\.|music|visual|drama/],
    [/physical|movement|bewegen|sport/, /\.pe\.|movement/],
  ];
  return xmlQuestions.map((item) => {
    const haystack = normalize([item.subcategoryId, item.topic, item.skillRef, item.tileTitlesNl, item.promptNl, item.lessonReferences].join(" "));
    const gradeLessons = byGrade.get(Number(item.grade)) || [];
    let candidates = [];
    for (const [sourcePattern, targetPattern] of patterns) {
      if (sourcePattern.test(haystack)) {
        candidates = gradeLessons.filter((lesson) => targetPattern.test(normalize(`${lesson.id} ${visible(lesson.title)} ${lesson.curriculumSubcategoryId} ${lesson.legacyRouting.subcategoryId}`)));
        if (candidates.length) break;
      }
    }
    candidates = candidates.filter((lesson) => lesson.legacyRouting.domain === item.domain);
    const lesson = candidates.length ? candidates[hash(item.exerciseId) % candidates.length] : null;
    const options = String(item.optionsNl || "").split(/\s*\|\s*/).filter(Boolean);
    return {
      exerciseId: item.exerciseId,
      source: "V1_CURATED_LEGACY_POOL",
      grade: Number(item.grade),
      difficulty: Number(item.difficultyStars || 0),
      category: item.domain,
      lessonId: lesson?.id || null,
      masteryTags: lesson?.masteryTags || [],
      exerciseType: item.inputType || "unknown",
      contentSignature: normalize(`${item.promptNl}|${item.correctAnswerNl}|${options.slice().sort().join("|")}`),
      curriculumStatus: lesson ? "MAPPED_CURRICULUM" : "EXTRA_ONLY_UNMAPPED",
      mappingConfidence: lesson ? "HIGH_CATEGORY_GRADE_DOMAIN_MATCH" : "UNMAPPED",
      testEligible: Boolean(lesson && item.promptNl && item.correctAnswerNl && (options.length === 0 || new Set(options.map(normalize)).size === options.length)),
      promptNl: item.promptNl,
      correctAnswerNl: item.correctAnswerNl,
      optionsNl: options,
      sourceFile: item.sourceFile,
    };
  });
}

function makeCuratedSet(set, items) {
  set.sourceMode = "CURATED";
  set.origin = "FIXED";
  set.curatedExerciseIds = items.map((item) => item.exerciseId);
  set.generatorId = null;
  set.generatorImplementationVersion = null;
  set.seedPolicy = null;
  delete set.parameters.question_pool_nl;
  delete set.parameters.first_item_signature;
  delete set.parameters.distractors_nl;
  set.parameters.v1_pool_id = "set.v1.curated.practice-1";
  set.parameters.exercise_type = unique(items.map((item) => item.exerciseType)).join("+") || "curated";
  set.questionBlueprints = blueprints(set, set.itemCount, "FIXED");
}

function setPoolAndMetadata(lesson, set, ordinal, themeId) {
  set.validationStatus = lesson.publicationStatus === "ACTIVE" ? "VALIDATED" : "NEEDS_REVIEW";
  set.publicationStatus = lesson.publicationStatus;
  set.generatorVersion = "v2";
  set.parameters ||= {};
  set.parameters.exercise_locales = "nl-BE";
  set.parameters.series_variant = String(ordinal);
  set.parameters.exercise_type = exerciseTypeFor(lesson, set);
  set.parameters.target_cluster = lesson.masteryTags.join("|");
  if (themeId) set.parameters.french_theme_tag = themeId;
  if (set.sourceMode === "GENERATED" && !REVIEWED_POOL_GENERATORS.has(set.generatorId)) {
    set.parameters.original_generator_id = set.generatorId;
    set.parameters.domain = lesson.legacyRouting.domain;
    set.parameters.skill = lesson.legacyRouting.subcategoryId;
    set.parameters.unit_id = lesson.unitId;
    set.parameters.pool_input_mode = /listening|audio/i.test(set.parameters.exercise_type) || themeId
      ? "LISTEN_AND_SELECT"
      : "TAP_CHOICE";
    set.generatorId = REVIEWED_POOL_GENERATOR_ID;
    set.generatorImplementationVersion = 1;
  }
  if (set.sourceMode === "GENERATED") {
    const pool = poolFor(lesson, set, ordinal, themeId);
    set.parameters.question_pool_nl = encodePool(pool);
    set.parameters.first_item_signature = normalize(`${pool[0].prompt}|${pool[0].answer}|${pool[0].options.slice().sort().join("|")}`);
  }
  if (set.generatorId === "generator.g2.atomic-curriculum") {
    set.parameters.distractors_nl = unique([
      `Een antwoord dat ${visible(lesson.title).toLowerCase()} verwisselt met een ander doel`,
      "Een stap die te vroeg stopt",
      "Een uitkomst die niet bij de gegeven informatie past",
    ]).join("|");
  } else {
    delete set.parameters.distractors_nl;
  }
}

function expandLesson(lesson, mappedByLesson) {
  const ordered = lesson.stages.sort((a, b) => a.sequence - b.sequence);
  const theory = ordered.find((stage) => stage.type === "THEORY" || stage.type === "ACQUIRE_UNDERSTAND");
  const exerciseStages = ordered.filter((stage) => stage.type === "EXERCISES");
  const test = ordered.find((stage) => stage.type === "MASTERY_TEST");
  if (!theory || exerciseStages.length < 2 || !test) throw new Error(`Unexpected stage structure: ${lesson.id}`);
  const practice = exerciseStages[0];
  const apply = exerciseStages[1];
  const guided = practice.exerciseSets.find((set) => set.purpose === "GUIDED") || practice.exerciseSets[0];
  const fading = practice.exerciseSets.find((set) => set.purpose === "FADING_SUPPORT") || practice.exerciseSets[1] || guided;
  const independent = practice.exerciseSets.find((set) => set.purpose === "INDEPENDENT_PRACTICE") || practice.exerciseSets.at(-1);
  const transfer = apply.exerciseSets.find((set) => set.purpose === "NEAR_TRANSFER") || apply.exerciseSets[0];
  const testA = test.exerciseSets[0];
  const themeId = themeForLesson(lesson);

  theory.type = "THEORY";
  theory.sequence = 1;
  theory.seriesIndex = null;
  theory.title = nl("Theorie");
  theory.required = true;
  theory.completionPolicy = "COMPLETED_REQUIRED_SETS";
  theory.exerciseSets = [retargetSet(theory.exerciseSets[0], lesson, theory, theory.exerciseSets[0].id, "Controlevragen", "GUIDED", 3, 1, 1)];

  practice.sequence = 2;
  practice.seriesIndex = 1;
  practice.title = nl("Oefeningen");
  practice.exerciseSets = [
    retargetSet(guided, lesson, practice, guided.id, "Begeleid oefenen", "GUIDED", 6, 1, 2),
    retargetSet(fading, lesson, practice, fading.id, "Steun afbouwen", "FADING_SUPPORT", 8, 2, 2),
    retargetSet(independent, lesson, practice, independent.id, "Zelfstandig oefenen A", "INDEPENDENT_PRACTICE", 10, 3, 2),
  ];

  apply.sequence = 3;
  apply.seriesIndex = 2;
  apply.title = nl("Oefeningen");
  apply.exerciseSets = [retargetSet(transfer, lesson, apply, transfer.id, "Toepassen in een nieuwe situatie", "NEAR_TRANSFER", 8, 1, 1)];

  const independentStageId = `${lesson.id}.exercises-independent-b`;
  const independentStage = {
    id: independentStageId,
    type: "EXERCISES",
    sequence: 4,
    seriesIndex: 3,
    sectionId: null,
    sectionTitle: null,
    title: nl("Oefeningen"),
    required: true,
    estimatedMinutes: 12,
    completionPolicy: "COMPLETED_REQUIRED_SETS",
    contentBlocks: [],
    exerciseSets: [],
  };
  const independentB = retargetSet(independent, lesson, independentStage, `${lesson.id}.set.independent-b`, "Zelfstandig oefenen B", "INDEPENDENT_PRACTICE", 10, 1, 1);
  const v1Set = retargetSet(independent, lesson, independentStage, `${lesson.id}.set.v1.curated.practice-1`, "V1-oefendril", "INDEPENDENT_PRACTICE", 10, 2, 1);
  const mapped = (mappedByLesson.get(lesson.id) || []).filter((item) => item.curriculumStatus === "MAPPED_CURRICULUM");
  if (mapped.length >= 10) makeCuratedSet(v1Set, mapped.slice(0, 10));
  else v1Set.parameters.v1_pool_status = "NO_EXACT_TEN_ITEM_MATCH_GENERATED_EQUIVALENT";
  const cumulative = retargetSet(independent, lesson, independentStage, `${lesson.id}.set.cumulative-review`, "Cumulatieve herhaling", "ERROR_REVIEW", 8, 3, 1);
  cumulative.injectErrorMemory = true;
  independentStage.exerciseSets = [independentB, v1Set, cumulative];

  const plusStageId = `${lesson.id}.exercises-plus`;
  const plusStage = {
    id: plusStageId,
    type: "EXERCISES",
    sequence: 5,
    seriesIndex: 4,
    sectionId: null,
    sectionTitle: null,
    title: nl("Oefeningen"),
    required: false,
    estimatedMinutes: 8,
    completionPolicy: "COMPLETED_REQUIRED_SETS",
    contentBlocks: [],
    exerciseSets: [],
  };
  const plus = retargetSet(transfer, lesson, plusStage, `${lesson.id}.set.plus-variation`, "Plus en extra variatie", "FAR_TRANSFER", 8, 1, 2);
  plusStage.exerciseSets = [plus];

  test.sequence = 6;
  test.seriesIndex = null;
  test.title = nl("Test");
  const preservedTestId = testA.id;
  const a = retargetSet(testA, lesson, test, preservedTestId, "Mastery test A", "MASTERY_TEST", 10, 1, 1);
  a.hintPolicy = "NONE";
  a.feedbackPolicy = "AFTER_TEST";
  a.parameters.exclude_recent_exact_items = "true";
  a.parameters.test_form = "A";
  delete a.parameters[WRONG];
  const b = retargetSet(testA, lesson, test, `${lesson.id}.set.retest-b`, "Mastery hertoets B", "MASTERY_TEST", 10, 2, 1);
  b.hintPolicy = "NONE";
  b.feedbackPolicy = "AFTER_TEST";
  b.parameters.exclude_recent_exact_items = "true";
  b.parameters.test_form = "B";
  delete b.parameters[WRONG];
  test.exerciseSets = [a, b];

  lesson.stages = [theory, practice, apply, independentStage, plusStage, test];
  const allSets = lesson.stages.flatMap((stage) => stage.exerciseSets || []);
  allSets.forEach((set) => { set.masteryTags = [...lesson.masteryTags]; });
  allSets.forEach((set, index) => setPoolAndMetadata(lesson, set, index + 1, themeId));
  if (themeId) rewriteFrenchTheory(lesson, themeId);
  lesson.definitionVersion = CATALOG_VERSION;
  delete lesson._feedbackThemeId;

  if (lesson.id.includes("vermenigvuldigen-als-gelijke-groepen")) {
    const tag = "g2.math.operations.tables-2-5-10";
    lesson.masteryTags = unique([...lesson.masteryTags, tag]);
    lesson.gatePolicy.requiredMasteryTags = unique([...lesson.gatePolicy.requiredMasteryTags, tag]);
    allSets.forEach((set) => set.masteryTags = unique([...set.masteryTags, tag]));
  }
  if (lesson.id.includes("delen-als-verdelen-en-groeperen")) {
    const tag = "g2.math.operations.division-facts-2-5-10";
    lesson.masteryTags = unique([...lesson.masteryTags, tag]);
    lesson.gatePolicy.requiredMasteryTags = unique([...lesson.gatePolicy.requiredMasteryTags, tag]);
    allSets.forEach((set) => set.masteryTags = unique([...set.masteryTags, tag]));
  }
}

function editorialStatus(id) {
  return { id: `${id}.translation-status.nl-be`, status: "DEFAULT", comment: "Brontaal en standaardtaal." };
}

function editorialQuestion(lesson, stage, set, blueprint, index, mappedById) {
  let record;
  let type = set.parameters?.exercise_type || "multiple_choice";
  if (set.sourceMode === "CURATED") {
    const mapped = mappedById.get(set.curatedExerciseIds[index]);
    const answer = mapped?.correctAnswerNl || "Juist antwoord";
    record = { prompt: mapped?.promptNl || "Voer de oefening uit.", answer, options: optionSet(answer, mapped?.optionsNl || []), explanation: `Het juiste antwoord is ${answer}.` };
    type = mapped?.exerciseType || type;
  } else {
    const pool = decodePool(set.parameters?.question_pool_nl).length
      ? decodePool(set.parameters?.question_pool_nl)
      : poolFor(lesson, set, Number(set.parameters?.series_variant || 1), set.parameters?.french_theme_tag || null);
    record = pool[index % pool.length];
  }
  const rotation = hash(blueprint.id) % 4;
  const options = [...record.options.slice(rotation), ...record.options.slice(0, rotation)];
  return {
    id: blueprint.id,
    lessonId: lesson.id,
    stageId: stage.id,
    exerciseSetId: set.id,
    type,
    question: record.prompt,
    translations: { "nl-BE": { question: record.prompt, explanation: record.explanation } },
    generated: set.sourceMode === "GENERATED",
    automaticGenerationEnabled: set.sourceMode === "GENERATED",
    generatorVersion: "v2",
    origin: set.origin,
    validationStatus: set.validationStatus,
    publicationStatus: set.publicationStatus,
    translationStatus: { "nl-BE": editorialStatus(blueprint.id) },
    editorComments: { "nl-BE": "Feedback L1/L2 curriculum-breed toegepast." },
    answers: blueprint.answerOptionIds.map((id, optionIndex) => {
      const value = options[optionIndex];
      return {
        id,
        value,
        correct: normalize(value) === normalize(record.answer),
        displays: { "nl-BE": value },
        speaks: { "nl-BE": value },
        translationStatus: { "nl-BE": editorialStatus(id) },
        contentFieldId: `${id}.field.value`,
      };
    }),
    acceptedAnswers: [],
    explanation: record.explanation,
    contentFieldIds: { question: `${blueprint.id}.field.question`, explanation: `${blueprint.id}.field.explanation` },
  };
}

function csvEscape(value) {
  const text = String(value ?? "");
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function counts(catalog) {
  const lessons = catalog.lessons.filter((lesson) => [1, 2].includes(lesson.grade));
  const sets = lessons.flatMap((lesson) => lesson.stages.flatMap((stage) => stage.exerciseSets || []));
  return {
    lessons: lessons.length,
    grade1Lessons: lessons.filter((lesson) => lesson.grade === 1).length,
    grade2Lessons: lessons.filter((lesson) => lesson.grade === 2).length,
    sets: sets.length,
    exercisePositions: sets.reduce((sum, set) => sum + set.itemCount, 0),
    v1CuratedSets: sets.filter((set) => set.sourceMode === "CURATED").length,
    masteryTestSets: sets.filter((set) => set.purpose === "MASTERY_TEST").length,
  };
}

fs.mkdirSync(reportDir, { recursive: true });
const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
const originalStableIds = stableIds(catalog);
const before = counts(catalog);

addFrenchLesson(catalog, "lesson.g1.fr.listening.franse-klanken-en-begroetingen", "beleefd-vragen-en-bedanken", "Beleefd vragen en bedanken", "FR-02", 1);
addFrenchLesson(catalog, "lesson.g2.fr.listening.klaswoorden-en-eenvoudige-opdrachten.klaswoorden-en-eenvoudige-opdrachten", "kleuren", "Franse kleuren", "FR-03", 2);
addFrenchLesson(catalog, "lesson.g2.fr.listening.klaswoorden-en-eenvoudige-opdrachten.klaswoorden-en-eenvoudige-opdrachten", "tellen-tot-twintig", "Tellen tot twintig in het Frans", "FR-04", 2);
addFrenchLesson(catalog, "lesson.g2.fr.speaking.naam-gevoel-en-voorkeur.voorkeur-zeggen", "eten-en-drinken", "Eten en drinken in het Frans", "FR-05", 2);
addFrenchLesson(catalog, "lesson.g2.fr.speaking.naam-gevoel-en-voorkeur.voorkeur-zeggen", "een-ijsje-bestellen", "Een ijsje bestellen", "FR-06", 2);
addFrenchLesson(catalog, "lesson.g2.fr.speaking.naam-gevoel-en-voorkeur.voorkeur-zeggen", "tegenstellingen", "Groot, klein, snel en traag", "FR-07", 2);

const audit = JSON.parse(fs.readFileSync(v1AuditPath, "utf8"));
const mappings = mapV1(audit.xmlQuestions || [], catalog.lessons);
const mappedByLesson = new Map();
for (const item of mappings) {
  if (!item.lessonId) continue;
  if (!mappedByLesson.has(item.lessonId)) mappedByLesson.set(item.lessonId, []);
  mappedByLesson.get(item.lessonId).push(item);
}

for (const lesson of catalog.lessons.filter((item) => [1, 2].includes(item.grade))) expandLesson(lesson, mappedByLesson);
catalog.definitionVersion = Math.max(Number(catalog.definitionVersion || 1), CATALOG_VERSION);

const grade1Crosswalk = JSON.parse(fs.readFileSync(grade1CrosswalkPath, "utf8"));
const grade1LessonsById = new Map(catalog.lessons.filter((lesson) => lesson.grade === 1).map((lesson) => [lesson.id, lesson]));
for (const row of grade1Crosswalk.units || []) {
  const lesson = grade1LessonsById.get(row.lessonId);
  if (!lesson) continue;
  const theoryStage = lesson.stages.find((stage) => stage.type === "THEORY");
  const exerciseStages = lesson.stages.filter((stage) => stage.type === "EXERCISES");
  const testStage = lesson.stages.find((stage) => stage.type === "MASTERY_TEST");
  const sets = lesson.stages.flatMap((stage) => stage.exerciseSets || []);
  row.evidence.theoryStageId = theoryStage?.id || "";
  row.evidence.exerciseStageIds = exerciseStages.map((stage) => stage.id);
  row.evidence.masteryTestStageId = testStage?.id || "";
  row.evidence.exerciseSetIds = sets.map((set) => set.id);
  row.evidence.generatorIds = unique(sets.flatMap((set) => [set.generatorId, set.parameters?.original_generator_id]));
  row.evidence.masteryTags = [...lesson.masteryTags];
  row.evidence.visualAssetIds = (lesson.visuals || []).map((visual) => visual.id);
}

const afterStableIds = new Set(stableIds(catalog));
const missingOriginalStableIds = originalStableIds.filter((id) => !afterStableIds.has(id));
if (missingOriginalStableIds.length) throw new Error(`Original stable IDs lost: ${missingOriginalStableIds.slice(0, 10).join(", ")}`);

const mappedById = new Map(mappings.map((item) => [item.exerciseId, item]));
const questions = [];
for (const lesson of catalog.lessons.filter((item) => item.grade === 2)) {
  for (const stage of lesson.stages) {
    for (const set of stage.exerciseSets || []) {
      set.questionBlueprints.forEach((blueprint, index) => questions.push(editorialQuestion(lesson, stage, set, blueprint, index, mappedById)));
    }
  }
}
const bank = {
  schemaVersion: 2,
  definitionVersion: catalog.definitionVersion,
  generatedAt: new Date().toISOString(),
  sourceLocale: "nl-BE",
  exerciseLocales: ["nl-BE"],
  translationPolicy: "Questions and answers are nl-BE runtime content; support translations remain lesson-content metadata.",
  questions,
};

const mappingReport = {
  schemaVersion: 1,
  generatedAt: new Date().toISOString(),
  source: "deliverables/lesson-hierarchy-source/poincare-question-audit.json#xmlQuestions",
  poolDefinitions: [
    { id: "set.v1.curated.practice-1", purpose: "first exact-match curated drill" },
    { id: "set.v1.curated.practice-2", purpose: "reserved second exact-match curated drill" },
    { id: "set.v1.curated.mixed-review", purpose: "reserved cumulative review pool" },
    { id: "set.v1.curated.test-candidate-pool", purpose: "eligible unseen test candidates only" },
  ],
  requiredMetadata: ["grade", "difficulty", "category", "lessonId", "masteryTags", "exerciseType", "contentSignature"],
  counts: {
    total: mappings.length,
    mappedCurriculum: mappings.filter((item) => item.curriculumStatus === "MAPPED_CURRICULUM").length,
    extraOnlyUnmapped: mappings.filter((item) => item.curriculumStatus === "EXTRA_ONLY_UNMAPPED").length,
    testEligible: mappings.filter((item) => item.testEligible).length,
  },
  items: mappings,
};

const coverage = catalog.lessons.filter((lesson) => [1, 2].includes(lesson.grade)).map((lesson) => {
  const sets = lesson.stages.flatMap((stage) => stage.exerciseSets || []);
  const exerciseSets = sets.filter((set) => set.purpose !== "MASTERY_TEST" && set.parameters?.theory_follow_up !== "true");
  const tests = sets.filter((set) => set.purpose === "MASTERY_TEST");
  const mappedV1 = mappedByLesson.get(lesson.id) || [];
  const adult = ["WRITING"].includes(lesson.legacyRouting.domain) || lesson.curriculumSubcategoryId.includes("speaking");
  return {
    lessonId: lesson.id,
    grade: lesson.grade,
    targetCluster: lesson.masteryTags.join("|"),
    officialCategory: lesson.curriculumCategoryId,
    currentStatus: lesson.publicationStatus,
    v2Sets: sets.length,
    v1MappedSets: sets.filter((set) => set.sourceMode === "CURATED").length,
    v1MappedExerciseCount: mappedV1.length,
    exerciseCount: exerciseSets.reduce((sum, set) => sum + set.itemCount, 0),
    testCount: tests.length,
    visualsModels: unique([...(lesson.visuals || []).map((visual) => visual.representationId || visual.id), ...sets.flatMap((set) => set.representationIds || [])]).join("|"),
    prerequisite: (lesson.prerequisiteLessonIds || []).join("|"),
    status: adult ? "ADULT_MEDIATED" : "FULL",
  };
});

const after = counts(catalog);
const delta = {
  schemaVersion: 1,
  generatedAt: new Date().toISOString(),
  task: "Feedback extrapolation L1/L2",
  sourceFeedbackRows: 550,
  scopeDecision: "The reviewed first exercise is treated as evidence of a generator-wide pattern; every set in every Grade 1 and Grade 2 lesson is covered.",
  before,
  after,
  additions: {
    frenchThemeLessons: 6,
    exerciseSetsPerLesson: 8,
    theoryChecksPerLesson: 1,
    testFormsPerLesson: 2,
    originalStableIdsPreserved: missingOriginalStableIds.length === 0,
  },
  validatorsRequired: [
    "firstExerciseSignatureUniqueWithinLesson",
    "noGenericDistractorSet",
    "noPracticeTestOverlapNormalized",
    "noRepeatedStimulusAcrossAdjacentSets",
    "answerOptionsVaryWhenRetryPolicyChanges",
  ],
  artifacts: [mappingPath, coverageJsonPath, coverageCsvPath, bankPath],
};

const coverageHeaders = Object.keys(coverage[0]);
const coverageCsv = [coverageHeaders.join(","), ...coverage.map((row) => coverageHeaders.map((key) => csvEscape(row[key])).join(","))].join("\r\n") + "\r\n";
const deltaMd = `# Feedback-extrapolatie L1/L2 — deltarapport\n\n` +
  `De 550 beoordeelde rijen zijn behandeld als signalen van curriculum-brede generatorpatronen. De correctie is daarom toegepast op alle vragenreeksen, niet alleen op de eerste oefening die werd nagekeken.\n\n` +
  `## Voor en na\n\n| Maatstaf | Voor | Na |\n|---|---:|---:|\n` +
  `| Lessen | ${before.lessons} | ${after.lessons} |\n| Reeksen | ${before.sets} | ${after.sets} |\n| Oefenposities | ${before.exercisePositions} | ${after.exercisePositions} |\n| V1-curated reeksen | ${before.v1CuratedSets} | ${after.v1CuratedSets} |\n| Toetsreeksen | ${before.masteryTestSets} | ${after.masteryTestSets} |\n\n` +
  `## Beslissingen\n\n- Bestaande stabiele IDs zijn behouden; nieuwe IDs zijn uitsluitend toegevoegd.\n- Elke les heeft één theoriecheck, acht oefenreeksen, mastery test A en hertoets B.\n- De acht oefenreeksen zijn gelijk verdeeld over één of twee toegelaten foute antwoorden.\n- Frans bevat expliciete thema-lessen voor FR-01 tot en met FR-08.\n- Leerjaar-2-wiskunde bevat minimaal tien reeksen per les en expliciete tafels/deeltafels 2, 5 en 10.\n- V1-items zijn alleen als curated reeks opgenomen bij ten minste tien lesgebonden matches; overige items blijven zichtbaar als extra of gemapte kandidaat.\n`;

fs.writeFileSync(catalogPath, `${JSON.stringify(catalog, null, 2)}\n`, "utf8");
fs.writeFileSync(grade1CrosswalkPath, `${JSON.stringify(grade1Crosswalk, null, 2)}\n`, "utf8");
fs.writeFileSync(bankPath, `${JSON.stringify(bank, null, 2)}\n`, "utf8");
fs.writeFileSync(mappingPath, `${JSON.stringify(mappingReport, null, 2)}\n`, "utf8");
fs.writeFileSync(coverageJsonPath, `${JSON.stringify({ schemaVersion: 1, generatedAt: new Date().toISOString(), rows: coverage }, null, 2)}\n`, "utf8");
fs.writeFileSync(coverageCsvPath, coverageCsv, "utf8");
fs.writeFileSync(deltaJsonPath, `${JSON.stringify(delta, null, 2)}\n`, "utf8");
fs.writeFileSync(deltaMdPath, deltaMd, "utf8");

console.log(JSON.stringify({ result: "APPLIED", before, after, mapping: mappingReport.counts, coverageRows: coverage.length, grade2EditorialQuestions: questions.length, missingOriginalStableIds: missingOriginalStableIds.length }, null, 2));
