param(
    [string]$OutputPath = "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json"
)

$ErrorActionPreference = "Stop"

function Text([string]$Nl, [string]$Fr, [string]$En, [string]$SpokenNl = "", [string]$SpokenFr = "", [string]$SpokenEn = "") {
    $result = [ordered]@{ visible = [ordered]@{ "nl-BE" = $Nl; "fr-BE" = $Fr; "en-US" = $En } }
    if ($SpokenNl) {
        $result.spoken = [ordered]@{ "nl-BE" = $SpokenNl; "fr-BE" = $SpokenFr; "en-US" = $SpokenEn }
    }
    return $result
}

function Block([string]$Id, [string]$Type, $Body, $Title = $null, [string[]]$VisualIds = @(), $Terms = @()) {
    $block = [ordered]@{ id = $Id; type = $Type }
    if ($null -ne $Title) { $block.title = $Title }
    if ($null -ne $Body) { $block.body = $Body }
    if ($Terms.Count -gt 0) { $block.terms = $Terms }
    if ($VisualIds.Count -gt 0) { $block.visualIds = $VisualIds }
    return $block
}

function Term($Term, $Definition, $Example = $null, $NonExample = $null) {
    $value = [ordered]@{ term = $Term; definition = $Definition }
    if ($null -ne $Example) { $value.example = $Example }
    if ($null -ne $NonExample) { $value.nonExample = $NonExample }
    return $value
}

function GeneratedSet(
    [string]$Id,
    [string]$Purpose,
    [int]$Count,
    [string]$GeneratorId,
    [hashtable]$Parameters,
    [string[]]$Tags,
    [string[]]$Representations,
    [string]$Hints,
    [string]$Feedback,
    [bool]$InjectErrors
) {
    return [ordered]@{
        id = $Id
        sourceMode = "GENERATED"
        purpose = $Purpose
        itemCount = $Count
        generatorId = $GeneratorId
        generatorVersion = 1
        seedPolicy = "STABLE_PER_PROFILE_ATTEMPT"
        parameters = $Parameters
        masteryTags = $Tags
        representationIds = $Representations
        hintPolicy = $Hints
        feedbackPolicy = $Feedback
        injectErrorMemory = $InjectErrors
    }
}

function Stage([string]$LessonId, [int]$Sequence, [string]$Type, $Title, $Blocks, $Sets, [Nullable[int]]$SeriesIndex = $null) {
    $suffix = switch ($Type) {
        "THEORY" { "theory" }
        "EXERCISES" { "exercises-$SeriesIndex" }
        "MASTERY_TEST" { "test" }
    }
    $stage = [ordered]@{
        id = "$LessonId.$suffix"
        type = $Type
        sequence = $Sequence
        title = $Title
        required = $true
        estimatedMinutes = @(10, 15, 15, 10)[$Sequence - 1]
        completionPolicy = @("VIEWED_REQUIRED_BLOCKS", "COMPLETED_REQUIRED_SETS", "COMPLETED_REQUIRED_SETS", "ASSESSMENT_RESULT")[$Sequence - 1]
        contentBlocks = @($Blocks)
        exerciseSets = @($Sets)
    }
    if ($null -ne $SeriesIndex) { $stage.seriesIndex = $SeriesIndex }
    return $stage
}

