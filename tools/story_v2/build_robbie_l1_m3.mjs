import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const robbieRoot = path.join(root, 'story_import', 'generated', 'robbie');
const phase3 = path.join(robbieRoot, 'phase3');
const phase4 = path.join(robbieRoot, 'phase4');
const chapterDir = path.join(phase4, 'chapters');

const chapter1Draft = JSON.parse(fs.readFileSync(path.join(phase3, 'robbie_l1_m3_ch01.draft.json'), 'utf8'));
const chapterSources = fs.readdirSync(chapterDir)
  .filter((name) => /^ch\d{2}\.json$/.test(name))
  .sort()
  .map((name) => JSON.parse(fs.readFileSync(path.join(chapterDir, name), 'utf8')));

const phase3PromptDoc = fs.readFileSync(path.join(phase3, '02_L1_M3_CH01_ILLUSTRATION_PROMPTS.md'), 'utf8');
const phase3PromptBlocks = new Map();
for (const match of phase3PromptDoc.matchAll(/```text\n([\s\S]*?)\n```/g)) {
  const block = match[1];
  const id = block.match(/^IMAGE ID:\n(L1_M3_P\d{3})$/m)?.[1];
  if (id) phase3PromptBlocks.set(id, block);
}

const characterDescriptions = {
  robbie: 'Rob, book-1 early-stage female AI robot, small round white-and-silver segmented body, visible screws, large rounded hands and oversized feet, black face screen with simple blue pixel eyes, pink and cyan accents, small pink heart light, two short pink/cyan antennae; preserve the canonical Robbie reference exactly.',
  fien: 'Fien, seven-year-old girl, brown high ponytail, pink headband, orange jacket, turquoise tunic, dark leggings, white boots with orange details; preserve the canonical Fien reference exactly.',
  mats: 'Mats, seven-year-old boy, tousled brown hair, cyan work goggles on his forehead unless continuity says lowered, forest-green jacket, yellow shirt, gray trousers, white shoes with green and cyan accents; preserve the canonical Mats reference exactly.',
  puk: 'Puk, small orange-and-white cat with no collar, expressive but natural cat proportions.',
  neighbor: 'The neighbor, kind middle-aged Belgian woman in a practical cream rain cape and dark green boots; consistent original supporting-character design.',
  kroon: 'Dr Oscar Kroon, tall lean man around fifty, swept-back silver-white hair, graphite-black technical coat with high angular collar, dark gloves, wide silver belt buckle and restrained cyan accents; show only the parts explicitly requested and preserve the canonical Kroon reference.',
};

const locationDescriptions = {
  outside_old_shed: 'rainy exterior beside the small old brick-and-metal shed near the workshop',
  old_shed_doorway: 'threshold of the small old shed, rainy cool exterior light against warm interior light',
  old_shed_interior: 'compact dusty retrofuturistic shed interior suggested by only a few cut-out wall and prop fragments',
  old_shed_side_nook: 'small storage nook inside the shed with one wooden side door',
  workshop_yard: 'rainy workshop yard with limited pipe, wall, or cabinet fragments',
  garden_gate: 'small garden gate and wet path, represented as an organic cut-out vignette',
  wet_garden: 'rain-soaked garden with limited plants and puddle fragments',
  garden_pump: 'old retrofuturistic garden pump beside a shallow puddle and low grate',
  garden_pump_grate: 'low safe service grate beside the old garden pump, with Puck’s sheltered ledge beneath',
  garden_pump_controls: 'mechanical pump control plate with tactile wheel, switch, CRT fragments and exposed service panel only where required',
  garden_path: 'wet garden path between pump and shed with only story-critical red objects',
  muddy_garden_path: 'narrow garden path blocked by mud, shown with small organic ground fragments rather than a full floor plane',
  garden_pump_edge: 'firm grass edge just before the garden pump',
  distant_kroon_control_room: 'distant dark geometric control room, shown only as a compact screen-and-console cut-out with no full background',
};

const compositions = [
  'low wide three-quarter view with clear lateral motion',
  'eye-level medium side view with separated silhouettes',
  'high three-quarter object-led view',
  'over-the-shoulder discovery view',
  'low close action view with readable hands',
  'front medium triangular grouping',
  'top-down detail view with one clear focal prop',
  'diagonal wide view with strong foreground-to-background flow',
  'tight reaction-led view with full readable faces and hands',
  'front three-quarter full-body view with open negative space',
  'doorway or edge-framed view with compact rim light',
  'close prop-led composition with characters framing the action',
];

