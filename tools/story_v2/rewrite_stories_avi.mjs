import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(scriptRoot, "..", "..");
const storyAsset = path.join(
  projectRoot,
  "app",
  "src",
  "main",
  "assets",
  "poincare",
  "content",
  "stories",
  "poincare-stories-v1.json",
);
const publicStoryAsset = path.join(
  projectRoot,
  "curriculum-library",
  "public",
  "data",
  "stories",
  "poincare-stories-v1.json",
);
const generatedGradeOneRoot = path.join(projectRoot, "story_import", "generated", "stories", "g1");
const locales = ["nl-BE", "fr-BE", "en-US"];

const aviLevels = new Map([
  ["g1-sam-en-de-maan", "Start"],
  ["g1-pip-zoekt-een-vis", "Start"],
  ["g1-de-raket-van-noor", "M3"],
  ["g1-lot-en-pim", "Start"],
  ["g1-mies-en-de-doos", "Start"],
  ["g1-mik-en-de-bal", "Start"],
  ["g1-sem-en-de-pet", "Start"],
  ["g1-bas-en-de-tas", "Start"],
  ["g1-kim-en-de-kip", "Start"],
  ["g1-mus-in-de-hut", "Start"],
  ["g1-tom-en-de-vos", "M3"],
  ["g1-kaat-en-de-maan", "M3"],
  ["g1-beer-bij-de-beek", "M3"],
  ["g1-noor-en-de-boot", "M3"],
  ["g1-suus-en-de-muur", "M3"],
  ["g1-fien-en-de-fiets", "M3"],
  ["g1-koen-en-de-koek", "M3"],
  ["g1-lies-in-de-tuin", "M3"],
  ["g1-pauw-op-de-muur", "E3"],
  ["g1-schat-in-het-zand", "E3"],
  ["g1-ring-voor-koning-kik", "E3"],
  ["g1-bank-bij-de-plank", "E3"],
  ["g1-meeuw-aan-zee", "E3"],
  ["g2-de-robot-die-niet-kon-slapen", "M4"],
  ["g2-het-geheim-onder-de-school", "E4"],
  ["g3-de-kaart-van-planeet-nox", "M5"],
]);

