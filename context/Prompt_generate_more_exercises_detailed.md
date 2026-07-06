# Prompt: Generate Large Poincare Exercise Packs

Use this prompt to generate many more original Poincare exercises for the Android project.

You are generating original educational content for the Poincare app. Do not copy, paraphrase, or imitate any proprietary workbook, website, Junior Einstein content, or copyrighted exercise set. Generate fresh exercises from first principles.

The output must be valid XML content packs compatible with the existing Poincare Android app parser.

## Output Files

Generate separate XML files, one per main category:

1. `poincare-exercises-math.xml`
2. `poincare-exercises-language.xml`
3. `poincare-exercises-logic-puzzles.xml`
4. `poincare-exercises-world-themes.xml`

Each file must be a complete XML document with this root:

```xml
<?xml version="1.0" encoding="utf-8"?>
<poincareContentPack
    version="3.0"
    packId="poincare-exercises-<category>-v3"
    mainCategory="<main-category>"
    created="2026-07-01"
    defaultLocale="nl-BE"
    source="original-generated"
    exerciseCount="<actual-count>"
    translationLocales="nl-BE,en-US,fr-BE">
```

Valid `mainCategory` values:

- `math`
- `language`
- `logic-puzzles`
- `world-themes`

## Required Global Structure Per XML File

Every XML file must contain these sections in this order:

```xml
<metadata>...</metadata>
<numberFormat ... />
<locales>...</locales>
<grades>...</grades>
<difficultyLevels>...</difficultyLevels>
<subcategories>...</subcategories>
<exercises>...</exercises>
```

Use these locale definitions exactly:

```xml
<locales>
  <locale code="nl-BE" label="Nederlands (Belgie)"/>
  <locale code="en-US" label="English (US)"/>
  <locale code="fr-BE" label="Francais (Belgique)"/>
</locales>
```

Use these grade definitions exactly:

```xml
<grades min="1" max="6">
  <grade value="1" display="1"/>
  <grade value="2" display="2"/>
  <grade value="3" display="3"/>
  <grade value="4" display="4"/>
  <grade value="5" display="5"/>
  <grade value="6" display="6"/>
</grades>
```

Use these difficulty definitions exactly:

```xml
<difficultyLevels minStars="1" maxStars="3">
  <difficulty stars="1" display="★" displayValue="1"/>
  <difficulty stars="2" display="★★" displayValue="2"/>
  <difficulty stars="3" display="★★★" displayValue="3"/>
</difficultyLevels>
```

## Number Formatting Rules

Visible numbers must be formatted as a Belgian/Dutch learner expects:

- Seven must be `7`, never `0.007`.
- Seven thousand must be `7.000`.
- Twelve thousand three hundred forty-five must be `12.345`.
- Decimal comma must be used for decimal values: `3,5`, `0,25`, `12,75`.
- Do not zero-pad whole numbers below 1.000.
- Do not use English decimal dots for visible values.
- Do not mix thousands separators and decimal separators incorrectly.

Use this number format element:

```xml
<numberFormat visibleIntegerPattern="#.###" visibleDecimalPattern="#.###,0" decimalSeparator="," groupingSeparator="." zeroPadBelowThousand="false" appliesTo="visible-values"/>
```

Grade and difficulty attributes must remain numeric attributes:

```xml
 grade="1" difficultyStars="2"
```

Optional display attributes may use normal visible values:

```xml
 gradeDisplay="1" difficultyValueDisplay="2" variationDisplay="14"
```

## Translation Requirements

Every exercise must contain exactly three translations:

1. `nl-BE`
2. `en-US`
3. `fr-BE`

Each translation must contain:

```xml
<title>...</title>
<instruction>...</instruction>
<question>...</question>
<answers>...</answers>
<feedback>
  <correct>...</correct>
  <incorrect>...</incorrect>
</feedback>
```

Important:

- The Dutch version is the source version.
- English and French must be faithful translations, not different exercises.
- Answer options must correspond semantically across all languages.
- Exactly one answer must have `correct="true"`.
- Each exercise must have at least 4 answer options for `multiple_choice` and `puzzle` XML items unless the exercise is explicitly open input.
- Do not duplicate the correct answer as an incorrect answer in the same translation.
- Keep language age-appropriate for children.

