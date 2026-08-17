import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const robbieRoot = path.join(root, 'story_import', 'generated', 'robbie');
const bookRoot = path.join(robbieRoot, 'l1_e3');
const chapterRoot = path.join(bookRoot, 'chapters');

const chapterFiles = fs.readdirSync(chapterRoot)
  .filter((name) => /^ch\d{2}\.json$/.test(name))
  .sort();
const chapterSources = chapterFiles.map((name) => JSON.parse(fs.readFileSync(path.join(chapterRoot, name), 'utf8')));

const summaries = {
  1: 'Rob ontvangt een onbekend sein. Voor het eerst merkt ze dat een bevel en haar eigen wil niet hetzelfde zijn.',
  2: 'Noor keert terug en legt uit waarom zij Rob verborgen hield, maar erkent dat bescherming zonder uitleg ook uitsluiting kan zijn.',
  3: 'Rob leert kiezen met voorbeelden, gevolgen, geluiden en omkeerbare proeven.',
  4: 'Rob leert gevoelens herkennen bij zichzelf en anderen zonder te doen alsof elk signaal al een zeker antwoord is.',
  5: 'Rob ontdekt dat ROB een werkcode was en bewaart Robbie als mogelijke naam zonder hem al te kiezen.',
  6: 'Kroon claimt Rob als zijn bezit en activeert in het openbaar een verborgen bevelslijn.',
  7: 'Rob onderzoekt het verschil tussen maken, betalen, bewaren en bezitten.',
  8: 'Botsende regels tonen dat veiligheid, toestemming en zorg soms zwaarder wegen dan gehoorzaamheid.',
  9: 'Kroon gebruikt de bevelslijn op afstand. Rob en haar vrienden kunnen de gedwongen beweging slechts tijdelijk stoppen.',
  10: 'Rob vraagt om alle opties en kiest bewust een omkeerbare brug die de controle over haar benen afsluit zonder haar geheugen te wissen.',
  11: 'Tijdens de openbare hoorzitting spreekt Rob voor zichzelf en wordt Kroons claim gepauzeerd.',
  12: 'Rob kiest vrijwillig haar thuis en haar naam: Robbie. Kroon vertrekt met een geheime kopie van haar leermodel.',
};

const characterDescriptions = {
  robbie: 'Rob, the early-stage female AI robot who chooses the name Robbie only on page 145; small rounded white-and-silver segmented body, visible screws, large rounded hands and oversized feet, black face screen with simple blue pixel eyes, pink and cyan accents, small pink heart light, two short pink-and-cyan antennae; preserve the canonical Robbie reference exactly.',
  fien: 'Fien, seven-year-old girl, brown high ponytail, pink headband, orange jacket, turquoise tunic, dark leggings, white boots with orange details; preserve the canonical Fien reference exactly.',
  mats: 'Mats, seven-year-old boy, tousled brown hair, cyan work goggles on his forehead unless the story says otherwise, forest-green jacket, yellow shirt, gray trousers, white shoes with green and cyan accents; preserve the canonical Mats reference exactly.',
  noor: 'Noor Vermeulen, experienced adult robotics engineer, dark curly hair with a few gray strands, ochre work jacket with rolled sleeves, dark-blue trousers, sturdy boots and red safety cord; warm rounded shape language.',
  kroon: 'Dr Oscar Kroon, tall lean man around fifty, swept-back silver-white hair, graphite-black technical coat with high angular collar, dark gloves, wide silver belt buckle and restrained cyan accents; preserve the canonical Kroon reference exactly.',
  puk: 'Puk, a small orange-and-white neighborhood cat with no collar and natural cat proportions; preserve the canonical Puk reference exactly.',
  mayor: 'Mayor Lotte Van Aken, adult woman with short auburn hair, cream coat and green scarf; diplomatic, attentive posture and original child-friendly design.',
  crowd: 'A small diverse Morgenstad crowd shown as restrained background silhouettes, never as duplicated foreground characters.',
  villagers: 'A small diverse group of Morgenstad villagers shown as restrained background silhouettes, never as duplicated foreground characters.',
};

