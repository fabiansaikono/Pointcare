import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const phaseRoot = path.join(root, "story_import", "generated", "robbie", "phase1");
const requiredFiles = [
  "00_FASE1_INDEX.md",
  "01_REEKSBĲBEL.md",
  "02_PERSONAGEBIJBEL.md",
  "03_BOEKEN_EN_HOOFDSTUKKEN.md",
  "04_ONTWIKKELINGSMATRIX.md",
  "05_VIDEOGAME_STIJLBIJBEL.md",
  "06_RISICOS_EN_GOEDKEURING.md",
];
const expectedBooks = [
  ["L1_M3", "M3"],
  ["L1_E3", "E3"],
  ["L2_M4", "M4"],
  ["L2_E4", "E4"],
  ["L3_M5", "M5"],
  ["L3_E5", "E5"],
  ["L4_M6", "M6"],
  ["L4_E6", "E6"],
];

function fail(message) {
  throw new Error(message);
}

for (const name of requiredFiles) {
  if (!fs.existsSync(path.join(phaseRoot, name))) fail(`Missing Phase 1 file: ${name}`);
}

const plans = fs.readFileSync(path.join(phaseRoot, "03_BOEKEN_EN_HOOFDSTUKKEN.md"), "utf8");
const bookMatches = [...plans.matchAll(/^# Boek \d+ — (.+)$/gmu)];
if (bookMatches.length !== 8) fail(`Expected 8 book plans, found ${bookMatches.length}`);

const books = [];
for (let index = 0; index < bookMatches.length; index += 1) {
  const start = bookMatches[index].index;
  const end = bookMatches[index + 1]?.index ?? plans.length;
  const section = plans.slice(start, end);
  const id = section.match(/^- Boek-ID: `([^`]+)`$/mu)?.[1];
  const avi = section.match(/^- AVI-niveau: ([A-Z0-9]+), voorlopig$/mu)?.[1];
  const chapters = [...section.matchAll(/^### Hoofdstuk (\d+) — (.+)\r?\n\r?\nPagina’s (\d+)–(\d+)\./gmu)].map((match) => ({
    number: Number(match[1]),
    title: match[2].trim(),
    start: Number(match[3]),
    end: Number(match[4]),
  }));
  if (chapters.length !== 12) fail(`${id || `Book ${index + 1}`} has ${chapters.length} chapters`);
  let cursor = 1;
  let twelve = 0;
  let thirteen = 0;
  for (const [chapterIndex, chapter] of chapters.entries()) {
    if (chapter.number !== chapterIndex + 1) fail(`${id} has invalid chapter number ${chapter.number}`);
    if (chapter.start !== cursor) fail(`${id} chapter ${chapter.number} starts at ${chapter.start}, expected ${cursor}`);
    const count = chapter.end - chapter.start + 1;
    if (count === 12) twelve += 1;
    else if (count === 13) thirteen += 1;
    else fail(`${id} chapter ${chapter.number} has ${count} pages`);
    cursor = chapter.end + 1;
  }
  if (cursor !== 151 || twelve !== 6 || thirteen !== 6) {
    fail(`${id} does not have 150 pages split into six 12-page and six 13-page chapters`);
  }
  books.push({ id, avi, title: bookMatches[index][1].trim(), chapters: chapters.length, pages: 150 });
}

for (const [index, [expectedId, expectedAvi]] of expectedBooks.entries()) {
  const book = books[index];
  if (book.id !== expectedId || book.avi !== expectedAvi) {
    fail(`Book ${index + 1} expected ${expectedId}/${expectedAvi}, found ${book.id}/${book.avi}`);
  }
}

const style = fs.readFileSync(path.join(phaseRoot, "05_VIDEOGAME_STIJLBIJBEL.md"), "utf8");
if (!style.includes("| **Totaal** | **550**")) fail("Style bible does not lock 550 interior illustrations");
if (!style.includes("#00FF00") || !style.includes("alpha")) fail("Style bible lacks chroma-key/alpha workflow");

const risk = fs.readFileSync(path.join(phaseRoot, "06_RISICOS_EN_GOEDKEURING.md"), "utf8");
const approvalQuestions = [...risk.matchAll(/^\d+\. .+\?$/gmu)].length;
if (approvalQuestions !== 5) fail(`Expected 5 approval questions, found ${approvalQuestions}`);

const accidentalImages = fs.readdirSync(phaseRoot).filter((name) => /\.(?:png|webp|jpe?g)$/iu.test(name));
if (accidentalImages.length > 0) fail(`Phase 1 contains premature images: ${accidentalImages.join(", ")}`);

console.log(JSON.stringify({
  status: "OK",
  phase: 1,
  books: books.length,
  chapters: books.reduce((sum, book) => sum + book.chapters, 0),
  plannedPages: books.reduce((sum, book) => sum + book.pages, 0),
  plannedInteriorIllustrations: 550,
  plannedCovers: 8,
  approvalQuestions,
  prematureImages: accidentalImages.length,
  bookPlan: books,
}, null, 2));