## Exercise ID Format

Every exercise id must be unique and deterministic.

Use this pattern:

```text
pc-v3-<mainCategory>-<subcategoryId>-g<grade two digits>-s<difficultyStars>-v<variation two or three digits>
```

Examples:

```text
pc-v3-math-add-subtract-g01-s1-v001
pc-v3-language-reading-g03-s2-v018
pc-v3-logic-puzzles-sudoku-g02-s3-v040
pc-v3-world-themes-animals-g01-s1-v012
```

Use lowercase IDs. Use hyphens, not spaces or underscores.

## Common Exercise Attributes

Every exercise must include:

```xml
<exercise
  id="..."
  mainCategory="..."
  subcategoryId="..."
  grade="1"
  gradeDisplay="1"
  difficultyStars="1"
  difficultyDisplay="★"
  difficultyValueDisplay="1"
  variation="1"
  variationDisplay="1"
  type="multiple_choice"
  contentType="online-exercise"
  iconRef="..."
  skillRef="..."
  skillIconRef="...">
```

Valid `type` values:

- `multiple_choice`
- `open_numeric`
- `open_text`
- `fill_blank`
- `puzzle`

Valid `contentType` values:

- `online-exercise`
- `game`

Use `contentType="game"` only for generated puzzle/game entries such as sudoku and math crosswords.

## Content Balance Requirements

For every subcategory:

- Generate exercises for grades 1 through 6.
- Generate difficulty levels 1, 2, and 3 stars.
- Generate many variations for every grade/difficulty combination.
- Recommended minimum: 30 variations per grade per difficulty per subcategory.
- Recommended high-volume target: 100 variations per grade per difficulty per subcategory.
- Avoid repeated wording where possible.
- Avoid repeated correct answer positions. The app shuffles answers, but source content should still be varied.
- Avoid exercises that are ambiguous or have multiple valid answers unless explicitly designed for that and accepted by the app.

## Main Category: Math

File: `poincare-exercises-math.xml`

Root:

```xml
<poincareContentPack ... mainCategory="math" ...>
```

Subcategories must be exactly:

```xml
<subcategory id="numbers-10000" label="getallen tot 10.000" labelEn="numbers up to 10.000" labelFr="nombres jusque 10.000" iconRef="topic-getallen-tot-10000" skillRef="NUM-PLACE" skillIconRef="skill-num-place"/>
<subcategory id="add-subtract" label="optellen en aftrekken" labelEn="addition and subtraction" labelFr="additions et soustractions" iconRef="topic-optellen-aftrekken" skillRef="ADD-SUB" skillIconRef="skill-add-sub"/>
<subcategory id="multiply-divide" label="vermenigvuldigen en delen" labelEn="multiplication and division" labelFr="multiplications et divisions" iconRef="topic-vermenigvuldigen-delen" skillRef="MUL-DIV" skillIconRef="skill-mul-div"/>
<subcategory id="division-remainder" label="delen met rest" labelEn="division with remainder" labelFr="divisions avec reste" iconRef="topic-delen-met-rest" skillRef="MUL-DIV" skillIconRef="skill-mul-div"/>
<subcategory id="fractions" label="breuken" labelEn="fractions" labelFr="fractions" iconRef="topic-breuken" skillRef="FRAC" skillIconRef="skill-frac"/>
<subcategory id="time" label="tijd" labelEn="time" labelFr="temps" iconRef="topic-tijd" skillRef="TIME" skillIconRef="skill-time"/>
<subcategory id="money" label="geld" labelEn="money" labelFr="argent" iconRef="topic-geld" skillRef="MONEY" skillIconRef="skill-money"/>
<subcategory id="measure" label="meten" labelEn="measurement" labelFr="mesures" iconRef="topic-meten" skillRef="MEASURE" skillIconRef="skill-measure"/>
<subcategory id="word-problems" label="vraagstukken" labelEn="word problems" labelFr="problemes" iconRef="topic-vraagstukken" skillRef="WORD" skillIconRef="skill-word"/>
<subcategory id="mixed-practice" label="gemengde herhaling" labelEn="mixed practice" labelFr="revision mixte" iconRef="topic-gemengde-herhaling" skillRef="LOGIC" skillIconRef="skill-logic"/>
<subcategory id="clock-reading" label="klokkijken" labelEn="telling time" labelFr="lecture de l'heure" iconRef="topic-klokkijken" skillRef="CLOCK-READ" skillIconRef="skill-clock-reading"/>
<subcategory id="times-tables" label="tafels" labelEn="times tables" labelFr="tables de multiplication" iconRef="topic-tafels" skillRef="TABLES" skillIconRef="skill-times-tables"/>
```