function ExerciseStages([string]$LessonId, [string]$GeneratorId, [hashtable]$BaseParameters, [string[]]$Tags, [string[]]$Representations, $AcquireBlocks) {
    $guided = $BaseParameters.Clone(); $guided.phase = "guided"
    $fading = $BaseParameters.Clone(); $fading.phase = "checkpoint"
    $independent = $BaseParameters.Clone(); $independent.phase = "checkpoint"
    $apply = $BaseParameters.Clone(); $apply.phase = "checkpoint"
    $test = $BaseParameters.Clone(); $test.phase = "checkpoint"; $test.exclude_recent_exact_items = "true"
    return @(
        (Stage $LessonId 1 "THEORY" (Text "Theorie" "Théorie" "Theory") $AcquireBlocks @()),
        (Stage $LessonId 2 "EXERCISES" (Text "Oefeningen" "Exercices" "Exercises") @(
            (Block "$LessonId.practice.summary" "SUMMARY" (Text "Je krijgt eerst hulp. Daarna verdwijnt de hulp stap voor stap." "Tu reçois d'abord de l'aide. Ensuite, l'aide disparaît peu à peu." "You get help first. Then the support fades step by step."))
        ) @(
            (GeneratedSet "$LessonId.set.guided" "GUIDED" 4 $GeneratorId $guided $Tags $Representations "FULL" "IMMEDIATE_EXPLANATORY" $false),
            (GeneratedSet "$LessonId.set.fading" "FADING_SUPPORT" 4 $GeneratorId $fading $Tags $Representations "FADING" "IMMEDIATE_EXPLANATORY" $true)
        ) 1),
        (Stage $LessonId 3 "EXERCISES" (Text "Oefeningen" "Exercices" "Exercises") @() @(
            (GeneratedSet "$LessonId.set.independent" "INDEPENDENT_PRACTICE" 6 $GeneratorId $independent $Tags $Representations "NONE" "IMMEDIATE_MINIMAL" $true),
            (GeneratedSet "$LessonId.set.transfer" "NEAR_TRANSFER" 4 $GeneratorId $apply $Tags $Representations "NONE" "AFTER_SET" $true)
        ) 2),
        (Stage $LessonId 4 "MASTERY_TEST" (Text "Toets" "Test" "Test") @() @(
            (GeneratedSet "$LessonId.set.test" "MASTERY_TEST" 10 $GeneratorId $test $Tags $Representations "NONE" "AFTER_TEST" $false)
        ))
    )
}

function Lesson(
    [string]$Id,
    [string]$UnitId,
    [int]$Grade,
    $Title,
    $Summary,
    [string]$Domain,
    [string]$Subcategory,
    [string[]]$LegacyIds,
    [string[]]$Goals,
    [string[]]$Tags,
    $Visuals,
    $Stages
) {
    return [ordered]@{
        id = $Id
        unitId = $UnitId
        grade = $Grade
        sequence = 1
        title = $Title
        summary = $Summary
        legacyRouting = [ordered]@{ domain = $Domain; subcategoryId = $Subcategory; legacyLessonIds = $LegacyIds }
        learningGoalIds = $Goals
        masteryTags = $Tags
        prerequisiteLessonIds = @()
        visuals = @($Visuals)
        stages = @($Stages)
        gatePolicy = [ordered]@{
            evidenceSource = "MASTERY_TEST_ONLY"
            minimumCorrectRatio = 0.8
            minimumIndependentItems = 10
            maximumHints = 0
            requiredMasteryTags = $Tags
            criticalTagMinimumCorrect = 1
            unlockOnPass = $true
            retentionCheckRequiredForMastered = $true
        }
        errorRepetitionPolicy = [ordered]@{
            enabled = $true
            repeatConceptNotExactItem = $true
            maxInjectedShare = 0.25
            reviewIntervalsDays = @(0, 1, 3, 7)
            resolveAfterConsecutiveCorrect = 2
        }
        definitionVersion = 1
    }
}

