Please update the Poincaré Android importer and category UI for content pack v3.7.

Important category correction:

- `time` belongs under `geometry`, not `math`.
- `measurement` belongs under `geometry`, not `math`.
- `geometry_shapes` remains under `geometry`.

After importing v3.7, the Math main category must show only math calculation subcategories such as addition, subtraction, multiplication, division, mixed arithmetic, number sequences, fractions, and money.

The Geometry main category must show:

- Time
- Measurement
- Shapes and geometry

Do not hardcode old category mappings. Use `poincare-exercises-index-v3.xml` as the source of truth for mainCategory/subcategory placement and counts.

Also validate during import:

- Every exercise ID is unique.
- Every exercise has exactly one correct answer.
- Answer values are unique within each exercise.
- Localized answer displays are unique within each exercise.
- Shape answers display symbols, not names.
- No Sudoku or Math Crossword XML exercises are imported.
- Time and Measurement exercises have `mainCategory="geometry"`.
- No exercise with `subCategory="time"` or `subCategory="measurement"` appears under Math.

If importing over older content, update the content and category placement but preserve progress for matching `progressKey`.