### Math Subcategory Requirements

#### `numbers-10000`

Exercise types:

- `multiple_choice`
- `open_numeric`

Skills:

- Number recognition
- Place value
- Ordering numbers
- Rounding
- Comparing numbers
- Number decomposition
- Finding missing number in sequence

Difficulty guidance:

- 1 star: numbers up to 100 for grade 1, up to 1.000 for higher grades.
- 2 stars: include place-value reasoning, ordering, and rounding.
- 3 stars: include multi-step comparison, nearest ten/hundred/thousand, sequences.

Examples:

```xml
<question>Welk getal is groter dan 348?</question>
<answer correct="true">354</answer>
```

```xml
<question>Wat is 7.438 afgerond op het dichtstbijzijnde honderdtal?</question>
<answer correct="true">7.400</answer>
```

#### `add-subtract`

Exercise types:

- `multiple_choice`
- `open_numeric`

Skills:

- Addition without carry
- Addition with carry
- Subtraction without borrow
- Subtraction with borrow
- Missing addend
- Number bonds
- Mental arithmetic

Difficulty guidance:

- 1 star: small numbers, no carry/borrow.
- 2 stars: carry/borrow, numbers up to grade-appropriate range.
- 3 stars: two-step calculations or missing values.

Rules:

- Avoid negative results unless explicitly asked for older grades.
- All answers must be integers.

#### `multiply-divide`

Exercise types:

- `multiple_choice`
- `open_numeric`

Skills:

- Multiplication facts
- Division facts
- Equal groups
- Missing factor
- Related multiplication/division pairs

Difficulty guidance:

- 1 star: tables 1, 2, 5, 10.
- 2 stars: tables up to 10.
- 3 stars: mixed tables, missing factor, simple two-step multiplication/division.

Rules:

- Division must be exact unless subcategory is `division-remainder`.
- Never generate decimal answers here.

#### `division-remainder`

Exercise types:

- `multiple_choice`
- `open_numeric`

Skills:

- Division with remainder
- Interpreting quotient and remainder
- Sharing into groups

Answer format:

Use one of these formats consistently within an exercise:

- `7 rest 2`
- `7 r 2`

Prefer `7 rest 2` in Dutch.

#### `fractions`

Exercise types:

- `multiple_choice`
- `open_text`

Skills:

- Recognize fractions
- Compare simple fractions
- Equivalent fractions
- Fraction of a quantity
- Simple addition/subtraction of like denominators

Answer format:

- Use `1/2`, `3/4`, `2/3`.
- Avoid complex Unicode fraction characters.

#### `time`

Exercise types:

- `multiple_choice`
- `open_text`

Skills:

- Days, weeks, months
- Duration
- Calendar reasoning
- Time before/after

Formats:

- Use `14:30` for digital time.
- Use plain language for younger grades: `half drie`, `kwart over vier`.

#### `clock-reading`

Exercise types:

- `multiple_choice`
- `open_text`

Skills:

- Hour
- Half hour
- Quarter past/to
- Five-minute intervals
- 24-hour time for higher grades

#### `money`

Exercise types:

- `multiple_choice`
- `open_numeric`
- `open_text`

Skills:

- Coin recognition
- Adding euro amounts
- Change
- Price comparison
- Simple shopping problems

Formatting:

- Use `€ 3,50` for money display.
- Use comma decimal separator.

#### `measure`

Exercise types:

- `multiple_choice`
- `open_text`
- `open_numeric`

Skills:

- Length, mass, volume
- Unit conversion
- Choosing correct unit
- Reading simple measurements

Use Belgian/Dutch units:

- mm, cm, m, km
- g, kg
- ml, cl, l