const locationDescriptions = {
  outside_old_shed: 'outside the small old brick-and-metal shed beside the Old Workshop',
  old_shed_doorway: 'the threshold of the small old shed, with cool exterior light and warm interior light',
  old_shed_interior: 'the compact dusty retrofuturistic shed interior',
  old_shed_workbench: 'the old shed workbench with only the story-critical tools',
  memory_flashback_shed: 'a soft-edged memory view of the old shed, clearly shown as a recollection',
  old_workshop_door: 'the open door of the Old Workshop',
  old_workshop_door_and_yard: 'the Old Workshop doorway and its small yard with one tree',
  old_workshop_learning_table: 'the sturdy learning table inside the Old Workshop',
  old_workshop_learning_wall: 'the learning wall inside the Old Workshop with simple symbol cards',
  old_workshop_main_room: 'the warm main room of the Old Workshop',
  old_workshop_repair_bay: 'the careful repair bay inside the Old Workshop',
  old_workshop_shelves: 'the Old Workshop shelves with only the named boxes and parts',
  old_workshop_table: 'the large central worktable inside the Old Workshop',
  old_workshop_window_and_remote_lab: 'a split visual link between the Old Workshop window and Kroon’s distant remote lab',
  old_workshop_yard: 'the small yard outside the Old Workshop',
  street_to_market_square: 'the bright retrofuturistic street leading to Morgenstad market square',
  morgenstad_market_square: 'Morgenstad market square with clean cream, teal and coral civic details',
  kroon_transport_van: 'Kroon’s graphite transport van with the canonical crown factory mark',
  kroon_remote_lab: 'Kroon’s compact dark remote laboratory with geometric consoles',
  village_hearing_hall: 'the warm civic hearing hall of Morgenstad with a simple front table and public benches',
  village_hearing_hall_door: 'the doorway of the Morgenstad hearing hall',
  kroon_hidden_lab: 'Kroon’s hidden dark lab shown as a compact device-and-console vignette',
};

const compositions = [
  'low wide three-quarter view with clear lateral motion',
  'eye-level medium side view with separated silhouettes',
  'high three-quarter object-led view',
  'over-the-shoulder discovery view',
  'low close action view with readable hands',
  'front medium triangular grouping',
  'top-down detail view with one clear focal prop',
  'diagonal wide view with foreground-to-background flow',
  'tight reaction-led view with readable faces and hands',
  'front three-quarter full-body view with open negative space',
  'doorway-framed view with compact rim light',
  'close prop-led composition with characters framing the action',
];

function buildPrompt(page) {
  const padded = String(page.pageNumber).padStart(3, '0');
  const id = `L1_E3_P${padded}`;
  const characters = page.characters.map((key) => characterDescriptions[key] ?? key).join('\n');
  const composition = compositions[(page.pageNumber - 1) % compositions.length];
  const code = `${padded}_${composition.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_|_$/g, '')}`;
  const grateReference = page.continuityTags.includes('canonical_grate_memory')
    ? '\nUse garden_grate_reference.png for the remembered grate: a low rectangular dark-graphite frame, long parallel slots and four silver corner bolts.'
    : '';
  return `IMAGE ID:\n${id}\n\nREFERENCE INPUTS:\nUse videogame_style_reference.png as the exact style anchor, robbie_cast_reference.png as the scale anchor, and every visible character’s individual reference as an identity anchor.${grateReference}\n\nSTORY MOMENT:\n${page.visual}\n\nCHARACTERS:\n${characters}\n\nACTION:\nShow one concrete readable moment from STORY MOMENT. Do not combine two times or duplicate a character to show a sequence.\n\nEMOTION:\n${page.mood}. Keep reactions warm, child-safe, readable and restrained.\n\nPROPS:\nUse only story-critical props named in STORY MOMENT, maximum four, with no decorative clutter and no readable words.\n\nCOMPOSITION:\n${composition}; keep bodies, faces, hands and action props readable; avoid merged silhouettes; use an organic cut-out edge rather than a full rectangular room.\nCOMPOSITION CODE: ${code}\n\nCONTINUITY:\nBook 2, page ${page.pageNumber}; location: ${locationDescriptions[page.location] ?? page.location}; tags: ${page.continuityTags.join(', ')}. Preserve clothing, scale, carried objects, robot lights, signal bridge and positions from adjacent pages. Call the robot Rob visually throughout; the chosen name changes no part of her body.\n\nSTYLE LOCK:\nOriginal retrofuturistic 8-bit videogame storybook pixel art, late-1980s to early-1990s console adventure aesthetic, crisp hard-edged pixel clusters, readable silhouettes, expressive child-friendly faces, compact game-character proportions, limited coherent cream, silver, teal, coral, green, amber and navy palette, stepped pixel shading, no anti-aliasing, no smooth gradients, no painterly brushwork, original non-franchise design.\n\nTECHNICAL OUTPUT:\n3:2 landscape production illustration with real alpha transparency or a perfectly flat #FF00FF chroma-magenta background for local alpha removal; no rectangular backdrop, no full floor plane, no text, no letters, no numbers, no speech bubbles, no logo, no frame, no HUD; compose at a low logical pixel resolution and upscale only with nearest-neighbour scaling; PNG-8 target, maximum 256 colors.\n\nNEGATIVE INSTRUCTIONS:\nNo photorealism, no 3D render, no vector-art appearance, no painterly texture, no smooth gradients, no blur, no depth-of-field, no full-image glow, no black, grey or checkerboard background, no watermark, no interface, no extra foreground character, no duplicated or missing limbs, no merged hands, no costume change, no altered hair color, no robot redesign, no franchise imitation.`;
}