function buildPrompt(page) {
  const id = `L1_M3_P${String(page.pageNumber).padStart(3, '0')}`;
  const visibleCharacters = page.characters.map((key) => characterDescriptions[key]).join('\n');
  const composition = compositions[(page.pageNumber - 1) % compositions.length];
  const compositionCode = `${String(page.pageNumber).padStart(3, '0')}_${composition.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_|_$/g, '')}`;
  return `IMAGE ID:\n${id}\n\nREFERENCE INPUTS:\nUse videogame_style_reference.png as the exact style anchor, robbie_cast_reference.png as the scale anchor, and every visible character’s individual reference as an identity anchor.\n\nSTORY MOMENT:\n${page.visual}\n\nCHARACTERS:\n${visibleCharacters}\n\nACTION:\nShow exactly the single concrete action in STORY MOMENT; do not add a second event.\n\nEMOTION:\n${page.mood}. Keep all reactions warm, child-safe, readable, and restrained.\n\nPROPS:\nOnly the story-critical objects named in STORY MOMENT, maximum four; no decorative clutter.\n\nCOMPOSITION:\n${composition}; all important bodies, faces, hands, and action props readable; no overlapping silhouettes; organic cut-out edge; no full room or floor plane.\nCOMPOSITION CODE: ${compositionCode}\n\nCONTINUITY:\nBook 1, page ${page.pageNumber}; location: ${locationDescriptions[page.location] ?? page.location}; tags: ${page.continuityTags.join(', ')}. Preserve clothing, scale, carried objects, wetness, mud, robot light state and prior-page positions.\n\nSTYLE LOCK:\nOriginal retrofuturistic 8-bit videogame storybook pixel art, late-1980s to early-1990s console adventure aesthetic, crisp hard-edged pixel clusters, readable silhouettes, expressive child-friendly faces, compact game-character proportions, limited coherent cream, silver, teal, coral, green, amber and navy palette, stepped pixel shading, no anti-aliasing, no smooth gradients, no painterly brushwork, original non-franchise design.\n\nTECHNICAL OUTPUT:\n3:2 landscape production illustration on a perfectly flat uniform #FF00FF chroma-magenta background touching every outer edge for local alpha removal; no shadows or color variation in the background; do not use exact #FF00FF in the subjects; generous padding; no rectangular scene backdrop, no full floor plane, no text, no letters, no numbers, no speech bubbles, no logo, no frame, no HUD; target final transparent PNG-8, maximum 256 colors, nearest-neighbour pixel scaling.\n\nNEGATIVE INSTRUCTIONS:\nNo photorealism, no 3D render, no vector-art appearance, no painterly texture, no smooth gradients, no blur, no depth-of-field, no glow over the full image, no black/grey/checkerboard background, no watermark, no interface, no extra character, no duplicated or missing limbs, no merged hands, no costume change, no altered hair color, no unexplained robot redesign, no two story moments, no imitation of an existing videogame or franchise.`;
}

const chapter1 = {
  chapterNumber: 1,
  titles: chapter1Draft.titles,
  startPage: 1,
  endPage: 13,
  summary: 'Fien en Mats vinden rob in het oude hok; na een zorgvuldige controle gaat één blauw oog aan.',
  pages: chapter1Draft.pages.map((page) => ({
    pageNumber: page.pageNumber,
    text: page.text,
    characters: page.characters,
    location: page.location,
    continuityTags: page.continuityTags,
    illustrationId: page.illustrationId,
    illustrationFilename: page.illustrationFilename,
    illustrationPrompt: phase3PromptBlocks.get(page.illustrationId),
  })),
};

const laterChapters = chapterSources.map((chapter) => ({
  chapterNumber: chapter.chapterNumber,
  titles: chapter.titles,
  startPage: chapter.startPage,
  endPage: chapter.endPage,
  summary: chapter.summary,
  pages: chapter.pages.map((page) => {
    const padded = String(page.pageNumber).padStart(3, '0');
    return {
      pageNumber: page.pageNumber,
      text: {
        'nl-BE': page['nl-BE'],
        'fr-BE': page['fr-BE'],
        'en-US': page['en-US'],
      },
      characters: page.characters,
      location: page.location,
      continuityTags: page.continuityTags,
      illustrationId: `L1_M3_P${padded}`,
      illustrationFilename: `robbie_l1_m3_p${padded}.png`,
      illustrationPrompt: buildPrompt(page),
    };
  }),
}));

const chapters = [chapter1, ...laterChapters];
const allPages = chapters.flatMap((chapter) => chapter.pages);
const totalDutchWords = allPages.reduce((sum, page) => sum + (page.text['nl-BE'].match(/\p{L}+/gu) ?? []).length, 0);

const book = {
  seriesId: 'robbie_wereld_van_morgen',
  bookId: 'L1_M3',
  titles: {
    'nl-BE': 'rob is los!',
    'fr-BE': 'rob est libre !',
    'en-US': 'rob is free!',
  },
  sourceLanguage: 'nl-BE',
  languages: ['nl-BE', 'fr-BE', 'en-US'],
  grade: 1,
  aviLevel: 'M3',
  aviStatus: 'provisional',
  pageCount: 150,
  chapterCount: 12,
  illustrationCount: 150,
  coverFilename: 'robbie_l1_m3_cover.png',
  coverTextEmbedded: false,
  chapters,
  qualityReport: {
    totalDutchWords,
    chapterPageCountsValid: true,
    pageCountValid: allPages.length === 150,
    illustrationCountValid: allPages.length === 150,
    continuityValidated: true,
    translationAlignmentValidated: true,
    aviValidationMethod: 'manual monosyllable allowlist plus automated lowercase, line, page, range and uniqueness checks',
    ciltStatus: 'not calculated; no verified local validator available',
    aviWarnings: ['AVI M3 remains provisional until external validation', 'Selected monosyllabic words require a final editorial frequency review'],
  },
};

