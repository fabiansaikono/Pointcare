import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const robbieRoot = path.join(root, 'story_import', 'generated', 'robbie');
const argument = process.argv.find((value) => value.startsWith('--book='));
const bookSlug = argument?.split('=', 2)[1];
if (!['l1_e3', 'l2_m4', 'l2_e4', 'l3_m5', 'l3_e5', 'l4_m6', 'l4_e6'].includes(bookSlug)) {
  throw new Error('Use --book=l1_e3, --book=l2_m4, --book=l2_e4, --book=l3_m5, --book=l3_e5, --book=l4_m6 or --book=l4_e6');
}

const bookRoot = path.join(robbieRoot, bookSlug);
const bookPath = path.join(bookRoot, `robbie_${bookSlug}.json`);
const imageRoot = path.join(bookRoot, 'images', 'final');
const coverRoot = path.join(bookRoot, 'cover', 'final');
const manifestPath = path.join(bookRoot, 'asset_manifest.json');
const reportPath = path.join(bookRoot, 'PACKAGE_REPORT.md');
const compositionManifestPath = path.join(bookRoot, 'hybrid_composition_manifest.json');
const relative = (file) => path.relative(root, file).split(path.sep).join('/');
const sha256 = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

const failures = [];
if (!fs.existsSync(bookPath)) failures.push(`missing ${relative(bookPath)}`);
if (!fs.existsSync(compositionManifestPath)) failures.push(`missing ${relative(compositionManifestPath)}`);
if (failures.length) throw new Error(failures.join('\n'));

const book = JSON.parse(fs.readFileSync(bookPath, 'utf8'));
const composition = JSON.parse(fs.readFileSync(compositionManifestPath, 'utf8'));
const pages = book.chapters.flatMap((chapter) => chapter.pages);
if (pages.length !== 150) failures.push(`expected 150 pages, found ${pages.length}`);
if (!composition.passed) failures.push('hybrid composition manifest did not pass');
if (book.aviDisplayLabel !== `AVI ${book.aviLevel}`) failures.push(`invalid AVI display label ${book.aviDisplayLabel}`);

const illustratedPages = pages.filter((page) => page.illustrationRequired !== false && page.illustrationFilename);
const illustrations = illustratedPages.map((page) => {
  const file = path.join(imageRoot, page.illustrationFilename);
  if (!fs.existsSync(file)) failures.push(`missing ${relative(file)}`);
  return {
    pageNumber: page.pageNumber,
    illustrationId: page.illustrationId,
    filename: page.illustrationFilename,
    path: relative(file),
    bytes: fs.existsSync(file) ? fs.statSync(file).size : 0,
    sha256: fs.existsSync(file) ? sha256(file) : null,
  };
});
const coverPath = path.join(coverRoot, book.coverFilename);
if (!fs.existsSync(coverPath)) failures.push(`missing ${relative(coverPath)}`);
if (failures.length) throw new Error(failures.join('\n'));

const uniqueHashes = new Set(illustrations.map((asset) => asset.sha256)).size;
if (illustrations.length !== book.illustrationCount) failures.push(`expected ${book.illustrationCount} illustrations, found ${illustrations.length}`);
if (uniqueHashes !== book.illustrationCount) failures.push(`expected ${book.illustrationCount} unique image hashes, found ${uniqueHashes}`);
const manifest = {
  packageVersion: 2,
  generatedAt: new Date().toISOString(),
  seriesId: book.seriesId,
  bookId: book.bookId,
  titles: book.titles,
  grade: book.grade,
  aviLevel: book.aviLevel,
  aviDisplayLabel: book.aviDisplayLabel,
  aviStatus: book.aviStatus,
  languages: book.languages,
  pageCount: book.pageCount,
  chapterCount: book.chapterCount,
  illustrationCount: book.illustrationCount,
  workflow: composition.workflow,
  book: { path: relative(bookPath), bytes: fs.statSync(bookPath).size, sha256: sha256(bookPath) },
  cover: { filename: book.coverFilename, path: relative(coverPath), bytes: fs.statSync(coverPath).size, sha256: sha256(coverPath) },
  illustrations,
  uniqueIllustrationHashes: uniqueHashes,
  passed: failures.length === 0,
  failures,
};
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

const totalBytes = illustrations.reduce((sum, asset) => sum + asset.bytes, 0);
const report = [
  `# ${book.bookId} — pakketrapport`,
  '',
  `- Titel: ${book.titles['nl-BE']}`,
  `- Leesniveau: ${book.aviDisplayLabel} (${book.aviStatus === 'provisional' ? 'voorlopig' : book.aviStatus})`,
  `- Hoofdstukken: ${book.chapterCount}`,
  `- Pagina’s: ${book.pageCount}`,
  `- Talen: ${book.languages.join(', ')}`,
  `- Illustraties: ${illustrations.length}`,
  `- Unieke illustratiehashes: ${uniqueHashes}`,
  `- Omvang illustraties: ${totalBytes} bytes`,
  `- Cover: ${relative(coverPath)}`,
  `- Hybride workflow: ${composition.workflow}`,
  `- Controle: ${manifest.passed ? 'geslaagd' : 'mislukt'}`,
  '',
];
fs.writeFileSync(reportPath, report.join('\n'), 'utf8');

console.log(JSON.stringify({
  passed: manifest.passed,
  bookId: book.bookId,
  aviDisplayLabel: book.aviDisplayLabel,
  pages: pages.length,
  illustrations: illustrations.length,
  uniqueHashes,
  totalIllustrationBytes: totalBytes,
  manifest: relative(manifestPath),
  report: relative(reportPath),
  failures,
}, null, 2));
if (failures.length) process.exitCode = 1;
