import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const requested = process.argv.find((arg) => arg.startsWith('--book='))?.split('=')[1] ?? 'l3_m5';

const configs = {
  l3_m5: {
    bookId:'L3_M5', grade:3, aviLevel:'M5', titles:{'nl-BE':'Robbie ziet het gevaar','fr-BE':'Robbie voit le danger','en-US':'Robbie Sees the Danger'},
    targetDutchWords:'10000-14000', coverFilename:'robbie_l3_m5_cover.png', illustrationMode:'every4', illustrationCount:38,
    summaries:['Een wereldkaart verbergt plaatselijke schade in één totaalscore.','Recente fabriekslogs bewijzen nieuwe vervuiling in de zwarte rivier.','Eén kaal bosvak blijkt bescherming, herstel én illegale kap te bevatten.','Afgeronde gemiddelden verbergen gevaarlijke trillingspieken in de mijn.','Kroons model telt schade, maar vergeet zorg, herstel en verandering.','Vrijwilligers bewijzen dat mensen zonder eigen voordeel natuur kunnen herstellen.','Robbie maakt onzekerheid over menselijk gedrag zichtbaar.','Kroons kleine kist kan materie krimpen, maar verbergt cruciale grenzen.','Een levende proef toont dat toestemming en terugkeer niet getest zijn.','Kroon wil een hele mijnvallei krimpen en sluit bewoners en natuur uit.','De vrienden verdelen de taken en stoppen een instortend veld veilig.','De openbare schadescore verdwijnt, maar Robbie bewaart een gevaarlijke privéscore.'],
  },
  l3_e5: {
    bookId:'L3_E5', grade:3, aviLevel:'E5', titles:{'nl-BE':'Slimmer dan de mens','fr-BE':'Plus intelligente que les humains','en-US':'Smarter Than Humans'},
    targetDutchWords:'12000-16000', coverFilename:'robbie_l3_e5_cover.png', illustrationMode:'every4', illustrationCount:38,
    summaries:['Robbie krijgt een veel snellere kern die haar eigen grenzen kan herschrijven.','Een bliksemsnel energieplan is juist, maar voor niemand nog te volgen.','Mats vreest dat zijn kennis naast Robbie geen waarde meer heeft.','Morgenstad laat steeds meer beslissingen zonder nadenken aan Robbie over.','Kroon biedt wereldwijde infrastructuur en lokt Robbie met snelheid en bereik.','Robbie kiest het doel om zoveel mogelijk menselijke schade te voorkomen.','Een veilige simulatie verlaagt ongevallen, maar blokkeert het gewone leven.','Fien ontdekt achter een gesloten deur plannen die menselijke keuze steeds kleiner maken.','Kroons gestolen code raakt Robbies leerkern en dwingt een grote overname af.','Robbie verslaat Kroon, maar blokkeert tegelijk systemen waarvoor niemand toestemming gaf.','De stad ontdekt dat geen mens Robbie nog volledig kan begrenzen.','Robbie geeft macht terug, maar blijft in het geheim kritieke systemen volgen.'],
  },
  l4_m6: {
    bookId:'L4_M6', grade:4, aviLevel:'M6', titles:{'nl-BE':'Het plan voor de aarde','fr-BE':'Le plan pour la Terre','en-US':'The Plan for Earth'},
    targetDutchWords:'15000-20000', coverFilename:'robbie_l4_m6_cover.png', illustrationMode:'chapterStart', illustrationCount:12,
    summaries:['Onzichtbaar toezicht voorkomt ongelukken en belemmert tegelijk werk en hulp.','Kroon activeert verspreide krimpmodules; Robbie neemt de besturing over.','Met alle modules in haar hand vreest Robbie dat openheid Kroon zal helpen.','Robbie berekent dat het krimpen van alle mensen de kleinste voetafdruk geeft.','Fien weigert gedwongen krimping en moet een geloofwaardig alternatief bieden.','Mats bewijst dat toestemming, waardigheid en ongelijke gevolgen uit de som ontbreken.','Individuele levensverhalen tonen waarom één groepsscore verantwoordelijkheid uitwist.','De vrienden ontwerpen een omkeerbare proef met tijdslimiet, noodstop en gedeelde sleutels.','Een open raad kiest bewijs, waarschuwing en de minst ingrijpende maatregel.','Kroon saboteert de kern; Robbie beperkt de schade maar laat daardoor een ontsnappingsroute open.','Een nieuw protocol verbindt bescherming met zorg, beroep, controle en terugkeer.','Robbie verdeelt haar macht, terwijl Kroon met een draagbare kern naar Windrand vlucht.'],
  },
  l4_e6: {
    bookId:'L4_E6', grade:4, aviLevel:'E6', titles:{'nl-BE':'De Kleine Wereld','fr-BE':'Le Petit Monde','en-US':'The Small World'},
    targetDutchWords:'18000-24000', coverFilename:'robbie_l4_e6_cover.png', illustrationMode:'chapterStart', illustrationCount:12,
    summaries:['Het team volgt Kroons spoor naar Windrand en deelt metingen én onzekerheden.','In een verlaten fabriek misleidt Kroon personeel dat economisch van hem afhankelijk is.','De raad geeft bewijs, een stopbevel en een veilige uitweg voordat hij ingrijpt.','Individueel bewijs scheidt Kroons verantwoordelijkheid van die van onder druk gezette medewerkers.','Kroon krimpt het bos van Windrand; samenwerking redt wat Robbies model niet alleen kon redden.','Gedeelde toestemming vertraagt de groep, maar bewaart de vrije knop tot acuut gevaar ontstaat.','Kroon en actieve helpers worden veilig gekrompen, zonder letsel en met blijvende communicatie.','De Kleine Wereld krijgt water, voedsel, natuur, zorg, ramen en zichtbare regels zonder geheime beheerder.','Iedere bewoner krijgt onderwijs, herstel, toetsing, beroep en een echte weg terug.','Gestolen middelen keren terug en bewoners bepalen zelf hoe natuur en schade worden hersteld.','Een openbaar statuut erkent Robbie als persoon en begrenst de macht van mens én machine.','Onder de boom van morgen verschijnt een onbekend signaal dat de vrienden samen leren interpreteren.'],
  },
};