const editorialRewrites = [
  {
    storyId: "nova_g1_01_de_raket",
    from: {
      "nl-BE": "Hij wijst naar Nova en zegt: ‘We doen elke taak rustig en goed.’",
      "fr-BE": "Il montre Nova et dit : « Nous faisons chaque tâche calmement et correctement. »",
      "en-US": "He points to Nova and says, “We will do every job calmly and well.”",
    },
    to: {
      "nl-BE": "Hij wijst naar Nova. ‘We doen elke taak rustig en goed,’ zegt hij.",
      "fr-BE": "Il montre Nova. « Nous faisons chaque tâche calmement et correctement », dit-il.",
      "en-US": "He points to Nova. “We will do every job calmly and well,” he says.",
    },
  },
  {
    storyId: "nova_g1_02_een_team",
    from: {
      "nl-BE": "Eerst test hij het stuur en de ronde knoppen van zijn vaste plaats.",
      "fr-BE": "Il teste d’abord les commandes et les boutons ronds de son poste fixe.",
      "en-US": "First he tests the controls and round buttons at his fixed station.",
    },
    to: {
      "nl-BE": "Eerst test hij het stuur. Daarna test hij de ronde knoppen.",
      "fr-BE": "Il teste d’abord les commandes. Ensuite, il teste les boutons ronds.",
      "en-US": "First he tests the controls. Then he tests the round buttons.",
    },
  },
  {
    storyId: "nova_g2_09_het_baken",
    from: {
      "nl-BE": "Ting, toeng, ting klinkt over Luma, en alle drie de zuilen sturen licht tot hoog in de hemel.",
      "fr-BE": "Ting, tong, ting résonne sur Luma, et les trois tours envoient leur lumière très haut dans le ciel.",
      "en-US": "Ting, tong, ting sounds across Luma, and all three towers send light high into the sky.",
    },
    to: {
      "nl-BE": "Ting, toeng, ting klinkt over Luma. Alle drie de zuilen sturen licht hoog in de hemel.",
      "fr-BE": "Ting, tong, ting résonne sur Luma. Les trois tours envoient leur lumière haut dans le ciel.",
      "en-US": "Ting, tong, ting sounds across Luma. All three towers send light high into the sky.",
    },
  },
  {
    storyId: "nova_g3_02_de_lichtvallei",
    from: {
      "nl-BE": "Matt noemt haar aandacht voor veiligheid, Harry haar kennis van de plek en Gina haar moed om eerst te kijken.",
      "fr-BE": "Matt parle de son attention à la sécurité, Harry de sa connaissance du lieu et Gina de son courage d’observer d’abord.",
      "en-US": "Matt names her care for safety, Harry her knowledge of the place, and Gina her courage to watch first.",
    },
    to: {
      "nl-BE": "Matt noemt haar zorg voor veiligheid. Harry noemt haar kennis van de plek. Gina noemt haar moed om eerst te kijken.",
      "fr-BE": "Matt parle de son attention à la sécurité. Harry parle de sa connaissance du lieu. Gina parle de son courage d’observer d’abord.",
      "en-US": "Matt names her care for safety. Harry names her knowledge of the place. Gina names her courage to watch first.",
    },
  },
  {
    storyId: "nova_g3_03_wat_we_geven",
    from: {
      "nl-BE": "Ze duwt het niet zomaar naar de crew, maar legt het bewust op Juliens prent tussen beide werelden.",
      "fr-BE": "Elle ne la pousse pas simplement vers l’équipage : elle la pose volontairement sur l’image de Julien entre les deux mondes.",
      "en-US": "She does not merely push it toward the crew, but deliberately places it on Julien’s picture between the two worlds.",
    },
    to: {
      "nl-BE": "Ze duwt het niet zomaar naar de crew. Ze legt het bewust op Juliens prent tussen beide werelden.",
      "fr-BE": "Elle ne la pousse pas simplement vers l’équipage. Elle la pose volontairement sur l’image de Julien entre les deux mondes.",
      "en-US": "She does not merely push it toward the crew. She deliberately places it on Julien’s picture between the two worlds.",
    },
  },
  {
    storyId: "nova_g3_04_drie_tonen_vaarwel",
    from: {
      "nl-BE": "Nina laat beelden over haar markeringen lopen: hun eerste voorzichtige ontmoeting, de geopende doorgang en het baken dat weer straalt.",
      "fr-BE": "Nina fait passer des images dans ses marques : leur première rencontre prudente, le passage ouvert et la balise qui brille de nouveau.",
      "en-US": "Nina sends images through her markings: their first careful meeting, the opened passage, and the beacon shining again.",
    },
    to: {
      "nl-BE": "Nina laat drie beelden over haar markeringen lopen. Het zijn hun eerste ontmoeting, de geopende doorgang en het stralende baken.",
      "fr-BE": "Nina fait passer trois images dans ses marques. Elles montrent leur première rencontre, le passage ouvert et la balise brillante.",
      "en-US": "Nina sends three images through her markings. They show their first meeting, the opened passage, and the shining beacon.",
    },
  },
  {
    storyId: "nova_g3_05_luma_wordt_kleiner",
    from: {
      "nl-BE": "Sally geeft iedereen tijd om nog één keer te kijken, daarna draait hij de raket naar de thuisroute.",
      "fr-BE": "Sally laisse à chacun le temps de regarder une dernière fois, puis il tourne la fusée vers la route du retour.",
      "en-US": "Sally gives everyone time for one last look, then turns the rocket toward the route home.",
    },
    to: {
      "nl-BE": "Sally geeft iedereen tijd om nog één keer te kijken. Daarna draait hij Nova naar de thuisroute.",
      "fr-BE": "Sally laisse à chacun le temps de regarder une dernière fois. Ensuite, il tourne Nova vers la route du retour.",
      "en-US": "Sally gives everyone time for one last look. Then he turns Nova toward the route home.",
    },
  },
  {
    storyId: "nova_g3_06_de_stille_weg",
    from: {
      "nl-BE": "Julien opent eindelijk zijn tekenboek en vult het ontbrekende beeld aan: Nina bij de lichtplanten, gezien door Nova’s raam.",
      "fr-BE": "Julien ouvre enfin son carnet et complète l’image manquante : Nina près des plantes lumineuses, vue par la fenêtre de Nova.",
      "en-US": "Julien finally opens his sketchbook and completes the missing picture: Nina beside the glow plants, seen through Nova’s window.",
    },
    to: {
      "nl-BE": "Julien opent eindelijk zijn tekenboek. Hij tekent Nina bij de lichtplanten, gezien door Nova’s raam.",
      "fr-BE": "Julien ouvre enfin son carnet. Il dessine Nina près des plantes lumineuses, vue par la fenêtre de Nova.",
      "en-US": "Julien finally opens his sketchbook. He draws Nina beside the glow plants, seen through Nova’s window.",
    },
  },
  {
    storyId: "nova_g3_07_de_zonnestorm",
    from: {
      "nl-BE": "Op zichzelf kan Nova die met haar schild opvangen, maar de storm duwt ook een oud lint van kleine brokstukken over hun route.",
      "fr-BE": "Nova peut les arrêter avec son bouclier, mais la tempête pousse aussi un ancien ruban de petits débris sur leur route.",
      "en-US": "Nova can stop them with her shield, but the storm also pushes an old ribbon of small debris across their route.",
    },
    to: {
      "nl-BE": "Nova kan de eerste deeltjes met haar schild opvangen. Maar de storm duwt ook een oud lint van brokstukken over hun route.",
      "fr-BE": "Nova peut arrêter les premières particules avec son bouclier. Mais la tempête pousse aussi un ancien ruban de débris sur leur route.",
      "en-US": "Nova can stop the first particles with her shield. But the storm also pushes an old ribbon of debris across their route.",
    },
  },
  {
    storyId: "nova_g3_07_de_zonnestorm",
    from: {
      "nl-BE": "Het voelt anders dan de eerste stenenstroom: Nova volgt geen slingerpad, maar wacht en beweegt op precies gekozen momenten.",
      "fr-BE": "Cela ne ressemble pas au premier courant de pierres : Nova ne suit pas un chemin sinueux, mais attend et avance à des moments précis.",
      "en-US": "This feels different from the first rock stream: Nova does not follow a winding path, but waits and moves at carefully chosen moments.",
    },
    to: {
      "nl-BE": "Dit voelt anders dan de eerste stenenstroom. Nova volgt geen slingerpad. Ze wacht en beweegt op precies gekozen momenten.",
      "fr-BE": "Cela ne ressemble pas au premier courant de pierres. Nova ne suit pas un chemin sinueux. Elle attend et avance aux bons moments.",
      "en-US": "This feels different from the first rock stream. Nova does not follow a winding path. She waits and moves at carefully chosen moments.",
    },
  },
  {
    storyId: "nova_g3_10_door_de_atmosfeer",
    from: {
      "nl-BE": "Julien volgt de dikke getekende boog, terwijl Gina door kleine openingen in de gloed naar de wolken zoekt.",
      "fr-BE": "Julien suit l’arc épais de sa carte pendant que Gina cherche les nuages à travers de petites ouvertures dans la lueur.",
      "en-US": "Julien follows the thick drawn arc while Gina looks for clouds through small openings in the glow.",
    },
    to: {
      "nl-BE": "Julien volgt de dikke getekende boog. Gina zoekt door kleine openingen in de gloed naar de wolken.",
      "fr-BE": "Julien suit l’arc épais de sa carte. Gina cherche les nuages à travers de petites ouvertures dans la lueur.",
      "en-US": "Julien follows the thick drawn arc. Gina looks for clouds through small openings in the glow.",
    },
  },
  {
    storyId: "nova_g3_12_verbonden",
    from: {
      "nl-BE": "In het verslag noemt hij haar een intelligente bewoner van Luma, helper van het baken en vriend van de crew.",
      "fr-BE": "Dans le rapport, il la nomme habitante intelligente de Luma, réparatrice de la balise et amie de l’équipage.",
      "en-US": "In the report, he calls her an intelligent resident of Luma, a helper of the beacon, and a friend of the crew.",
    },
    to: {
      "nl-BE": "In het verslag noemt hij haar een intelligente bewoner van Luma. Hij noemt haar ook helper van het baken en vriend van de crew.",
      "fr-BE": "Dans le rapport, il la nomme habitante intelligente de Luma. Il la décrit aussi comme réparatrice de la balise et amie de l’équipage.",
      "en-US": "In the report, he calls her an intelligent resident of Luma. He also calls her a beacon helper and a friend of the crew.",
    },
  },
  {
    storyId: "nova_g3_12_verbonden",
    from: {
      "nl-BE": "Gina weet het al voor de bevestiging: Nina’s baken werkt nog, en haar antwoord heeft de aarde bereikt.",
      "fr-BE": "Gina le sait avant même la confirmation : la balise de Nina fonctionne encore et sa réponse a atteint la Terre.",
      "en-US": "Gina knows before the confirmation: Nina’s beacon still works, and her answer has reached Earth.",
    },
    to: {
      "nl-BE": "Gina weet het al voor de bevestiging. Nina’s baken werkt nog en haar antwoord heeft de aarde bereikt.",
      "fr-BE": "Gina le sait avant même la confirmation. La balise de Nina fonctionne encore et sa réponse a atteint la Terre.",
      "en-US": "Gina knows before the confirmation. Nina’s beacon still works, and her answer has reached Earth.",
    },
  },
];

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function splitSentences(value) {
  const text = String(value || "").trim();
  if (!text) return [];
  const matches = text.match(/[^.!?]+(?:[.!?]+(?:\s*["'’”»]+)?)?|[.!?]+/gu) || [text];
  return matches.map((part) => part.trim()).filter(Boolean);
}

function wordCount(value) {
  return String(value || "").trim().split(/\s+/u).filter(Boolean).length;
}

function partitionContiguously(parts, targetCount) {
  if (parts.length === targetCount) return [...parts];
  if (targetCount <= 0 || parts.length < targetCount) {
    throw new Error(`Cannot align ${parts.length} sentence(s) to ${targetCount} page segment(s)`);
  }
  const result = [];
  let cursor = 0;
  for (let index = 0; index < targetCount; index += 1) {
    const remainingParts = parts.length - cursor;
    const remainingGroups = targetCount - index;
    const take = Math.ceil(remainingParts / remainingGroups);
    result.push(parts.slice(cursor, cursor + take).join(" "));
    cursor += take;
  }
  return result;
}

function alignedSegments(values, targetCount) {
  const result = {};
  for (const locale of locales) {
    const sentences = splitSentences(values?.[locale]);
    result[locale] = partitionContiguously(sentences, targetCount);
  }
  return result;
}

function mergeShortestAdjacentPair(records) {
  let bestIndex = -1;
  let bestWeight = Number.POSITIVE_INFINITY;
  for (let index = 0; index < records.length - 1; index += 1) {
    const left = records[index];
    const right = records[index + 1];
    if (left.sourcePageIndex !== right.sourcePageIndex) continue;
    const weight = wordCount(left.visibleText["nl-BE"]) + wordCount(right.visibleText["nl-BE"]);
    if (weight < bestWeight) {
      bestWeight = weight;
      bestIndex = index;
    }
  }
  if (bestIndex < 0) throw new Error("No safe adjacent page segments were available to merge");
  const left = records[bestIndex];
  const right = records[bestIndex + 1];
  for (const locale of locales) {
    left.visibleText[locale] = `${left.visibleText[locale]} ${right.visibleText[locale]}`.trim();
    left.speechText[locale] = `${left.speechText[locale]} ${right.speechText[locale]}`.trim();
  }
  records.splice(bestIndex + 1, 1);
}

function collapseReflowedNovaPages(pages) {
  const groups = [];
  const bySourceId = new Map();
  for (const page of pages) {
    const sourceId = page.id.replace(/-s\d+$/u, "");
    let group = bySourceId.get(sourceId);
    if (!group) {
      group = [];
      bySourceId.set(sourceId, group);
      groups.push(group);
    }
    group.push(page);
  }
  return groups.map((group) => {
    const first = group[0];
    const page = { id: first.id.replace(/-s\d+$/u, "") };
    const illustrationAsset = group.map((entry) => entry.illustrationAsset).find(Boolean);
    if (illustrationAsset) page.illustrationAsset = illustrationAsset;
    if (first.chapter != null) page.chapter = first.chapter;
    const chapterTitles = group.map((entry) => entry.chapterTitles).find((value) => Object.keys(value || {}).length > 0);
    if (chapterTitles) page.chapterTitles = chapterTitles;
    page.visibleText = Object.fromEntries(locales.map((locale) => [
      locale,
      group.map((entry) => entry.visibleText?.[locale]).filter(Boolean).join(" "),
    ]));
    page.speechText = Object.fromEntries(locales.map((locale) => [
      locale,
      group.map((entry) => entry.speechText?.[locale] || entry.visibleText?.[locale]).filter(Boolean).join(" "),
    ]));
    return page;
  });
}

function applyEditorialRewrites(storyId, sourcePages) {
  const rewrites = editorialRewrites.filter((rewrite) => rewrite.storyId === storyId);
  for (const rewrite of rewrites) {
    let matched = false;
    for (const page of sourcePages) {
      for (const field of ["visibleText", "speechText"]) {
        if (!page[field]) continue;
        for (const locale of locales) {
          if (page[field][locale]?.includes(rewrite.from[locale])) {
            page[field][locale] = page[field][locale].replace(rewrite.from[locale], rewrite.to[locale]);
            matched = true;
          }
        }
      }
    }
    if (!matched && !sourcePages.some((page) => locales.some((locale) => page.visibleText?.[locale]?.includes(rewrite.to[locale])))) {
      throw new Error(`Editorial rewrite source was not found for ${storyId}`);
    }
  }
}

function reflowNovaStory(story) {
  if (!story.id.startsWith("nova_")) return story;
  const sourcePages = story.pages.length === 4 ? story.pages : collapseReflowedNovaPages(story.pages);
  if (sourcePages.length !== 4) {
    throw new Error(`${story.id} could not be reconstructed into four illustrated source scenes`);
  }
  applyEditorialRewrites(story.id, sourcePages);

  const records = [];
  sourcePages.forEach((sourcePage, sourcePageIndex) => {
    const nlSegments = splitSentences(sourcePage.visibleText?.["nl-BE"]);
    const segmentCount = nlSegments.length;
    const visible = alignedSegments(sourcePage.visibleText, segmentCount);
    const spokenSource = Object.keys(sourcePage.speechText || {}).length > 0
      ? sourcePage.speechText
      : sourcePage.visibleText;
    const spoken = alignedSegments(spokenSource, segmentCount);

    for (let segmentIndex = 0; segmentIndex < segmentCount; segmentIndex += 1) {
      records.push({
        sourcePage,
        sourcePageIndex,
        segmentIndex,
        visibleText: Object.fromEntries(locales.map((locale) => [locale, visible[locale][segmentIndex]])),
        speechText: Object.fromEntries(locales.map((locale) => [locale, spoken[locale][segmentIndex]])),
      });
    }
  });

  if (story.grade === 2) {
    while (records.length > 20) mergeShortestAdjacentPair(records);
  }

  const expectedRange = story.grade === 1 ? [10, 15] : story.grade === 2 ? [15, 20] : [20, 30];
  if (records.length < expectedRange[0] || records.length > expectedRange[1]) {
    throw new Error(`${story.id} has ${records.length} reflowed pages; expected ${expectedRange[0]}-${expectedRange[1]}`);
  }

  const nextSegmentBySource = new Map();
  const pages = records.map((record) => {
    const sequence = nextSegmentBySource.get(record.sourcePageIndex) || 0;
    nextSegmentBySource.set(record.sourcePageIndex, sequence + 1);
    const source = record.sourcePage;
    const page = {
      id: sequence === 0 ? source.id : `${source.id}-s${String(sequence + 1).padStart(2, "0")}`,
    };
    if (story.grade <= 2 && source.illustrationAsset) {
      page.illustrationAsset = source.illustrationAsset;
    } else if (story.grade === 3 && sequence === 0 && source.illustrationAsset) {
      page.illustrationAsset = source.illustrationAsset;
    }
    if (source.chapter != null) page.chapter = source.chapter;
    if (sequence === 0 && Object.keys(source.chapterTitles || {}).length > 0) {
      page.chapterTitles = source.chapterTitles;
    }
    page.visibleText = record.visibleText;
    page.speechText = record.speechText;
    return page;
  });

  return {
    ...story,
    estimatedMinutes: Math.max(story.estimatedMinutes, Math.ceil(pages.length / 1.5)),
    pages,
  };
}

const catalogue = readJson(storyAsset);
for (const story of catalogue.stories) {
  const fixedLevel = story.id.startsWith("nova_g1_")
    ? "E3"
    : story.id.startsWith("nova_g2_")
      ? "E4"
      : story.id.startsWith("nova_g3_")
        ? "E5"
        : aviLevels.get(story.id);
  if (!fixedLevel) throw new Error(`No AVI level assigned to ${story.id}`);
  story.aviLevel = fixedLevel;
}
catalogue.stories = catalogue.stories.map(reflowNovaStory);

writeJson(storyAsset, catalogue);
writeJson(publicStoryAsset, catalogue);

if (fs.existsSync(generatedGradeOneRoot)) {
  const canonicalById = new Map(catalogue.stories.map((story) => [story.id, story]));
  for (const name of fs.readdirSync(generatedGradeOneRoot).filter((entry) => entry.endsWith(".json"))) {
    const target = path.join(generatedGradeOneRoot, name);
    const generatedStory = readJson(target);
    const canonicalStory = canonicalById.get(generatedStory.id);
    if (!canonicalStory) throw new Error(`Generated source ${name} has no canonical story`);
    writeJson(target, canonicalStory);
  }
}

const summary = Object.fromEntries(
  [1, 2, 3].map((grade) => [
    `grade${grade}`,
    catalogue.stories
      .filter((story) => story.grade === grade)
      .reduce((counts, story) => {
        counts[story.aviLevel] = (counts[story.aviLevel] || 0) + 1;
        return counts;
      }, {}),
  ]),
);
console.log(JSON.stringify({ stories: catalogue.stories.length, avi: summary }, null, 2));
