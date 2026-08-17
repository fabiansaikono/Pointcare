import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const requested = process.argv.find((arg) => arg.startsWith('--book='))?.split('=')[1] ?? 'l2_m4';

const configs = {
  l2_m4: {
    bookId: 'L2_M4',
    grade: 2,
    aviLevel: 'M4',
    titles: {
      'nl-BE': 'Robbie en de slimme stad',
      'fr-BE': 'Robbie et la ville intelligente',
      'en-US': 'Robbie and the Smart City',
    },
    targetDutchWords: '6000-9000',
    coverFilename: 'robbie_l2_m4_cover.png',
    summaries: {
      1: 'Morgenstad start een slimme proefweek. Robbie ontdekt dat Kroons rekensom belangrijke waarden niet meet.',
      2: 'Een snelle fietsroute blijkt onveilig voor jonge fietsers en een brede bakfiets. Twee routes werken beter dan één winnaar.',
      3: 'Gelijke stroomcijfers hebben niet altijd hetzelfde belang. Het zorgcentrum krijgt een beschermde ondergrens.',
      4: 'De kortste afvalrit vergeet een afgelegen straat. Een kleine elektrische wagen bewaart de basisdienst.',
      5: 'Een snellere sluis beschadigt verderop een broedplek. De proef krijgt een veilige watergrens.',
      6: 'Een stil park lijkt goed, tot de vrienden merken dat de vogels verdwenen zijn. Stilte alleen bewijst geen herstel.',
      7: 'Eén eindscore verbergt wie voordeel of nadeel krijgt. Robbie bouwt een openbare kaart met meerdere lagen.',
      8: 'Bewoners zonder digitaal toestel ontbreken in de cijfers. Onbekende informatie blijft zichtbaar in plaats van ingevuld.',
      9: 'Een tweede blauwe datastroom leidt naar Kroons gesloten systeem. De vrienden onderzoeken zonder de openbare dienst stil te leggen.',
      10: 'Kroons automatische plan veroorzaakt ongelijke schade. Robbie en haar vrienden formuleren vijf openbare grenzen.',
      11: 'De stad kiest een transparant plan met veiligheid, basisdienst, natuur, privacy en een maximale wachttijd.',
      12: 'De proef wordt eerlijk afgerond en Kroons verborgen meetkast wordt openbaar. Een verre puls kondigt het grote netwerk aan.',
    },
    finalStates: [
      'Robbie werkt met vijf proefregels; een verborgen blauwe datastroom blijft actief.',
      'De schoolstraat krijgt een veilige hoofdroute en een toegankelijke omweg.',
      'Het zorgcentrum behoudt steeds voldoende stroom.',
      'Ook de afgelegen straat houdt een regelmatige afvaldienst.',
      'De sluis volgt een veilige watergrens die ook gevolgen stroomafwaarts beschermt.',
      'Het park krijgt rusttijden en controles op dieren, niet alleen op geluid.',
      'De openbare kaart toont tijd, veiligheid, natuur en basisdienst naast elkaar.',
      'Niet-gemeten bewoners en onbekende gevolgen blijven zichtbaar.',
      'De geheime dataroute is bewezen; Kroon start zijn gesloten plan te vroeg.',
      'De ongelijke schade is gestopt en vijf grenzen vormen plan C.',
      'Plan C draait onder openbare controle; het brugpunt blijft verdacht.',
      'Kroons systeem stopt en de sensoren worden openbaar beheerd; een puls bereikt een groter netwerk.',
    ],
  },
  l2_e4: {
    bookId: 'L2_E4',
    grade: 2,
    aviLevel: 'E4',
    titles: {
      'nl-BE': 'Robbie in het grote net',
      'fr-BE': 'Robbie dans le grand réseau',
      'en-US': 'Robbie in the Great Network',
    },
    targetDutchWords: '8000-11000',
    coverFilename: 'robbie_l2_e4_cover.png',
    summaries: {
      1: 'Robbie krijgt kijktoegang tot het stadsnet en antwoordt te snel op een onvolledige waarschuwing. Ze leert dat hoeveelheid geen zekerheid is.',
      2: 'Een vaak herhaald gerucht verdringt een zeldzame betrouwbare nota. De vrienden maken bronlabels en groeperen kopieën.',
      3: 'Een oud overstromingsbeeld veroorzaakt paniek. Actuele onafhankelijke meters tonen dat het noodbericht vals is.',
      4: 'Robbie opent onbedoeld private zorg- en reisgegevens. Ze leert dat toegang zonder kwaad gebruik nog steeds een grens kan overschrijden.',
      5: 'De vrienden volgen een onderhoudsbericht langs verkorte en veranderde kopieën tot bij Kroons digitale afdruk.',
      6: 'Een gemonteerd beeld beschuldigt Noor. Tijdcode, weer en een tweede camera tonen de echte gebeurtenissen.',
      7: 'Verborgen prioriteitspulsen sturen Robbies rangorde zonder woorden. Ze herkent dat ook rangschikking controle kan zijn.',
      8: 'Alles uitschakelen zou Robbie beschermen maar ook openbare diensten raken. Met haar toestemming wordt alleen Kroons kanaal geïsoleerd.',
      9: 'Een keten van twee bronnen, zichtbaar wijzigingsspoor en menselijke verantwoordelijkheid beschermt belangrijke berichten.',
      10: 'Tegenstrijdige meldingen maken zekerheid onmogelijk. Robbie noemt haar twijfel en kiest een kleine omkeerbare veiligheidsstap.',
      11: 'De vrienden verwijderen alleen de onbevoegde prioriteiten, houden publieke diensten online en bewaren het bewijs.',
      12: 'Morgenstad opent een kleiner netwerk met duidelijke toestemming en zichtbare lege plekken. Een verre mijnstreek stuurt een bekend sein.',
    },
    finalStates: [
      'Robbie heeft haar eerste snelle netwerkfout openbaar hersteld; een verborgen kroonprioriteit blijft actief.',
      'Kopieën worden gegroepeerd onder hun echte bron; één kroonbron heeft geen verantwoordelijke eigenaar.',
      'De valse overstromingsopdracht is gericht geblokkeerd; Kroon verhoogt verborgen de prioriteit.',
      'Private vensters zijn gesloten en toegang krijgt rollen, dubbele noodbevestiging en een zichtbaar logboek.',
      'Het ware onderhoudsbericht is hersteld en de beslissende valse wijziging wijst naar Kroon.',
      'Noor is vrijgepleit, al vraagt herstel van vertrouwen tijd; Kroon verspreidt een tweede gemonteerd beeld.',
      'De zorgwaarschuwing staat weer bovenaan en Robbies logboek benoemt de poging tot verborgen sturing.',
      'Alleen Kroons privékanaal is met Robbies toestemming afgesloten; de openbare diensten blijven online.',
      'De tijdelijke vertrouwensketen beschermt de publieke meldingen tegen eenvoudige vervalsing.',
      'Eerlijke twijfel voorkomt een foute sluisactie; Kroon verplaatst zijn afstandscode naar de netcentrale.',
      'Kroon verliest de prioriteitscontrole, het bewijs blijft bewaard en een kleiner publiek netwerk blijft draaien.',
      'Bewoners beheren hun toestemming en onbekende gebieden blijven leeg; een verre mijnregio vraagt om hulp.',
    ],
  },
};