#### `word-problems`

Exercise types:

- `multiple_choice`
- `open_numeric`

Skills:

- Translate story to arithmetic
- One-step and two-step problems
- Addition, subtraction, multiplication, division

Rules:

- Keep stories short.
- Use age-appropriate vocabulary.
- Avoid cultural assumptions.

#### `mixed-practice`

Exercise types:

- `multiple_choice`
- `open_numeric`

Skills:

- Mixed review of current grade math skills.
- Vary operation and topic.

#### `times-tables`

Exercise types:

- `multiple_choice`
- `open_numeric`

Skills:

- Multiplication tables
- Missing factor
- Division relation
- Speed practice

## Main Category: Language

File: `poincare-exercises-language.xml`

Root:

```xml
<poincareContentPack ... mainCategory="language" ...>
```

Important app mapping:

- Subcategories `writing`, `dictation`, `sentence-structure`, and `grammar` are shown under the app main category `Schrijven` / WRITING.
- All other language subcategories are shown under `Communicatie` / READING.

Subcategories must be exactly:

```xml
<subcategory id="reading" label="lezen" labelEn="reading" labelFr="lecture" iconRef="topic-lezen" skillRef="READ" skillIconRef="skill-reading"/>
<subcategory id="writing" label="schrijven" labelEn="writing" labelFr="ecriture" iconRef="topic-schrijven" skillRef="WRITE" skillIconRef="skill-writing"/>
<subcategory id="listening" label="luisteren" labelEn="listening" labelFr="ecoute" iconRef="topic-luisteren" skillRef="LISTEN" skillIconRef="skill-listening"/>
<subcategory id="speaking" label="spreken" labelEn="speaking" labelFr="expression orale" iconRef="topic-spreken" skillRef="SPEAK" skillIconRef="skill-speaking"/>
<subcategory id="reading-comprehension" label="begrijpend lezen" labelEn="reading comprehension" labelFr="comprehension a la lecture" iconRef="topic-begrijpend-lezen" skillRef="READ-COMP" skillIconRef="skill-reading-comprehension"/>
<subcategory id="spelling" label="spelling" labelEn="spelling" labelFr="orthographe" iconRef="topic-spelling" skillRef="SPELL" skillIconRef="skill-spelling"/>
<subcategory id="dictation" label="dictee" labelEn="dictation" labelFr="dictee" iconRef="topic-dictee" skillRef="DICT" skillIconRef="skill-dictation"/>
<subcategory id="vocabulary" label="woordenschat" labelEn="vocabulary" labelFr="vocabulaire" iconRef="topic-woordenschat" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="letters" label="letters" labelEn="letters" labelFr="lettres" iconRef="topic-letters" skillRef="LETTERS" skillIconRef="skill-letters"/>
<subcategory id="phonological-awareness" label="fonologisch bewustzijn" labelEn="phonological awareness" labelFr="conscience phonologique" iconRef="topic-phonological-awareness" skillRef="PHONO" skillIconRef="skill-phonological-awareness"/>
<subcategory id="rhyming" label="rijmen" labelEn="rhyming" labelFr="rimes" iconRef="topic-rhyming" skillRef="RHYME" skillIconRef="skill-rhyming"/>
<subcategory id="blending-segmenting" label="hakken en plakken" labelEn="blending and segmenting" labelFr="decouper et fusionner les sons" iconRef="topic-blending-segmenting" skillRef="BLEND-SEG" skillIconRef="skill-blending-segmenting"/>
<subcategory id="technical-reading" label="technisch lezen" labelEn="technical reading" labelFr="lecture technique" iconRef="topic-technical-reading" skillRef="TECH-READ" skillIconRef="skill-technical-reading"/>
<subcategory id="sentence-structure" label="zinsbouw" labelEn="sentence structure" labelFr="structure de phrase" iconRef="topic-sentence-structure" skillRef="SENT-STRUCT" skillIconRef="skill-sentence-structure"/>
<subcategory id="grammar" label="grammatica" labelEn="grammar" labelFr="grammaire" iconRef="topic-grammar" skillRef="GRAMMAR" skillIconRef="skill-grammar"/>
```

### Language Subcategory Requirements