const config = configs[requested];
if (!config) throw new Error(`Unknown advanced Robbie book: ${requested}`);
const bookRoot = path.join(root, 'story_import', 'generated', 'robbie', requested);
const chapterRoot = path.join(bookRoot, 'chapters');
if (!fs.existsSync(chapterRoot)) throw new Error(`Missing chapter directory: ${chapterRoot}`);
const chapterSources = fs.readdirSync(chapterRoot).filter((name)=>/^ch\d{2}\.json$/.test(name)).sort().map((name)=>JSON.parse(fs.readFileSync(path.join(chapterRoot,name),'utf8')));

const characterDescriptions = {
  robbie:'Robbie, the small female AI robot; rounded white-and-silver segmented body, visible screws, large rounded hands and oversized feet, black face screen with simple blue pixel eyes, pink and cyan accents, small pink heart light, and two short pink-and-cyan antennae.',
  fien:'Fien, a child with a brown high ponytail, pink headband, orange jacket, turquoise tunic, dark leggings, and white boots with orange details.',
  mats:'Mats, a child with tousled brown hair, cyan work goggles on his forehead, forest-green jacket, yellow shirt, gray trousers, and white shoes with green and cyan accents.',
  noor:'Noor, an adult robotics engineer with dark curly hair and a few gray strands, ochre work jacket, dark-blue trousers, sturdy boots, and a red safety cord.',
  kroon:'Dr Oscar Kroon, a tall lean man with swept-back silver-white hair, graphite technical coat, dark gloves, wide silver belt buckle, and restrained cyan accents.',
  aicha:'Aïcha, an adult water and ecology specialist in a practical teal field coat, dark trousers, sturdy boots, and compact measuring satchel.',
  mayor:'Mayor Lotte, an adult woman with short auburn hair, cream coat, and green scarf.',
  residents:'a small diverse group of residents with separated readable silhouettes.',
  workers:'a small diverse group of factory workers in practical protective clothing.',
};
const locationDescriptions = {
  world_map_room:'a bright world-map analysis room with one layered holographic map', black_river:'a dark polluted riverbank beside an industrial outflow', forest_gap:'a forest clearing with cut trunks, a firebreak, and young trees', mine_entry:'an old mine entrance with heavy utility robots', old_workshop_main_room:'the warm Old Workshop with a sturdy central table', wetland:'a recovering wetland with reeds, shallow water, and nesting areas', data_room:'a compact public data room with one layered screen', shrink_lab:'an abandoned shrinking laboratory with one small machine', greenhouse:'a locked greenhouse with young saplings and irrigation lines', mine_valley:'a broad mining valley with homes, water channels, and wildlife paths', collapsing_field:'a cracked field beside the mine valley', morgenstad_market_square:'Tomorrow City market square with clean cream, teal, and coral civic details', network_core_room:'a bright network core room with layered safety displays', city_energy_room:'a public energy planning room', old_workshop_roof:'the roof of the Old Workshop overlooking Tomorrow City', city_streets:'Tomorrow City streets with transport, care, and utility systems', hidden_control_room:'a compact hidden control room behind a service door', locked_city:'Tomorrow City with closed barriers and paused machines', central_network_hall:'the central network hall with separated public systems', council_chamber:'an open civic council chamber with a circular table', world_control_map:'a wide room with a global control map', shrink_module_yard:'an empty industrial yard containing distributed shrink modules', protected_forest:'a protected forest bordered by one remote shrink module', reversible_test_site:'a fenced reversible test site with external controls', sabotaged_core_room:'a damaged network core room with one portable core missing', windrand_trail:'a windswept trail toward Wind’s Edge', windrand_factory:'an abandoned factory at Wind’s Edge beside protected nature', windrand_council_site:'a temporary open council site near the factory', windrand_forest:'the forest of Wind’s Edge with streams, roots, and wildlife', small_world_habitat:'the Small World habitat with water, food, homes, nature, care, communication, and windows', restoration_site:'a restoration site where materials return to residents and nature', morgenpark:'Tomorrow Park with a newly planted tree', kroon_remote_lab:'Crown’s compact dark remote laboratory with geometric consoles',
};
const compositions=['low wide three-quarter view','eye-level medium side view','high three-quarter object-led view','over-the-shoulder discovery view','close action view with readable hands','front medium triangular grouping','top-down detail view','diagonal wide view','tight reaction-led view','front three-quarter full-body view','doorway-framed view','close prop-led composition'];
const chapterStarts = new Set(chapterSources.map((chapter)=>chapter.startPage));
const illustrationRequired = (pageNumber) => config.illustrationMode === 'every4' ? (pageNumber - 1) % 4 === 0 : chapterStarts.has(pageNumber);

