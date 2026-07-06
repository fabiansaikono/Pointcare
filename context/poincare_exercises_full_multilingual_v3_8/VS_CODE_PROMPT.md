Please update the Poincaré Android importer and exercise UI for content pack v3.8.

Important visible-label rules:

- Do not add variant numbers or variant labels to exercise prompts.
- Do not show hidden generator values in visible labels.
- Do not show underscores in prompts.
- Use the prompt text from XML as the visible label.
- Do not prefix prompts with category, theme, variant, sequence, or debug values.

Prompt speech / TTS:

- When speaking a prompt, prefer `prompt/@ttsText`.
- Use the visible prompt text only as fallback.
- This is required for math, so `-` or `−` is read as minus/min/moins and not as “dash”.

Shape answers:

- Shape answer buttons may display symbols such as `■`, `●`, `▲`, `▭`, `◆`, `★`, `⬡`, `⬟`.
- When the user taps a shape answer button or accessibility reads it, do not read the Unicode name, ASCII number, or words like “filled”.
- Instead, read `answer/speak[@locale=currentLocale]`.
- Example: display `■`, speak `square` in English, `vierkant` in Dutch, `carré` in French.

Article grammar exercises:

- Some grammar exercises intentionally have only two answer options.
- Do not force four options.
- Example visible prompt: `Wat is het bepaald lidwoord van tafel?`
- Example answers: `de tafel` and `het tafel`.

Importer requirements:

- Import v3.8 idempotently.
- Preserve progress using `exercise/@progressKey`.
- Use localized answer displays for visible answer labels.
- Use `answer/@value` only for canonical correctness/progress, not for visible text when displays exist.
- Use `poincare-exercises-index-v3.xml` as the source of truth for categories and counts.
- Keep `time`, `measurement`, and `geometry_shapes` under Geometry.
- Do not import Sudoku or Math Crossword from XML.

Tests to add:

- No visible prompt contains `variant`, `variante`, or trailing fake numeric suffixes.
- No visible prompt contains underscores.
- Article exercises have exactly two answer options.
- Shape answers display symbols but expose localized `speak` text.
- Math prompts have `ttsText` where minus is written as a word.
- World knowledge grade 1 science does not repeat the same exact question 100 times.
- Importing v3.8 over older packs preserves progress.
