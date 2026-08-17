# Curriculumstructuur leerjaar 1 — migratie en extrapolatie

## Bron en afbakening

- Bronbestand: `Poincare_Lesvolgorde.xlsx`, werkblad `Sheet1`, rijen 2–212.
- Bronhash (SHA-256): `00ab9164c2de359b1bad3a7c5869c0ab0b0f81d7ada01bb35540074c6503f58b`.
- Kolom B overschrijft kolom A wanneer B ingevuld is.
- Kolom D overschrijft kolom C wanneer D ingevuld is.
- Kolom E bepaalt de subcategoriesequentie binnen een hoofdcategorie.
- Kolom H bepaalt de lesstap binnen een subcategorie.
- Kolom K overschrijft de Nederlandse samenvatting uit kolom J wanneer K ingevuld is.
- Verhalen en de puzzeltaxonomie vallen buiten deze migratie en zijn niet gewijzigd.
- `Sheet2` is niet gebruikt: het bevat niet de gevraagde kolomstructuur en overlapt onvolledig met `Sheet1`.

## Uitgevoerde migratie leerjaar 1

- 92 niet-verhaal-lessen zijn één-op-één gekoppeld aan de Excelbron.
- De lessen vallen na de migratie onder 5 hoofdcategorieën en 30 subcategorieën.
- De legacyroute (`legacyRouting.domain` en `legacyRouting.subcategoryId`) blijft behouden voor bestaande oefengeneratoren; de zichtbare en canonieke structuur gebruikt `curriculumCategoryId` en `curriculumSubcategoryId`.
- Nieuwe canonieke subcategorieën zijn toegevoegd voor:
  - `geometry.patterns_orientation` — Patronen & oriëntatie;
  - `math.addition` — Optellen;
  - `math.subtraction` — Aftrekken;
  - `math.mixed_calculations` — Gemengde berekeningen;
  - `language.listening_speaking` — Luisteren & spreken.
- Bestaande subcategorieën zijn hergebruikt bij samenvoegingen:
  - Literatuur & tekstbeleving is voor leerjaar 1 opgenomen in Begrijpend lezen;
  - Klank-letter & fonologisch bewustzijn is voor leerjaar 1 opgenomen in Dictee;
  - Techniek & ontwerpen is voor leerjaar 1 opgenomen in Wetenschap & onderzoek.
- Zichtbare namen zijn aangepast naar onder meer `Rekenen met geld` en `Samenleving & sociale vaardigheden`.
- De twee kolom-K-correcties zijn toegepast op:
  - `Dictee · Eén ontbrekende letter`;
  - `Dictee · Meerdere ontbrekende letters`.

## Sequentie- en ontgrendelingsregels

- `gradeSequences` in het curriculummanifest bewaart kolom E per leerjaar.
- De app filtert subcategorieën op het gekozen leerjaar en sorteert ze op kolom E.
- Een subcategorie met een hogere E-sequentie wordt pas actief wanneer alle lessen uit lagere beschikbare subcategoriesequenties binnen dezelfde hoofdcategorie voltooid zijn.
- De lesvolgorde bewaart de exacte H-waarde uit Excel.
- Gelijke H-waarden zijn parallelle lessen: zij worden samen beschikbaar. De volgende H-stap opent pas nadat alle lessen van de voorgaande H-stap voltooid zijn.
- Er zijn 5 parallelle H-stappen in de huidige leerjaar-1-data, vooral door het samenbrengen van vroegere subcategorieën bij Wereldoriëntatie.
- Bestaande voortgang wordt niet opnieuw vergrendeld wanneer een leerling al aan een latere subcategorie is begonnen.
- De gecontroleerde voorbeelden bevestigen dat `Samenstellen en splitsen tot 10 en 20` stap 6 blijft binnen `Tellen, getallen & plaatswaarde`, na `Hoeveelheden tellen en structureren tot 20` op stap 4.
- `Schrijven & schriftelijke expressie` bewaart onder meer de volgorde `Een woord bij een prent` (1), `Spaties tussen woorden` (2), `Een korte zin maken` (4), `Hoofdletter en punt` (6) en `Een kort bericht schrijven` (9). Niet-bestaande tussenstappen worden niet dichtgeschoven.

## Controle van Excelrijen 115/116 en 122/123

