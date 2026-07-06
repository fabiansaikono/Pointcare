# Poincaré originele content starterpack

Dit pakket bevat een veilige basis om content in Poincaré te laden zonder content van Junior Einstein of andere derden te kopiëren.

## Bestanden

- `poincare-original-math-l4-starter-001.xml`  
  Originele wiskundecontent voor vierde leerjaar: artikels, oefeningen en werkblad-definities.

- `poincare-content-pack-v1.xsd`  
  Een XML Schema Definition om de basisstructuur te valideren.

- `poincare-import-notes.md`  
  Advies voor de importer en contentmodellering.

## Contentmodel

Het XML-model ondersteunt:

- `metadata`: titel, bron, licentie, copyrightnotitie
- `locales`: standaardtaal en toekomstige vertalingen
- `skills`: vaardigheidscodes voor progress tracking
- `articles`: korte uitlegartikels
- `exercises`: interactieve oefeningen
- `worksheets`: printvriendelijke bundels die verwijzen naar oefeningen
- `importConfig`: praktische hints voor deduplicatie en progress tracking

## Juridisch veilige werkwijze

Gebruik dit model voor:

1. eigen oefeningen;
2. automatisch gegenereerde originele oefeningen;
3. open content met passende licentie;
4. content waarvoor je expliciete toestemming/API/exportrecht hebt.

Niet gebruiken voor het kopiëren of scrapen van content achter accounts of betaalmuren.
