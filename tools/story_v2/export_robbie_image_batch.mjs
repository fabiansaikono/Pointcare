import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..', '..');
const bookPath = path.join(root, 'story_import', 'generated', 'robbie', 'phase4', 'robbie_l1_m3.json');
const refDir = path.join(root, 'story_import', 'generated', 'robbie', 'references', 'final');
const sourceDir = path.join(root, 'story_import', 'generated', 'robbie', 'l1_m3', 'images', 'source');
const finalDir = path.join(root, 'story_import', 'generated', 'robbie', 'l1_m3', 'images', 'final');
const gratePages = new Set([50, 51, 52, 53, 54, 55, 56, 60, 61, 63, 76, 77, 78, 79, 80, 81, 83, 84, 85, 101, 102, 125, 126, 132, 134, 135]);

const from = Number(process.argv[2] ?? 1);
const to = Number(process.argv[3] ?? from);
if (!Number.isInteger(from) || !Number.isInteger(to) || from < 1 || to < from || to > 150) {
  throw new Error('Usage: node export_robbie_image_batch.mjs <from 1..150> <to 1..150>');
}

const book = JSON.parse(fs.readFileSync(bookPath, 'utf8'));
const pages = book.chapters.flatMap((chapter) => chapter.pages);
const characterRefs = {
  robbie: 'robbie_character_reference.png',
  rob: 'robbie_character_reference.png',
  fien: 'fien_character_reference.png',
  mats: 'mats_character_reference.png',
  puk: 'puk_character_reference.png',
  neighbor: 'neighbor_character_reference.png',
  buur: 'neighbor_character_reference.png',
  kroon: 'kroon_character_reference.png',
};

function getReferences(page) {
  const files = ['videogame_style_reference.png', 'robbie_cast_reference.png'];
  const characterNames = (page.characters ?? []).map((character) => String(character).toLowerCase());
  for (const character of characterNames) {
    if (['puk', 'neighbor', 'buur', 'kroon'].includes(character)) {
      const filename = characterRefs[character];
      if (filename) files.push(filename);
    }
  }
  const prompt = String(page.illustrationPrompt ?? '').toLowerCase();
  const tags = (page.continuityTags ?? []).join(' ').toLowerCase();
  if (prompt.includes('factory mark') || prompt.includes('kroon mark') || tags.includes('factory_mark') || page.pageNumber === 150) {
    files.push('kroon_factory_mark_reference.png');
  }
  if (prompt.includes('grate') || tags.includes('grate') || gratePages.has(page.pageNumber)) {
    files.push('garden_grate_reference.png');
  }
  for (const character of characterNames) {
    if (files.length >= 5) break;
    if (['puk', 'neighbor', 'buur', 'kroon'].includes(character)) continue;
    const filename = characterRefs[character];
    if (filename) files.push(filename);
  }
  return [...new Set(files)].slice(0, 5).map((file) => path.join(refDir, file));
}

function chromaPrompt(page) {
  const prompt = String(page.illustrationPrompt ?? '');
  const tags = (page.continuityTags ?? []).join(' ').toLowerCase();
  const pageLocks = {
    55: `\n\nPAGE-SPECIFIC PUK LOCK:\nPuk remains completely beneath the closed flat grate on the dry ledge. She may be seen only through the seven slots. Do not place or duplicate any cat above or beside the grate. Mats stops Fien before the grate moves.`,
    60: `\n\nPAGE-SPECIFIC PUK AND COLOR LOCK:\nThe grate stays closed and flat. Puk remains completely below it on the ledge; Fien gently reaches fingers through one slot to touch Puk's orange-and-white head. Fien keeps her canonical full colors: brown hair, pink headband, orange jacket, turquoise tunic, dark leggings, white-and-orange boots. Puk keeps orange-and-white fur. No grayscale, white cat, duplicate cat, or open grate.`,
    77: `\n\nPAGE-SPECIFIC PUK LOCK:\nPuk remains completely beneath the closed flat grate and is visible only through the seven slots. Do not place or duplicate a cat above, on, or beside the grate. Fien reaches toward but does not lift it.`,
    84: `\n\nPAGE-SPECIFIC STUCK-GRATE LOCK:\nThe grate remains fully closed, flat, seated in its frame, and visibly stuck. Fien reaches only two fingers safely through one slot; Puk remains completely below on the dry ledge and sniffs those fingers. Do not lift, tilt, open, remove, or bend the grate. No cat above it.`,
  };
  const grateLock = /grate/i.test(prompt) || tags.includes('grate') || gratePages.has(page.pageNumber)
    ? `\n\nCANONICAL GARDEN GRATE LOCK:\nUse the supplied garden_grate_reference.png as the exact object identity. The same single low horizontal rectangular dark graphite-gray grate must appear: thick raised rectangular outer frame, exactly seven long straight parallel front-to-back slots, exactly four small square silver corner bolts. Preserve this design even when perspective, opening angle, water level, or character interaction changes. The grate may be closed, held, lifted, or released only as the story moment requires. Never replace it with a mesh, cross-grid, square hatch, round drain, thin loose bars, a different slot count, or a raised box.`
    : '';
  return `${prompt}${grateLock}${pageLocks[page.pageNumber] ?? ''}\n\nGENERATION OVERRIDE FOR CLEAN ALPHA PROCESSING:\nGenerate the complete cut-out vignette centered on one absolutely flat, uniform, fully opaque chroma-magenta background (#FF00FF). This chroma field is temporary and will be removed locally. Do not use magenta, hot pink, fuchsia, purple-pink, or near-#FF00FF pixels anywhere inside the characters, props, lighting, reflections, highlights, shadows, outlines, or scene elements. Preserve crisp hard pixel edges. Do not crop any head, hand, foot, antenna, tail, tool, or required prop. No cast shadow outside the vignette. The background must be a single solid color with no texture, gradient, noise, vignette, halo, border, scenery, floor, or checkerboard. Ignore any earlier instruction requesting native transparency; use only the flat #FF00FF chroma background for this generation.`;
}

const result = pages
  .filter((page) => page.pageNumber >= from && page.pageNumber <= to)
  .map((page) => ({
    pageNumber: page.pageNumber,
    illustrationId: page.illustrationId,
    filename: page.illustrationFilename,
    prompt: chromaPrompt(page),
    refs: getReferences(page),
    sourcePath: path.join(sourceDir, page.illustrationFilename.replace(/\.png$/i, '_chroma.png')),
    rgbaPath: path.join(sourceDir, page.illustrationFilename.replace(/\.png$/i, '_rgba.png')),
    finalPath: path.join(finalDir, page.illustrationFilename),
  }));

process.stdout.write(JSON.stringify(result));