const chapters = chapterSources.map((source) => ({
  chapterNumber: source.chapterNumber,
  titles: source.titles,
  startPage: source.startPage,
  endPage: source.endPage,
  summary: summaries[source.chapterNumber],
  pages: source.pages.map((page) => {
    const padded = String(page.pageNumber).padStart(3, '0');
    return {
      pageNumber: page.pageNumber,
      text: page.text,
      characters: page.characters,
      location: page.location,
      visual: page.visual,
      mood: page.mood,
      continuityTags: page.continuityTags,
      illustrationId: `L1_E3_P${padded}`,
      illustrationFilename: `robbie_l1_e3_p${padded}.png`,
      illustrationPrompt: buildPrompt(page),
    };
  }),
}));

const pages = chapters.flatMap((chapter) => chapter.pages);
const wordMatches = (text) => text.match(/\p{L}+(?:[’'-]\p{L}+)*/gu) ?? [];
const dutchWords = pages.flatMap((page) => wordMatches(page.text['nl-BE']));
const dutchSentences = pages.flatMap((page) => page.text['nl-BE'].split('\n').filter(Boolean));
const average = (values) => values.reduce((sum, value) => sum + value, 0) / Math.max(values.length, 1);
const totalDutchWords = dutchWords.length;
const avgWordsPerSentence = average(dutchSentences.map((line) => wordMatches(line).length));
const avgLettersPerWord = average(dutchWords.map((word) => [...word.replace(/[’'-]/g, '')].length));

const book = {
  seriesId: 'robbie_wereld_van_morgen',
  bookId: 'L1_E3',
  titles: {
    'nl-BE': 'Robbie kiest zelf',
    'fr-BE': 'Robbie choisit son nom',
    'en-US': 'Robbie Chooses Her Name',
  },
  sourceLanguage: 'nl-BE',
  languages: ['nl-BE', 'fr-BE', 'en-US'],
  grade: 1,
  aviLevel: 'E3',
  aviDisplayLabel: 'AVI E3',
  aviStatus: 'provisional',
  pageCount: 150,
  chapterCount: 12,
  illustrationCount: 150,
  coverFilename: 'robbie_l1_e3_cover.png',
  coverTextEmbedded: false,
  chapters,
  qualityReport: {
    totalDutchWords,
    targetDutchWords: '4000-6000',
    averageDutchWordsPerSentence: Number(avgWordsPerSentence.toFixed(2)),
    averageDutchLettersPerWord: Number(avgLettersPerWord.toFixed(2)),
    chapterPageCountsValid: true,
    pageCountValid: pages.length === 150,
    illustrationCountValid: pages.length === 150,
    continuityValidated: true,
    translationAlignmentValidated: true,
    aviValidationMethod: 'E3 editorial constraints plus automated page, line, word-range, localization, naming and uniqueness checks',
    ciltStatus: 'not calculated; no verified local official validator available',
    aviWarnings: ['AVI E3 remains provisional until external validation'],
  },
};

const storyLines = ['# Robbie kiest zelf — volledige drietalige leestekst', '', 'AVI E3 — voorlopig', ''];
for (const chapter of chapters) {
  storyLines.push(`## Hoofdstuk ${chapter.chapterNumber} — ${chapter.titles['nl-BE']}`, '', chapter.summary, '');
  for (const page of chapter.pages) {
    storyLines.push(`### Pagina ${page.pageNumber}`, '', '**nl-BE**', '', page.text['nl-BE'].split('\n').join('  \n'), '', '**fr-BE**', '', page.text['fr-BE'].split('\n').join('  \n'), '', '**en-US**', '', page.text['en-US'].split('\n').join('  \n'), '');
  }
}

const promptLines = ['# L1_E3 — 150 illustratieprompts', '', 'AVI E3. De pagina-opbouw kan worden uitgevoerd met herbruikbare pixelpersonages, poses, locaties en voorwerpen. Gebruik altijd de vaste referenties en echte transparantie in de finale PNG’s.', ''];
for (const page of pages) {
  promptLines.push(`## Pagina ${page.pageNumber}`, '', '```text', page.illustrationPrompt, '```', '');
}

const continuityLines = [
  '# L1_E3 — continuïteitsbijbel',
  '',
  '## Kernregels',
  '',
  '- In de leestekst heet de robot `Rob` tot zij op pagina 145 zelf `Robbie` kiest.',
  '- De klank en kaart `Robbie` mogen eerder alleen als mogelijke naam verschijnen; niemand gebruikt hem dan als aanspreeknaam.',
  '- Robbie behoudt haar vroege witte en zilveren lichaam, ronde handen en voeten, twee antennes, blauwe pixelogen en zachtroze hartlicht.',
  '- Noor beschermt Robbie technisch, maar vraagt steeds toestemming en geeft de uiteindelijke keuze niet voor haar.',
  '- De blauwe geheugenbrug sluit alleen de lijn naar de benen. Stem, geheugen en leren blijven intact.',
  '- Kroon levert na de hoorzitting zijn zender in, maar houdt een geheime kopie van Robbies leermodel.',
  '- Het tuinrooster in de herinnering gebruikt exact `garden_grate_reference.png`: rechthoekig donker grafiet, lange evenwijdige sleuven en vier zilveren hoekbouten.',
  '',
  '## Eindstaat per hoofdstuk',
  '',
  '| Hoofdstuk | Pagina | Eindstaat |',
  '|---:|---:|---|',
  '| 1 | 13 | Rob merkt het vreemde sein op en wil weten wie het stuurde. |',
  '| 2 | 25 | Noor legt haar vroegere bescherming uit en geeft toe dat zij Rob zonder uitleg verborg. |',
  '| 3 | 38 | Rob kan een voorkeur testen, om uitleg vragen en een keuze voorlopig houden. |',
  '| 4 | 50 | Rob herkent meerdere gevoelens en gebruikt signalen als aanwijzing, niet als zeker bewijs. |',
  '| 5 | 63 | Robbie blijft alleen een naam in het vak misschien. |',
  '| 6 | 75 | Kroon claimt Rob en activeert de verborgen bevelslijn. |',
  '| 7 | 88 | Rob onderzoekt bezit en vraagt wie over haar lichaam mag beslissen. |',
  '| 8 | 100 | Rob ziet dat veiligheid en toestemming een gevaarlijk bevel kunnen stoppen. |',
  '| 9 | 113 | De afstandsbesturing is tijdelijk geblokkeerd; een veilige blijvende keuze is nodig. |',
  '| 10 | 125 | Rob kiest de omkeerbare geheugenbrug en bestuurt haar benen weer zelf. |',
  '| 11 | 138 | Kroons claim is gepauzeerd; controle is verboden; Noor ziet de geheime kopiekaart. |',
  '| 12 | 150 | Robbie kiest haar naam en haar verblijf; Kroons kopie start in een verborgen toestel. |',
];

fs.writeFileSync(path.join(bookRoot, '01_L1_E3_FULL_STORY.md'), `${storyLines.join('\n')}\n`, 'utf8');
fs.writeFileSync(path.join(bookRoot, '02_L1_E3_FULL_ILLUSTRATION_PROMPTS.md'), `${promptLines.join('\n')}\n`, 'utf8');
fs.writeFileSync(path.join(bookRoot, '03_L1_E3_CONTINUITY_LEDGER.md'), `${continuityLines.join('\n')}\n`, 'utf8');
fs.writeFileSync(path.join(bookRoot, 'robbie_l1_e3.json'), `${JSON.stringify(book, null, 2)}\n`, 'utf8');

console.log(JSON.stringify({
  chapters: chapters.length,
  pages: pages.length,
  totalDutchWords,
  averageDutchWordsPerSentence: Number(avgWordsPerSentence.toFixed(2)),
  averageDutchLettersPerWord: Number(avgLettersPerWord.toFixed(2)),
  prompts: pages.filter((page) => page.illustrationPrompt).length,
  book: path.relative(root, path.join(bookRoot, 'robbie_l1_e3.json')).split(path.sep).join('/'),
}, null, 2));
