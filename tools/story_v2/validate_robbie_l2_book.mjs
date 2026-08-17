import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const requested = process.argv.find((arg) => arg.startsWith('--book='))?.split('=')[1] ?? 'l2_m4';
const configs = {
  l2_m4: { bookId: 'L2_M4', level: 'M4', minWords: 6000, maxWords: 9000, maxLineWords: 15 },
  l2_e4: { bookId: 'L2_E4', level: 'E4', minWords: 8000, maxWords: 11000, maxLineWords: 18 },
};
const config = configs[requested];
if (!config) throw new Error('Use --book=l2_m4 or --book=l2_e4');

const bookRoot = path.join(root, 'story_import', 'generated', 'robbie', requested);
const bookPath = path.join(bookRoot, `robbie_${requested}.json`);
const reportPath = path.join(bookRoot, `04_${config.bookId}_BOOK_QA.json`);
const book = JSON.parse(fs.readFileSync(bookPath, 'utf8'));
const failures = [];
const warnings = [];
const pages = book.chapters.flatMap((chapter) => chapter.pages);
const words = (text) => text.match(/\p{L}+(?:[’'-]\p{L}+)*/gu) ?? [];

if (book.bookId !== config.bookId) failures.push(`bookId is ${book.bookId}`);
if (book.grade !== 2) failures.push(`grade is ${book.grade}`);
if (book.aviLevel !== config.level || book.aviDisplayLabel !== `AVI ${config.level}` || book.aviStatus !== 'provisional') failures.push(`AVI metadata must be ${config.level} / AVI ${config.level} / provisional`);
if (book.pageCount !== 150 || pages.length !== 150) failures.push(`expected 150 pages, found metadata=${book.pageCount}, actual=${pages.length}`);
if (book.chapterCount !== 12 || book.chapters.length !== 12) failures.push(`expected 12 chapters, found metadata=${book.chapterCount}, actual=${book.chapters.length}`);
if (book.illustrationCount !== 75) failures.push(`illustrationCount is ${book.illustrationCount}`);
if (book.coverFilename !== `robbie_${requested}_cover.png` || book.coverTextEmbedded !== false) failures.push('cover metadata is invalid');
if (JSON.stringify(book.languages) !== JSON.stringify(['nl-BE', 'fr-BE', 'en-US'])) failures.push('language order is invalid');

const expectedChapterRanges = [[1,13],[14,25],[26,38],[39,50],[51,63],[64,75],[76,88],[89,100],[101,113],[114,125],[126,138],[139,150]];
for (let index = 0; index < book.chapters.length; index += 1) {
  const chapter = book.chapters[index];
  const [start, end] = expectedChapterRanges[index];
  if (chapter.chapterNumber !== index + 1) failures.push(`chapter index ${index} has number ${chapter.chapterNumber}`);
  if (chapter.startPage !== start || chapter.endPage !== end) failures.push(`chapter ${chapter.chapterNumber} range is ${chapter.startPage}-${chapter.endPage}, expected ${start}-${end}`);
  if (chapter.pages.length !== end - start + 1) failures.push(`chapter ${chapter.chapterNumber} has ${chapter.pages.length} pages`);
  for (const locale of ['nl-BE', 'fr-BE', 'en-US']) if (!chapter.titles?.[locale]?.trim()) failures.push(`chapter ${chapter.chapterNumber} lacks ${locale} title`);
}

const illustrationIds = new Set();
const illustrationFiles = new Set();
const prompts = new Set();
const storyBeats = new Set();
const localeTexts = Object.fromEntries(book.languages.map((locale) => [locale, new Set()]));
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
  if (!page.storyBeatId?.trim()) failures.push(`page ${expected} lacks storyBeatId`);
  else if (storyBeats.has(page.storyBeatId)) failures.push(`duplicate storyBeatId ${page.storyBeatId}`);
  storyBeats.add(page.storyBeatId);

  for (const locale of book.languages) {
    const text = page.text?.[locale];
    if (typeof text !== 'string' || !text.trim()) failures.push(`page ${expected} has empty ${locale}`);
    else {
      if (localeTexts[locale].has(text)) failures.push(`duplicate ${locale} text at page ${expected}`);
      localeTexts[locale].add(text);
      const lines = text.split('\n').filter(Boolean);
      if (lines.length < 3 || lines.length > 8) warnings.push(`page ${expected} has ${lines.length} ${locale} lines`);
      for (const line of lines) {
        if (!/[.!?…’”»]$/u.test(line.trim())) warnings.push(`page ${expected} has a ${locale} line without terminal punctuation`);
      }
    }
  }

  const nlLines = page.text['nl-BE'].split('\n').filter(Boolean);
  for (const line of nlLines) {
    const size = words(line).length;
    lineWordCounts.push(size);
    totalDutchLines += 1;
    if (size > config.maxLineWords) longLines.push({ pageNumber: expected, words: size, line });
  }
  const nlWords = words(page.text['nl-BE']);
  totalDutchWords += nlWords.length;
  totalDutchLetters += nlWords.reduce((sum, word) => sum + [...word.replace(/[’'-]/g, '')].length, 0);

  const illustrationExpected = expected % 2 === 1;
  if (page.illustrationRequired !== illustrationExpected) failures.push(`page ${expected} illustrationRequired is ${page.illustrationRequired}`);
  if (illustrationExpected) {
    const expectedId = `${config.bookId}_P${padded}`;
    const expectedFilename = `robbie_${requested}_p${padded}.png`;
    if (page.illustrationId !== expectedId) failures.push(`page ${expected} illustrationId is ${page.illustrationId}`);
    if (page.illustrationFilename !== expectedFilename) failures.push(`page ${expected} illustrationFilename is ${page.illustrationFilename}`);
    if (!page.illustrationPrompt?.includes(`IMAGE ID:\n${expectedId}`)) failures.push(`page ${expected} prompt lacks its image ID`);
    for (const marker of ['STORY MOMENT:', 'CHARACTERS:', 'COMPOSITION:', 'CONTINUITY:', 'STYLE LOCK:', 'TECHNICAL OUTPUT:', 'NEGATIVE INSTRUCTIONS:']) {
      if (!page.illustrationPrompt?.includes(marker)) failures.push(`page ${expected} prompt lacks ${marker}`);
    }
    if (illustrationIds.has(page.illustrationId)) failures.push(`duplicate illustrationId ${page.illustrationId}`);
    if (illustrationFiles.has(page.illustrationFilename)) failures.push(`duplicate illustrationFilename ${page.illustrationFilename}`);
    if (prompts.has(page.illustrationPrompt)) failures.push(`duplicate illustration prompt at page ${expected}`);
    illustrationIds.add(page.illustrationId);
    illustrationFiles.add(page.illustrationFilename);
    prompts.add(page.illustrationPrompt);
  } else if (page.illustrationId !== null || page.illustrationFilename !== null || page.illustrationPrompt !== null) {
    failures.push(`page ${expected} must not define a separate illustration`);
  }

  const nl = page.text['nl-BE'];
  const fr = page.text['fr-BE'];
  const en = page.text['en-US'];
  if (/\b(?:Fien|Mats|Noor|Kroon|Lotte)\b/u.test(fr)) failures.push(`page ${expected} has an unlocalized name in fr-BE`);
  if (/\b(?:Fien|Mats|Noor|Kroon|Lotte|Fanny|Mathis|Couronne|Charlotte)\b/u.test(en)) failures.push(`page ${expected} has an unlocalized name in en-US`);
  if (/\b(?:Fanny|Mathis|Nora|Couronne|Charlotte|Faye|Max|Crown|Lottie)\b/u.test(nl)) failures.push(`page ${expected} has a foreign name in nl-BE`);
  if (/\bMorgenstad\b/u.test(fr) || /\bMorgenstad\b/u.test(en)) failures.push(`page ${expected} has an unlocalized city name`);
}

if (totalDutchWords < config.minWords || totalDutchWords > config.maxWords) failures.push(`Dutch word count ${totalDutchWords} is outside ${config.minWords}-${config.maxWords}`);
if (longLines.length) warnings.push(`${longLines.length} Dutch lines exceed ${config.maxLineWords} words; editorial review recommended`);
const averageWordsPerLine = lineWordCounts.reduce((sum, value) => sum + value, 0) / Math.max(totalDutchLines, 1);
const averageLettersPerWord = totalDutchLetters / Math.max(totalDutchWords, 1);

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
  targetDutchWords: `${config.minWords}-${config.maxWords}`,
  dutchLines: totalDutchLines,
  averageDutchWordsPerLine: Number(averageWordsPerLine.toFixed(2)),
  averageDutchLettersPerWord: Number(averageLettersPerWord.toFixed(2)),
  maximumDutchWordsOnLine: Math.max(...lineWordCounts),
  longLines,
  uniqueStoryBeatIds: storyBeats.size,
  uniqueIllustrationIds: illustrationIds.size,
  uniqueIllustrationFilenames: illustrationFiles.size,
  uniqueIllustrationPrompts: prompts.size,
  failures,
  warnings,
};

fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
console.log(JSON.stringify(report, null, 2));
if (failures.length) process.exitCode = 1;
