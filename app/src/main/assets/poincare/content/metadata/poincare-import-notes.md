# Importnotities voor Poincaré

## Aanbevolen importflow

1. Parse `poincareContentPack`.
2. Controleer `version` en `packId`.
3. Importeer of update `skills`.
4. Importeer `articles`.
5. Importeer `exercises` met `exercise/@id` als stabiele sleutel.
6. Importeer `worksheets` als bundels met `exerciseRef`-verwijzingen.
7. Bewaar progressie los van de content, zodat content kan worden geüpdatet zonder progressie te verliezen.

## Progress tracking

Bewaar per kindprofiel en per oefening:

- `exerciseId`
- `attemptCount`
- `correctCount`
- `lastAnswer`
- `lastCorrect`
- `timeSpentSeconds`
- `hintCount`
- `languageUsed`
- `lastSeenAt`
- `spacedRepetitionDueAt`

## Type-afhandeling

- `open_numeric`: verwacht exact numeriek antwoord, eventueel met normalisatie.
- `open_text`: vergelijk genormaliseerde strings, met toleranties voor komma/punt/euroteken.
- `fill_blank`: één of meer invulvelden. In deze starter is het antwoord voorlopig als één string opgeslagen.
- `multiple_choice`: gebruik `answers/answer[@correct="true"]`.

## Meertaligheid

De starter bevat hoofdzakelijk `nl-BE`, maar het schema voorziet `locales`. Voor latere uitbreidingen kan je per element toevoegen:

```xml
<translations>
  <translation locale="fr-BE">
    <text>...</text>
  </translation>
  <translation locale="en">
    <text>...</text>
  </translation>
</translations>
```

## Thema's en UX

Gebruik `worksheet/theme` of extra tags voor Poincaré-thema's zoals ruimte, planeten, raketten of wetenschappers. Houd de inhoud los van de presentatie zodat dezelfde oefening in meerdere skins kan worden gebruikt.