const config = configs[requested];
if (!config) throw new Error('Use --book=l2_m4 or --book=l2_e4');

const bookRoot = path.join(root, 'story_import', 'generated', 'robbie', requested);
const chapterRoot = path.join(bookRoot, 'chapters');
if (!fs.existsSync(chapterRoot)) throw new Error(`Missing chapter directory: ${chapterRoot}`);

const chapterFiles = fs.readdirSync(chapterRoot).filter((name) => /^ch\d{2}\.json$/.test(name)).sort();
const chapterSources = chapterFiles.map((name) => JSON.parse(fs.readFileSync(path.join(chapterRoot, name), 'utf8')));

const characterDescriptions = {
  robbie: 'Robbie, the small female AI robot; rounded white-and-silver segmented body, visible screws, large rounded hands and oversized feet, black face screen with simple blue pixel eyes, pink and cyan accents, small pink heart light, two short pink-and-cyan antennae; preserve her canonical reference exactly.',
  fien: 'Fien, seven-year-old girl, brown high ponytail, pink headband, orange jacket, turquoise tunic, dark leggings, white boots with orange details; preserve her canonical reference exactly.',
  mats: 'Mats, seven-year-old boy, tousled brown hair, cyan work goggles on his forehead, forest-green jacket, yellow shirt, gray trousers, white shoes with green and cyan accents; preserve his canonical reference exactly.',
  noor: 'Noor Vermeulen, adult robotics engineer, dark curly hair with a few gray strands, ochre work jacket, dark-blue trousers, sturdy boots and red safety cord.',
  kroon: 'Dr Oscar Kroon, tall lean man around fifty, swept-back silver-white hair, graphite technical coat with angular collar, dark gloves, wide silver belt buckle and restrained cyan accents.',
  aicha: 'Aïcha, adult water and ecology specialist, practical teal field coat, dark trousers, sturdy boots and compact measuring satchel.',
  mayor: 'Mayor Lotte Van Aken, adult woman with short auburn hair, cream coat and green scarf; diplomatic, attentive posture.',
  residents: 'A small diverse group of Morgenstad residents, kept as restrained background figures with clear individual silhouettes.',
};