$additionId = "lesson.g1.math.addition.within10.meaning-and-model"
$additionTags = @("addition.meaning", "addition.within10")
$additionVisuals = @(
    [ordered]@{ id = "visual.g1.math.addition.ten-frame"; kind = "MATH_REPRESENTATION"; representationId = "math.ten_frame.v1"; palettePolicy = "THEME"; transparentBackground = $true; altText = [ordered]@{ "nl-BE" = "Een tienraam met een duidelijke vijf- en tienstructuur."; "fr-BE" = "Un cadre de dix avec une structure claire de cinq et de dix."; "en-US" = "A ten-frame with a clear five and ten structure." }; licenseId = "poincare-original" },
    [ordered]@{ id = "visual.g1.math.addition.part-whole"; kind = "MATH_REPRESENTATION"; representationId = "math.part_whole.v1"; palettePolicy = "THEME"; transparentBackground = $true; altText = [ordered]@{ "nl-BE" = "Een deel-geheelmodel met twee delen en één geheel."; "fr-BE" = "Un modèle partie-tout avec deux parties et un tout."; "en-US" = "A part-whole model with two parts and one whole." }; licenseId = "poincare-original" }
)
$additionBlocks = @(
    (Block "$additionId.intent" "LEARNING_INTENT" (Text "Je leert hoeveel er samen zijn wanneer je erbij doet." "Tu apprends combien il y en a en tout lorsque tu ajoutes." "You learn how many there are altogether when you add.")),
    (Block "$additionId.words" "VOCABULARY" $null $null @() @(
        (Term (Text "optellen" "additionner" "add") (Text "Twee hoeveelheden samen nemen of iets erbij doen." "Réunir deux quantités ou ajouter quelque chose." "Join two quantities or add something.")),
        (Term (Text "som" "somme" "sum") (Text "Het antwoord van een optelling." "Le résultat d'une addition." "The answer to an addition."))
    )),
    (Block "$additionId.how" "HOW_IT_WORKS" (Text "Leg het eerste deel op het tienraam. Voeg het tweede deel toe. Tel het geheel." "Place la première partie dans le cadre de dix. Ajoute la deuxième partie. Compte le tout." "Put the first part on the ten-frame. Add the second part. Count the whole.") $null @("visual.g1.math.addition.ten-frame")),
    (Block "$additionId.correct" "CORRECT_RESULT_RULE" (Text "Het totaal op het tienraam en het geheel in het deel-geheelmodel moeten hetzelfde zijn." "Le total dans le cadre de dix et le tout dans le modèle partie-tout doivent être identiques." "The ten-frame total and the whole in the part-whole model must match.") $null @("visual.g1.math.addition.ten-frame", "visual.g1.math.addition.part-whole")),
    (Block "$additionId.example" "WORKED_EXAMPLE" (Text "Vier en drie samen zijn zeven: 4 + 3 = 7." "Quatre et trois font sept : 4 + 3 = 7." "Four and three make seven: 4 + 3 = 7.") $null @("visual.g1.math.addition.part-whole")),
    (Block "$additionId.example-two" "WORKED_EXAMPLE" (Text "Twee en vijf samen zijn zeven: 2 + 5 = 7." "Deux et cinq font sept : 2 + 5 = 7." "Two and five make seven: 2 + 5 = 7.") $null @("visual.g1.math.addition.ten-frame")),
    (Block "$additionId.nonexample" "NON_EXAMPLE" (Text "Tel niet alleen het tweede deel; je telt beide delen samen." "Ne compte pas seulement la deuxième partie ; compte les deux parties ensemble." "Do not count only the second part; count both parts together."))
)
$additionParams = @{ grade = "1"; operation = "addition"; max_result = "10"; editorial_status = "approved" }
$additionStages = ExerciseStages $additionId "generator.g1.math.addition_subtraction.foundations" $additionParams $additionTags @("math.ten_frame.v1", "math.part_whole.v1") $additionBlocks