#### `reading`

Exercise types:

- `multiple_choice`

Skills:

- Word recognition
- Sentence reading
- Identify subject/object/action in sentence
- Match word to meaning

Use short sentences for grades 1-2.

#### `reading-comprehension`

Exercise types:

- `multiple_choice`

Skills:

- Literal comprehension
- Inference
- Sequence of events
- Cause and effect

For higher grades, use short paragraphs of 2-4 sentences.

#### `listening`

Exercise types:

- `multiple_choice`

Important app behavior:

- These exercises show a play button.
- The play button speaks the correct target value/word, not the full visible question.
- The visible question is spoken only when the user taps the question text.
- For grades 1 and 2, answer options use a two-tap behavior: first tap speaks/selects the option, second tap submits.

Generation rules:

- The correct answer should be the exact item that must be heard.
- Options should be short: letters, syllables, words, or short phrases.
- Avoid answers that sound too similar unless the skill is explicitly auditory discrimination.

Good pattern:

```xml
<instruction>Luister en kies wat je hoort.</instruction>
<question>Tik op de luisterknop. Welk woord hoor je?</question>
<answers>
  <answer id="a" correct="true">maan</answer>
  <answer id="b" correct="false">man</answer>
  <answer id="c" correct="false">maar</answer>
  <answer id="d" correct="false">naam</answer>
</answers>
```

#### `speaking`

Exercise types:

- `multiple_choice`

Since the current app does not record speech, make speaking exercises recognition/planning exercises:

- Choose what to say in a situation.
- Choose the polite sentence.
- Choose the sentence that answers the question.

#### `writing`

Exercise types:

- `open_text`
- `fill_blank`
- `multiple_choice`

Skills:

- Word completion
- Copy word
- Choose correct written form
- Short sentence completion

#### `dictation`

Exercise types:

- `open_text`
- `multiple_choice`

Current app can use text entry or choice. Keep dictation words short and clear.

#### `spelling`

Exercise types:

- `multiple_choice`
- `open_text`
- `fill_blank`

Skills:

- Correct spelling
- Vowel/consonant patterns
- Common spelling rules
- Homophones for higher grades

#### `vocabulary`

Exercise types:

- `multiple_choice`

Skills:

- Meaning
- Synonyms
- Antonyms
- Category membership
- Context word choice

#### `letters`

Exercise types:

- `multiple_choice`
- `puzzle`

Skills:

- Letter recognition
- Uppercase/lowercase matching
- Initial sound
- Final sound

#### `phonological-awareness`

Exercise types:

- `multiple_choice`

Skills:

- First sound
- Last sound
- Number of syllables
- Sound isolation

#### `rhyming`

Exercise types:

- `multiple_choice`

Skills:

- Recognize rhyming words
- Choose word that rhymes
- Identify odd one out

#### `blending-segmenting`

Exercise types:

- `multiple_choice`

Skills:

- Blend sounds into words
- Segment word into sounds
- Choose word from sound parts

#### `technical-reading`

Exercise types:

- `multiple_choice`

Skills:

- Read pseudo-words
- Read compound words
- Read longer words by chunks
- Recognize syllable structure

#### `sentence-structure`

Exercise types:

- `multiple_choice`
- `fill_blank`

Skills:

- Put sentence in correct order
- Choose grammatically correct sentence
- Complete sentence with right word

#### `grammar`

Exercise types:

- `multiple_choice`
- `fill_blank`

Skills:

- Nouns, verbs, adjectives
- Singular/plural
- Verb tense
- Pronouns
- Sentence punctuation

## Main Category: Logic Puzzles

File: `poincare-exercises-logic-puzzles.xml`

Root:

```xml
<poincareContentPack ... mainCategory="logic-puzzles" ...>
```

Subcategories must be exactly:

