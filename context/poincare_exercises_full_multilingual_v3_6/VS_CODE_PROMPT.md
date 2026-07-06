Please update the Poincaré Android importer and exercise/category UI for content pack v3.6.

Main fixes:

1. Do not show visible theme labels in exercises.

The prompt text from XML is already cleaned. The app must not add category/theme labels like:

- Theme
- Thema
- Thème
- World knowledge
- Geometry
- Vocabulary

inside the visible exercise question label.

The visible prompt should be direct, for example:

- `Translate the Dutch word into French.`
- `Tap play. Choose the word you hear.`
- `How much is 7 + 5?`

Do not show:

- `Theme: animals`
- `World knowledge: animals`
- `Geometry: shapes`

2. Fix translation routing by app language.

The old generic translation tile is not used anymore.

The old subcategories are removed:

- `translation`
- `translation_to_english`
- `translation_to_french`

Use the new locale-aware translation subcategories:

- `translation_nl_to_en`, sourceLocale `nl-BE`, targetLocale `en-US`
- `translation_nl_to_fr`, sourceLocale `nl-BE`, targetLocale `fr-BE`
- `translation_en_to_nl`, sourceLocale `en-US`, targetLocale `nl-BE`
- `translation_en_to_fr`, sourceLocale `en-US`, targetLocale `fr-BE`
- `translation_fr_to_nl`, sourceLocale `fr-BE`, targetLocale `nl-BE`
- `translation_fr_to_en`, sourceLocale `fr-BE`, targetLocale `en-US`

On the category screen, show only translation subcategories where:

```kotlin
subcategory.sourceLocale == currentAppLocale
```

Examples:

If the app language is `en-US`, show only:

- English to Dutch
- English to French

Do not show Dutch-to-French exercises when the app is in English.

If the app language is `nl-BE`, show only:

- Dutch to English
- Dutch to French

If the app language is `fr-BE`, show only:

- French to Dutch
- French to English

3. Audio requirements.

For exercises with `type="audio_choice"` or `type="audio_translation_choice"`, use the `<audioSources>` block.

When `audioSources/@visibleText="false"`:

- show a play button
- do not show the source word in the prompt
- read `audio/@text` aloud
- pronounce it using `audio/@ttsLanguage`
- cache or identify it using `audio/@audioId`
- never read `audioId` aloud

Example:

```xml
<audioSources visibleText="false" playButtonOnly="true" ttsRequired="true" kind="translation_word" sourceLocale="en-US" targetLocale="fr-BE">
  <audio locale="en-US" ttsLanguage="en-US" text="house" audioId="translation-en-to-fr-g1-seq0001-en-us" />
</audioSources>
```

For this example, show a play button that reads `house` in English. The child selects the French translation.

4. Answer display rule.

Do not display `answer/@value` directly if localized display values exist.

Use:

```kotlin
answer.displayForLocale(currentLocale)
```

Validation should still use:

```kotlin
answer.value
```

or:

```kotlin
answer.isCorrect
```

5. Progress and counts.

Use `exercise/@id` and `progressKey` as stable identifiers.

Use `poincare-exercises-index-v3.xml` as the source of truth for counts.

Each subcategory has 600 exercises:

- 100 per grade
- grades 1 to 6

Translation has six direction subcategories, but the UI should show only two translation tiles for the active app language.

6. Tests to add.

Add tests for:

- all XML files parse
- no visible prompt contains `Theme`, `Thema` or `Thème`
- old translation subcategories are not imported
- app locale `en-US` shows only `translation_en_to_nl` and `translation_en_to_fr`
- app locale `nl-BE` shows only `translation_nl_to_en` and `translation_nl_to_fr`
- app locale `fr-BE` shows only `translation_fr_to_nl` and `translation_fr_to_en`
- audio translation exercises contain `audioId`, `text`, `locale`, and `ttsLanguage`
- no `audio/@text` contains underscores
- when `visibleText=false`, the UI does not show the source word in the prompt
- answer options use localized display text
- progress is kept when importing a newer pack with existing IDs

Focus only on importer, category filtering, prompt display, audio playback metadata, localized answer display, and progress/count tracking. Do not rewrite unrelated theme or navigation code.
