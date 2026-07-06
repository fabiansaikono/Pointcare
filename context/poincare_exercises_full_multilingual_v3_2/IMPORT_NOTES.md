# Import notes for Poincaré Android

1. Copy the XML files into the app content import folder.
2. Import `poincare-exercises-index-v3.xml` first.
3. Import the four main category XML files using the fixed file names.
4. Use the `id` / `progressKey` as the stable key for user progress.
5. Store progress separately from the XML, for example in Room or DataStore.
6. Use the index counts for total subcategory and grade counts.
7. Use progress rows to calculate:
   - total exercises in subcategory
   - total exercises in selected grade
   - completed exercises
   - retry_wrong exercises
   - not_started exercises
8. Do not duplicate answer options in the UI. The XML already enforces unique displays and values, but the import should verify this.
9. For shape exercises, show the symbol from `<display>`, never convert it back to text.
10. Do not expect sudoku or math_crossword XML exercises. Route those subcategories to their generators.