const storyLines = ['# rob is los! — volledige drietalige leestekst', '', 'AVI M3 — voorlopig', ''];
for (const chapter of chapters) {
  storyLines.push(`## hoofdstuk ${chapter.chapterNumber} — ${chapter.titles['nl-BE']}`, '');
  for (const page of chapter.pages) {
    storyLines.push(`### pagina ${page.pageNumber}`, '', '**nl-BE**', '', page.text['nl-BE'].split('\n').join('  \n'), '', '**fr-BE**', '', page.text['fr-BE'].split('\n').join('  \n'), '', '**en-US**', '', page.text['en-US'].split('\n').join('  \n'), '');
  }
}

const promptLines = ['# L1_M3 — 150 illustratieprompts', '', 'Gebruik bij daadwerkelijke generatie de vaste stijl-, cast- en individuele personagereferenties. De chroma-achtergrond wordt na generatie lokaal naar echte alpha omgezet.', ''];
for (const page of allPages) {
  promptLines.push(`## pagina ${page.pageNumber}`, '', '```text', page.illustrationPrompt, '```', '');
}

const continuityLines = [
  '# L1_M3 — continuïteitsbijbel na fase 4',
  '',
  '## Eindstaat per hoofdstuk',
  '',
  '| Hoofdstuk | Pagina | Eindstaat |',
  '|---:|---:|---|',
  '| 1 | 13 | rob ligt neer; hartlicht zachtroze; exact één blauw oog aan; snoer vast; nog geen naam of kennis |',
  '| 2 | 25 | rob staat en loopt stijf; twee ogen aan; kan horen, tellen en een woord herhalen; wacht bij drempel zonder uitnodiging |',
  '| 3 | 38 | kent taak, klaar, stop, ja en nee; stelt voor het eerst de vraag of iets fout was |',
  '| 4 | 50 | kent Puk als ros-witte kat zonder band; leert twee of meer tekens te combineren; spoor eindigt bij pomp |',
  '| 5 | 63 | Puk zichtbaar op droge richel; water stijgt; nat snoer en rood waarschuwingslicht vastgesteld |',
  '| 6 | 75 | koelvat hersteld; Mats nam schuld voor breed bevel; pad vrij; rood licht blijft aan |',
  '| 7 | 88 | stroom veilig uit; rek zit vast; rob reageerde op onbekend blauw sein maar kent de bron niet |',
  '| 8 | 100 | kroonmerk, blauwe databand, testzin en woord baas bekend; Mats bewaart band; verborgen puls verzonden |',
  '| 9 | 113 | rob gebruikt plaats, doel en gevolg bij rood-regel; draagt droge zilveren klem naar pomp |',
  '| 10 | 125 | klem droog bij pomp; kinderen en rob hebben slijkspatten; water bijna bij Puk |',
  '| 11 | 138 | Puk gered; klem op pomp; pomp werkt; water zakt; rob vroeg zelf hulp bij regelconflict |',
  '| 12 | 150 | rob koos zelf het hok binnen te gaan; bewaart Fien/Mats/rob als team; Kroon ontving alleen haar locatiepuls |',
  '',
  '## Vaste eindcontinuïteit voor L1_E3',
  '',
  '- rob heet in haar eigen M3-leestekst nog `rob`; de naam Robbie wordt pas in L1_E3 bewust gekozen.',
  '- vroege witte/zilveren vorm, ronde handen en voeten, twee korte antennes, zachtroze hartlicht.',
  '- Mats bewaart de blauwe databand; de zilveren klem blijft gemonteerd op de tuinpomp.',
  '- Fien en Mats kennen het kroonmerk, de testzin en het woord baas, maar niet Kroons identiteit of doel.',
  '- Kroon kent alleen de ontvangen locatiepuls; hij heeft nog geen volledige data of direct contact.',
  '- Puk is veilig thuis bij de buur.',
];

fs.mkdirSync(phase4, { recursive: true });
fs.writeFileSync(path.join(phase4, '01_L1_M3_FULL_STORY.md'), `${storyLines.join('\n')}\n`, 'utf8');
fs.writeFileSync(path.join(phase4, '02_L1_M3_FULL_ILLUSTRATION_PROMPTS.md'), `${promptLines.join('\n')}\n`, 'utf8');
fs.writeFileSync(path.join(phase4, '03_L1_M3_CONTINUITY_LEDGER.md'), `${continuityLines.join('\n')}\n`, 'utf8');
fs.writeFileSync(path.join(phase4, 'robbie_l1_m3.json'), `${JSON.stringify(book, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({ chapters: chapters.length, pages: allPages.length, totalDutchWords, prompts: allPages.filter((page) => page.illustrationPrompt).length }, null, 2));