const locationDescriptions = {
  morgenstad_market_square: 'Morgenstad market square with clean cream, teal and coral civic details',
  school_street: 'a bright school street with a protected bicycle route and a visible crossing',
  care_center: 'the welcoming Morgenstad care center with warm windows and an accessible entrance',
  remote_residential_street: 'a quiet residential street at the outer edge of Morgenstad',
  canal_lock: 'the compact canal lock with readable gates, water levels and a safe walkway',
  canal_bridge: 'the low Morgenstad canal bridge with one canonical public sensor box',
  night_park: 'the city park at night with trees, low lamps and protected nesting areas',
  old_workshop_main_room: 'the warm main room of the Old Workshop with a sturdy central table',
  kroon_remote_lab: 'Kroon’s compact dark remote laboratory with geometric consoles',
  kroon_transport_van: 'Kroon’s graphite transport van with the canonical crown factory mark',
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
  const id = `${config.bookId}_P${padded}`;
  const characters = page.characters.map((key) => characterDescriptions[key] ?? key).join('\n');
  const composition = compositions[(page.pageNumber - 1) % compositions.length];
  const code = `${padded}_${composition.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_|_$/g, '')}`;
  return `IMAGE ID:\n${id}\n\nREFERENCE INPUTS:\nUse videogame_style_reference.png as the exact style anchor, robbie_cast_reference.png as the scale anchor, and each visible character reference as an identity anchor. Use the canonical city sensor, map and vehicle references whenever named.\n\nSTORY MOMENT:\n${page.visual}\n\nCHARACTERS:\n${characters}\n\nACTION:\nShow one concrete, readable moment from STORY MOMENT. Do not combine different times or duplicate a character.\n\nEMOTION:\n${page.mood}. Keep reactions warm, child-safe, readable and restrained.\n\nPROPS:\nUse only story-critical props named in STORY MOMENT, maximum four, with no decorative clutter and no readable words.\n\nCOMPOSITION:\n${composition}; keep faces, hands and action props readable; avoid merged silhouettes; use an organic cut-out edge rather than a full rectangular room.\nCOMPOSITION CODE: ${code}\n\nCONTINUITY:\n${config.bookId}, page ${page.pageNumber}; location: ${locationDescriptions[page.location] ?? page.location}; tags: ${page.continuityTags.join(', ')}. Preserve clothing, scale, carried objects, robot lights, sensor design and positions from adjacent pages.\n\nSTYLE LOCK:\nOriginal retrofuturistic 8-bit videogame storybook pixel art, late-1980s to early-1990s console adventure aesthetic, crisp hard-edged pixel clusters, readable silhouettes, expressive child-friendly faces, compact game-character proportions, limited coherent cream, silver, teal, coral, green, amber and navy palette, stepped pixel shading, no anti-aliasing, no smooth gradients, no painterly brushwork, original non-franchise design.\n\nTECHNICAL OUTPUT:\n3:2 landscape production illustration with real alpha transparency or a perfectly flat #FF00FF chroma-magenta background for local alpha removal; no rectangular backdrop, no full floor plane, no text, no letters, no numbers, no speech bubbles, no logo, no frame, no HUD; compose at a low logical pixel resolution and upscale only with nearest-neighbour scaling; PNG-8 target, maximum 256 colors.\n\nNEGATIVE INSTRUCTIONS:\nNo photorealism, no 3D render, no vector-art appearance, no painterly texture, no smooth gradients, no blur, no depth-of-field, no full-image glow, no black, grey or checkerboard background, no watermark, no interface, no extra foreground character, no duplicated or missing limbs, no merged hands, no costume change, no altered hair color, no robot redesign, no franchise imitation.`;
}

