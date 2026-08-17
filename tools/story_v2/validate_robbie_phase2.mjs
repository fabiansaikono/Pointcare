import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const phase2 = path.join(root, 'story_import', 'generated', 'robbie', 'phase2');
const refs = path.join(root, 'story_import', 'generated', 'robbie', 'references', 'final');
const prompts = path.join(root, 'story_import', 'generated', 'robbie', 'prompts', 'reference_prompts.md');

const approvalsPath = path.join(phase2, '00_APPROVALS_AND_LOCALIZATION.md');
const planPath = path.join(phase2, '01_L1_M3_PRODUCTION_PLAN.md');
const qaPath = path.join(phase2, '02_REFERENCE_QA.json');

const requiredFiles = [approvalsPath, planPath, qaPath, prompts];
const requiredRefs = [
  'videogame_style_reference.png',
  'robbie_character_reference.png',
  'fien_character_reference.png',
  'mats_character_reference.png',
  'kroon_character_reference.png',
  'robbie_cast_reference.png',
];

const failures = [];
for (const file of requiredFiles) {
  if (!fs.existsSync(file)) failures.push(`Ontbrekend bestand: ${path.relative(root, file)}`);
}
for (const file of requiredRefs) {
  if (!fs.existsSync(path.join(refs, file))) failures.push(`Ontbrekende referentie: ${file}`);
}

let ranges = [];
let totalPages = 0;
if (fs.existsSync(planPath)) {
  const plan = fs.readFileSync(planPath, 'utf8');
  const chapterCount = (plan.match(/^## Hoofdstuk \d+ /gm) || []).length;
  if (chapterCount !== 12) failures.push(`Verwacht 12 hoofdstukken, vond ${chapterCount}`);

  ranges = [...plan.matchAll(/^- Pagina’s: (\d+)–(\d+) \((\d+)\)$/gm)].map((match) => ({
    start: Number(match[1]),
    end: Number(match[2]),
    count: Number(match[3]),
  }));
  if (ranges.length !== 12) failures.push(`Verwacht 12 paginabereiken, vond ${ranges.length}`);
  ranges.forEach((range, index) => {
    const expectedStart = index === 0 ? 1 : ranges[index - 1].end + 1;
    if (range.start !== expectedStart) failures.push(`Niet-aansluitend bereik bij hoofdstuk ${index + 1}`);
    if (range.end - range.start + 1 !== range.count) failures.push(`Foute paginatelling bij hoofdstuk ${index + 1}`);
    if (range.count < 10 || range.count > 15) failures.push(`Hoofdstuk ${index + 1} valt buiten 10–15 pagina’s`);
    totalPages += range.count;
  });
  if (totalPages !== 150 || ranges.at(-1)?.end !== 150) failures.push(`Boek moet exact 150 pagina’s tellen, vond ${totalPages}`);
  for (const marker of ['exact 150', '18–24 woorden per pagina', 'L1_M3_P150', 'Fase 2-goedkeuringspoort']) {
    if (!plan.includes(marker)) failures.push(`Plan mist marker: ${marker}`);
  }
}

if (fs.existsSync(approvalsPath)) {
  const approvals = fs.readFileSync(approvalsPath, 'utf8');
  for (const marker of ['nl-BE', 'fr-BE', 'en-US', 'Fanny', 'Faye', 'Mathis', 'Max', 'Oscar Couronne', 'Oscar Crown']) {
    if (!approvals.includes(marker)) failures.push(`Lokalisatie mist marker: ${marker}`);
  }
}

let imageQaPassed = false;
if (fs.existsSync(qaPath)) {
  const qa = JSON.parse(fs.readFileSync(qaPath, 'utf8'));
  imageQaPassed = qa.passed === true && qa.assetCount === 6;
  if (!imageQaPassed) failures.push('Technische beeldcontrole is niet volledig geslaagd');
}

const report = {
  passed: failures.length === 0,
  phase: 2,
  chapters: ranges.length,
  plannedPages: totalPages,
  referenceImages: requiredRefs.filter((file) => fs.existsSync(path.join(refs, file))).length,
  imageQaPassed,
  languages: ['nl-BE', 'fr-BE', 'en-US'],
  failures,
};

console.log(JSON.stringify(report, null, 2));
if (!report.passed) process.exitCode = 1;