```xml
<subcategory id="sudoku" label="sudoku" labelEn="sudoku" labelFr="sudoku" iconRef="topic-sudoku" skillRef="LOGIC" skillIconRef="skill-logic"/>
<subcategory id="math-crosswords" label="math crosswords" labelEn="math crosswords" labelFr="mots croises mathematiques" iconRef="topic-math-crosswords" skillRef="LOGIC" skillIconRef="skill-logic"/>
<subcategory id="logic" label="logica" labelEn="logic" labelFr="logique" iconRef="topic-logica" skillRef="LOGIC" skillIconRef="skill-logic"/>
<subcategory id="memory" label="memory" labelEn="memory" labelFr="memory" iconRef="topic-memory" skillRef="MEMORY" skillIconRef="skill-memory"/>
<subcategory id="mazes" label="doolhoven" labelEn="mazes" labelFr="labyrinthes" iconRef="topic-doolhoven" skillRef="MAZES" skillIconRef="skill-mazes"/>
<subcategory id="spatial-insight" label="topologie / ruimtelijk inzicht" labelEn="spatial insight" labelFr="reperage spatial" iconRef="topic-spatial-insight" skillRef="SPATIAL" skillIconRef="skill-spatial-insight"/>
<subcategory id="shapes" label="vormen" labelEn="shapes" labelFr="formes" iconRef="topic-shapes" skillRef="SHAPES" skillIconRef="skill-shapes"/>
<subcategory id="patterns" label="patronen" labelEn="patterns" labelFr="motifs" iconRef="topic-patterns" skillRef="PATTERNS" skillIconRef="skill-patterns"/>
```

### Logic Puzzle Subcategory Requirements

#### `sudoku`

Exercise type:

- `puzzle`

Content type:

- `game`

Important app behavior:

- The app generates an interactive Sudoku game by difficulty.
- XML entries are used as launch/configuration entries.
- The question text should introduce the game, not contain a full Sudoku grid.

Difficulty behavior in app:

- 1 star: 4x4 Sudoku
- 2 stars: 6x6 Sudoku
- 3 stars: 9x9 Sudoku

Recommended XML pattern:

```xml
<title>Sudoku</title>
<instruction>Vul de sudoku aan.</instruction>
<question>Sleep de cijfers naar de juiste vakjes. Elk cijfer mag maar een keer per rij, kolom en vak voorkomen.</question>
<answers>
  <answer id="a" correct="true">start</answer>
  <answer id="b" correct="false">stop</answer>
  <answer id="c" correct="false">pauze</answer>
  <answer id="d" correct="false">overslaan</answer>
</answers>
```

#### `math-crosswords`

Exercise type:

- `puzzle`

Content type:

- `game`

Important app behavior:

- The app generates an interactive 17x17 logical math crossword by difficulty.
- Empty grid cells are part of the logical grid but not visible.
- XML entries are launch/configuration entries.

Recommended XML question:

```xml
<question>Sleep de getallen naar de juiste vakjes zodat alle horizontale en verticale sommen kloppen.</question>
```

#### `logic`

Exercise types:

- `multiple_choice`

Skills:

- Deduction
- True/false reasoning
- Ordering clues
- Odd one out
- Simple condition logic

#### `memory`

Exercise types:

- `multiple_choice`

Since the app does not yet implement a full memory-card game, create observation and recall choices:

- Which item was shown?
- Which item belongs together?
- Which sequence is correct?

#### `mazes`

Exercise types:

- `multiple_choice`

Skills:

- Direction following
- Which route reaches target
- Left/right/up/down
- Count steps

#### `spatial-insight`

Exercise types:

- `multiple_choice`

Skills:

- Position words
- Rotation
- Mirror images
- Top/bottom/left/right
- Simple maps

#### `shapes`

Exercise types:

- `multiple_choice`

Skills:

- Recognize shapes
- Count sides/corners
- Match 2D/3D shapes
- Shape properties

#### `patterns`

Exercise types:

- `multiple_choice`

Skills:

- Complete sequence
- Repeating patterns
- Growing patterns
- Numeric and visual patterns

## Main Category: World Themes

File: `poincare-exercises-world-themes.xml`

Root:

```xml
<poincareContentPack ... mainCategory="world-themes" ...>
```

Important app mapping:

- `world-themes` exercises are shown under the app main category `Communicatie` / READING.

Subcategories must be exactly:

