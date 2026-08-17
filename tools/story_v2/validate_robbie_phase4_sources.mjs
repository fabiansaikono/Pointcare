import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const phase4 = path.join(root, 'story_import', 'generated', 'robbie', 'phase4');
const chapterDir = path.join(phase4, 'chapters');
const allowlistPath = path.join(phase4, 'm3_monosyllable_allowlist.txt');
const failures = [];

if (!fs.existsSync(chapterDir)) failures.push('Hoofdstukmap ontbreekt');
if (!fs.existsSync(allowlistPath)) failures.push('Eenlettergrepige woordenlijst ontbreekt');

const allowlist = new Set(
  fs.existsSync(allowlistPath)
    ? fs.readFileSync(allowlistPath, 'utf8').split(/\r?\n/).map((word) => word.trim()).filter(Boolean)
    : [],
);
const forbidden = new Set(['robbie', 'robot', 'computer', 'werkplaats', 'metaal', 'kabel', 'antenne', 'paardenstaart']);
const chapterFiles = fs.existsSync(chapterDir)
  ? fs.readdirSync(chapterDir).filter((name) => /^ch\d{2}\.json$/.test(name)).sort()
  : [];

const chapters = chapterFiles.map((name) => JSON.parse(fs.readFileSync(path.join(chapterDir, name), 'utf8')));
const unknown = new Map();
const pages = [];
for (const chapter of chapters) {
  if (chapter.pages.length !== chapter.endPage - chapter.startPage + 1) {
    failures.push(`Hoofdstuk ${chapter.chapterNumber}: paginabereik en paginalijst verschillen`);
  }
  chapter.pages.forEach((page, index) => {
    pages.push(page);
    const expectedPage = chapter.startPage + index;
    if (page.pageNumber !== expectedPage) failures.push(`Hoofdstuk ${chapter.chapterNumber}: verwacht pagina ${expectedPage}`);
    for (const language of ['nl-BE', 'fr-BE', 'en-US']) {
      if (typeof page[language] !== 'string' || !page[language].trim()) failures.push(`Pagina ${page.pageNumber}: lege ${language}-tekst`);
    }
    const dutch = page['nl-BE'];
    const words = dutch.match(/\p{L}+/gu) ?? [];
    const lines = dutch.split('\n').filter((line) => line.trim());
    if (words.length < 18 || words.length > 24) failures.push(`Pagina ${page.pageNumber}: ${words.length} woorden, verwacht 18–24`);
    if (dutch !== dutch.toLocaleLowerCase('nl-BE')) failures.push(`Pagina ${page.pageNumber}: hoofdletter in nl-BE`);
    if (lines.length < 3 || lines.length > 5) failures.push(`Pagina ${page.pageNumber}: ${lines.length} regels, verwacht 3–5`);
    if (lines.some((line) => !/[.!?]$/.test(line.trim()))) failures.push(`Pagina ${page.pageNumber}: regel zonder eenvoudig eindteken`);
    for (const rawWord of words) {
      const word = rawWord.toLocaleLowerCase('nl-BE');
      if (forbidden.has(word)) failures.push(`Pagina ${page.pageNumber}: verboden M3-woord '${word}'`);
      if (!allowlist.has(word)) {
        const hits = unknown.get(word) ?? [];
        hits.push(page.pageNumber);
        unknown.set(word, hits);
      }
    }
    if (!Array.isArray(page.characters) || page.characters.length < 1 || page.characters.length > 3) {
      failures.push(`Pagina ${page.pageNumber}: verwacht 1–3 zichtbare personages`);
    }
    if (!page.visual || !page.location || !page.mood || !Array.isArray(page.continuityTags)) {
      failures.push(`Pagina ${page.pageNumber}: onvolledige beeld- of continuïteitsdata`);
    }
    if (/\b(Fien|Mats|Faye|Max)\b/u.test(page['fr-BE'])) failures.push(`Pagina ${page.pageNumber}: verkeerde naam in fr-BE`);
    if (/\b(Fien|Mats|Fanny|Mathis)\b/u.test(page['en-US'])) failures.push(`Pagina ${page.pageNumber}: verkeerde naam in en-US`);
  });
}

for (let index = 1; index < pages.length; index += 1) {
  if (pages[index].pageNumber !== pages[index - 1].pageNumber + 1) failures.push(`Niet-aansluitende pagina’s ${pages[index - 1].pageNumber}/${pages[index].pageNumber}`);
}

const totalWords = pages.reduce((sum, page) => sum + (page['nl-BE'].match(/\p{L}+/gu) ?? []).length, 0);
const report = {
  passed: failures.length === 0 && unknown.size === 0,
  chaptersPresent: chapters.map((chapter) => chapter.chapterNumber),
  pagesPresent: pages.length,
  firstPage: pages.at(0)?.pageNumber ?? null,
  lastPage: pages.at(-1)?.pageNumber ?? null,
  dutchWords: totalWords,
  unknownAllowlistWords: Object.fromEntries([...unknown.entries()].sort(([a], [b]) => a.localeCompare(b, 'nl-BE'))),
  failures,
};

console.log(JSON.stringify(report, null, 2));
if (!report.passed) process.exitCode = 1;