$soundId = "lesson.g1.reading.initial-sound.identify-and-compare"
$soundTags = @("phoneme.initial.identify", "phoneme.initial.compare")
$soundVisuals = @(
    [ordered]@{ id = "visual.g1.reading.elkonin-boxes"; kind = "PHONICS_DIAGRAM"; representationId = "reading.elkonin_boxes.v1"; palettePolicy = "THEME"; transparentBackground = $true; altText = [ordered]@{ "nl-BE" = "Drie klankvakjes, één vakje voor elke gehoorde klank."; "fr-BE" = "Trois cases de sons, une case pour chaque son entendu."; "en-US" = "Three sound boxes, one box for each sound heard." }; licenseId = "poincare-original" }
)
$soundBlocks = @(
    (Block "$soundId.intent" "LEARNING_INTENT" (Text "Je leert de eerste klank van een woord horen en vergelijken." "Tu apprends à entendre et comparer le premier son d'un mot." "You learn to hear and compare the first sound in a word.")),
    (Block "$soundId.words" "VOCABULARY" $null $null @() @(
        (Term (Text "klank" "son" "sound" "klank" "son" "sound") (Text "Wat je hoort wanneer je een woord langzaam zegt." "Ce que tu entends quand tu prononces un mot lentement." "What you hear when you say a word slowly.")),
        (Term (Text "beginklank" "son initial" "initial sound") (Text "De eerste klank die je in een woord hoort." "Le premier son que tu entends dans un mot." "The first sound you hear in a word."))
    )),
    (Block "$soundId.how" "HOW_IT_WORKS" (Text "Zeg het woord langzaam. Luister naar de eerste klank. Leg voor elke klank een fiche in een klankvakje." "Dis le mot lentement. Écoute le premier son. Place un jeton dans une case pour chaque son." "Say the word slowly. Listen to the first sound. Put one token in a box for each sound." "Zeg het woord langzaam. Luister alleen naar de eerste klank." "Dis le mot lentement. Écoute seulement le premier son." "Say the word slowly. Listen only to the first sound.") $null @("visual.g1.reading.elkonin-boxes")),
    (Block "$soundId.correct" "CORRECT_RESULT_RULE" (Text "Twee woorden passen samen wanneer hun eerste gehoorde klank dezelfde is." "Deux mots vont ensemble lorsque leur premier son entendu est le même." "Two words match when their first heard sound is the same.")),
    (Block "$soundId.example" "WORKED_EXAMPLE" (Text "Maan en muis beginnen allebei met de klank m." "Maman et moto commencent tous les deux par le son m." "Moon and mouse both start with the sound m.")),
    (Block "$soundId.nonexample" "NON_EXAMPLE" (Text "Kijk niet alleen naar de eerste letter; luister naar de eerste klank." "Ne regarde pas seulement la première lettre ; écoute le premier son." "Do not only look at the first letter; listen to the first sound."))
)
$soundParams = @{ grade = "1"; roadmap_entry = "roadmap.g1.spelling.phonology.sound_letter"; focus = "first_sound"; editorial_status = "approved" }
$soundStages = ExerciseStages $soundId "generator.g1.spelling.phonology.sound_letter" $soundParams $soundTags @("reading.elkonin_boxes.v1") $soundBlocks

$lengthId = "lesson.g1.geometry.length.compare-and-order"
$lengthTags = @("measurement.length.compare", "measurement.length.order")
$lengthVisuals = @(
    [ordered]@{ id = "visual.g1.geometry.length.ruler"; kind = "MEASUREMENT_DIAGRAM"; representationId = "measurement.ruler_compare.v1"; palettePolicy = "THEME"; transparentBackground = $true; altText = [ordered]@{ "nl-BE" = "Twee linten met hetzelfde beginpunt naast een meetlat in centimeter."; "fr-BE" = "Deux rubans avec le même point de départ à côté d'une règle en centimètres."; "en-US" = "Two ribbons sharing a start point beside a centimeter ruler." }; licenseId = "poincare-original" }
)
$lengthBlocks = @(
    (Block "$lengthId.intent" "LEARNING_INTENT" (Text "Je leert lengtes vergelijken en van kort naar lang ordenen." "Tu apprends à comparer des longueurs et à les classer de la plus courte à la plus longue." "You learn to compare lengths and order them from shortest to longest.")),
    (Block "$lengthId.words" "VOCABULARY" $null $null @() @(
        (Term (Text "langer" "plus long" "longer") (Text "Een lengte die verder reikt vanaf hetzelfde beginpunt." "Une longueur qui va plus loin depuis le même point de départ." "A length that reaches farther from the same start point.")),
        (Term (Text "korter" "plus court" "shorter") (Text "Een lengte die minder ver reikt vanaf hetzelfde beginpunt." "Une longueur qui va moins loin depuis le même point de départ." "A length that reaches less far from the same start point."))
    )),
    (Block "$lengthId.how" "HOW_IT_WORKS" (Text "Leg de beginpunten gelijk. Kijk welk uiteinde verder ligt of lees de centimeters op de meetlat." "Aligne les points de départ. Regarde quelle extrémité va le plus loin ou lis les centimètres sur la règle." "Line up the start points. See which end reaches farther or read the centimeters on the ruler.") $null @("visual.g1.geometry.length.ruler")),
    (Block "$lengthId.correct" "CORRECT_RESULT_RULE" (Text "De langste lengte heeft bij hetzelfde beginpunt het verste uiteinde en het grootste aantal centimeter." "La plus grande longueur a l'extrémité la plus éloignée et le plus grand nombre de centimètres." "The longest length has the farthest end and the greatest number of centimeters.")),
    (Block "$lengthId.example" "WORKED_EXAMPLE" (Text "Een lint van 8 cm is langer dan een lint van 5 cm." "Un ruban de 8 cm est plus long qu'un ruban de 5 cm." "An 8 cm ribbon is longer than a 5 cm ribbon.")),
    (Block "$lengthId.example-two" "WORKED_EXAMPLE" (Text "Van kort naar lang orden je 3 cm, 6 cm en 9 cm." "Du plus court au plus long : 3 cm, 6 cm et 9 cm." "From shortest to longest: 3 cm, 6 cm, and 9 cm.")),
    (Block "$lengthId.nonexample" "NON_EXAMPLE" (Text "Vergelijk geen linten met verschillende beginpunten; leg ze eerst gelijk." "Ne compare pas des rubans dont les points de départ diffèrent ; aligne-les d'abord." "Do not compare ribbons with different start points; line them up first."))
)
$lengthParams = @{ grade = "1"; roadmap_entry = "roadmap.g1.geometry.measurement_shapes.foundations"; focus = "comparison"; editorial_status = "approved" }
$lengthStages = ExerciseStages $lengthId "generator.g1.geometry.measurement_shapes.foundations" $lengthParams $lengthTags @("measurement.ruler_compare.v1") $lengthBlocks