```xml
<subcategory id="colors" label="kleuren" labelEn="colors" labelFr="couleurs" iconRef="topic-colors" skillRef="COLORS" skillIconRef="skill-colors"/>
<subcategory id="emotions" label="emoties" labelEn="emotions" labelFr="emotions" iconRef="topic-emotions" skillRef="EMOTIONS" skillIconRef="skill-emotions"/>
<subcategory id="animals" label="dieren" labelEn="animals" labelFr="animaux" iconRef="theme-animals" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="vehicles" label="voertuigen" labelEn="vehicles" labelFr="vehicules" iconRef="theme-vehicles" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="seasons" label="seizoenen" labelEn="seasons" labelFr="saisons" iconRef="theme-seasons" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="days" label="dagen" labelEn="days" labelFr="jours" iconRef="theme-days" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="months" label="maanden" labelEn="months" labelFr="mois" iconRef="theme-months" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="food" label="eten" labelEn="food" labelFr="nourriture" iconRef="theme-food" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="fruit" label="fruit" labelEn="fruit" labelFr="fruits" iconRef="theme-fruit" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="vegetables" label="groenten" labelEn="vegetables" labelFr="legumes" iconRef="theme-vegetables" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="body" label="lichaam" labelEn="body" labelFr="corps" iconRef="theme-body" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="nature" label="natuur" labelEn="nature" labelFr="nature" iconRef="theme-nature" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="space" label="ruimte" labelEn="space" labelFr="espace" iconRef="theme-space" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="music" label="muziek" labelEn="music" labelFr="musique" iconRef="theme-music" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
<subcategory id="sport" label="sport" labelEn="sport" labelFr="sport" iconRef="theme-sport" skillRef="VOCAB" skillIconRef="skill-vocabulary"/>
```

### World Themes Requirements

Exercise types:

- `multiple_choice`
- Optional `fill_blank` for higher grades

Skills:

- Vocabulary recognition
- Category membership
- Function/use of object
- Word meaning
- Simple world knowledge
- Match word to theme

Theme-specific guidance:

- `colors`: identify color category, color mixing for higher grades, descriptive color words.
- `emotions`: identify emotion from situation, synonym/antonym, appropriate reaction.
- `animals`: animal names, habitats, young animals, sounds, characteristics.
- `vehicles`: vehicle type, where it travels, purpose.
- `seasons`: season characteristics, order of seasons, weather.
- `days`: weekdays, yesterday/today/tomorrow.
- `months`: month order, seasons, holidays only if generic and not culturally narrow.
- `food`: food categories, meals, healthy choices.
- `fruit`: fruit names, colors, categories.
- `vegetables`: vegetable names, colors, categories.
- `body`: body parts, senses, simple health.
- `nature`: plants, weather, landscapes, animals in nature.
- `space`: planets, moon, stars, astronauts; keep science age-appropriate.
- `music`: instruments, rhythm, high/low/loud/soft sounds.
- `sport`: sports, equipment, movement verbs.

## Difficulty by Grade

### Grade 1

- Short questions.
- Concrete words.
- Mostly recognition and one-step reasoning.
- Answer options should be short.

### Grade 2

- Slightly longer questions.
- Simple two-step reasoning.
- More distractors that are plausible.

### Grade 3

- More reading comprehension.
- Larger numbers.
- Multi-step arithmetic begins.
- More vocabulary nuance.

### Grade 4

- More complex language.
- Conversions and multi-step math.
- More abstract reasoning.

### Grade 5

- Fractions, decimals, percentages may appear in math.
- More inference in reading.
- More precise grammar and vocabulary.

### Grade 6

- Preparation for secondary-school style reasoning.
- Multi-step problems.
- Richer texts and higher abstraction.

## Difficulty Stars

### 1 star

- Direct recognition or one-step calculation.
- Few distractors that are close to correct.
- Short instructions.

### 2 stars

- More plausible distractors.
- Slightly larger numbers or longer text.
- One extra reasoning step.

### 3 stars

- Multi-step reasoning.
- More distractors that reveal common mistakes.
- Higher precision or abstraction.

## Answer Option Rules

For `multiple_choice`:

- Use exactly four answers: `a`, `b`, `c`, `d`.
- Exactly one must be `correct="true"`.
- Distractors must be plausible and educational.
- Avoid duplicate answer text in the same translation.
- Avoid trick questions.
- Avoid ambiguous wording.

