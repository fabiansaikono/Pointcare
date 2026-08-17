import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const bookPath = path.join(root, 'story_import', 'generated', 'robbie', 'l1_e3', 'robbie_l1_e3.json');
const reportPath = path.join(root, 'story_import', 'generated', 'robbie', 'l1_e3', '04_L1_E3_BOOK_QA.json');
const book = JSON.parse(fs.readFileSync(bookPath, 'utf8'));
const failures = [];
const warnings = [];
const pages = book.chapters.flatMap((chapter) => chapter.pages);
const words = (text) => text.match(/\p{L}+(?:[’'-]\p{L}+)*/gu) ?? [];
const countWords = (text) => words(text).length;

if (book.bookId !== 'L1_E3') failures.push(`bookId is ${book.bookId}`);
if (book.grade !== 1) failures.push(`grade is ${book.grade}`);
if (book.aviLevel !== 'E3' || book.aviDisplayLabel !== 'AVI E3' || book.aviStatus !== 'provisional') {
  failures.push('AVI metadata must be E3 / AVI E3 / provisional');
}
if (book.pageCount !== 150 || pages.length !== 150) failures.push(`expected 150 pages, found metadata=${book.pageCount}, actual=${pages.length}`);
if (book.chapterCount !== 12 || book.chapters.length !== 12) failures.push(`expected 12 chapters, found metadata=${book.chapterCount}, actual=${book.chapters.length}`);
if (book.illustrationCount !== 150) failures.push(`illustrationCount is ${book.illustrationCount}`);
if (book.coverFilename !== 'robbie_l1_e3_cover.png' || book.coverTextEmbedded !== false) failures.push('cover metadata is invalid');
if (JSON.stringify(book.languages) !== JSON.stringify(['nl-BE', 'fr-BE', 'en-US'])) failures.push('language order is invalid');

const expectedChapterRanges = [[1,13],[14,25],[26,38],[39,50],[51,63],[64,75],[76,88],[89,100],[101,113],[114,125],[126,138],[139,150]];
for (let index = 0; index < book.chapters.length; index += 1) {
  const chapter = book.chapters[index];
  const [start, end] = expectedChapterRanges[index];
  if (chapter.chapterNumber !== index + 1) failures.push(`chapter index ${index} has number ${chapter.chapterNumber}`);
  if (chapter.startPage !== start || chapter.endPage !== end) failures.push(`chapter ${chapter.chapterNumber} range is ${chapter.startPage}-${chapter.endPage}, expected ${start}-${end}`);
  if (chapter.pages.length !== end - start + 1) failures.push(`chapter ${chapter.chapterNumber} has ${chapter.pages.length} pages`);
}

const pageTextKeys = ['nl-BE', 'fr-BE', 'en-US'];
const illustrationIds = new Set();
const illustrationFiles = new Set();
const prompts = new Set();
const localeTexts = Object.fromEntries(pageTextKeys.map((locale) => [locale, new Set()]));
const lineWordCounts = [];
const longLines = [];
let totalDutchWords = 0;
let totalDutchLetters = 0;
let totalDutchLines = 0;

for (let index = 0; index < pages.length; index += 1) {
  const page = pages[index];
  const expected = index + 1;
  const padded = String(expected).padStart(3, '0');
  if (page.pageNumber !== expected) failures.push(`page ${index} has pageNumber ${page.pageNumber}, expected ${expected}`);
  for (const locale of pageTextKeys) {
    if (typeof page.text?.[locale] !== 'string' || !page.text[locale].trim()) failures.push(`page ${expected} has empty ${locale}`);
    else {
      if (localeTexts[locale].has(page.text[locale])) failures.push(`duplicate ${locale} text at page ${expected}`);
      localeTexts[locale].add(page.text[locale]);
    }
  }
  const nlLines = page.text['nl-BE'].split('\n').filter(Boolean);
  if (nlLines.length < 3 || nlLines.length > 6) warnings.push(`page ${expected} has ${nlLines.length} Dutch lines`);
  for (const line of nlLines) {
    const size = countWords(line);
    lineWordCounts.push(size);
    totalDutchLines += 1;
    if (size > 13) longLines.push({ pageNumber: expected, words: size, line });
  }
  const nlWords = words(page.text['nl-BE']);
  totalDutchWords += nlWords.length;
  totalDutchLetters += nlWords.reduce((sum, word) => sum + [...word.replace(/[’'-]/g, '')].length, 0);

  const expectedId = `L1_E3_P${padded}`;
  const expectedFilename = `robbie_l1_e3_p${padded}.png`;
  if (page.illustrationId !== expectedId) failures.push(`page ${expected} illustrationId is ${page.illustrationId}`);
  if (page.illustrationFilename !== expectedFilename) failures.push(`page ${expected} illustrationFilename is ${page.illustrationFilename}`);
  if (!page.illustrationPrompt?.includes(`IMAGE ID:\n${expectedId}`)) failures.push(`page ${expected} prompt lacks its image ID`);
  for (const marker of ['STORY MOMENT:', 'CHARACTERS:', 'COMPOSITION:', 'CONTINUITY:', 'STYLE LOCK:', 'TECHNICAL OUTPUT:', 'NEGATIVE INSTRUCTIONS:']) {
    if (!page.illustrationPrompt?.includes(marker)) failures.push(`page ${expected} prompt lacks ${marker}`);
  }
  if (page.continuityTags.includes('canonical_grate_memory') && !page.illustrationPrompt.includes('garden_grate_reference.png')) failures.push(`page ${expected} lacks canonical grate reference`);
  if (illustrationIds.has(page.illustrationId)) failures.push(`duplicate illustrationId ${page.illustrationId}`);
  if (illustrationFiles.has(page.illustrationFilename)) failures.push(`duplicate illustrationFilename ${page.illustrationFilename}`);
  if (prompts.has(page.illustrationPrompt)) failures.push(`duplicate illustration prompt at page ${expected}`);
  illustrationIds.add(page.illustrationId);
  illustrationFiles.add(page.illustrationFilename);
  prompts.add(page.illustrationPrompt);

  const fr = page.text['fr-BE'];
  const en = page.text['en-US'];
  if (/\b(?:Fien|Mats|Noor|Kroon)\b/u.test(fr)) failures.push(`page ${expected} has an unlocalized name in fr-BE`);
  if (/\b(?:Fien|Mats|Noor|Kroon|Fanny|Mathis|Couronne)\b/u.test(en)) failures.push(`page ${expected} has an unlocalized name in en-US`);
  if (/\b(?:Fanny|Mathis|Nora|Couronne|Faye|Max)\b/u.test(page.text['nl-BE'])) failures.push(`page ${expected} has a foreign name in nl-BE`);

  const earlyRobbie = page.text['nl-BE'].match(/\bRobbie(?:s)?\b/gu) ?? [];
  const candidateAllowed = (expected >= 56 && expected <= 63) || (expected >= 143 && expected <= 144);
  if (expected < 145 && earlyRobbie.length > 0 && !candidateAllowed) failures.push(`page ${expected} uses Robbie before the choice`);
  if (expected >= 64 && expected <= 142 && earlyRobbie.length > 0) failures.push(`page ${expected} treats Robbie as chosen too early`);
}

if (totalDutchWords < 4000 || totalDutchWords > 6000) failures.push(`Dutch word count ${totalDutchWords} is outside 4000-6000`);
if (!pages[144].text['nl-BE'].includes('Ik heet Robbie')) failures.push('page 145 does not contain the explicit chosen-name sentence');
if (!pages.slice(145).every((page) => page.text['nl-BE'].includes('Robbie') || page.pageNumber === 149 || page.pageNumber === 150)) warnings.push('not every post-choice page uses the chosen name explicitly');

const averageWordsPerLine = lineWordCounts.reduce((sum, value) => sum + value, 0) / Math.max(totalDutchLines, 1);
const averageLettersPerWord = totalDutchLetters / Math.max(totalDutchWords, 1);
if (longLines.length) warnings.push(`${longLines.length} Dutch lines exceed 13 words; editorial review recommended`);

const report = {
  passed: failures.length === 0,
  generatedAt: new Date().toISOString(),
  bookId: book.bookId,
  aviLevel: book.aviLevel,
  aviDisplayLabel: book.aviDisplayLabel,
  aviStatus: book.aviStatus,
  chapters: book.chapters.length,
  pages: pages.length,
  languages: book.languages,
  dutchWords: totalDutchWords,
  targetDutchWords: '4000-6000',
  dutchLines: totalDutchLines,
  averageDutchWordsPerLine: Number(averageWordsPerLine.toFixed(2)),
  averageDutchLettersPerWord: Number(averageLettersPerWord.toFixed(2)),
  maximumDutchWordsOnLine: Math.max(...lineWordCounts),
  longLines,
  uniqueIllustrationIds: illustrationIds.size,
  uniqueIllustrationFilenames: illustrationFiles.size,
  uniqueIllustrationPrompts: prompts.size,
  failures,
  warnings,
};

fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
console.log(JSON.stringify(report, null, 2));
if (failures.length) process.exitCode = 1;
