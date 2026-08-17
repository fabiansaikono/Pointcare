import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..', '..');
const robbie = path.join(root, 'story_import', 'generated', 'robbie');
const bookSource = path.join(robbie, 'phase4', 'robbie_l1_m3.json');
const packageRoot = path.join(robbie, 'l1_m3');
const bookTarget = path.join(packageRoot, 'robbie_l1_m3.json');
const imageRoot = path.join(packageRoot, 'images', 'final');
const coverPath = path.join(packageRoot, 'cover', 'final', 'robbie_l1_m3_cover.png');
const manifestPath = path.join(robbie, 'phase5', '02_L1_M3_ASSET_MANIFEST.json');

const sha256 = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const relative = (file) => path.relative(root, file).split(path.sep).join('/');

const book = JSON.parse(fs.readFileSync(bookSource, 'utf8'));
const pages = book.chapters.flatMap((chapter) => chapter.pages);
const failures = [];
if (pages.length !== 150) failures.push(`expected 150 pages, found ${pages.length}`);

const assets = pages.map((page) => {
  const file = path.join(imageRoot, page.illustrationFilename);
  if (!fs.existsSync(file)) failures.push(`missing ${file}`);
  return {
    pageNumber: page.pageNumber,
    illustrationId: page.illustrationId,
    filename: page.illustrationFilename,
    path: relative(file),
    bytes: fs.existsSync(file) ? fs.statSync(file).size : 0,
    sha256: fs.existsSync(file) ? sha256(file) : null,
  };
});
if (!fs.existsSync(coverPath)) failures.push(`missing ${coverPath}`);
if (failures.length) throw new Error(failures.join('\n'));

fs.mkdirSync(packageRoot, { recursive: true });
fs.copyFileSync(bookSource, bookTarget);
const manifest = {
  packageVersion: 1,
  generatedAt: new Date().toISOString(),
  seriesId: book.seriesId,
  bookId: book.bookId,
  grade: book.grade,
  aviLevel: book.aviLevel,
  aviStatus: book.aviStatus,
  languages: book.languages,
  pageCount: book.pageCount,
  chapterCount: book.chapterCount,
  book: { path: relative(bookTarget), bytes: fs.statSync(bookTarget).size, sha256: sha256(bookTarget) },
  cover: { filename: book.coverFilename, path: relative(coverPath), bytes: fs.statSync(coverPath).size, sha256: sha256(coverPath) },
  illustrations: assets,
};
fs.mkdirSync(path.dirname(manifestPath), { recursive: true });
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({
  passed: true,
  bookId: manifest.bookId,
  pages: manifest.pageCount,
  chapters: manifest.chapterCount,
  illustrations: manifest.illustrations.length,
  uniqueHashes: new Set(manifest.illustrations.map((asset) => asset.sha256)).size,
  packageBook: manifest.book.path,
  cover: manifest.cover.path,
  manifest: relative(manifestPath),
}, null, 2));