$seasonsId = "lesson.g2.world.nature.seasons.order-and-signs"
$seasonsTags = @("seasons.order", "seasons.signs")
$seasonsVisuals = @(
    [ordered]@{ id = "visual.g2.world.seasons.cycle"; kind = "WORLD_BICOLOR_ILLUSTRATION"; representationId = "world.season_cycle.v1"; assetPathSvg = "poincare/visuals/world/seasons-cycle-v1.svg"; assetPathPng = "poincare/visuals/world/seasons-cycle-v1.png"; palettePolicy = "PLANET_GOLD_WHITE_ONLY"; transparentBackground = $true; altText = [ordered]@{ "nl-BE" = "Een kring met lente, zomer, herfst en winter in vaste volgorde."; "fr-BE" = "Un cercle avec le printemps, l'été, l'automne et l'hiver dans un ordre fixe."; "en-US" = "A cycle showing spring, summer, autumn, and winter in a fixed order." }; licenseId = "poincare-original" }
)
$seasonsBlocks = @(
    (Block "$seasonsId.intent" "LEARNING_INTENT" (Text "Je leert de vier seizoenen in volgorde zetten en typische tekens herkennen." "Tu apprends à mettre les quatre saisons dans l'ordre et à reconnaître des indices typiques." "You learn to order the four seasons and recognize typical signs.")),
    (Block "$seasonsId.words" "VOCABULARY" $null $null @() @(
        (Term (Text "seizoen" "saison" "season") (Text "Een deel van het jaar met typische veranderingen in weer, daglicht en natuur." "Une partie de l'année avec des changements typiques de météo, de lumière et de nature." "A part of the year with typical changes in weather, daylight, and nature.")),
        (Term (Text "lente, zomer, herfst, winter" "printemps, été, automne, hiver" "spring, summer, autumn, winter") (Text "De vier seizoenen in hun vaste kring." "Les quatre saisons dans leur cycle fixe." "The four seasons in their fixed cycle."))
    )),
    (Block "$seasonsId.how" "HOW_IT_WORKS" (Text "De seizoenen volgen elkaar steeds op: lente, zomer, herfst, winter en opnieuw lente." "Les saisons se suivent toujours : printemps, été, automne, hiver, puis de nouveau printemps." "The seasons always follow one another: spring, summer, autumn, winter, then spring again.") $null @("visual.g2.world.seasons.cycle")),
    (Block "$seasonsId.correct" "CORRECT_RESULT_RULE" (Text "Kies een seizoen op basis van meerdere typische aanwijzingen samen." "Choisis une saison à partir de plusieurs indices typiques réunis." "Choose a season using several typical clues together.")),
    (Block "$seasonsId.example" "WORKED_EXAMPLE" (Text "Nieuwe knoppen en jonge blaadjes passen bij de lente." "De nouveaux bourgeons et de jeunes feuilles correspondent au printemps." "New buds and young leaves match spring.")),
    (Block "$seasonsId.nonexample" "NON_EXAMPLE" (Text "Eén warme winterdag maakt het nog geen zomer." "Une journée chaude en hiver ne transforme pas l'hiver en été." "One warm winter day does not make it summer."))
)
$seasonsParams = @{ grade = "2"; roadmap_entry = "roadmap.g2.world.integrated_foundations"; focus = "seasons"; editorial_status = "approved" }
$seasonsStages = ExerciseStages $seasonsId "generator.g2.world.integrated_foundations" $seasonsParams $seasonsTags @() $seasonsBlocks

