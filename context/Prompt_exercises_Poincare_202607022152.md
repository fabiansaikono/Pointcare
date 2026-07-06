Please update the Poincaré XML exercise importer for content pack v3.3.

The previous importer may be showing English values in nl-BE because it reads `answer/@value` or the first `<display>` element instead of selecting the display by locale.

Important rule:

* `answer/@value` is the stable canonical value used for correctness.
* `answer/display[@locale=currentLocale]` is the text that must be shown to the user.
* Never show `answer/@value` directly in the UI unless no display exists.

Example XML:

```xml
<answer id="reading_d1_g1_seq0001_answer3" correct="true" value="book">
  <display locale="nl-BE">boek</display>
  <display locale="en-US">book</display>
  <display locale="fr-BE">livre</display>
</answer>
```

If the current app locale is `nl-BE`, show:

```text
boek
```

If the current app locale is `en-US`, show:

```text
book
```

If the current app locale is `fr-BE`, show:

```text
livre
```

Do not show:

```text
book
```

for every locale.

Update the parser/importer model so each answer stores:

```kotlin
data class ExerciseAnswer(
    val id: String,
    val value: String,
    val isCorrect: Boolean,
    val displays: Map<String, String>
)
```

Add a helper:

```kotlin
fun ExerciseAnswer.displayForLocale(locale: String): String {
    return displays[locale]
        ?: displays["nl-BE"]
        ?: displays["en-US"]
        ?: displays.values.firstOrNull()
        ?: value
}
```

Use the same logic for solutions:

* Prefer `solution/display[@locale=currentLocale]`
* Then `solution/@displayNlBe`
* Then `solution/@displayEnUs`
* Then `solution/@display`
* Then `solution/@value`

Update the UI so answer buttons/cards show:

```kotlin
answer.displayForLocale(currentLocale)
```

but answer validation still uses:

```kotlin
answer.value
```

or:

```kotlin
answer.isCorrect
```

depending on the existing implementation.

Add tests:

1. For `reading_d1_g1_seq0001`, nl-BE answer display for value `book` must be `boek`.
2. For `reading_d1_g1_seq0001`, en-US answer display for value `book` must be `book`.
3. For `reading_d1_g1_seq0001`, fr-BE answer display for value `book` must be `livre`.
4. The app must never use `answer/@value` as visible text when a localized display exists.
5. The importer must keep `answer/@value` stable for correctness tracking.
6. The importer must keep `exercise/@id` and `exercise/@progressKey` unchanged.
7. All answer displays must be unique inside one exercise for the selected locale.
8. Shape answers must still display shape symbols such as `■`, `●`, `▲`, `▭`, `◆`, `★`, `⬡`, `⬟`.

Do not rewrite the exercise content generator. Focus only on XML parsing, answer display localization, and UI rendering of localized answer options.
