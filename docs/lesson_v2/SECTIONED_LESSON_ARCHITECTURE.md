# Three-part lesson architecture v2

Status: normatief runtime- en datacontract  
Versie: 2026-08-14

## Doel

Iedere niet-verhaal-kernles heeft voor het kind exact drie zichtbare delen:

1. Theorie
2. Oefenen
3. Toets

“Oefenen” mag meerdere afzonderlijke reeksen bevatten. Een reeks is geen extra lesdeel.

## Canonieke fasevolgorde

```text
THEORY
EXERCISES (seriesIndex 1)
EXERCISES (seriesIndex 2)
...
MASTERY_TEST
```

Harde regels:

- exact één `THEORY`, altijd eerst;
- één of meer `EXERCISES`, uitsluitend tussen theorie en toets;
- iedere `EXERCISES`-fase bevat precies één oefenreeks;
- `seriesIndex` is doorlopend vanaf 1;
- exact één `MASTERY_TEST`, altijd laatst;
- alleen de eindtoets levert bewijs voor de lespoort;
- een intermediaire oefenreeks bevat geen verborgen eindtoets.

## Theorie

Theorie is redactioneel gecontroleerd. Zij bevat minimaal:

- één korte `LEARNING_INTENT`-introductiepagina die doel en context in kindtaal benoemt;
- `HOW_IT_WORKS`;
- `CORRECT_RESULT_RULE`;
- `WORKED_EXAMPLE`;
- `NON_EXAMPLE` wanneer pedagogisch passend;
- afzonderlijke begrippen-/definitiepagina's na de introductie, ook voor leerjaar-1-Wereldoriëntatie.

De introductie vat geen reeks definities samen. Eén volgende pagina behandelt precies één hoofdconcept, aanpak of voorbeeld. Een pagina na de eerste kan terug naar de vorige pagina. De bovenste Theorie/Oefenen/Toets-tabs worden niet binnen de actieve inhoud herhaald.

## Oefenen

Elke reeks verklaart:

- ID, titel, doel en `seriesIndex`;
- bronmodus `CURATED` of `GENERATED`;
- itemaantal en mastery-tags;
- representatie, hints en feedback;
- validatie- en publicatiestatus;
- `wrong answers allowed` met waarde 1 of 2.

Minimaal begeleid en zelfstandig oefenen zijn aanwezig. Standaard gebruikt de eerste helft van de reeksen twee toegelaten fouten en de tweede helft één; bij een oneven aantal krijgt de strengere helft één reeks meer.

Voor een gegenereerde vraagbank gelden bijkomend:

- minimaal `itemCount` unieke en volledige records voor een vaste nagekeken bank; een procedurele generator levert daarnaast de variatie die zijn eigen contract vereist;
- geen promptduplicaten door antwoordpermutaties;
- `phase=guided` voor theorie-opvolging/begeleid oefenen, `phase=independent` voor zelfstandig oefenen en `phase=test` voor de eindtoets;
- een legacywaarde zoals `checkpoint` ontstaat alleen in een expliciete generatoradapter;
- `itemCount` en blueprints worden na deduplicatie opnieuw op het werkelijk beschikbare aanbod afgestemd.

## Toets

De eindtoets:

- dekt alle verplichte mastery-tags;
- gebruikt nieuwe equivalente items wanneer een procedurele generator dit kan garanderen;
- sluit recente exacte items uit bij procedurele generatie; een vaste nagekeken bank mag bewust theorie- of oefenvragen opnieuw toetsen;
- heeft voldoende unieke, nog niet gebruikte equivalente items om `minimumIndependentItems` werkelijk te halen;
- toont geen hints;
- geeft feedback na afloop;
- ondersteunt idempotente voortgang en beloning.

## Navigatie

- Theorie → eerste oefenreeks.
- Reeks n → reeks n+1.
- Laatste reeks → toets.
- Toetsresultaat → les/lessenoverzicht volgens het schermcontract.
- Hervatten gebruikt stabiele les-, fase-, reeks- en poging-ID's.

## Compatibiliteit

De runtime mag historische typen `ACQUIRE_UNDERSTAND`, `PRACTISE` en `APPLY_DEMONSTRATE` nog inlezen om bestaande inhoud en voortgang te behouden. Nieuwe of gemigreerde lessen schrijven deze typen niet meer. Legacyrouting bepaalt nooit zichtbare namen, groepering of volgorde.

De compatibiliteitsadapter mag pas worden verwijderd wanneer:

1. `remainingLegacyLessons == 0`;
2. alle ID- en voortgangsmigraties bestaan;
3. de volledige V2-runtime- en catalogusvalidatie slaagt.

## Validatorcontract

De validator blokkeert minimaal:

- ontbrekende of dubbele fasen;
- verkeerde fasevolgorde;
- onderbroken reeksindexen;
- meer dan één toets;
- niet-ondersteunde nieuwe legacytypes;
- actieve les met niet-valide actieve oefenset;
- gegenereerde set zonder generatorversie, implementatieversie en seedbeleid;
- gegenereerde set met niet-ondersteunde fasewaarde of een te kleine unieke vraagbank;
- `itemCount`, blueprints of gate-minimum groter dan het werkelijk genereerbare unieke aanbod;
- single-choice zonder exact één inhoudelijk juist antwoord;
- toets zonder volledige mastery-dekking.

Zie `docs/CURRICULUM_ARCHITECTURE_PLAYBOOK.md` voor het volledige redactionele, UI-, release- en leerjaarcontract.