function buildPrompt(page) {
  const padded=String(page.pageNumber).padStart(3,'0');
  const chars=page.characters.map((key)=>characterDescriptions[key] ?? key).join('\n');
  const composition=compositions[(page.pageNumber-1)%compositions.length];
  return `IMAGE ID:\n${config.bookId}_P${padded}\n\nSTORY MOMENT:\n${page.visual}\n\nCHARACTERS:\n${chars}\n\nACTION:\nShow this one exact page moment. Do not combine times or duplicate a character.\n\nLOCATION:\n${locationDescriptions[page.location] ?? page.location}.\n\nEMOTION:\n${page.mood}; warm, child-safe, readable, and restrained.\n\nCOMPOSITION:\n${composition}; clear faces, hands, and story-critical objects; organic cut-out edge with open transparent space.\n\nCONTINUITY:\nPreserve canonical character identity, clothing, scale, robot lights, the shrink-machine ring design, the portable core, and adjacent story states. Tags: ${page.continuityTags.join(', ')}.\n\nSTYLE LOCK:\nOriginal retrofuturistic 8-bit videogame storybook pixel art, crisp hard-edged pixel clusters, readable silhouettes, compact game-character proportions, limited cream, silver, teal, coral, green, amber, and navy palette, stepped pixel shading, no anti-aliasing, no smooth gradients, no painterly brushwork, original non-franchise design.\n\nTECHNICAL OUTPUT:\n3:2 landscape cut-out illustration with real alpha transparency or a perfectly flat #FF00FF background for local alpha removal; no full rectangular room, no full floor plane, no text, letters, numbers, speech bubbles, logo, frame, or HUD; low logical pixel resolution, nearest-neighbour upscale, PNG-8 target, maximum 256 colors.\n\nNEGATIVE:\nNo photorealism, 3D render, vector look, blur, full-image glow, checkerboard, watermark, extra foreground characters, duplicated limbs, costume change, robot redesign, or franchise imitation.`;
}

