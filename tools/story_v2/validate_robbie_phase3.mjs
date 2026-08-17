import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const phase3 = path.join(root, 'story_import', 'generated', 'robbie', 'phase3');

const files = {
  story: path.join(phase3, '01_L1_M3_CH01_STORY.md'),
  prompts: path.join(phase3, '02_L1_M3_CH01_ILLUSTRATION_PROMPTS.md'),
  avi: path.join(phase3, '03_L1_M3_CH01_AVI_AUDIT.md'),
  continuity: path.join(phase3, '04_L1_M3_CH01_CONTINUITY.md'),
  quality: path.join(phase3, '05_PHASE3_KWALITEITSRAPPORT.md'),
  json: path.join(phase3, 'robbie_l1_m3_ch01.draft.json'),
};

const failures = [];
for (const [label, file] of Object.entries(files)) {
  if (!fs.existsSync(file)) failures.push(`Ontbrekend ${label}-bestand: ${path.relative(root, file)}`);
}

if (failures.length) {
  console.log(JSON.stringify({ passed: false, phase: 3, failures }, null, 2));
  process.exit(1);
}

const chapter = JSON.parse(fs.readFileSync(files.json, 'utf8'));
const storyDoc = fs.readFileSync(files.story, 'utf8');
const promptDoc = fs.readFileSync(files.prompts, 'utf8');
const continuityDoc = fs.readFileSync(files.continuity, 'utf8');
const aviDoc = fs.readFileSync(files.avi, 'utf8');

const oneSyllableWords = new Set(`
  tik op het dak fien rent door de plas mats met haar mee daar staat een oud hok
  duwt aan deur kraakt luid is klein en kil stof prikt in hun neus
  pakt zijn lamp klik licht gaat glijdt langs wand kijkt om zich heen
  er ligt veel spul bak scheef tang bij pot ziet doek
  iets stapt dooft zwart schuift wat
  gilt heel kort grijpt arm ze staan stil dan lacht zacht dat maakt mij bang
  tikt komt weer wijst naar snoer uit
  tilt helpt valt grond lijf van blik groot
  wit grijs kop rond hart die geeft geen zit
  buigt stop zegt raak niet wij zien eerst of goed
  knielt hij veegt weg droog wacht naast hem
  doet vast klinkt nu gloeit woord
  oog fel blauw snel hen
`.trim().split(/\s+/));

const forbiddenM3Words = new Set([
  'robbie',
  'robot',
  'computer',
  'werkplaats',
  'metaal',
  'kabel',
  'antenne',
  'paardenstaart',
]);

if (chapter.bookId !== 'L1_M3') failures.push('Verkeerde bookId');
if (chapter.chapterNumber !== 1) failures.push('Verkeerd hoofdstuknummer');
if (chapter.aviLevel !== 'M3' || chapter.aviStatus !== 'provisional') failures.push('AVI-status is niet M3/provisional');
if (chapter.pageCount !== 13 || chapter.pages.length !== 13) failures.push('Hoofdstuk moet exact 13 pagina’s hebben');
if (chapter.illustrationCount !== 13) failures.push('Hoofdstuk moet exact 13 illustratieprompts hebben');

let totalWords = 0;
let totalSentences = 0;
let totalLetters = 0;
const unknownWords = new Set();
const seenPages = new Set();
const seenBeats = new Set();
const seenImages = new Set();
const seenFilenames = new Set();

