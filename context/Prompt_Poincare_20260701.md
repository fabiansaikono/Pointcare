I have two asset/content zip files that need to be implemented:

1. `poincare_icon_assets_final_complete_v4.zip`
2. `poincare_original_content_starter.zip`

The first zip contains the final complete icon library.
The second zip contains original educational content in XML format for Poincaré, including exercises and import metadata.

The goal is to implement both:

* the icon asset system
* the XML educational content import system

Important context:

* The app is called **Poincaré**.
* The app is an educational app for children.
* The main visual theme is dark / space-like.
* The main background color is `#050119`.
* The app should support multiple child profiles.
* Content should be imported in a reusable and future-proof way.
* The app should later support Dutch/Flemish, French and English.
* The first content pack is Dutch/Flemish.
* Do not use or import Junior Einstein assets or copied Junior Einstein content.
* The included Poincaré XML exercises are original internal-use content.
* Do not break existing screens or navigation.
* Inspect the existing project structure before changing anything.
* Reuse the existing architecture, package name, theme system, database layer and UI framework.
* If the app uses Jetpack Compose, implement Compose-friendly helpers.
* If the app uses XML views, implement View/XML-friendly helpers.
* If both are present, support both.

---

# Part 1 — Import the icon assets

Unzip:

`poincare_icon_assets_final_complete_v4.zip`

From the zip:

Copy all files from:

`android-vector-drawables/`

into:

`app/src/main/res/drawable/`

Make sure every drawable filename is valid for Android resources:

* lowercase
* underscores only
* no hyphens
* no spaces
* `.xml` extension

Do not copy the SVG files into `res/drawable` unless the project already uses SVGs somewhere. Prefer Android VectorDrawable XML for runtime use.

Copy these metadata files into:

`app/src/main/assets/poincare/icons/`

Files to copy:

* `poincare-icons-final-complete-pack-v1.xml`
* `poincare-icon-pack-v1.2-final-complete.xsd`
* `manifest.json`

Do not copy `icon-preview.html` into the app unless there is already an internal debug/preview feature where it belongs.

---

# Part 2 — Import the educational content XML

Unzip:

`poincare_original_content_starter.zip`

Copy the original educational content files into:

`app/src/main/assets/poincare/content/`

Expected files include:

* `poincare-original-math-l4-starter-001.xml`
* `poincare-content-pack-v1.xsd`
* `poincare-minimal-import-test.xml`
* `poincare-import-notes.md`
* `README.md`

If the later icon-enhanced content file is also present, copy it too:

* `poincare-original-math-l4-starter-001-with-icons.xml`
* `poincare-content-pack-v1.1-with-icons.xsd`

Preferred behavior:

* If `poincare-original-math-l4-starter-001-with-icons.xml` exists, use that as the primary import source.
* Otherwise, use `poincare-original-math-l4-starter-001.xml`.
* Keep the minimal test XML available for parser tests.

The content XML may include:

* articles
* exercises
* worksheets
* skills
* locales
* import config
* metadata
* topics
* difficulty levels
* answer options
* feedback
* icon references
* skill icon references

The implementation should parse the XML in a tolerant way. Do not crash if a section is missing.

---

# Part 3 — Create a reusable Kotlin icon registry

Create a small icon catalog/registry layer.

Suggested files:

* `PoincareIcon.kt`
* `PoincareIconCatalog.kt`
* `PoincareIconResolver.kt`

Use the existing package structure. Do not invent a new root package if the app already has one.

The icon registry should support at least these sections from the mapping XML:

* `skillIcons`
* `topicIcons`
* `uiIcons`
* `rewardIcons`
* `themeIcons`
* `contentTypeIcons`

The parser should also be tolerant of future sections. If a new section appears, do not crash. Store the section name as a string.

Create a data model similar to:

```kotlin
data class PoincareIcon(
    val id: String,
    val section: String,
    val label: String?,
    val androidVectorFile: String?,
    val svgFile: String?,
    val style: String?,
    val viewport: String?,
    val key: String?,
    val skillRef: String?,
    val topic: String?,
    val contentType: String?,
    val rewardType: String?,
    val theme: String?
)
```