- Rijen 115 en 116 zijn inhoudelijk identiek voor `Beleefd vragen en bedanken`.
- Rijen 122 en 123 zijn inhoudelijk identiek voor `Tellen tot twintig`.
- In de broncatalogus bestaan voor beide titels afzonderlijke lesrecords voor leerjaar 1 en leerjaar 2.
- Daarom is niets verwijderd:
  - leerjaar 1 koppelt aan rij 115 respectievelijk 122;
  - leerjaar 2 koppelt later aan rij 116 respectievelijk 123.
- Dit voorkomt dat geldige leerjaar-2-lessen als vermeend duplicaat worden gewist.

## Eén bron en export naar Android

- De genormaliseerde, reproduceerbare bron staat in `curriculum-library/data/structure/poincare-lesson-order-v1.json`.
- `curriculum-library/scripts/apply-curriculum-structure.mjs` past de bron toe op één gekozen leerjaar.
- `npm run data:sync` bouwt de catalogus en past daarna automatisch `apply-curriculum-structure.mjs --grade=1` toe. Een gewone regeneratie kan de Excelroute, H-stappen en prerequisites daardoor niet meer met een generieke sortering overschrijven.
- `curriculum-library/scripts/audit-curriculum-structure.mjs` controleert bronmapping, namen, E/H-sequenties, samenvattingen, prerequisites en web/app-pariteit.
- De Android-export heeft een `--skip-stories`-modus. Daarmee worden curriculum en oefeningen geëxporteerd zonder verhaalbestanden te herschrijven.
- De webbron en Android-assets bevatten na export exact dezelfde 223 niet-verhaal-lessen.
- De actuele lokale export heeft release-ID `2026-08-14-a0830c3b166f` en checksum `a0830c3b166f415534b8fa145def9aaaee9a76f0d6ca9c37cb71937fd9cc55b0`.
- Web/Android-pariteit is noodzakelijk maar niet voldoende: de Android-unit-/runtimesuite moet eveneens volledig slagen. De huidige status is 341/341; publicatie en installatie blijven afzonderlijke, expliciete handelingen.

## Extrapolatie naar leerjaar 2

Gebruik dezelfde regels, zonder de leerjaar-1-structuur opnieuw te interpreteren:

1. Voer eerst een dry-run uit:
   `node scripts/apply-curriculum-structure.mjs --grade=2 --dry-run`
2. Controleer dat alle 123 niet-verhaal-lessen van leerjaar 2 een bronrij krijgen.
3. Bevestig dat rij 116 en rij 123 aan de leerjaar-2-records worden gekoppeld.
4. Pas de migratie toe:
   `node scripts/apply-curriculum-structure.mjs --grade=2`
5. Laat bestaande legacy-IDs voor leerjaar 3–6 staan; koppel geen conflicterende legacy-aliases aan de nieuwe canonieke IDs.
6. Controleer of `geometry.weight_volume` wordt toegevoegd. Verhoog dan de verwachte volledige taxonomiegrootte in `CurriculumManifestValidator` van 60 naar 61.
7. Exporteer zonder verhalen:
   `node scripts/export-curriculum-to-android.mjs --skip-stories`
8. Voer de structuuraudit uit:
   `node scripts/audit-curriculum-structure.mjs --grade=2`
9. Draai webtypecheck/build, Android-catalogustests en specifiek de leerjaar-2-regressietest.
10. Vergelijk de verhaalhashes vóór en na export; zij moeten identiek blijven.

## Kwaliteitscriteria voor volgende leerjaren

- Een zichtbaar label komt uit het manifest, nooit uit een legacyroute.
- Een les heeft exact één canonieke categorie en subcategorie.
- Legacyroutes dienen alleen runtimecompatibiliteit.
- E bepaalt subcategorie-ontgrendeling; H bepaalt lesstappen.
- Gelijke H-waarden betekenen parallel, niet dubbel of willekeurig.
- Een ingevulde overschrijfkolom heeft altijd voorrang op de oude waarde.
- Duplicaten worden beoordeeld op les-ID en leerjaar, niet alleen op titel of tekst.
- Verhalen worden alleen meegenomen wanneer een opdracht dat expliciet vraagt.
- Elke migratie eindigt met een bronaudit én een web/app-pariteitscontrole.
- Iedere gemigreerde snapshot eindigt bovendien met runtimegeneratie voor alle sets en een volledig groene Android-unit-/catalogussuite. Een gelijke checksum kan een onuitvoerbaar generatorcontract niet goedkeuren.
