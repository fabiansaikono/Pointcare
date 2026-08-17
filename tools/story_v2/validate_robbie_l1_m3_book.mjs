import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const phase4 = path.join(root, 'story_import', 'generated', 'robbie', 'phase4');
const bookPath = path.join(phase4, 'robbie_l1_m3.json');
const promptPath = path.join(phase4, '02_L1_M3_FULL_ILLUSTRATION_PROMPTS.md');
const allowlistPath = path.join(phase4, 'm3_monosyllable_allowlist.txt');
const reportPath = path.join(phase4, '04_L1_M3_BOOK_QA.json');

const failures = [];
for (const file of [bookPath, promptPath, allowlistPath]) {
  if (!fs.existsSync(file)) failures.push(`Ontbrekend bestand: ${path.basename(file)}`);
}
if (failures.length) {
  console.log(JSON.stringify({ passed: false, failures }, null, 2));
  process.exit(1);
}

const book = JSON.parse(fs.readFileSync(bookPath, 'utf8'));
const promptDoc = fs.readFileSync(promptPath, 'utf8');
const allowlist = new Set(fs.readFileSync(allowlistPath, 'utf8').split(/\r?\n/).map((word) => word.trim()).filter(Boolean));
const pages = book.chapters.flatMap((chapter) => chapter.pages);
const unknown = new Set();
const ids = new Set();
const filenames = new Set();
let totalWords = 0;
let totalSentences = 0;
let totalLetters = 0;

if (book.bookId !== 'L1_M3') failures.push('Verkeerde bookId');
if (book.chapterCount !== 12 || book.chapters.length !== 12) failures.push('Boek moet 12 hoofdstukken hebben');
if (book.pageCount !== 150 || pages.length !== 150) failures.push('Boek moet 150 pagina’s hebben');
if (book.illustrationCount !== 150) failures.push('Boek moet 150 illustraties plannen');
if (book.aviLevel !== 'M3' || book.aviStatus !== 'provisional') failures.push('AVI-status moet M3/provisional zijn');
if (book.coverTextEmbedded !== false) failures.push('Covertekst mag niet in PNG worden ingebakken');

book.chapters.forEach((chapter, chapterIndex) => {
  if (chapter.chapterNumber !== chapterIndex + 1) failures.push(`Fout hoofdstuknummer bij index ${chapterIndex}`);
  const expectedCount = chapter.endPage - chapter.startPage + 1;
  if (chapter.pages.length !== expectedCount || expectedCount < 10 || expectedCount > 15) failures.push(`Foute lengte hoofdstuk ${chapter.chapterNumber}`);
});

pages.forEach((page, index) => {
  const expected = index + 1;
  const padded = String(expected).padStart(3, '0');
  if (page.pageNumber !== expected) failures.push(`Verkeerde paginavolgorde bij ${expected}`);
  if (page.illustrationId !== `L1_M3_P${padded}`) failures.push(`Foute illustrationId op pagina ${expected}`);
  if (page.illustrationFilename !== `robbie_l1_m3_p${padded}.png`) failures.push(`Foute illustratiebestandsnaam op pagina ${expected}`);
  if (ids.has(page.illustrationId)) failures.push(`Dubbele illustrationId ${page.illustrationId}`);
  if (filenames.has(page.illustrationFilename)) failures.push(`Dubbele bestandsnaam ${page.illustrationFilename}`);
  ids.add(page.illustrationId);
  filenames.add(page.illustrationFilename);
  for (const language of ['nl-BE', 'fr-BE', 'en-US']) {
    if (!page.text?.[language]?.trim()) failures.push(`Lege ${language}-tekst op pagina ${expected}`);
  }
  const dutch = page.text['nl-BE'];
  const words = dutch.match(/\p{L}+/gu) ?? [];
  const lines = dutch.split('\n').filter(Boolean);
  totalWords += words.length;
  totalSentences += lines.length;
  totalLetters += words.reduce((sum, word) => sum + [...word].length, 0);
  if (words.length < 18 || words.length > 24) failures.push(`Pagina ${expected}: ${words.length} woorden`);
  if (dutch !== dutch.toLocaleLowerCase('nl-BE')) failures.push(`Hoofdletter in nl-BE op pagina ${expected}`);
  if (lines.some((line) => !/[.!?]$/.test(line.trim()))) failures.push(`Regel zonder eindteken op pagina ${expected}`);
  words.forEach((word) => {
    const normalized = word.toLocaleLowerCase('nl-BE');
    if (!allowlist.has(normalized)) unknown.add(normalized);
  });
  if (!page.illustrationPrompt?.includes(page.illustrationId)) failures.push(`Prompt mist eigen ID op pagina ${expected}`);
  for (const marker of ['STORY MOMENT:', 'CHARACTERS:', 'ACTION:', 'EMOTION:', 'PROPS:', 'COMPOSITION:', 'CONTINUITY:', 'STYLE LOCK:', 'TECHNICAL OUTPUT:', 'NEGATIVE INSTRUCTIONS:']) {
    if (!page.illustrationPrompt?.includes(marker)) failures.push(`Prompt ${page.illustrationId} mist ${marker}`);
  }
  if (!promptDoc.includes(page.illustrationId)) failures.push(`Promptdocument mist ${page.illustrationId}`);
});

if (unknown.size) failures.push(`Niet-gecontroleerde M3-woorden: ${[...unknown].sort((a, b) => a.localeCompare(b, 'nl-BE')).join(', ')}`);
if (totalWords < 2700 || totalWords > 3500) failures.push(`Boekwoorddoel buiten band: ${totalWords}`);

const promptBlocks = [...promptDoc.matchAll(/```text\n([\s\S]*?)\n```/g)].map((match) => match[1]);
if (promptBlocks.length !== 150 || new Set(promptBlocks).size !== 150) failures.push(`Verwacht 150 unieke promptblokken, vond ${promptBlocks.length}/${new Set(promptBlocks).size}`);

const report = {
  passed: failures.length === 0,
  bookId: book.bookId,
  chapters: book.chapters.length,
  pages: pages.length,
  languages: book.languages,
  aviLevel: book.aviLevel,
  aviStatus: book.aviStatus,
  dutchWords: totalWords,
  dutchSentences: totalSentences,
  averageWordsPerSentence: Number((totalWords / totalSentences).toFixed(2)),
  averageLettersPerWord: Number((totalLetters / totalWords).toFixed(2)),
  uniqueIllustrationIds: ids.size,
  uniquePromptBlocks: new Set(promptBlocks).size,
  unknownM3Words: [...unknown].sort((a, b) => a.localeCompare(b, 'nl-BE')),
  failures,
};

fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
console.log(JSON.stringify(report, null, 2));
if (!report.passed) process.exitCode = 1;