The parser should read the XML from:

`assets/poincare/icons/poincare-icons-final-complete-pack-v1.xml`

It should parse each `<icon />` element and keep all known attributes.

---

# Part 4 — Resolve icon XML filenames to Android drawable resource IDs

Create a resolver function that converts mapping entries like:

`android-vector-drawables/ic_ui_home.xml`

to an Android drawable resource ID:

`R.drawable.ic_ui_home`

Do this safely.

Recommended approach:

* Strip folder path.
* Remove `.xml`.
* Normalize to Android resource name.
* Resolve using `resources.getIdentifier(...)`.
* Cache the result in memory.
* Return a fallback icon if the icon cannot be resolved.

Add a fallback drawable from the imported pack, preferably:

`ic_ui_info`

or another neutral icon if `ic_ui_info` does not exist.

Expose functions like:

```kotlin
@DrawableRes
fun resolveDrawableRes(context: Context, icon: PoincareIcon): Int

@DrawableRes
fun resolveById(context: Context, iconId: String): Int

@DrawableRes
fun resolveTopic(context: Context, topic: String): Int

@DrawableRes
fun resolveSkill(context: Context, skillRef: String): Int

@DrawableRes
fun resolveUi(context: Context, key: String): Int

@DrawableRes
fun resolveReward(context: Context, key: String): Int

@DrawableRes
fun resolveContentType(context: Context, key: String): Int
```

Normalize topic/key lookups:

* trim
* lowercase
* normalize accents where useful
* treat spaces, underscores and hyphens as equivalent where practical

Examples:

* `"begrijpend lezen"` should match the reading comprehension icon.
* `"math crosswords"` should match the math crosswords icon.
* `"ouderzone"` or `"parent-zone"` should resolve to the parent zone icon if present.
* `"snoepje"` should resolve to the candy/reward icon if present.
* `"poepje"` should resolve to the poop/reward icon if present.

---

# Part 5 — Create reusable Kotlin models for educational content

Create a clean content model layer for the Poincaré XML content.

Suggested files:

* `PoincareContentPack.kt`
* `PoincareExercise.kt`
* `PoincareArticle.kt`
* `PoincareWorksheet.kt`
* `PoincareAnswer.kt`
* `PoincareFeedback.kt`
* `PoincareSkill.kt`
* `PoincareContentParser.kt`
* `PoincareContentRepository.kt`

Adapt this to the existing architecture if similar models already exist.

Suggested models:

```kotlin
data class PoincareContentPack(
    val id: String?,
    val version: String?,
    val language: String?,
    val title: String?,
    val description: String?,
    val metadata: Map<String, String>,
    val skills: List<PoincareSkill>,
    val exercises: List<PoincareExercise>,
    val articles: List<PoincareArticle>,
    val worksheets: List<PoincareWorksheet>
)
```

```kotlin
data class PoincareExercise(
    val id: String,
    val subject: String?,
    val grade: Int?,
    val topic: String?,
    val skillRef: String?,
    val difficulty: Int?,
    val type: String?,
    val iconRef: String?,
    val skillIconRef: String?,
    val instruction: String?,
    val questionText: String?,
    val answers: List<PoincareAnswer>,
    val feedback: PoincareFeedback?,
    val tags: List<String>
)
```

```kotlin
data class PoincareAnswer(
    val id: String?,
    val text: String,
    val correct: Boolean
)
```

```kotlin
data class PoincareFeedback(
    val correct: String?,
    val incorrect: String?
)
```

```kotlin
data class PoincareSkill(
    val id: String,
    val label: String?,
    val description: String?,
    val iconRef: String?
)
```

Keep the parser tolerant. XML fields may evolve later.

---

# Part 6 — Parse the exercise XML

Create a parser that reads from:

`assets/poincare/content/poincare-original-math-l4-starter-001-with-icons.xml`

if available.

Fallback to:

`assets/poincare/content/poincare-original-math-l4-starter-001.xml`

The parser should support at least:

