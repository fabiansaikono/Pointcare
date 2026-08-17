import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'..','..');
const robbieRoot=path.join(root,'story_import','generated','robbie');
const configs=[
  {slug:'l3_m5',bookId:'L3_M5',title:'Robbie ziet het gevaar',level:'M5',words:[10000,14000],illustrations:38},
  {slug:'l3_e5',bookId:'L3_E5',title:'Slimmer dan de mens',level:'E5',words:[12000,16000],illustrations:38},
  {slug:'l4_m6',bookId:'L4_M6',title:'Het plan voor de aarde',level:'M6',words:[15000,20000],illustrations:12},
  {slug:'l4_e6',bookId:'L4_E6',title:'De Kleine Wereld',level:'E6',words:[18000,24000],illustrations:12},
];
const failures=[]; const results=[];
const libraryPath=path.join(robbieRoot,'hybrid_library','library_manifest.json');
const library=JSON.parse(fs.readFileSync(libraryPath,'utf8'));
if(!library.passed||library.generatedComponents!==123)failures.push(`hybrid library invalid: passed=${library.passed}, components=${library.generatedComponents}`);
for(const config of configs){
  const dir=path.join(robbieRoot,config.slug);
  const book=JSON.parse(fs.readFileSync(path.join(dir,`robbie_${config.slug}.json`),'utf8'));
  const qa=JSON.parse(fs.readFileSync(path.join(dir,`04_${config.bookId}_BOOK_QA.json`),'utf8'));
  const composition=JSON.parse(fs.readFileSync(path.join(dir,'hybrid_composition_manifest.json'),'utf8'));
  const assets=JSON.parse(fs.readFileSync(path.join(dir,'asset_manifest.json'),'utf8'));
  const imageFiles=fs.readdirSync(path.join(dir,'images','final')).filter((name)=>name.endsWith('.png'));
  const required=[`01_${config.bookId}_FULL_STORY.md`,`02_${config.bookId}_FULL_ILLUSTRATION_PROMPTS.md`,`03_${config.bookId}_CONTINUITY_LEDGER.md`,`04_${config.bookId}_BOOK_QA.json`,'PACKAGE_REPORT.md','hybrid_composition_manifest.json','asset_manifest.json',`robbie_${config.slug}.json`];
  for(const name of required)if(!fs.existsSync(path.join(dir,name)))failures.push(`${config.bookId} missing ${name}`);
  if(!fs.existsSync(path.join(dir,'qa','images_contact_sheet.png')))failures.push(`${config.bookId} missing image contact sheet`);
  if(book.bookId!==config.bookId||book.titles['nl-BE']!==config.title||book.aviDisplayLabel!==`AVI ${config.level}`)failures.push(`${config.bookId} identity or AVI label invalid`);
  if(book.pageCount!==150||book.chapterCount!==12||book.chapters.length!==12)failures.push(`${config.bookId} page/chapter metadata invalid`);
  if(!qa.passed||qa.dutchWords<config.words[0]||qa.dutchWords>config.words[1])failures.push(`${config.bookId} text QA failed`);
  if(!composition.passed||composition.illustrationCount!==config.illustrations||composition.uniquePageHashes!==config.illustrations)failures.push(`${config.bookId} composition QA failed`);
  if(!assets.passed||assets.illustrationCount!==config.illustrations||assets.uniqueIllustrationHashes!==config.illustrations||imageFiles.length!==config.illustrations)failures.push(`${config.bookId} package QA failed`);
  results.push({bookId:config.bookId,title:config.title,aviDisplayLabel:book.aviDisplayLabel,aviStatus:book.aviStatus,chapters:book.chapters.length,pages:book.chapters.flatMap((chapter)=>chapter.pages).length,dutchWords:qa.dutchWords,languages:book.languages,illustrations:assets.illustrationCount,uniqueIllustrationHashes:assets.uniqueIllustrationHashes,cover:assets.cover.path,textQaPassed:qa.passed,imageQaPassed:composition.passed,packagePassed:assets.passed});
}
const report={passed:failures.length===0,generatedAt:new Date().toISOString(),scope:'Robbie L3 and L4',library:{passed:library.passed,components:library.generatedComponents},books:results,totals:{books:results.length,chapters:results.reduce((sum,item)=>sum+item.chapters,0),pages:results.reduce((sum,item)=>sum+item.pages,0),illustrations:results.reduce((sum,item)=>sum+item.illustrations,0),dutchWords:results.reduce((sum,item)=>sum+item.dutchWords,0)},aviStatus:'provisional until external official AVI validation',failures};
fs.writeFileSync(path.join(robbieRoot,'05_L3_L4_DELIVERY_QA.json'),`${JSON.stringify(report,null,2)}\n`,'utf8');
const lines=['# Robbie L3 en L4 — eindrapport','','| Boek | Titel | Niveau | Hoofdstukken | Pagina’s | Nederlandse woorden | Illustraties |','|---|---|---:|---:|---:|---:|---:|'];
for(const item of results)lines.push(`| ${item.bookId} | ${item.title} | ${item.aviDisplayLabel} | ${item.chapters} | ${item.pages} | ${item.dutchWords} | ${item.illustrations} |`);
lines.push('','## Controle','',`- Tekst, vertalingen, structuur, illustraties, covers en pakketten: ${report.passed?'geslaagd':'mislukt'}.`,`- Herbruikbare transparante pixelcomponenten: ${library.generatedComponents}.`,`- Totaal: ${report.totals.books} boeken, ${report.totals.chapters} hoofdstukken, ${report.totals.pages} pagina’s en ${report.totals.illustrations} illustraties.`,`- AVI-status: voorlopig tot externe officiële validatie.`,...(failures.length?['','## Problemen','',...failures.map((failure)=>`- ${failure}`)]:[]),'');
fs.writeFileSync(path.join(robbieRoot,'L3_L4_DELIVERY_REPORT.md'),lines.join('\n'),'utf8');
console.log(JSON.stringify(report,null,2)); if(failures.length)process.exitCode=1;