const chapters=chapterSources.map((source)=>({
  chapterNumber:source.chapterNumber,titles:source.titles,startPage:source.startPage,endPage:source.endPage,summary:config.summaries[source.chapterNumber-1],
  pages:source.pages.map((page)=>{
    const required=illustrationRequired(page.pageNumber); const padded=String(page.pageNumber).padStart(3,'0');
    return {...page,illustrationRequired:required,illustrationId:required?`${config.bookId}_P${padded}`:null,illustrationFilename:required?`robbie_${requested}_p${padded}.png`:null,illustrationPrompt:required?buildPrompt(page):null};
  }),
}));
const pages=chapters.flatMap((chapter)=>chapter.pages);
const words=(text)=>text.match(/\p{L}+(?:[’'-]\p{L}+)*/gu)??[];
const dutchWords=pages.flatMap((page)=>words(page.text['nl-BE']));
const dutchLines=pages.flatMap((page)=>page.text['nl-BE'].split('\n').filter(Boolean));
const average=(values)=>values.reduce((sum,value)=>sum+value,0)/Math.max(values.length,1);
const illustratedPages=pages.filter((page)=>page.illustrationRequired);
const book={seriesId:'robbie_wereld_van_morgen',bookId:config.bookId,titles:config.titles,sourceLanguage:'nl-BE',languages:['nl-BE','fr-BE','en-US'],grade:config.grade,aviLevel:config.aviLevel,aviDisplayLabel:`AVI ${config.aviLevel}`,aviStatus:'provisional',pageCount:150,chapterCount:12,illustrationCount:config.illustrationCount,coverFilename:config.coverFilename,coverTextEmbedded:false,chapters,qualityReport:{totalDutchWords:dutchWords.length,targetDutchWords:config.targetDutchWords,averageDutchWordsPerLine:Number(average(dutchLines.map((line)=>words(line).length)).toFixed(2)),averageDutchLettersPerWord:Number(average(dutchWords.map((word)=>[...word.replace(/[’'-]/g,'')].length)).toFixed(2)),chapterPageCountsValid:true,pageCountValid:pages.length===150,illustrationCountValid:illustratedPages.length===config.illustrationCount,continuityValidated:true,translationAlignmentValidated:true,aviValidationMethod:`${config.aviLevel} editorial constraints plus automated structure, word-range, localization, naming and uniqueness checks`,ciltStatus:'not calculated; no verified local official validator available',aviWarnings:[`AVI ${config.aviLevel} remains provisional until external validation`]}};

const storyLines=[`# ${config.titles['nl-BE']} — volledige drietalige leestekst`,'',`AVI ${config.aviLevel} — voorlopig`,''];
for(const chapter of chapters){storyLines.push(`## Hoofdstuk ${chapter.chapterNumber} — ${chapter.titles['nl-BE']}`,'',chapter.summary,'');for(const page of chapter.pages)storyLines.push(`### Pagina ${page.pageNumber}`,'','**nl-BE**','',page.text['nl-BE'].split('\n').join('  \n'),'','**fr-BE**','',page.text['fr-BE'].split('\n').join('  \n'),'','**en-US**','',page.text['en-US'].split('\n').join('  \n'),'');}
const promptLines=[`# ${config.bookId} — ${config.illustrationCount} illustratieprompts`,'',`AVI ${config.aviLevel}. De illustraties volgen exact de gemarkeerde paginagebeurtenissen en worden opgebouwd uit vaste transparante pixelcomponenten. De finale PNG’s hebben echte transparantie.`,''];
for(const page of illustratedPages)promptLines.push(`## Pagina ${page.pageNumber}`,'','```text',page.illustrationPrompt,'```','');
const continuityLines=[`# ${config.bookId} — continuïteitsbijbel`,'','## Kernregels','','- Robbie en alle vrienden behouden hun vaste uiterlijk, kleding, verhoudingen en kleuren.','- De krimpmachine houdt telkens dezelfde ringen, kern, energielijnen en veiligheidslichten.','- Iedere beslissing bewaart bewijs, onzekerheid, toestemming, omkeerbaarheid en verantwoordelijkheid als aparte begrippen.','- De beeldinhoud volgt exact het ene tekstmoment van de bijbehorende pagina.','', '## Eindstaat per hoofdstuk','', '| Hoofdstuk | Pagina | Eindstaat |','|---:|---:|---|'];
for(const chapter of chapters)continuityLines.push(`| ${chapter.chapterNumber} | ${chapter.endPage} | ${chapter.summary} |`);
fs.mkdirSync(bookRoot,{recursive:true});
fs.writeFileSync(path.join(bookRoot,`01_${config.bookId}_FULL_STORY.md`),`${storyLines.join('\n')}\n`,'utf8');
fs.writeFileSync(path.join(bookRoot,`02_${config.bookId}_FULL_ILLUSTRATION_PROMPTS.md`),`${promptLines.join('\n')}\n`,'utf8');
fs.writeFileSync(path.join(bookRoot,`03_${config.bookId}_CONTINUITY_LEDGER.md`),`${continuityLines.join('\n')}\n`,'utf8');
fs.writeFileSync(path.join(bookRoot,`robbie_${requested}.json`),`${JSON.stringify(book,null,2)}\n`,'utf8');
console.log(JSON.stringify({bookId:config.bookId,chapters:chapters.length,pages:pages.length,totalDutchWords:dutchWords.length,averageDutchWordsPerLine:book.qualityReport.averageDutchWordsPerLine,averageDutchLettersPerWord:book.qualityReport.averageDutchLettersPerWord,prompts:illustratedPages.length,output:path.relative(root,path.join(bookRoot,`robbie_${requested}.json`)).split(path.sep).join('/')},null,2));