$catalog = [ordered]@{
    schemaVersion = 2
    definitionVersion = 1
    lessons = @(
        (Lesson $additionId "unit.g1.math.addition.foundations" 1 (Text "Optellen tot 10" "Additionner jusqu'à 10" "Add within 10") (Text "Begrijp, oefen, pas toe en bewijs dat je kunt optellen tot 10." "Comprends, entraîne-toi, applique et montre que tu sais additionner jusqu'à 10." "Understand, practise, apply, and show that you can add within 10.") "MATH" "addition" @("lesson.math.addition.g1.combine.to10") @("goal.g1.math.addition.within10") $additionTags $additionVisuals $additionStages),
        (Lesson $soundId "unit.g1.reading.phonemic-awareness" 1 (Text "Hoor de beginklank" "Entends le son initial" "Hear the initial sound") (Text "Luister naar de eerste klank en vergelijk woorden." "Écoute le premier son et compare des mots." "Listen to the first sound and compare words.") "READING" "reading" @("lesson.g1.spelling.phonology.sound_letter.explore") @("goal.g1.reading.initial-sound") $soundTags $soundVisuals $soundStages),
        (Lesson $lengthId "unit.g1.geometry.measurement.length" 1 (Text "Vergelijk en orden lengtes" "Comparer et classer des longueurs" "Compare and order lengths") (Text "Vergelijk vanuit hetzelfde beginpunt en orden van kort naar lang." "Compare à partir du même point de départ et classe du plus court au plus long." "Compare from the same start point and order from shortest to longest.") "GEOMETRY" "measurement" @() @("goal.g1.measurement.length.compare") $lengthTags $lengthVisuals $lengthStages),
        (Lesson $seasonsId "unit.g2.world.nature.seasons" 2 (Text "De seizoenen in een kring" "Le cycle des saisons" "The cycle of seasons") (Text "Orden lente, zomer, herfst en winter en herken typische tekens." "Mets le printemps, l'été, l'automne et l'hiver dans l'ordre et reconnais leurs indices." "Order spring, summer, autumn, and winter and recognize their signs.") "WORLD_KNOWLEDGE" "nature" @() @("goal.g2.world.seasons") $seasonsTags $seasonsVisuals $seasonsStages)
    )
}

$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputPath))
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($resolvedOutput)) | Out-Null
$json = $catalog | ConvertTo-Json -Depth 100
# Windows PowerShell 5.1 reads a UTF-8 script without a BOM through the active
# ANSI code page. Repair that single conversion before writing canonical UTF-8.
# PowerShell 7 already parses UTF-8 correctly and must not be transformed.
if ($PSVersionTable.PSEdition -eq "Desktop") {
    $windows1252 = [System.Text.Encoding]::GetEncoding(1252)
    $json = [System.Text.Encoding]::UTF8.GetString($windows1252.GetBytes($json))
}
[System.IO.File]::WriteAllText($resolvedOutput, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
Write-Output $resolvedOutput
