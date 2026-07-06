# Import notes — Poincaré exercises v3

## Locale handling

Elke oefening bevat drie vertalingen:

- `nl-BE`
- `en-US`
- `fr-BE`

Gebruik `nl-BE` als default. De user-facing Franse Belgische variant staat als `fr-BE`. Als je importer intern `Fr-Be` binnenkrijgt, map die naar `fr-BE`.

## Bestandsstrategie

Importeer één XML-bestand per hoofdcategorie:

- math
- language
- logic-puzzles
- world-themes

Gebruik `poincare-exercises-index-v3.xml` om alle bestanden te ontdekken.

## Importprioriteit

Gebruik deze velden in volgorde voor iconen:

1. `iconRef`
2. `subcategoryId`
3. `skillRef`
4. `contentType`
5. fallback icon

## Progress tracking

Gebruik `exercise/@id` als stabiele sleutel. De combinatie van:

- `mainCategory`
- `subcategoryId`
- `grade`
- `difficultyStars`
- `variation`

maakt de oefening makkelijk filterbaar.

## Visible value formatting

Alle numerieke waarden die voor kinderen zichtbaar zijn, staan als string in de gevraagde Belgische notatie.

Machinevelden blijven numeriek:

- `grade`
- `difficultyStars`
- `variation`

Displayvelden:

- `gradeDisplay`
- `difficultyValueDisplay`
- `variationDisplay`

## Quality note

Deze pack is een brede originele seed-bibliotheek. Gebruik hem als startdataset. Later kan je per subcategorie verfijnen met pedagogische leerlijnen, adaptieve moeilijkheidsopbouw en audio-assets.
