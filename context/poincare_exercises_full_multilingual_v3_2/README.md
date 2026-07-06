# Poincaré exercise content pack v3.2

Generated: 2026-07-02

This pack contains 19200 exercises.

## Volume rule

- 100 exercises per included subcategory per grade
- 6 grades
- 600 exercises per included subcategory
- Difficulty levels are mixed inside every subcategory/grade: 34 one-star, 33 two-star, 33 three-star exercises

## Excluded generated puzzle types

The following subcategories are intentionally not included in this XML exercise pack because the app has generators for them:

- math_crossword
- sudoku

## Fixed file names

- poincare-exercises-math-v3.xml
- poincare-exercises-language-v3.xml
- poincare-exercises-logic-puzzles-v3.xml
- poincare-exercises-world-themes-v3.xml
- poincare-exercises-index-v3.xml
- poincare-content-pack-v3-full.xsd
- manifest.json
- IMPORT_NOTES.md
- VS_CODE_PROMPT.md

## ID policy

Every exercise id follows:

`{subCategory}_d{difficulty}_g{grade}_seq{sequence4}`

Example:

`addition_d1_g1_seq0001`

The same value is also stored as `progressKey`.

## Progress tracking

Track progress by `progressKey` with these states:

- `not_started`
- `completed`
- `retry_wrong`

When the user answers incorrectly, mark the exercise as `retry_wrong`.
When the user later answers it correctly, mark it as `completed`.

## Counts

Use `poincare-exercises-index-v3.xml` or `manifest.json` to show:

- total exercises per subcategory
- total exercises per grade
- completed count from local progress
- retry count from local progress
- not-started count

## Answer rules

- Answer option displays are unique inside each exercise.
- Answer values are unique inside each exercise.
- Shape answers use symbols like `■`, `●`, `▲`, `▭`, `◆`, `★`, `⬡`, `⬟` instead of text.
- Visible numbers use Belgian formatting where applicable.