for (const [index, page] of chapter.pages.entries()) {
  const expectedPage = index + 1;
  const padded = String(expectedPage).padStart(3, '0');
  if (page.pageNumber !== expectedPage) failures.push(`Niet-aansluitend paginanummer bij index ${index}`);
  if (seenPages.has(page.pageNumber)) failures.push(`Dubbel paginanummer ${page.pageNumber}`);
  seenPages.add(page.pageNumber);
  if (seenBeats.has(page.storyBeatId)) failures.push(`Dubbele storyBeatId ${page.storyBeatId}`);
  seenBeats.add(page.storyBeatId);
  if (page.illustrationId !== `L1_M3_P${padded}`) failures.push(`Foute illustrationId op pagina ${expectedPage}`);
  if (page.illustrationFilename !== `robbie_l1_m3_p${padded}.png`) failures.push(`Foute illustratiebestandsnaam op pagina ${expectedPage}`);
  if (seenImages.has(page.illustrationId)) failures.push(`Dubbele illustrationId ${page.illustrationId}`);
  seenImages.add(page.illustrationId);
  if (seenFilenames.has(page.illustrationFilename)) failures.push(`Dubbele illustratiebestandsnaam ${page.illustrationFilename}`);
  seenFilenames.add(page.illustrationFilename);
  if (page.illustrationRequired !== true) failures.push(`Illustratie ontbreekt op pagina ${expectedPage}`);

  for (const language of ['nl-BE', 'fr-BE', 'en-US']) {
    if (typeof page.text?.[language] !== 'string' || !page.text[language].trim()) {
      failures.push(`Lege ${language}-tekst op pagina ${expectedPage}`);
    }
    for (const line of (page.text?.[language] ?? '').split('\n').filter(Boolean)) {
      if (!storyDoc.includes(line)) failures.push(`Storydocument en JSON verschillen op pagina ${expectedPage} (${language}): ${line}`);
    }
  }

  const dutch = page.text['nl-BE'];
  const lines = dutch.split('\n').filter((line) => line.trim());
  const words = dutch.match(/\p{L}+/gu) ?? [];
  totalWords += words.length;
  totalSentences += lines.length;
  totalLetters += words.reduce((sum, word) => sum + [...word].length, 0);
  if (words.length !== 20) failures.push(`Pagina ${expectedPage} heeft ${words.length} Nederlandse woorden in plaats van 20`);
  if (dutch !== dutch.toLocaleLowerCase('nl-BE')) failures.push(`Hoofdletter in Nederlandse leestekst op pagina ${expectedPage}`);
  if (lines.some((line) => !/[.!?]$/.test(line.trim()))) failures.push(`Niet-afgesloten zin/regel op pagina ${expectedPage}`);
  for (const rawWord of words) {
    const word = rawWord.toLocaleLowerCase('nl-BE');
    if (!oneSyllableWords.has(word)) unknownWords.add(word);
    if (forbiddenM3Words.has(word)) failures.push(`Verboden M3-woord '${word}' op pagina ${expectedPage}`);
  }

  const french = page.text['fr-BE'];
  const english = page.text['en-US'];
  if (/\b(Fien|Mats|Faye|Max)\b/u.test(french)) failures.push(`Verkeerde lokale naam in fr-BE op pagina ${expectedPage}`);
  if (/\b(Fien|Mats|Fanny|Mathis)\b/u.test(english)) failures.push(`Verkeerde lokale naam in en-US op pagina ${expectedPage}`);
  if (!promptDoc.includes(`L1_M3_P${padded}`)) failures.push(`Prompt-ID ontbreekt voor pagina ${expectedPage}`);
}

if (unknownWords.size) failures.push(`Niet-gecontroleerde Nederlandse woorden: ${[...unknownWords].sort().join(', ')}`);
if (totalWords !== 260) failures.push(`Hoofdstuk heeft ${totalWords} Nederlandse woorden in plaats van 260`);

const promptIds = [...promptDoc.matchAll(/^L1_M3_P(\d{3})$/gm)].map((match) => match[0]);
const compositionCodes = [...promptDoc.matchAll(/^COMPOSITION CODE: (.+)$/gm)].map((match) => match[1].trim());
const promptBlocks = [...promptDoc.matchAll(/```text\n([\s\S]*?)\n```/g)].map((match) => match[1]);
if (promptIds.length !== 13 || new Set(promptIds).size !== 13) failures.push('Promptdocument heeft niet exact 13 unieke image IDs');
if (compositionCodes.length !== 13 || new Set(compositionCodes).size !== 13) failures.push('Promptdocument heeft niet exact 13 unieke compositiecodes');
if (promptBlocks.length !== 13 || new Set(promptBlocks).size !== 13) failures.push('Promptdocument heeft niet exact 13 unieke promptblokken');
for (const marker of ['STORY MOMENT:', 'CHARACTERS:', 'ACTION:', 'EMOTION:', 'PROPS:', 'COMPOSITION:', 'CONTINUITY:', 'STYLE LOCK:', 'TECHNICAL OUTPUT:', 'NEGATIVE INSTRUCTIONS:']) {
  const count = promptBlocks.filter((block) => block.includes(marker)).length;
  if (count !== 13) failures.push(`Promptveld '${marker}' staat in ${count}/13 prompts`);
}

for (const marker of ['exact één blauw oog aan', 'hartlicht gloeit zachtroze', 'heeft nog geen naam gekregen', 'Kroon is nog niet zichtbaar']) {
  if (!continuityDoc.includes(marker)) failures.push(`Continuïteitsrapport mist marker: ${marker}`);
}
for (const marker of ['Exact 260 woorden', 'Geen hoofdletters', '0 | 0', 'AVI M3 — voorlopig']) {
  if (!aviDoc.includes(marker)) failures.push(`AVI-rapport mist marker: ${marker}`);
}

const avgSentenceWords = totalSentences ? totalWords / totalSentences : 0;
const avgWordLetters = totalWords ? totalLetters / totalWords : 0;
const report = {
  passed: failures.length === 0,
  phase: 3,
  bookId: chapter.bookId,
  chapter: chapter.chapterNumber,
  pages: chapter.pages.length,
  languages: chapter.languages,
  dutchWords: totalWords,
  dutchSentences: totalSentences,
  averageWordsPerSentence: Number(avgSentenceWords.toFixed(2)),
  averageLettersPerWord: Number(avgWordLetters.toFixed(2)),
  unknownMonosyllableWhitelistWords: [...unknownWords].sort(),
  uniqueIllustrationPrompts: new Set(promptBlocks).size,
  uniqueCompositionCodes: new Set(compositionCodes).size,
  finalIllustrationsGenerated: chapter.pages.some((page) => fs.existsSync(path.join(phase3, page.illustrationFilename))),
  failures,
};

console.log(JSON.stringify(report, null, 2));
if (!report.passed) process.exitCode = 1;
