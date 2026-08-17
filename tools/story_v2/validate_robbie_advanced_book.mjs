import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'..','..');
const requested=process.argv.find((arg)=>arg.startsWith('--book='))?.split('=')[1]??'l3_m5';
const configs={
  l3_m5:{bookId:'L3_M5',grade:3,level:'M5',minWords:10000,maxWords:14000,maxLineWords:22,illustrations:38,mode:'every4'},
  l3_e5:{bookId:'L3_E5',grade:3,level:'E5',minWords:12000,maxWords:16000,maxLineWords:26,illustrations:38,mode:'every4'},
  l4_m6:{bookId:'L4_M6',grade:4,level:'M6',minWords:15000,maxWords:20000,maxLineWords:30,illustrations:12,mode:'chapterStart'},
  l4_e6:{bookId:'L4_E6',grade:4,level:'E6',minWords:18000,maxWords:24000,maxLineWords:34,illustrations:12,mode:'chapterStart'},
};
const config=configs[requested]; if(!config)throw new Error(`Unknown book ${requested}`);
const bookRoot=path.join(root,'story_import','generated','robbie',requested);
const bookPath=path.join(bookRoot,`robbie_${requested}.json`);
const reportPath=path.join(bookRoot,`04_${config.bookId}_BOOK_QA.json`);
const book=JSON.parse(fs.readFileSync(bookPath,'utf8'));
const failures=[]; const warnings=[]; const pages=book.chapters.flatMap((chapter)=>chapter.pages);
const words=(text)=>text.match(/\p{L}+(?:[’'-]\p{L}+)*/gu)??[];
const expectedRanges=[[1,13],[14,25],[26,38],[39,50],[51,63],[64,75],[76,88],[89,100],[101,113],[114,125],[126,138],[139,150]];
if(book.bookId!==config.bookId)failures.push(`bookId is ${book.bookId}`);
if(book.grade!==config.grade)failures.push(`grade is ${book.grade}`);
if(book.aviLevel!==config.level||book.aviDisplayLabel!==`AVI ${config.level}`||book.aviStatus!=='provisional')failures.push('AVI metadata is invalid');
if(book.pageCount!==150||pages.length!==150)failures.push(`expected 150 pages, found ${pages.length}`);
if(book.chapterCount!==12||book.chapters.length!==12)failures.push(`expected 12 chapters, found ${book.chapters.length}`);
if(book.illustrationCount!==config.illustrations)failures.push(`illustrationCount is ${book.illustrationCount}`);
if(book.coverFilename!==`robbie_${requested}_cover.png`||book.coverTextEmbedded!==false)failures.push('cover metadata is invalid');
if(JSON.stringify(book.languages)!==JSON.stringify(['nl-BE','fr-BE','en-US']))failures.push('language order is invalid');
const chapterStarts=new Set(expectedRanges.map((range)=>range[0]));
for(let i=0;i<book.chapters.length;i+=1){const chapter=book.chapters[i];const[start,end]=expectedRanges[i];if(chapter.chapterNumber!==i+1||chapter.startPage!==start||chapter.endPage!==end||chapter.pages.length!==end-start+1)failures.push(`chapter ${i+1} structure is invalid`);for(const locale of book.languages)if(!chapter.titles?.[locale]?.trim())failures.push(`chapter ${i+1} lacks ${locale} title`);}
const storyBeats=new Set(),illustrationIds=new Set(),illustrationFiles=new Set(),prompts=new Set();
const localeTexts=Object.fromEntries(book.languages.map((locale)=>[locale,new Set()]));
const localeLineCounts=Object.fromEntries(book.languages.map((locale)=>[locale,new Map()]));
const lineWordCounts=[]; const longLines=[]; let totalDutchWords=0,totalDutchLetters=0,totalDutchLines=0;
for(let i=0;i<pages.length;i+=1){
  const page=pages[i],expected=i+1,padded=String(expected).padStart(3,'0');
  if(page.pageNumber!==expected)failures.push(`page index ${i} is numbered ${page.pageNumber}`);
  if(!page.storyBeatId?.trim()||storyBeats.has(page.storyBeatId))failures.push(`missing or duplicate storyBeatId at page ${expected}`); storyBeats.add(page.storyBeatId);
  for(const locale of book.languages){const value=page.text?.[locale];if(typeof value!=='string'||!value.trim())failures.push(`page ${expected} has empty ${locale}`);else{if(localeTexts[locale].has(value))failures.push(`duplicate ${locale} page text at ${expected}`);localeTexts[locale].add(value);const lines=value.split('\n').filter(Boolean);for(const line of lines)localeLineCounts[locale].set(line,(localeLineCounts[locale].get(line)??0)+1);const expectedLines={M5:6,E5:8,M6:9,E6:12}[config.level];if(lines.length!==expectedLines)failures.push(`page ${expected} has ${lines.length} ${locale} lines, expected ${expectedLines}`);}}
  const nlLines=page.text['nl-BE'].split('\n').filter(Boolean);for(const line of nlLines){const size=words(line).length;lineWordCounts.push(size);totalDutchLines+=1;if(size>config.maxLineWords)longLines.push({pageNumber:expected,words:size,line});}
  const nlWords=words(page.text['nl-BE']);totalDutchWords+=nlWords.length;totalDutchLetters+=nlWords.reduce((sum,word)=>sum+[...word.replace(/[’'-]/g,'')].length,0);
  const illustrationExpected=config.mode==='every4'?(expected-1)%4===0:chapterStarts.has(expected);
  if(page.illustrationRequired!==illustrationExpected)failures.push(`page ${expected} illustration flag is invalid`);
  if(illustrationExpected){const expectedId=`${config.bookId}_P${padded}`,expectedFilename=`robbie_${requested}_p${padded}.png`;if(page.illustrationId!==expectedId||page.illustrationFilename!==expectedFilename)failures.push(`page ${expected} illustration identity is invalid`);for(const marker of ['IMAGE ID:','STORY MOMENT:','CHARACTERS:','LOCATION:','COMPOSITION:','CONTINUITY:','STYLE LOCK:','TECHNICAL OUTPUT:','NEGATIVE:'])if(!page.illustrationPrompt?.includes(marker))failures.push(`page ${expected} prompt lacks ${marker}`);if(illustrationIds.has(page.illustrationId)||illustrationFiles.has(page.illustrationFilename)||prompts.has(page.illustrationPrompt))failures.push(`duplicate illustration data at page ${expected}`);illustrationIds.add(page.illustrationId);illustrationFiles.add(page.illustrationFilename);prompts.add(page.illustrationPrompt);}else if(page.illustrationId!==null||page.illustrationFilename!==null||page.illustrationPrompt!==null)failures.push(`page ${expected} has unwanted illustration data`);
  const nl=page.text['nl-BE'],fr=page.text['fr-BE'],en=page.text['en-US'];
  if(/\b(?:Fien|Mats|Noor|Kroon|Lotte|Daan|Morgenstad|Windrand|Kleine Wereld)\b/u.test(fr))failures.push(`page ${expected} has unlocalized fr-BE naming`);
  if(/\b(?:Fien|Mats|Kroon|Lotte|Daan|Damien|Fanny|Mathis|Couronne|Charlotte|Morgenstad|Windrand|Kleine Wereld|Petit Monde)\b/u.test(en))failures.push(`page ${expected} has unlocalized en-US naming`);
  if(/\b(?:Fanny|Mathis|Nora|Couronne|Charlotte|Damien|Dean|Faye|Max|Crown|Lottie|Cité-de-Demain|Tomorrow City|Lisière-du-Vent|Wind’s Edge|Petit Monde|Small World)\b/u.test(nl))failures.push(`page ${expected} has foreign nl-BE naming`);
}
const lineRepetition=Object.fromEntries(book.languages.map((locale)=>{const counts=localeLineCounts[locale];const repeatedLines=[...counts.entries()].filter(([,count])=>count>1).map(([line,count])=>({line,count}));return[locale,{lines:[...counts.values()].reduce((sum,count)=>sum+count,0),uniqueLines:counts.size,repeatedOccurrences:repeatedLines.reduce((sum,item)=>sum+item.count,0),repeatedLines}];}));
for(const locale of book.languages)if(lineRepetition[locale].repeatedOccurrences>0)failures.push(`${locale} contains ${lineRepetition[locale].repeatedOccurrences} repeated line occurrences`);
if(totalDutchWords<config.minWords||totalDutchWords>config.maxWords)failures.push(`Dutch word count ${totalDutchWords} is outside ${config.minWords}-${config.maxWords}`);
if(longLines.length)warnings.push(`${longLines.length} Dutch lines exceed ${config.maxLineWords} words; editorial review recommended`);
const report={passed:failures.length===0,generatedAt:new Date().toISOString(),bookId:book.bookId,aviLevel:book.aviLevel,aviDisplayLabel:book.aviDisplayLabel,aviStatus:book.aviStatus,chapters:book.chapters.length,pages:pages.length,languages:book.languages,dutchWords:totalDutchWords,targetDutchWords:`${config.minWords}-${config.maxWords}`,dutchLines:totalDutchLines,averageDutchWordsPerLine:Number((lineWordCounts.reduce((sum,value)=>sum+value,0)/Math.max(totalDutchLines,1)).toFixed(2)),averageDutchLettersPerWord:Number((totalDutchLetters/Math.max(totalDutchWords,1)).toFixed(2)),maximumDutchWordsOnLine:Math.max(...lineWordCounts),longLines,lineRepetition,uniqueStoryBeatIds:storyBeats.size,uniqueIllustrationIds:illustrationIds.size,uniqueIllustrationFilenames:illustrationFiles.size,uniqueIllustrationPrompts:prompts.size,failures,warnings};
fs.writeFileSync(reportPath,`${JSON.stringify(report,null,2)}\n`,'utf8');console.log(JSON.stringify(report,null,2));if(failures.length)process.exitCode=1;