## Feedback Rules

Every translation must have feedback:

```xml
<feedback>
  <correct>...</correct>
  <incorrect>...</incorrect>
</feedback>
```

Correct feedback:

- Short.
- Positive.
- Mention the reasoning when helpful.

Incorrect feedback:

- Encouraging.
- Gives a hint, not the full answer unless the question is very simple.

## Tags

Every exercise must include tags:

```xml
<tags>
  <tag><mainCategory></tag>
  <tag><subcategoryId></tag>
  <tag>grade-<grade></tag>
  <tag>stars-<difficultyStars></tag>
</tags>
```

Example:

```xml
<tags>
  <tag>math</tag>
  <tag>add-subtract</tag>
  <tag>grade-2</tag>
  <tag>stars-1</tag>
</tags>
```

## Required Validation Before Returning XML

Before returning the generated XML, verify:

1. XML is well-formed.
2. Root `exerciseCount` equals the actual number of `<exercise>` elements.
3. Every exercise id is unique.
4. Every exercise has translations for `nl-BE`, `en-US`, `fr-BE`.
5. Every translation has exactly one correct answer.
6. No duplicate answer text within the same translation.
7. Every subcategory id matches the exact allowed list.
8. Every grade is 1 through 6.
9. Every difficulty is 1 through 3.
10. Visible whole numbers are not zero-padded.
11. `7` is shown as `7`, not `0.007`.
12. `7.000` is used only when the value is seven thousand.
13. Decimal values use comma decimal separator.
14. No copyrighted or copied content is present.
15. Language is age-appropriate.

## Minimal Exercise Template

```xml
<exercise id="pc-v3-math-add-subtract-g01-s1-v001" mainCategory="math" subcategoryId="add-subtract" grade="1" gradeDisplay="1" difficultyStars="1" difficultyDisplay="★" difficultyValueDisplay="1" variation="1" variationDisplay="1" type="multiple_choice" contentType="online-exercise" iconRef="topic-optellen-aftrekken" skillRef="ADD-SUB" skillIconRef="skill-add-sub">
  <translations>
    <translation locale="nl-BE">
      <title>Optellen</title>
      <instruction>Kies het juiste antwoord.</instruction>
      <question>Wat is 4 + 3?</question>
      <answers>
        <answer id="a" correct="true">7</answer>
        <answer id="b" correct="false">6</answer>
        <answer id="c" correct="false">8</answer>
        <answer id="d" correct="false">9</answer>
      </answers>
      <feedback>
        <correct>Juist. 4 + 3 = 7.</correct>
        <incorrect>Tel rustig verder vanaf 4.</incorrect>
      </feedback>
    </translation>
    <translation locale="en-US">
      <title>Addition</title>
      <instruction>Choose the correct answer.</instruction>
      <question>What is 4 + 3?</question>
      <answers>
        <answer id="a" correct="true">7</answer>
        <answer id="b" correct="false">6</answer>
        <answer id="c" correct="false">8</answer>
        <answer id="d" correct="false">9</answer>
      </answers>
      <feedback>
        <correct>Correct. 4 + 3 = 7.</correct>
        <incorrect>Count on carefully from 4.</incorrect>
      </feedback>
    </translation>
    <translation locale="fr-BE">
      <title>Addition</title>
      <instruction>Choisis la bonne reponse.</instruction>
      <question>Combien font 4 + 3 ?</question>
      <answers>
        <answer id="a" correct="true">7</answer>
        <answer id="b" correct="false">6</answer>
        <answer id="c" correct="false">8</answer>
        <answer id="d" correct="false">9</answer>
      </answers>
      <feedback>
        <correct>Correct. 4 + 3 = 7.</correct>
        <incorrect>Compte calmement a partir de 4.</incorrect>
      </feedback>
    </translation>
  </translations>
  <tags>
    <tag>math</tag>
    <tag>add-subtract</tag>
    <tag>grade-1</tag>
    <tag>stars-1</tag>
  </tags>
</exercise>
```

## Final Instruction To Generator

Generate large, balanced, original XML exercise packs following every rule above. Prefer quality, correctness, and variety over rushing. Return complete XML files, not fragments, unless explicitly asked for only a subset.