* metadata
* skills
* exercises
* subject
* grade
* topic
* skillRef
* difficulty
* type
* iconRef
* skillIconRef
* instruction
* question text
* answers
* correct answer flags
* feedback
* tags if present

Parsing requirements:

* Do not crash on missing optional fields.
* Log parser warnings in debug builds only.
* Ignore unknown tags safely.
* Preserve unknown metadata where practical.
* Return an empty list rather than crashing if the file is missing.
* Make the parser future-proof for additional content packs.

Use Android-safe XML parsing. Avoid insecure XML parser settings. Disable external entity resolution if applicable.

---

# Part 7 — Create a content repository

Create a repository that exposes the imported content to the app.

Suggested API:

```kotlin
class PoincareContentRepository {
    suspend fun loadContentPacks(): List<PoincareContentPack>
    suspend fun loadExercises(): List<PoincareExercise>
    suspend fun getExerciseById(id: String): PoincareExercise?
    suspend fun getExercisesByTopic(topic: String): List<PoincareExercise>
    suspend fun getExercisesBySkill(skillRef: String): List<PoincareExercise>
    suspend fun getExercisesByGrade(grade: Int): List<PoincareExercise>
    suspend fun getExercisesByDifficulty(difficulty: Int): List<PoincareExercise>
}
```

If the app already has a repository/database layer, integrate with that instead of creating a parallel system.

Preferred behavior:

* Load bundled XML content from assets on first app launch or first repository access.
* Cache parsed content in memory.
* If the app already uses Room, consider importing the XML exercises into Room.
* If Room is used, make the import idempotent.
* Do not duplicate exercises on every launch.
* Use exercise IDs as stable unique keys.

---

# Part 8 — Connect exercises to icons

When showing an exercise, topic, skill, worksheet, article or content card:

Preferred icon resolution order:

1. If the content has `iconRef`, resolve by icon ID.
2. Else if the content has `topic`, resolve by topic.
3. Else if the content has `skillRef`, resolve by skill.
4. Else if the content has `type`, resolve by content type.
5. Else use a fallback icon.

Examples:

* `topic="breuken"` should resolve to the fractions icon.
* `topic="sudoku"` should resolve to the sudoku icon.
* `skillRef="SPELL"` should resolve to the spelling icon.
* `type="multiple_choice"` should resolve to a generic online exercise or quiz icon.
* `type="worksheet"` should resolve to the worksheet icon.
* `iconRef="topic-breuken"` should directly resolve that icon ID.

---

# Part 9 — Add UI helpers

If the project uses Jetpack Compose, create helper composables such as:

```kotlin
@Composable
fun PoincareIcon(
    icon: PoincareIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
)
```

Also create convenience helpers such as:

```kotlin
@Composable
fun PoincareTopicIcon(topic: String, ...)

@Composable
fun PoincareSkillIcon(skillRef: String, ...)

@Composable
fun PoincareUiIcon(key: String, ...)

@Composable
fun PoincareExerciseIcon(exercise: PoincareExercise, ...)
```

Use the app’s existing colors. If no icon tint is provided, use a theme-aware icon color that works on `#050119`.

If the project uses XML Views, create extensions/helpers such as:

```kotlin
fun ImageView.setPoincareIcon(icon: PoincareIcon, tint: Int? = null)

fun ImageView.setPoincareTopicIcon(topic: String, tint: Int? = null)

fun ImageView.setPoincareUiIcon(key: String, tint: Int? = null)

fun ImageView.setPoincareExerciseIcon(exercise: PoincareExercise, tint: Int? = null)
```

---

# Part 10 — Add a simple exercise list / preview where safe

If the app already has a learning/exercise overview screen, integrate the imported XML exercises into that screen.

If there is no safe existing screen, add a small debug-only preview screen if the project already has a debug/developer area.

The preview should show:

* exercise title or question
* topic
* skillRef
* difficulty
* type
* resolved icon
* number of answers
* correct answer indicator only in debug mode or when reviewing content

Do not expose answer correctness in the normal child exercise flow unless the exercise has been answered.

---

# Part 11 — Add a simple exercise runner only if it fits the existing app

If there is already an exercise flow, wire the parsed exercises into it.