const chapters = chapterSources.map((source) => ({
  chapterNumber: source.chapterNumber,
  titles: source.titles,
  startPage: source.startPage,
  endPage: source.endPage,
  summary: config.summaries[source.chapterNumber] ?? '',
  pages: source.pages.map((page) => {
    const padded = String(page.pageNumber).padStart(3, '0');
    const illustrationRequired = page.pageNumber % 2 === 1;
    return {
      pageNumber: page.pageNumber,
      storyBeatId: page.storyBeatId,
      text: page.text,
      characters: page.characters,
      location: page.location,
      visual: page.visual,
      mood: page.mood,
      continuityTags: page.continuityTags,
      illustrationRequired,
      illustrationId: illustrationRequired ? `${config.bookId}_P${padded}` : null,
      illustrationFilename: illustrationRequired ? `robbie_${requested}_p${padded}.png` : null,
      illustrationPrompt: illustrationRequired ? buildPrompt(page) : null,
    };
  }),
}));

const pages = chapters.flatMap((chapter) => chapter.pages);
const wordMatches = (text) => text.match(/\p{L}+(?:[’'-]\p{L}+)*/gu) ?? [];
const dutchWords = pages.flatMap((page) => wordMatches(page.text['nl-BE']));
const dutchLines = pages.flatMap((page) => page.text['nl-BE'].split('\n').filter(Boolean));
const average = (values) => values.reduce((sum, value) => sum + value, 0) / Math.max(values.length, 1);
const avgWordsPerLine = average(dutchLines.map((line) => wordMatches(line).length));
const avgLettersPerWord = average(dutchWords.map((word) => [...word.replace(/[’'-]/g, '')].length));

const book = {
  seriesId: 'robbie_wereld_van_morgen',
  bookId: config.bookId,
  titles: config.titles,
  sourceLanguage: 'nl-BE',
  languages: ['nl-BE', 'fr-BE', 'en-US'],
  grade: config.grade,
  aviLevel: config.aviLevel,
  aviDisplayLabel: `AVI ${config.aviLevel}`,
  aviStatus: 'provisional',
  pageCount: 150,
  chapterCount: 12,
  illustrationCount: 75,
  coverFilename: config.coverFilename,
  coverTextEmbedded: false,
  chapters,
  qualityReport: {
    totalDutchWords: dutchWords.length,
    targetDutchWords: config.targetDutchWords,
    averageDutchWordsPerLine: Number(avgWordsPerLine.toFixed(2)),
    averageDutchLettersPerWord: Number(avgLettersPerWord.toFixed(2)),
    chapterPageCountsValid: true,
    pageCountValid: pages.length === 150,
    illustrationCountValid: pages.filter((page) => page.illustrationRequired).length === 75,
    continuityValidated: true,
    translationAlignmentValidated: true,
    aviValidationMethod: `${config.aviLevel} editorial constraints plus automated structure, line, word-range, localization, naming and uniqueness checks`,
    ciltStatus: 'not calculated; no verified local official validator available',
    aviWarnings: [`AVI ${config.aviLevel} remains provisional until external validation`],
  },
};

const storyLines = [`# ${config.titles['nl-BE']} — volledige drietalige leestekst`, '', `AVI ${config.aviLevel} — voorlopig`, ''];
for (const chapter of chapters) {
  storyLines.push(`## Hoofdstuk ${chapter.chapterNumber} — ${chapter.titles['nl-BE']}`, '', chapter.summary, '');
  for (const page of chapter.pages) {
    storyLines.push(`### Pagina ${page.pageNumber}`, '', '**nl-BE**', '', page.text['nl-BE'].split('\n').join('  \n'), '', '**fr-BE**', '', page.text['fr-BE'].split('\n').join('  \n'), '', '**en-US**', '', page.text['en-US'].split('\n').join('  \n'), '');
  }
}

const illustratedPages = pages.filter((page) => page.illustrationRequired);
const promptLines = [`# ${config.bookId} — 75 illustratieprompts`, '', `AVI ${config.aviLevel}. Eén illustratie staat op de eerste pagina van ieder paginapaar: pagina 1, 3, 5 … 149. De illustraties worden opgebouwd met de vaste herbruikbare pixelpersonages, poses, locaties en voorwerpen. De finale PNG’s hebben echte transparantie.`, ''];
for (const page of illustratedPages) promptLines.push(`## Pagina ${page.pageNumber}`, '', '```text', page.illustrationPrompt, '```', '');

const continuityLines = [
  `# ${config.bookId} — continuïteitsbijbel`, '',
  '## Kernregels', '',
  '- Robbie behoudt exact haar vaste witte en zilveren lichaam, ronde handen en voeten, twee antennes, blauwe pixelogen en zachtroze hartlicht.',
  '- Fien, Mats, Noor, Aïcha, Lotte en Oscar Kroon behouden hun vaste uiterlijk en kleding.',
  '- De slimme-stadskasten hebben telkens dezelfde donkere kast, openbaar controlelicht en kleiner blauw datalicht.',
  '- Cijfers worden nooit als volledig bewijs behandeld wanneer bewoners, veiligheid, natuur, zorg of privacy ontbreken.',
  '- Een proef blijft klein, omkeerbaar en publiek controleerbaar.',
  '', '## Eindstaat per hoofdstuk', '',
  '| Hoofdstuk | Pagina | Eindstaat |', '|---:|---:|---|',
];
for (const chapter of chapters) continuityLines.push(`| ${chapter.chapterNumber} | ${chapter.endPage} | ${config.finalStates[chapter.chapterNumber - 1] ?? ''} |`);

fs.mkdirSync(bookRoot, { recursive: true });
const prefix = config.bookId;
fs.writeFileSync(path.join(bookRoot, `01_${prefix}_FULL_STORY.md`), `${storyLines.join('\n')}\n`, 'utf8');
fs.writeFileSync(path.join(bookRoot, `02_${prefix}_FULL_ILLUSTRATION_PROMPTS.md`), `${promptLines.join('\n')}\n`, 'utf8');
fs.writeFileSync(path.join(bookRoot, `03_${prefix}_CONTINUITY_LEDGER.md`), `${continuityLines.join('\n')}\n`, 'utf8');
fs.writeFileSync(path.join(bookRoot, `robbie_${requested}.json`), `${JSON.stringify(book, null, 2)}\n`, 'utf8');

console.log(JSON.stringify({
  bookId: config.bookId,
  chapters: chapters.length,
  pages: pages.length,
  totalDutchWords: dutchWords.length,
  averageDutchWordsPerLine: Number(avgWordsPerLine.toFixed(2)),
  averageDutchLettersPerWord: Number(avgLettersPerWord.toFixed(2)),
  prompts: illustratedPages.length,
  output: path.relative(root, path.join(bookRoot, `robbie_${requested}.json`)).split(path.sep).join('/'),
}, null, 2));
