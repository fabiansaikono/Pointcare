import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const storyFile = path.join(projectRoot, "app", "src", "main", "assets", "poincare", "content", "stories", "poincare-stories-v1.json");
const bookFile = path.join(projectRoot, "app", "src", "main", "assets", "poincare", "content", "stories", "poincare-story-books-v1.json");
const publicStoryFile = path.join(projectRoot, "curriculum-library", "public", "data", "stories", "poincare-stories-v1.json");
const assetRoot = path.join(projectRoot, "app", "src", "main", "assets");
const locales = ["nl-BE", "fr-BE", "en-US"];
const allowedByGrade = {
  1: new Set(["Start", "M3", "E3"]),
  2: new Set(["M4", "E4"]),
  3: new Set(["M5", "E5"]),
  4: new Set(["M6", "E6"]),
};
const maximumWordsPerPage = { Start: 8, M3: 10, E3: 15, M4: 14, E4: 20, M5: 200, E5: 25 };
const maximumRobbieWordsPerPage = { M3: 35, E3: 45, M4: 55, E4: 70, M5: 95, E5: 130, M6: 140, E6: 180 };

function fail(message) {
  throw new Error(message);
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function words(value) {
  return String(value || "").trim().split(/\s+/u).filter(Boolean);
}

function assertLocalized(values, label) {
  const keys = Object.keys(values || {}).sort();
  if (JSON.stringify(keys) !== JSON.stringify([...locales].sort())) fail(`${label} has locales ${keys.join(", ")}`);
  for (const locale of locales) if (!String(values[locale] || "").trim()) fail(`${label} is empty for ${locale}`);
}

const catalogue = readJson(storyFile);
const publicCatalogue = readJson(publicStoryFile);
const books = readJson(bookFile);
if (JSON.stringify(catalogue) !== JSON.stringify(publicCatalogue)) fail("App and curriculum-library story catalogues differ");
if (catalogue.schemaVersion !== 1 || catalogue.stories.length !== 64) fail("Expected schema version 1 and 64 stories");

const storyIds = new Set();
const pageIds = new Set();
const levelCounts = new Map();
const forbiddenAgePhrases = /\b(?:jaar oud|jarige|jongste|oudste|years? old|year-old|youngest|oldest|âgé(?:e)?|le plus jeune|la plus jeune|l’aîné|l’aînée)\b/iu;

for (const story of catalogue.stories) {
  if (storyIds.has(story.id)) fail(`Duplicate story ID ${story.id}`);
  storyIds.add(story.id);
  if (!allowedByGrade[story.grade]?.has(story.aviLevel)) fail(`${story.id} has invalid ${story.aviLevel} for Grade ${story.grade}`);
  levelCounts.set(`${story.grade}:${story.aviLevel}`, (levelCounts.get(`${story.grade}:${story.aviLevel}`) || 0) + 1);
  assertLocalized(Object.fromEntries(locales.map((locale) => [locale, story.translations?.[locale]?.title])), `${story.id} title`);
  assertLocalized(Object.fromEntries(locales.map((locale) => [locale, story.translations?.[locale]?.description])), `${story.id} description`);
  if (!fs.existsSync(path.join(assetRoot, story.coverAsset))) fail(`${story.id} cover is missing: ${story.coverAsset}`);
  if (!story.pages?.length) fail(`${story.id} has no pages`);

  const localPageIds = new Set();
  for (const page of story.pages) {
    if (localPageIds.has(page.id) || pageIds.has(`${story.id}:${page.id}`)) fail(`Duplicate page ID ${story.id}/${page.id}`);
    localPageIds.add(page.id);
    pageIds.add(`${story.id}:${page.id}`);
    assertLocalized(page.visibleText, `${story.id}/${page.id} visible text`);
    for (const locale of locales) {
      const text = page.visibleText[locale];
      if (story.seriesId !== "robbie_wereld_van_morgen" && forbiddenAgePhrases.test(text)) fail(`${story.id}/${page.id} contains a forbidden visible age phrase`);
      const count = words(text).length;
      const maximum = story.seriesId === "robbie_wereld_van_morgen"
        ? maximumRobbieWordsPerPage[story.aviLevel]
        : maximumWordsPerPage[story.aviLevel];
      if (count > maximum) {
        fail(`${story.id}/${page.id} has ${count} ${locale} words at AVI ${story.aviLevel}`);
      }
      if (page.speechText && Object.keys(page.speechText).length > 0 && !String(page.speechText[locale] || "").trim()) {
        fail(`${story.id}/${page.id} has incomplete speech text for ${locale}`);
      }
    }
    if (page.illustrationAsset && !fs.existsSync(path.join(assetRoot, page.illustrationAsset))) {
      fail(`${story.id}/${page.id} image is missing: ${page.illustrationAsset}`);
    }
  }

  for (const question of story.questions || []) {
    assertLocalized(question.prompt, `${story.id}/${question.id} prompt`);
    assertLocalized(question.explanation, `${story.id}/${question.id} explanation`);
    for (const locale of locales) {
      if (question.options?.[locale]?.length !== 3) fail(`${story.id}/${question.id} needs three ${locale} options`);
      if (question.correctIndex < 0 || question.correctIndex >= question.options[locale].length) fail(`${story.id}/${question.id} has an invalid answer index`);
    }
  }
}

const expectedDistribution = new Map([
  ["1:Start", 9], ["1:M3", 10], ["1:E3", 14],
  ["2:M4", 2], ["2:E4", 12],
  ["3:M5", 2], ["3:E5", 13],
  ["4:M6", 1], ["4:E6", 1],
]);
for (const [key, expected] of expectedDistribution) {
  if (levelCounts.get(key) !== expected) fail(`Expected ${expected} stories at ${key}, found ${levelCounts.get(key) || 0}`);
}

const storyById = new Map(catalogue.stories.map((story) => [story.id, story]));
const trilogyBooks = [
  "nova_space_01_ready_for_launch_g1",
  "nova_space_02_journey_through_space_g2",
  "nova_space_03_goodbye_nina_g3",
].map((id) => books.books.find((book) => book.id === id) || fail(`Missing trilogy book ${id}`));

for (const [bookIndex, book] of trilogyBooks.entries()) {
  const grade = bookIndex + 1;
  const level = ["E3", "E4", "E5"][bookIndex];
  const range = [[10, 15], [15, 20], [20, 30]][bookIndex];
  const expectedUniqueImages = [4, 4, 2][bookIndex];
  for (const storyId of book.storyIds) {
    const story = storyById.get(storyId) || fail(`Unknown trilogy story ${storyId}`);
    if (story.grade !== grade || story.aviLevel !== level) fail(`${storyId} has the wrong trilogy grade or AVI level`);
    if (story.pages.length < range[0] || story.pages.length > range[1]) fail(`${storyId} has ${story.pages.length} pages`);
    const images = story.pages.map((page) => page.illustrationAsset).filter(Boolean);
    if (new Set(images).size !== expectedUniqueImages) fail(`${storyId} does not preserve its ${expectedUniqueImages} source images`);
    if (grade <= 2 && images.length !== story.pages.length) fail(`${storyId} must keep an illustration on every reflowed page`);
  }
}

console.log(JSON.stringify({
  status: "OK",
  stories: catalogue.stories.length,
  pages: catalogue.stories.reduce((sum, story) => sum + story.pages.length, 0),
  distribution: Object.fromEntries([...levelCounts.entries()].sort()),
  novaPages: Object.fromEntries(trilogyBooks.map((book) => [book.id, book.storyIds.reduce((sum, id) => sum + storyById.get(id).pages.length, 0)])),
}, null, 2));