For `multiple_choice` exercises:

* Show instruction.
* Show question text.
* Show answer options.
* Let the child choose an answer.
* Show positive feedback when correct.
* Show gentle hint/feedback when incorrect.
* Save progress if the app already has progress tracking.

Do not create a completely separate exercise engine if the app already has one. Use adapters.

If no exercise engine exists yet, create only a minimal, isolated prototype screen that can later be replaced.

---

# Part 12 — Progress tracking integration

If the app already has progress tracking or child profiles:

* Link exercise attempts to the active child profile.
* Store:

  * exercise ID
  * child profile ID
  * selected answer
  * correctness
  * timestamp
  * attempt number
  * duration if available
* Do not require online sync for this implementation unless the app already has sync infrastructure.
* Keep it local-first.
* Make the model ready for later online sync.

If progress tracking does not exist yet, do not overbuild it. Add a small TODO-friendly interface, for example:

```kotlin
interface PoincareProgressTracker {
    suspend fun recordExerciseAttempt(
        profileId: String,
        exerciseId: String,
        selectedAnswerId: String?,
        correct: Boolean
    )
}
```

---

# Part 13 — Debug/preview screen for icon and content validation

If the project has a debug menu, developer menu or preview screen area, add a combined validation screen.

The screen should show:

## Icon validation

Grouped icons by section:

* Skills
* Topics
* UI
* Rewards
* Themes
* Content types

Each item should show:

* icon
* id
* label
* key/topic/skillRef where relevant

## Content validation

Show:

* total number of exercises
* number of exercises by topic
* number of exercises by skill
* number of exercises by difficulty
* number of exercises with valid icon resolution
* number of exercises using fallback icon
* parser warnings if any

Do not add a new visible production navigation item unless the app already has a developer/debug area.

---

# Part 14 — Add tests where practical

Add lightweight tests for the icon system:

* parsing the icon XML
* resolving known IDs
* normalizing topic names
* fallback behavior when an icon is missing

Test examples:

* `topic-lezen`
* `topic-sudoku`
* `topic-math-crosswords`
* `topic-begrijpend-lezen`
* `skill-spelling`
* `ui-home`
* `ui-settings`
* `reward-snoepje`
* `reward-poepje`

Add lightweight tests for the content parser:

* parse the minimal import test XML
* parse the full starter exercise XML
* verify that exercises are loaded
* verify that exercise IDs are unique
* verify that multiple choice answers are parsed
* verify that at least one correct answer exists where expected
* verify that topic and skillRef fields are parsed
* verify that iconRef and skillIconRef are parsed if present
* verify that missing optional fields do not crash the parser

Do not add heavy UI tests unless the project already uses them.

---

# Part 15 — Build and validate

After implementation:

* Run Gradle sync if needed.
* Run formatting if the project has a formatter.
* Run unit tests if available.
* Build the debug variant.

Fix any:

* invalid resource names
* XML parser issues
* unresolved drawable references
* missing asset paths
* duplicate content imports
* broken package imports
* Compose/XML UI errors

---

# Acceptance criteria

The implementation is complete when:

1. All Android VectorDrawable XML icons from the icon pack are available under `res/drawable`.
2. The icon mapping XML and manifest are available under `assets/poincare/icons/`.
3. The original Poincaré exercise XML files are available under `assets/poincare/content/`.
4. The app has a reusable Kotlin icon catalog and resolver.
5. Icons can be resolved by:

   * id
   * topic
   * skillRef
   * UI key
   * reward key
   * content type
6. Missing icons do not crash the app.
7. The resolver returns a safe fallback icon.
8. The app has a reusable XML content parser.
9. Exercises can be loaded from the bundled XML.
10. Exercises can be queried by:

* ID
* topic
* skillRef
* grade
* difficulty

11. Exercises resolve to the correct icons where possible.
12. Multiple-choice exercises can be displayed or made available to the existing exercise flow.
13. Existing screens are not broken.
14. The implementation is local-first and future-proof for later online sync.
15. The project builds successfully.

Keep the implementation clean, minimal and aligned with the existing architecture.
