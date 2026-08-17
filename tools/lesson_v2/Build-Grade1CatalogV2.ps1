param(
    [string]$ReferenceRoot = ".tmp/PoincareWorkPackageV2_20260730/Poincare_Codex_Work_Package_Lessonarchitecture_v2/curriculum_reference/Poincare_curriculum_vlaanderen",
    [string]$PilotCatalogPath = "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json",
    [string]$ManifestPath = "app/src/main/assets/poincare/curriculum/curriculum-manifest-v1.json",
    [string]$KnowledgeProfilesPath = "app/src/main/resources/poincare/curriculum/grade1-unit-knowledge-profiles.json",
    [string]$OutputPath = "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json",
    [string]$IndexPath = "docs/lesson_v2/reports/GRADE1_CATALOG_INDEX.json"
)

$ErrorActionPreference = "Stop"

function Text([string]$Nl, [string]$Fr = "", [string]$En = "") {
    if ([string]::IsNullOrWhiteSpace($Fr)) { $Fr = $Nl }
    if ([string]::IsNullOrWhiteSpace($En)) { $En = $Nl }
    return [ordered]@{ visible = [ordered]@{ "nl-BE" = $Nl; "fr-BE" = $Fr; "en-US" = $En } }
}

function ProfileText($Values) {
    if (@($Values).Count -ne 3) { throw "A unit profile value must contain NL, FR and EN." }
    return Text ([string]$Values[0]) ([string]$Values[1]) ([string]$Values[2])
}

function ProfileSentence([string]$PrefixNl, [string]$PrefixFr, [string]$PrefixEn, $Values) {
    return Text "$PrefixNl$($Values[0])" "$PrefixFr$($Values[1])" "$PrefixEn$($Values[2])"
}

function ProfileTerm($Values, $Goal) {
    return [ordered]@{
        term = ProfileText $Values
        definition = Text `
            "‘$($Values[0])’ hoort bij deze les: $($Goal[0])" `
            "« $($Values[1]) » appartient à cette leçon : $($Goal[1])" `
            "‘$($Values[2])’ belongs in this lesson: $($Goal[2])"
    }
}

function Block([string]$Id, [string]$Type, $Body = $null, $Title = $null, [string[]]$VisualIds = @(), $Terms = @()) {
    $block = [ordered]@{ id = $Id; type = $Type }
    if ($null -ne $Title) { $block.title = $Title }
    if ($null -ne $Body) { $block.body = $Body }
    if (@($Terms).Count -gt 0) { $block.terms = @($Terms) }
    if (@($VisualIds).Count -gt 0) { $block.visualIds = @($VisualIds) }
    return $block
}

function Term([string]$Word, [string]$Skill) {
    return [ordered]@{
        term = Text $Word
        definition = Text "‘$Word’ is een kernwoord waarmee je leert om $Skill."
        example = Text "Je gebruikt het woord ‘$Word’ wanneer je over deze les uitlegt wat je doet."
    }
}

function GeneratedSet(
    [string]$Id,
    [string]$Purpose,
    [int]$Count,
    [string]$GeneratorId,
    [hashtable]$Parameters,
    [string[]]$Tags,
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
        generatorVersion = "v2"
        generatorImplementationVersion = 1
        origin = "GENERATED"
        seedPolicy = "STABLE_PER_PROFILE_ATTEMPT"
        parameters = $Parameters
        masteryTags = $Tags
        representationIds = @()
        hintPolicy = $Hints
        feedbackPolicy = $Feedback
        injectErrorMemory = $InjectErrors
    }
}

function GeneratorSpec($Unit, [string]$Phase, [hashtable]$GenericParameters) {
    $unitId = [string]$Unit.unit_id
    $specializedPhase = if ($Phase -eq "guided") { "guided" } else { "checkpoint" }
    $base = @{ grade = "1"; phase = $specializedPhase; editorial_status = "approved" }
    if ($unitId -match '^unit\.g1\.math\.operations\.(optellen|aftrekken)') {
        $operation = if ($unitId -match 'aftrekken') { "subtraction" } else { "addition" }
        return [ordered]@{
            generatorId = "generator.g1.math.addition_subtraction.foundations"
            parameters = @{} + $base + @{ operation = $operation; max_result = "20" }
        }
    }
    if ($unitId -match '^unit\.g1\.math\.numbers\.') {
        return [ordered]@{
            generatorId = "generator.g1.math.number_sense.foundations"
            parameters = @{} + $base + @{ roadmap_entry = "roadmap.g1.math.number_sense.foundations" }
        }
    }
    if ($unitId -match '^unit\.g1\.math\.operations\.rekenfeiten' -or $unitId -match '^unit\.g1\.math\.problem_solving\.') {
        return [ordered]@{
            generatorId = "generator.g1.math.addition_subtraction.foundations"
            parameters = @{} + $base + @{ operation = "addition"; max_result = "20" }
        }
    }
    if ($unitId -match '^unit\.g1\.math\.measurement\.(lengte|tijd|geld)' -or $unitId -match '^unit\.g1\.math\.geometry\.vlakke') {
        $focus = if ($unitId -match '\.tijd-') { "time" } elseif ($unitId -match '\.geld-') { "money" } elseif ($unitId -match '\.vlakke-') { "shapes" } else { "comparison" }
        return [ordered]@{
            generatorId = "generator.g1.geometry.measurement_shapes.foundations"
            parameters = @{} + $base + @{ roadmap_entry = "roadmap.g1.geometry.measurement_shapes.foundations"; focus = $focus }
        }
    }
    if ($unitId -match '^unit\.g1\.math\.(fractions|data)\.' -or $unitId -match '^unit\.g1\.math\.geometry\.orientatie-') {
        return [ordered]@{
            generatorId = "generator.g1.math.concepts"
            parameters = $GenericParameters
        }
    }
    if ($unitId -match '^unit\.g1\.nl\.technical_reading\.(luisteren-naar-klanken|klank-tekenkoppeling)' -or $unitId -match '^unit\.g1\.nl\.spelling\.') {
        $focus = if ($unitId -match 'luisteren-naar-klanken') { "first_sound" } else { "mixed" }
        return [ordered]@{
            generatorId = "generator.g1.spelling.phonology.sound_letter"
            parameters = @{} + $base + @{ roadmap_entry = "roadmap.g1.spelling.phonology.sound_letter"; focus = $focus }
        }
    }
    if ($unitId -match '^unit\.g1\.nl\.technical_reading\.') {
        $readingFocus = if ($unitId -match 'mkm-woorden') { "mkm" } elseif ($unitId -match 'lange-klinkers') { "vowel_teams" } elseif ($unitId -match 'medeklinkerclusters') { "consonant_clusters" } else { "frequent_words" }
        return [ordered]@{
            generatorId = "generator.g1.language.technical_reading.decoding"
            parameters = @{} + $base + @{ roadmap_entry = "roadmap.g1.language.technical_reading.decoding"; unit_id = $unitId; reading_focus = $readingFocus }
        }
    }
    if ($unitId -match '^unit\.g1\.nl\.(comprehension|literature)\.') {
        $literacyFocus = if ($unitId -match 'zinnen-en-korte-teksten') { "sentence_short_text" } elseif ($unitId -match 'voorspellen-verbinden') { "predict_connect_retell" } else { "book_orientation_text_types" }
        return [ordered]@{
            generatorId = "generator.g1.language.early_literacy.story_awareness"
            parameters = @{} + $base + @{ roadmap_entry = "roadmap.g1.language.early_literacy.story_awareness"; unit_id = $unitId; literacy_focus = $literacyFocus }
        }
    }
    if ($unitId -match '^unit\.g1\.nl\.(handwriting|composition)\.') {
        return [ordered]@{
            generatorId = "generator.g1.language.writing.letters_sentences"
            parameters = @{} + $base + @{ roadmap_entry = "roadmap.g1.language.writing.letters_sentences" }
        }
    }
    if ($unitId -match '^unit\.g1\.nl\.oral\.') {
        return [ordered]@{
            generatorId = "generator.g1.language.listening.instructions_stories"
            parameters = @{} + $base + @{ roadmap_entry = "roadmap.g1.language.listening.instructions_stories" }
        }
    }
    if ($unitId -match '^unit\.g1\.fr\.') {
        return [ordered]@{
            generatorId = "generator.g1.language.french_basics"
            parameters = $GenericParameters
        }
    }
    if ($unitId -match '^unit\.g1\.science_tech\.' -or $unitId -match '^unit\.g1\.geo\.(physical|environment|fieldwork)\.') {
        return [ordered]@{
            generatorId = "generator.g1.world.environment_science"
            parameters = @{} + $base + @{ roadmap_entry = "roadmap.g1.world.environment_science"; unit_id = $unitId }
        }
    }
    if ($unitId -match '^unit\.g1\.attitudes\.citizenship_safety\.') {
        return [ordered]@{
            generatorId = "generator.g1.world.traffic.pedestrian_safety"
            parameters = @{} + $base + @{ roadmap_entry = "roadmap.g1.world.traffic.pedestrian_safety" }
        }
    }
    if ($unitId -match '^unit\.g1\.attitudes\.') {
        return [ordered]@{
            generatorId = "generator.g1.world.citizenship.cooperation"
            parameters = @{} + $base + @{ roadmap_entry = "roadmap.g1.world.citizenship.cooperation" }
        }
    }
    if (
        $unitId -match '^unit\.g1\.geo\.(maps|human)\.' -or
        $unitId -match '^unit\.g1\.(history|ict|arts|pe)\.'
    ) {
        return [ordered]@{
            generatorId = "generator.g1.applied_domains"
            parameters = $GenericParameters
        }
    }
    return [ordered]@{ generatorId = "generator.g1.curriculum.catalog"; parameters = $GenericParameters }
}

function Stage(
    [string]$Id,
    [string]$Type,
    [int]$Sequence,
    [bool]$Required,
    [int]$Minutes,
    $Blocks,
    $Sets,
    [Nullable[int]]$SeriesIndex = $null
) {
    $stage = [ordered]@{
        id = $Id
        type = $Type
        sequence = $Sequence
        title = switch ($Type) {
            "THEORY" { Text "Theorie" "Théorie" "Theory" }
            "EXERCISES" { Text "Oefeningen" "Exercices" "Exercises" }
            "PRACTISE" { Text "Oefenen" "S'exercer" "Practise" }
            "APPLY_DEMONSTRATE" { Text "Toepassen" "Appliquer" "Apply" }
            "MASTERY_TEST" { Text "Toets" "Test" "Test" }
        }
        required = $Required
        estimatedMinutes = $Minutes
        completionPolicy = switch ($Type) {
            "THEORY" { "VIEWED_REQUIRED_BLOCKS" }
            "EXERCISES" { "COMPLETED_REQUIRED_SETS" }
            "PRACTISE" { "COMPLETED_REQUIRED_SETS" }
            "APPLY_DEMONSTRATE" { "COMPLETED_REQUIRED_SETS" }
            "MASTERY_TEST" { "ASSESSMENT_RESULT" }
        }
        contentBlocks = @($Blocks)
        exerciseSets = @($Sets)
    }
    if ($null -ne $SeriesIndex) { $stage.seriesIndex = $SeriesIndex }
    return $stage
}

function RoutingFor($Unit) {
    $id = [string]$Unit.unit_id
    if ($Unit.subject_id -eq "math") {
        if ($id -match "measurement\.lengte-massa-en-inhoud") { return @("GEOMETRY", "measurement") }
        if ($id -match "measurement\.tijd") { return @("GEOMETRY", "time") }
        if ($id -match "geometry\.vlakke") { return @("GEOMETRY", "geometry_shapes") }
        if ($id -match "geometry\.orientatie") { return @("GEOMETRY", "geometry_shapes") }
        if ($id -match "operations\.optellen") { return @("MATH", "addition") }
        if ($id -match "operations\.aftrekken") { return @("MATH", "subtraction") }
        if ($id -match "fractions") { return @("MATH", "fractions") }
        if ($id -match "measurement\.geld") { return @("MATH", "money") }
        if ($id -match "data\.") { return @("MATH", "statistics") }
        if ($id -match "numbers\.getallenlijn") { return @("MATH", "number_sequences") }
        return @("MATH", "mixed_arithmetic")
    }
    if ($Unit.subject_id -eq "nl") {
        if ($id -eq "unit.g1.nl.technical_reading.automatiseren-van-frequente-woorden") { return @("READING", "review") }
        if ($id -match "handwriting\.") { return @("WRITING", "letter_tracing") }
        if ($id -match "spelling\.") { return @("WRITING", "spelling") }
        if ($id -match "composition\.") { return @("WRITING", "sentence_order") }
        if ($id -match "oral\.") { return @("READING", "listening") }
        if ($id -match "literature\.") { return @("READING", "reading") }
        return @("READING", "reading")
    }
    if ($Unit.subject_id -eq "fr") { return @("READING", "vocabulary") }
    if ($Unit.subject_id -eq "science_tech") {
        if ($id -match "planten") { return @("WORLD_KNOWLEDGE", "plants") }
        if ($id -match "dieren") { return @("WORLD_KNOWLEDGE", "animals") }
        if ($id -match "human_body") { return @("WORLD_KNOWLEDGE", "human_body") }
        if ($id -match "technology") { return @("WORLD_KNOWLEDGE", "technology") }
        if ($id -match "organisms") { return @("WORLD_KNOWLEDGE", "nature") }
        return @("WORLD_KNOWLEDGE", "science")
    }
    if ($Unit.subject_id -eq "geo") {
        if ($id -match "weer-en-seizoenen") { return @("WORLD_KNOWLEDGE", "weather_climate") }
        if ($id -match "environment") { return @("WORLD_KNOWLEDGE", "environment_sustainability") }
        return @("WORLD_KNOWLEDGE", "geography")
    }
    if ($Unit.subject_id -eq "history") { return @("WORLD_KNOWLEDGE", "history") }
    if ($Unit.subject_id -eq "arts") { return @("WORLD_KNOWLEDGE", "arts_culture") }
    if ($Unit.subject_id -eq "ict") { return @("WORLD_KNOWLEDGE", "technology") }
    if ($Unit.subject_id -eq "attitudes") {
        if ($id -match "safety") { return @("WORLD_KNOWLEDGE", "traffic_safety") }
        return @("WORLD_KNOWLEDGE", "citizenship_society")
    }
    return @("WORLD_KNOWLEDGE", "everyday_life")
}

function CurriculumLocationFor($Unit) {
    $id = [string]$Unit.unit_id
    if ($Unit.subject_id -eq "math") {
        if ($id -match '\.numbers\.') { return @("number_sense", "math.number_place_value") }
        if ($id -match '\.operations\.') { return @("number_sense", "math.addition_subtraction") }
        if ($id -match '\.fractions\.') { return @("number_sense", "math.fractions") }
        if ($id -match '\.measurement\.geld') { return @("number_sense", "math.money_financial_literacy") }
        if ($id -match '\.measurement\.tijd') { return @("measurement_geometry", "geometry.time_calendar") }
        if ($id -match '\.measurement\.lengte') { return @("measurement_geometry", "geometry.length_perimeter_scale") }
        if ($id -match '\.geometry\.vlakke') { return @("measurement_geometry", "geometry.shapes_lines_angles") }
        if ($id -match '\.geometry\.orientatie') { return @("measurement_geometry", "geometry.spatial_orientation_coordinates") }
        if ($id -match '\.data\.') { return @("number_sense", "math.tables_graphs_statistics") }
        return @("number_sense", "math.problem_solving")
    }
    if ($Unit.subject_id -eq "nl") {
        if ($id -match '\.spelling\.') { return @("spelling_grammar", "spelling.phonology_letter_sound") }
        if ($id -match '\.technical_reading\.') { return @("language_skills", "language.technical_reading") }
        if ($id -match '\.comprehension\.') { return @("language_skills", "language.reading_comprehension") }
        if ($id -match '\.(handwriting|composition)\.') { return @("language_skills", "language.writing_expression") }
        if ($id -match '\.oral\.') { return @("language_skills", "language.listening_comprehension") }
        return @("language_skills", "language.literature_experience")
    }
    if ($Unit.subject_id -eq "fr") { return @("language_skills", "language.listening_comprehension") }
    if ($Unit.subject_id -eq "science_tech") {
        if ($id -match '\.organisms\.') { return @("world_orientation", "world.nature_living_world") }
        if ($id -match '\.human_body\.') { return @("world_orientation", "world.body_health") }
        if ($id -match '\.technology\.') { return @("world_orientation", "world.technology_design") }
        return @("world_orientation", "world.science_inquiry")
    }
    if ($Unit.subject_id -eq "geo") {
        if ($id -match '\.environment\.') { return @("world_orientation", "world.environment_sustainability") }
        return @("world_orientation", "world.geography_space")
    }
    if ($Unit.subject_id -eq "history") { return @("world_orientation", "world.history_time") }
    if ($Unit.subject_id -eq "ict") { return @("world_orientation", "world.digital_media_literacy") }
    if ($Unit.subject_id -eq "attitudes") {
        if ($id -match 'safety') { return @("world_orientation", "world.traffic_mobility_safety") }
        return @("world_orientation", "world.citizenship_social")
    }
    if ($Unit.subject_id -eq "pe" -and $id -match 'health') { return @("world_orientation", "world.body_health") }
    return @("world_orientation", "world.daily_life_self_reliance")
}

function Set-ObjectProperty($Object, [string]$Name, $Value) {
    if ($null -eq $Object) { return }
    if ($Object -is [System.Collections.IDictionary]) { $Object[$Name] = $Value }
    else { $Object | Add-Member -MemberType NoteProperty -Name $Name -Value $Value -Force }
}

function StableToken([string]$Value) {
    $token = $Value.ToLowerInvariant() -replace '[^a-z0-9]+', '-'
    $token = $token.Trim('-')
    if ([string]::IsNullOrWhiteSpace($token)) { return "field" }
    return $token
}

function TranslationReview([string]$FieldId, $Text) {
    $nl = [string]$Text.visible.'nl-BE'
    $records = @()
    foreach ($locale in @('nl-BE', 'fr-BE', 'en-US')) {
        $value = [string]$Text.visible.$locale
        $status = if ($locale -eq 'nl-BE') { 'DEFAULT' } elseif ([string]::IsNullOrWhiteSpace($value)) { 'MISSING' } elseif ($value -eq $nl) { 'NEEDS_REVIEW' } else { 'COMPLETE' }
        $records += [ordered]@{
            id = "$FieldId.translation.$((StableToken $locale))"
            locale = $locale
            status = $status
            comment = ""
            fallbackLocale = $(if ($status -eq 'MISSING') { 'nl-BE' } else { $null })
        }
    }
    return @($records)
}

function Add-ContentField($Fields, [string]$FieldId, [string]$OwnerId, [string]$Path, $Text) {
    if ($null -eq $Text -or $null -eq $Text.visible) { return }
    [void]$Fields.Add([ordered]@{
        id = $FieldId
        ownerId = $OwnerId
        path = $Path
        translations = @(TranslationReview $FieldId $Text)
    })
}

function Add-LessonEditorialMetadata($Lesson, [string]$CategoryId, [string]$SubcategoryId) {
    $lessonId = [string]$Lesson.id
    $publicationStatus = [string]$Lesson.publicationStatus
    if ([string]::IsNullOrWhiteSpace($publicationStatus)) { $publicationStatus = 'DRAFT_REVIEW_REQUIRED' }
    Set-ObjectProperty $Lesson 'publicationStatus' $publicationStatus
    Set-ObjectProperty $Lesson 'curriculumCategoryId' $CategoryId
    Set-ObjectProperty $Lesson 'curriculumSubcategoryId' $SubcategoryId
    Set-ObjectProperty $Lesson 'description' $Lesson.summary
    Set-ObjectProperty $Lesson 'pedagogicalLogic' (Text `
        "De les bouwt op van expliciete uitleg en concrete voorbeelden naar begeleid oefenen, zelfstandig toepassen en een toets zonder hints." `
        "La leçon progresse d'une explication explicite et d'exemples concrets vers l'entraînement guidé, l'application autonome et un test sans indices." `
        "The lesson progresses from explicit explanation and concrete examples to guided practice, independent application and a test without hints.")
    Set-ObjectProperty $Lesson 'defaultLocale' 'nl-BE'

    $fields = [System.Collections.ArrayList]::new()
    Add-ContentField $fields "$lessonId.field.title" $lessonId 'title' $Lesson.title
    Add-ContentField $fields "$lessonId.field.description" $lessonId 'description' $Lesson.description
    Add-ContentField $fields "$lessonId.field.pedagogical-logic" $lessonId 'pedagogicalLogic' $Lesson.pedagogicalLogic

    foreach ($visual in @($Lesson.visuals)) {
        if ($null -eq $visual) { continue }
        Add-ContentField $fields "$($visual.id).field.alt-text" ([string]$visual.id) 'altText' ([ordered]@{ visible = $visual.altText })
    }

    foreach ($stage in @($Lesson.stages)) {
        if ($null -eq $stage) { continue }
        if ([string]$stage.type -in @('EXERCISES', 'PRACTICE')) {
            Set-ObjectProperty $stage 'type' 'PRACTISE'
            Set-ObjectProperty $stage 'title' (Text 'Oefenen' 'S''entraîner' 'Practice')
        } elseif ([string]$stage.type -eq 'APPLY') {
            Set-ObjectProperty $stage 'type' 'APPLY_DEMONSTRATE'
        } elseif ([string]$stage.type -eq 'TEST') {
            Set-ObjectProperty $stage 'type' 'MASTERY_TEST'
        }
        Add-ContentField $fields "$($stage.id).field.title" ([string]$stage.id) 'title' $stage.title
        $pageNumber = 1
        foreach ($block in @($stage.contentBlocks)) {
            if ($null -eq $block) { continue }
            if ($stage.type -eq 'THEORY' -and $block.type -in @('CORRECT_RESULT_RULE', 'WORKED_EXAMPLE', 'NON_EXAMPLE')) { $pageNumber = 2 }
            Set-ObjectProperty $block 'pageId' "$($stage.id).page.$pageNumber"
            Set-ObjectProperty $block 'pageSequence' $pageNumber
            $fieldIds = [ordered]@{}
            if ($null -ne $block.title) {
                $fieldIds.title = "$($block.id).field.title"
                Add-ContentField $fields $fieldIds.title ([string]$block.id) 'title' $block.title
            }
            if ($null -ne $block.body) {
                $fieldIds.body = "$($block.id).field.body"
                Add-ContentField $fields $fieldIds.body ([string]$block.id) 'body' $block.body
            }
            $termIndex = 0
            foreach ($term in @($block.terms)) {
                if ($null -eq $term) { continue }
                $termIndex += 1
                $termLabel = [string]$term.term.visible.'nl-BE'
                $termId = "$($block.id).term.$((StableToken $termLabel)).$termIndex"
                Set-ObjectProperty $term 'id' $termId
                $termFieldIds = [ordered]@{}
                foreach ($name in @('term', 'definition', 'example', 'nonExample')) {
                    $value = $term.$name
                    if ($null -ne $value) {
                        $termFieldIds[$name] = "$termId.field.$((StableToken $name))"
                        Add-ContentField $fields $termFieldIds[$name] $termId $name $value
                    }
                }
                Set-ObjectProperty $term 'contentFieldIds' $termFieldIds
            }
            Set-ObjectProperty $block 'contentFieldIds' $fieldIds
        }

        $setIndex = 0
        foreach ($set in @($stage.exerciseSets)) {
            if ($null -eq $set) { continue }
            $setIndex += 1
            $setTitle = if ($stage.type -eq 'APPLY_DEMONSTRATE') {
                Text "Toepassing $setIndex" "Application $setIndex" "Application $setIndex"
            } elseif ($stage.type -eq 'MASTERY_TEST') {
                Text "Toets $setIndex" "Test $setIndex" "Test $setIndex"
            } else {
                Text "Oefenreeks $setIndex" "Série d'exercices $setIndex" "Exercise set $setIndex"
            }
            Set-ObjectProperty $set 'title' $setTitle
            Set-ObjectProperty $set 'sequence' $setIndex
            Set-ObjectProperty $set 'lessonId' $lessonId
            Set-ObjectProperty $set 'stageId' ([string]$stage.id)
            Set-ObjectProperty $set 'exerciseSetId' ([string]$set.id)
            Set-ObjectProperty $set 'validationStatus' $(if ($publicationStatus -eq 'ACTIVE') { 'VALIDATED' } else { 'NEEDS_REVIEW' })
            Set-ObjectProperty $set 'publicationStatus' $publicationStatus
            Set-ObjectProperty $set 'technicalExecution' $true
            $questionBlueprints = @()
            for ($questionIndex = 1; $questionIndex -le [int]$set.itemCount; $questionIndex++) {
                $questionId = "$($set.id).question.$questionIndex"
                $questionBlueprints += [ordered]@{
                    id = $questionId
                    lessonId = $lessonId
                    stageId = [string]$stage.id
                    exerciseSetId = [string]$set.id
                    generatorVersion = 'v2'
                    origin = [string]$set.origin
                    validationStatus = [string]$set.validationStatus
                    publicationStatus = $publicationStatus
                    answerOptionIds = @(1..4 | ForEach-Object { "$questionId.answer.$_" })
                }
            }
            Set-ObjectProperty $set 'questionBlueprints' $questionBlueprints
            Add-ContentField $fields "$($set.id).field.title" ([string]$set.id) 'title' $setTitle
        }
    }

    Set-ObjectProperty $Lesson 'contentFields' @($fields)
    $localeStatus = [ordered]@{}
    foreach ($locale in @('nl-BE', 'fr-BE', 'en-US')) {
        $translations = @($fields | ForEach-Object { @($_.translations) } | Where-Object locale -eq $locale)
        $status = if ($locale -eq 'nl-BE') { 'DEFAULT' } elseif ($translations.status -contains 'MISSING') { 'MISSING' } elseif ($translations.status -contains 'NEEDS_REVIEW') { 'NEEDS_REVIEW' } else { 'COMPLETE' }
        $localeStatus[$locale] = [ordered]@{ id = "$lessonId.translation-status.$((StableToken $locale))"; status = $status; comment = '' }
    }
    Set-ObjectProperty $Lesson 'translationStatus' $localeStatus
}

function LessonIdFor($Unit) {
    $id = [string]$Unit.unit_id
    if ($id -eq "unit.g1.math.operations.optellen-tot-10-en-20") { return "lesson.g1.math.addition.within10.meaning-and-model" }
    if ($id -eq "unit.g1.nl.technical_reading.luisteren-naar-klanken-in-woorden") { return "lesson.g1.reading.initial-sound.identify-and-compare" }
    if ($id -eq "unit.g1.math.measurement.lengte-massa-en-inhoud-vergelijken") { return "lesson.g1.geometry.length.compare-and-order" }
    return $id -replace '^unit\.', 'lesson.'
}

function ExamplesFor($Unit) {
    $id = [string]$Unit.unit_id
    $examples = switch -Regex ($id) {
        'hoeveelheden-tellen' { @("Je ziet twaalf fiches als tien fiches en nog twee: 12.", "Vijf stippen en nog vier stippen zijn samen negen stippen.", "Vijftien is meer dan dertien.") ; break }
        'getallenlijn-en-volgorde' { @("Op de getallenlijn staat 8 tussen 7 en 9.", "Na 14 komt 15; vóór 14 staat 13.", "Twee sprongen vooruit vanaf 6 brengen je bij 8.") ; break }
        'samenstellen-en-splitsen' { @("Zeven kun je splitsen in 5 en 2.", "Tien kun je splitsen in 6 en 4.", "Bij 8 = 3 + ? ontbreekt het deel 5.") ; break }
        'optellen-tot' { @("Vier erbij drie geeft zeven: 4 + 3 = 7.", "Acht en twee vullen samen het tienraam: 8 + 2 = 10.", "9 + 4 kun je splitsen als 9 + 1 + 3 = 13.") ; break }
        'aftrekken-tot' { @("Van negen haal je drie weg: 9 - 3 = 6.", "Het verschil tussen 8 en 5 is 3.", "Bij 12 - 5 vul je van 5 aan tot 12: het verschil is 7.") ; break }
        'rekenfeiten' { @("De dubbel van 4 is 8.", "6 + 7 is bijna dubbel 6: 12 + 1 = 13.", "Omdat 3 + 8 = 11, is 8 + 3 ook 11.") ; break }
        'helft-dubbel-en-kwart' { @("De helft van acht blokken is vier blokken.", "Een kwart van acht is twee.", "Dubbel drie is zes.") ; break }
        'lengte-massa-en-inhoud' { @("Een lint van 8 cm is langer dan een lint van 5 cm.", "Op een balans zakt het zwaarste voorwerp omlaag.", "In de volle maatbeker zit meer dan in de halfvolle beker.") ; break }
        'tijd-kalender-en-klok' { @("Na maandag komt dinsdag.", "De korte wijzer op 3 en de lange wijzer op 12 is drie uur.", "Halfvier ligt tussen drie uur en vier uur.") ; break }
        'geld-tot' { @("Een munt van 2 euro en een munt van 1 euro zijn samen 3 euro.", "Tien euro kun je maken met twee briefjes van 5 euro.", "Je betaalt 8 euro met 10 euro en krijgt 2 euro terug.") ; break }
        'vlakke-figuren' { @("Een driehoek heeft drie zijden en drie hoeken.", "Een vierkant heeft vier even lange zijden.", "Een bol kan rollen en heeft geen vlakke zijden.") ; break }
        'orientatie-patronen' { @("In rood-blauw-rood-blauw komt daarna rood.", "De ster staat rechts van de maan.", "Een spiegelbeeld heeft aan beide kanten dezelfde vorm.") ; break }
        'gegevens-verzamelen' { @("Vier streepjes en een vijfde dwarsstreep vormen een groepje van vijf.", "In een beeldgrafiek met zes appels is het aantal 6.", "Een tabel helpt aantallen ordelijk vergelijken.") ; break }
        'rekenverhalen' { @("Er zijn 5 vogels en 2 vliegen erbij: 5 + 2 = 7 vogels.", "Van 9 ballen rollen er 3 weg: 9 - 3 = 6 ballen.", "Controleer of het antwoord past bij de vraag van het verhaal.") ; break }
        'klankzuivere-woorden' { @("Bij ‘vis’ hoor je /v/ - /i/ - /s/ en schrijf je v-i-s.", "Bij ‘tak’ leg je drie klankfiches en schrijf je drie letters.", "Lees het geschreven woord opnieuw om je dictee te controleren.") ; break }
        'van-woord-naar-correcte-zin' { @("‘De kat slaapt.’ begint met een hoofdletter en eindigt met een punt.", "Van ‘Mila - leest - een boek’ maak je ‘Mila leest een boek.’", "Een zin vertelt een volledige gedachte.") ; break }
        'kleine-letters-hoofdletters' { @("Begin een kleine letter bij het aangegeven startpunt.", "De hoofdletter M gebruik je aan het begin van Mila.", "Laat tussen twee geschreven woorden een duidelijke spatie.") ; break }
        'schrijfhouding-pengreep' { @("Zet beide voeten stevig neer en houd je schouders ontspannen.", "Houd het potlood losjes tussen duim en vingers.", "Volg de pijlenroute van startpunt naar eindpunt.") ; break }
        default { @("Je benoemt het kernbegrip en toont het met een passend voorbeeld.", "Je gebruikt het eerste model stap voor stap en vertelt wat je ziet.", "Je controleert je antwoord met de succesregel van de les.") }
    }
    return @($examples)
}

function Join-Safe($Values) {
    return (@($Values) | ForEach-Object { ([string]$_).Replace('|', '/').Trim() } | Where-Object { $_ }) -join '|'
}

function KnowledgeParameters($Unit, $Acquire, $Practice, $Transfer, $Peers, [string]$Domain, [string]$Phase, [string[]]$Examples) {
    $profile = $knowledgeProfileByUnit[[string]$Unit.unit_id]
    if ($null -ne $profile) {
        return @{
            grade = "1"; unit_id = [string]$Unit.unit_id; domain = $Domain
            skill = (([string]$Unit.domain_id) -replace '[^a-zA-Z0-9_]+', '_')
            phase = $Phase; editorial_status = "approved"
            title_nl = [string]$profile.title[0]; title_fr = [string]$profile.title[1]; title_en = [string]$profile.title[2]
            focus_nl = [string]$profile.facts[0][0]; focus_fr = [string]$profile.facts[0][1]; focus_en = [string]$profile.facts[0][2]
            target_nl = [string]$profile.facts[1][0]; target_fr = [string]$profile.facts[1][1]; target_en = [string]$profile.facts[1][2]
            how_nl = [string]$profile.facts[2][0]; how_fr = [string]$profile.facts[2][1]; how_en = [string]$profile.facts[2][2]
            check_nl = [string]$profile.facts[3][0]; check_fr = [string]$profile.facts[3][1]; check_en = [string]$profile.facts[3][2]
            example_nl = [string]$profile.facts[4][0]; example_fr = [string]$profile.facts[4][1]; example_en = [string]$profile.facts[4][2]
            non_example_nl = [string]$profile.warning[0]; non_example_fr = [string]$profile.warning[1]; non_example_en = [string]$profile.warning[2]
            terms_nl = Join-Safe @($profile.terms | ForEach-Object { $_[0] }); terms_fr = Join-Safe @($profile.terms | ForEach-Object { $_[1] }); terms_en = Join-Safe @($profile.terms | ForEach-Object { $_[2] })
            models_nl = [string]$profile.facts[5][0]; models_fr = [string]$profile.facts[5][1]; models_en = [string]$profile.facts[5][2]
            distractor_focus_nl = [string]$profile.warning[0]; distractor_focus_fr = [string]$profile.warning[1]; distractor_focus_en = [string]$profile.warning[2]
            distractor_target_nl = [string]$profile.facts[7][0]; distractor_target_fr = [string]$profile.facts[7][1]; distractor_target_en = [string]$profile.facts[7][2]
            distractor_terms_nl = [string]$profile.facts[8][0]; distractor_terms_fr = [string]$profile.facts[8][1]; distractor_terms_en = [string]$profile.facts[8][2]
            distractor_models_nl = [string]$profile.facts[9][0]; distractor_models_fr = [string]$profile.facts[9][1]; distractor_models_en = [string]$profile.facts[9][2]
        }
    }
    $procedures = @($Acquire.theory.procedural_knowledge | Select-Object -First 3)
    $how = if ($procedures.Count -gt 0) { ($procedures -join ' Daarna: ') } else { [string]$Unit.core_skill }
    $check = @($Acquire.success_criteria_student_language)[2]
    if ([string]::IsNullOrWhiteSpace($check)) { $check = "Ik controleer of mijn antwoord, uitvoering of conclusie klopt." }
    $misconception = @($Acquire.common_misconceptions)[0]
    if ([string]::IsNullOrWhiteSpace($misconception)) { $misconception = "Alleen gokken zonder de aangeleerde aanpak te gebruiken." }
    $peerFocus = @($Peers | ForEach-Object { $_.focus } | Select-Object -First 2)
    $peerSkills = @($Peers | ForEach-Object { $_.core_skill } | Select-Object -First 2)
    $peerTerms = @($Peers | ForEach-Object { $_.vocabulary } | Select-Object -First 8)
    $peerModels = @($Peers | ForEach-Object { $_.models } | Select-Object -First 6)
    return @{
        grade = "1"; unit_id = [string]$Unit.unit_id; domain = $Domain
        skill = (([string]$Unit.domain_id) -replace '[^a-zA-Z0-9_]+', '_')
        phase = $Phase; editorial_status = "approved"
        title_nl = [string]$Unit.title; title_fr = [string]$Unit.title; title_en = [string]$Unit.title
        focus_nl = [string]$Unit.focus; focus_fr = [string]$Unit.focus; focus_en = [string]$Unit.focus
        target_nl = [string]$Unit.core_skill; target_fr = [string]$Unit.core_skill; target_en = [string]$Unit.core_skill
        how_nl = $how; how_fr = $how; how_en = $how
        check_nl = $check; check_fr = $check; check_en = $check
        example_nl = $Examples[0]; example_fr = $Examples[0]; example_en = $Examples[0]
        non_example_nl = $misconception; non_example_fr = $misconception; non_example_en = $misconception
        terms_nl = Join-Safe $Unit.vocabulary; terms_fr = Join-Safe $Unit.vocabulary; terms_en = Join-Safe $Unit.vocabulary
        models_nl = Join-Safe $Unit.models; models_fr = Join-Safe $Unit.models; models_en = Join-Safe $Unit.models
        distractor_focus_nl = Join-Safe $peerFocus; distractor_focus_fr = Join-Safe $peerFocus; distractor_focus_en = Join-Safe $peerFocus
        distractor_target_nl = Join-Safe $peerSkills; distractor_target_fr = Join-Safe $peerSkills; distractor_target_en = Join-Safe $peerSkills
        distractor_terms_nl = Join-Safe $peerTerms; distractor_terms_fr = Join-Safe $peerTerms; distractor_terms_en = Join-Safe $peerTerms
        distractor_models_nl = Join-Safe $peerModels; distractor_models_fr = Join-Safe $peerModels; distractor_models_en = Join-Safe $peerModels
    }
}

$resolvedReference = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $ReferenceRoot))
$unitsPath = Join-Path $resolvedReference "data/units.json"
$lessonsPath = Join-Path $resolvedReference "data/lessons.json"
if (-not (Test-Path -LiteralPath $unitsPath -PathType Leaf) -or -not (Test-Path -LiteralPath $lessonsPath -PathType Leaf)) {
    throw "The supplied curriculum reference is incomplete: $resolvedReference"
}

$allUnits = Get-Content -Raw -LiteralPath $unitsPath -Encoding UTF8 | ConvertFrom-Json
$allSourceLessons = Get-Content -Raw -LiteralPath $lessonsPath -Encoding UTF8 | ConvertFrom-Json
$knowledgeProfiles = @(Get-Content -Raw -LiteralPath $KnowledgeProfilesPath -Encoding UTF8 | ConvertFrom-Json)
$curriculumManifest = Get-Content -Raw -LiteralPath $ManifestPath -Encoding UTF8 | ConvertFrom-Json
$knowledgeProfileByUnit = @{}
foreach ($profile in $knowledgeProfiles) {
    $knowledgeProfileByUnit[[string]$profile.unitId] = $profile
}
$units = @($allUnits | Where-Object grade -eq 1 | Sort-Object subject_id, sequence_in_grade_subject)
$sourceLessons = @($allSourceLessons | Where-Object grade -eq 1)
$sourceByUnit = $sourceLessons | Group-Object unit_id -AsHashTable -AsString
$pilotCatalog = Get-Content -Raw -LiteralPath $PilotCatalogPath -Encoding UTF8 | ConvertFrom-Json
$retainedGrade2 = @($pilotCatalog.lessons | Where-Object grade -eq 2)
$generatedLessons = @()
$index = @()
$previousBySubject = @{}

foreach ($unit in $units) {
    $source = @($sourceByUnit[[string]$unit.unit_id])
    $acquire = @($source | Where-Object phase -eq "acquire")[0]
    $practice = @($source | Where-Object phase -eq "practice")[0]
    $transfer = @($source | Where-Object phase -eq "transfer")[0]
    if ($null -eq $acquire -or $null -eq $practice -or $null -eq $transfer) {
        throw "Unit $($unit.unit_id) does not have acquire, practice and transfer source fiches."
    }
    $route = RoutingFor $unit
    $domain = $route[0]
    $subcategory = $route[1]
    $curriculumLocation = CurriculumLocationFor $unit
    $curriculumCategoryId = $curriculumLocation[0]
    $curriculumSubcategoryId = $curriculumLocation[1]
    $manifestCategory = @($curriculumManifest.categories | Where-Object id -eq $curriculumCategoryId)[0]
    if ($null -eq $manifestCategory -or $curriculumSubcategoryId -notin @($manifestCategory.subcategories.id)) {
        throw "Unknown curriculum location $curriculumCategoryId/$curriculumSubcategoryId for $($unit.unit_id)."
    }
    $lessonId = LessonIdFor $unit
    $knowledgeProfile = $knowledgeProfileByUnit[[string]$unit.unit_id]
    $examples = ExamplesFor $unit
    $visualKey = (([string]$unit.unit_id) -replace '^unit.g1.', '') -replace '[^a-z0-9.-]+', '-'
    $visualId = "visual.g1.$visualKey"
    $assetBase = "poincare/visuals/grade1/$visualKey"
    $visualKind = if ($domain -eq "WORLD_KNOWLEDGE") { "WORLD_BICOLOR_ILLUSTRATION" } elseif ($domain -eq "GEOMETRY") { "MEASUREMENT_DIAGRAM" } elseif ($domain -eq "MATH") { "MATH_REPRESENTATION" } else { "GENERIC_DIAGRAM" }
    $palette = if ($domain -eq "WORLD_KNOWLEDGE") { "PLANET_GOLD_WHITE_ONLY" } else { "POINCARE_TRICOLOUR" }
    $visual = [ordered]@{
        id = $visualId; kind = $visualKind; representationId = "curriculum.$visualKey.v1"
        assetPathSvg = "$assetBase.svg"; assetPathPng = "$assetBase.png"
        palettePolicy = $palette; transparentBackground = $true
        altText = [ordered]@{
            "nl-BE" = "Een instructieschema voor $($unit.title) met de kernbegrippen en hun verband."
            "fr-BE" = "Een instructieschema voor $($unit.title) met de kernbegrippen en hun verband."
            "en-US" = "Een instructieschema voor $($unit.title) met de kernbegrippen en hun verband."
        }
        licenseId = "poincare-original"
    }
    if ($null -ne $knowledgeProfile) {
        $visual.altText = [ordered]@{
            "nl-BE" = "Een instructieschema voor $($knowledgeProfile.title[0]) met concrete voorbeelden uit de les."
            "fr-BE" = "Un schéma d'apprentissage pour $($knowledgeProfile.title[1]) avec des exemples concrets de la leçon."
            "en-US" = "An instructional diagram for $($knowledgeProfile.title[2]) with concrete lesson examples."
        }
    }
    $visuals = @($visual)
    $secondaryVisualId = $null
    if ($unit.unit_id -eq "unit.g1.math.operations.optellen-tot-10-en-20") {
        $visual.id = "visual.g1.math.addition.within10.example1"
        $visual.representationId = "math.addition.counters.3_plus_2.v1"
        $visual.assetPathSvg = $null
        $visual.assetPathPng = "poincare/visuals/theory/addition-within10-v1.png"
        $visual.altText = [ordered]@{
            "nl-BE" = "Drie gele bollen en twee blauwe bollen worden samengevoegd in één tienraam."
            "fr-BE" = "Trois jetons jaunes et deux jetons bleus sont réunis dans un cadre de dix."
            "en-US" = "Three yellow counters and two blue counters are joined in one ten-frame."
        }
        $visualId = $visual.id
        $secondaryVisualId = "visual.g1.math.addition.within10.example2"
        $visuals = @($visual, [ordered]@{
            id = $secondaryVisualId; kind = "MATH_REPRESENTATION"; representationId = "math.addition.counters.4_plus_3.v1"
            assetPathPng = "poincare/visuals/theory/addition-within10-example2-v1.png"
            palettePolicy = "POINCARE_TRICOLOUR"; transparentBackground = $true
            altText = [ordered]@{
                "nl-BE" = "Vier gele bollen en drie blauwe bollen worden samengevoegd in één tienraam."
                "fr-BE" = "Quatre jetons jaunes et trois jetons bleus sont réunis dans un cadre de dix."
                "en-US" = "Four yellow counters and three blue counters are joined in one ten-frame."
            }
            licenseId = "poincare-original"
        })
    } elseif ($unit.unit_id -eq "unit.g1.nl.technical_reading.luisteren-naar-klanken-in-woorden") {
        $visual.id = "visual.g1.reading.initial-sound.listen"
        $visual.kind = "PHONICS_DIAGRAM"
        $visual.representationId = "reading.initial_sound.listen.v1"
        $visual.assetPathSvg = $null
        $visual.assetPathPng = "poincare/visuals/theory/initial-sound-v1.png"
        $visual.altText = [ordered]@{
            "nl-BE" = "Een oor luistert naar de eerste klank van een gesproken woord."
            "fr-BE" = "Une oreille écoute le premier son d’un mot prononcé."
            "en-US" = "An ear listens for the first sound in a spoken word."
        }
        $visualId = $visual.id
        $visuals = @($visual)
    } elseif ($unit.unit_id -eq "unit.g1.math.measurement.lengte-massa-en-inhoud-vergelijken") {
        $visual.id = "visual.g1.geometry.length.compare"
        $visual.kind = "MEASUREMENT_DIAGRAM"
        $visual.representationId = "geometry.length.direct_compare.v1"
        $visual.assetPathSvg = $null
        $visual.assetPathPng = "poincare/visuals/theory/length-compare-v1.png"
        $visual.altText = [ordered]@{
            "nl-BE" = "Twee stroken liggen aan hetzelfde beginpunt; de onderste strook is langer."
            "fr-BE" = "Deux bandes partent du même point; la bande du bas est plus longue."
            "en-US" = "Two strips share the same starting point; the lower strip is longer."
        }
        $visualId = $visual.id
        $visuals = @($visual)
    }
    $terms = @($unit.vocabulary | ForEach-Object { Term ([string]$_) ([string]$unit.core_skill) })
    $procedure = @($acquire.theory.procedural_knowledge | Select-Object -First 4) -join "`n"
    $check = @($acquire.success_criteria_student_language)[2]
    if ([string]::IsNullOrWhiteSpace($check)) { $check = "Ik controleer of mijn antwoord, uitvoering of conclusie klopt." }
    $misconception = @($acquire.common_misconceptions)[0]
    if ([string]::IsNullOrWhiteSpace($misconception)) { $misconception = "Alleen gokken zonder het model of de controle te gebruiken." }
    $blocks = @(
        (Block "$lessonId.intent" "LEARNING_INTENT" (Text "Je leert om $($unit.core_skill).")),
        (Block "$lessonId.words" "VOCABULARY" $null (Text "Kernwoorden") @() $terms),
        (Block "$lessonId.meaning" "CHILD_FRIENDLY_DEFINITION" (Text ([string]$unit.focus)) (Text "Wat betekent dit?")),
        (Block "$lessonId.how" "HOW_IT_WORKS" (Text $procedure) (Text "Zo werkt het") @($visualId)),
        (Block "$lessonId.correct" "CORRECT_RESULT_RULE" (Text $check) (Text "Zo controleer je")),
        (Block "$lessonId.example-1" "WORKED_EXAMPLE" (Text $examples[0]) (Text "Voorbeeld 1") @($visualId)),
        (Block "$lessonId.example-2" "WORKED_EXAMPLE" (Text $examples[1]) (Text "Voorbeeld 2") $(if ($secondaryVisualId) { @($secondaryVisualId) } else { @() })),
        (Block "$lessonId.example-3" "WORKED_EXAMPLE" (Text $examples[2]) (Text "Voorbeeld 3")),
        (Block "$lessonId.non-example" "NON_EXAMPLE" (Text "Dit werkt niet: $misconception") (Text "Let op"))
    )
    $lessonTitle = Text ([string]$unit.title)
    $lessonSummary = Text ([string]$unit.focus)
    if ($null -ne $knowledgeProfile) {
        $profileTerms = @($knowledgeProfile.terms | ForEach-Object { ProfileTerm $_ $knowledgeProfile.facts[0] })
        $blocks = @(
            (Block "$lessonId.intent" "LEARNING_INTENT" (ProfileSentence "Je leert: " "Tu apprends ceci : " "You learn this: " $knowledgeProfile.facts[0])),
            (Block "$lessonId.words" "VOCABULARY" $null (Text "Kernwoorden" "Mots-clés" "Key words") @() $profileTerms),
            (Block "$lessonId.meaning" "CHILD_FRIENDLY_DEFINITION" (ProfileText $knowledgeProfile.facts[0]) (Text "Wat betekent dit?" "Qu'est-ce que cela signifie ?" "What does this mean?")),
            (Block "$lessonId.how" "HOW_IT_WORKS" (Text `
                "$($knowledgeProfile.facts[1][0])`n$($knowledgeProfile.facts[2][0])`n$($knowledgeProfile.facts[3][0])" `
                "$($knowledgeProfile.facts[1][1])`n$($knowledgeProfile.facts[2][1])`n$($knowledgeProfile.facts[3][1])" `
                "$($knowledgeProfile.facts[1][2])`n$($knowledgeProfile.facts[2][2])`n$($knowledgeProfile.facts[3][2])") `
                (Text "Zo werkt het" "Voici comment faire" "How it works") @($visualId)),
            (Block "$lessonId.correct" "CORRECT_RESULT_RULE" (ProfileText $knowledgeProfile.facts[7]) (Text "Zo controleer je" "Voici comment vérifier" "How to check")),
            (Block "$lessonId.example-1" "WORKED_EXAMPLE" (ProfileText $knowledgeProfile.facts[4]) (Text "Voorbeeld 1" "Exemple 1" "Example 1") @($visualId)),
            (Block "$lessonId.example-2" "WORKED_EXAMPLE" (ProfileText $knowledgeProfile.facts[5]) (Text "Voorbeeld 2" "Exemple 2" "Example 2")),
            (Block "$lessonId.example-3" "WORKED_EXAMPLE" (ProfileText $knowledgeProfile.facts[6]) (Text "Voorbeeld 3" "Exemple 3" "Example 3")),
            (Block "$lessonId.non-example" "NON_EXAMPLE" (ProfileText $knowledgeProfile.warning) (Text "Let op" "Attention" "Watch out"))
        )
        $lessonTitle = ProfileText $knowledgeProfile.title
        $lessonSummary = ProfileText $knowledgeProfile.facts[0]
    } elseif ($lessonId -eq "lesson.g1.math.addition.within10.meaning-and-model") {
        $lessonTitle = Text "Optellen tot 10 en 20" "Additionner jusqu’à 10 et 20" "Add within 10 and 20"
        $lessonSummary = Text "Voeg hoeveelheden samen met een tienraam en controleer de som." "Réunis des quantités dans un cadre de dix et vérifie la somme." "Join quantities with a ten-frame and check the sum."
        $blocks = @(
            (Block "$lessonId.intent" "LEARNING_INTENT" (Text "Je leert hoeveel er samen zijn wanneer je erbij doet." "Tu apprends combien il y en a en tout lorsque tu ajoutes." "You learn how many there are altogether when you add.")),
            (Block "$lessonId.words" "VOCABULARY" $null (Text "Kernwoorden" "Mots-clés" "Key words") @() @(
                [ordered]@{ term = Text "optellen" "additionner" "add"; definition = Text "Twee hoeveelheden samen nemen of iets erbij doen." "Réunir deux quantités ou ajouter quelque chose." "Join two quantities or add something." },
                [ordered]@{ term = Text "som" "somme" "sum"; definition = Text "Het antwoord van een optelling." "Le résultat d’une addition." "The answer to an addition." }
            )),
            (Block "$lessonId.how" "HOW_IT_WORKS" (Text "Leg het eerste deel neer. Voeg het tweede deel toe. Vul het tienraam van links naar rechts en tel het geheel." "Place la première partie. Ajoute la deuxième. Remplis le cadre de dix de gauche à droite et compte le tout." "Put down the first part. Add the second part. Fill the ten-frame from left to right and count the whole.") (Text "Zo werkt het" "Voici comment faire" "How it works") @("visual.g1.math.addition.within10.example1")),
            (Block "$lessonId.correct" "CORRECT_RESULT_RULE" (Text "Het aantal bollen bovenaan en in het tienraam moet hetzelfde zijn." "Le nombre de jetons en haut et dans le cadre de dix doit être identique." "The number of counters above and in the ten-frame must match.")),
            (Block "$lessonId.example-1" "WORKED_EXAMPLE" (Text "Drie en twee samen zijn vijf: 3 + 2 = 5." "Trois et deux font cinq : 3 + 2 = 5." "Three and two make five: 3 + 2 = 5.") (Text "Voorbeeld 1" "Exemple 1" "Example 1")),
            (Block "$lessonId.example-2" "WORKED_EXAMPLE" (Text "Vier en drie samen zijn zeven: 4 + 3 = 7." "Quatre et trois font sept : 4 + 3 = 7." "Four and three make seven: 4 + 3 = 7.") (Text "Voorbeeld 2" "Exemple 2" "Example 2") @("visual.g1.math.addition.within10.example2")),
            (Block "$lessonId.non-example" "NON_EXAMPLE" (Text "Tel niet alleen het tweede deel; tel beide delen samen." "Ne compte pas seulement la deuxième partie ; compte les deux parties ensemble." "Do not count only the second part; count both parts together."))
        )
    } elseif ($lessonId -eq "lesson.g1.reading.initial-sound.identify-and-compare") {
        $lessonTitle = Text "Hoor de beginklank" "Entends le son initial" "Hear the initial sound"
        $lessonSummary = Text "Luister naar de eerste klank en vergelijk woorden." "Écoute le premier son et compare des mots." "Listen to the first sound and compare words."
        $blocks = @(
            (Block "$lessonId.intent" "LEARNING_INTENT" (Text "Je leert de eerste klank van een woord horen en vergelijken." "Tu apprends à entendre et comparer le premier son d’un mot." "You learn to hear and compare the first sound in a word.")),
            (Block "$lessonId.words" "VOCABULARY" $null (Text "Kernwoorden" "Mots-clés" "Key words") @() @(
                [ordered]@{ term = Text "klank" "son" "sound"; definition = Text "Wat je hoort wanneer je een woord langzaam zegt." "Ce que tu entends quand tu prononces un mot lentement." "What you hear when you say a word slowly." },
                [ordered]@{ term = Text "beginklank" "son initial" "initial sound"; definition = Text "De eerste klank die je in een woord hoort." "Le premier son que tu entends dans un mot." "The first sound you hear in a word." }
            )),
            (Block "$lessonId.how" "HOW_IT_WORKS" (Text "Zeg het woord langzaam. Luister alleen naar de eerste klank." "Dis le mot lentement. Écoute seulement le premier son." "Say the word slowly. Listen only to the first sound.") (Text "Zo werkt het" "Voici comment faire" "How it works") @("visual.g1.reading.initial-sound.listen")),
            (Block "$lessonId.correct" "CORRECT_RESULT_RULE" (Text "Twee woorden passen samen wanneer hun eerste gehoorde klank dezelfde is." "Deux mots vont ensemble lorsque leur premier son entendu est le même." "Two words match when their first heard sound is the same.")),
            (Block "$lessonId.example-1" "WORKED_EXAMPLE" (Text "Maan en muis beginnen allebei met de klank m." "Maman et moto commencent tous les deux par le son m." "Moon and mouse both start with the sound m.")),
            (Block "$lessonId.non-example" "NON_EXAMPLE" (Text "Kijk niet alleen naar de eerste letter; luister naar de eerste klank." "Ne regarde pas seulement la première lettre ; écoute le premier son." "Do not only look at the first letter; listen to the first sound."))
        )
    } elseif ($lessonId -eq "lesson.g1.geometry.length.compare-and-order") {
        $lessonTitle = Text "Vergelijk en orden lengtes" "Comparer et classer des longueurs" "Compare and order lengths"
        $lessonSummary = Text "Vergelijk vanuit hetzelfde beginpunt en orden van kort naar lang." "Compare à partir du même point de départ et classe du plus court au plus long." "Compare from the same start point and order from shortest to longest."
        $blocks = @(
            (Block "$lessonId.intent" "LEARNING_INTENT" (Text "Je leert lengtes vergelijken en van kort naar lang ordenen." "Tu apprends à comparer des longueurs et à les classer de la plus courte à la plus longue." "You learn to compare lengths and order them from shortest to longest.")),
            (Block "$lessonId.words" "VOCABULARY" $null (Text "Kernwoorden" "Mots-clés" "Key words") @() @(
                [ordered]@{ term = Text "langer" "plus long" "longer"; definition = Text "Een lengte die verder reikt vanaf hetzelfde beginpunt." "Une longueur qui va plus loin depuis le même point de départ." "A length that reaches farther from the same start point." },
                [ordered]@{ term = Text "korter" "plus court" "shorter"; definition = Text "Een lengte die minder ver reikt vanaf hetzelfde beginpunt." "Une longueur qui va moins loin depuis le même point de départ." "A length that reaches less far from the same start point." }
            )),
            (Block "$lessonId.how" "HOW_IT_WORKS" (Text "Leg de beginpunten gelijk. Kijk welk uiteinde verder ligt of lees de centimeters op de meetlat." "Aligne les points de départ. Regarde quelle extrémité va le plus loin ou lis les centimètres sur la règle." "Line up the start points. See which end reaches farther or read the centimeters on the ruler.") (Text "Zo werkt het" "Voici comment faire" "How it works") @("visual.g1.geometry.length.compare")),
            (Block "$lessonId.correct" "CORRECT_RESULT_RULE" (Text "De langste lengte heeft bij hetzelfde beginpunt het verste uiteinde." "La plus grande longueur a l’extrémité la plus éloignée depuis le même départ." "The longest length has the farthest end from the same start.")),
            (Block "$lessonId.example-1" "WORKED_EXAMPLE" (Text "Een lint van 8 cm is langer dan een lint van 5 cm." "Un ruban de 8 cm est plus long qu’un ruban de 5 cm." "An 8 cm ribbon is longer than a 5 cm ribbon.")),
            (Block "$lessonId.example-2" "WORKED_EXAMPLE" (Text "Van kort naar lang orden je 3 cm, 6 cm en 9 cm." "Du plus court au plus long : 3 cm, 6 cm et 9 cm." "From shortest to longest: 3 cm, 6 cm, and 9 cm.")),
            (Block "$lessonId.non-example" "NON_EXAMPLE" (Text "Vergelijk geen linten met verschillende beginpunten; leg ze eerst gelijk." "Ne compare pas des rubans dont les points de départ diffèrent ; aligne-les d’abord." "Do not compare ribbons with different start points; line them up first."))
        )
    }
    $peers = @($units | Where-Object { $_.subject_id -eq $unit.subject_id -and $_.unit_id -ne $unit.unit_id })
    $guided = GeneratorSpec $unit "guided" (KnowledgeParameters $unit $acquire $practice $transfer $peers $domain "guided" $examples)
    $independent = GeneratorSpec $unit "independent" (KnowledgeParameters $unit $acquire $practice $transfer $peers $domain "independent" $examples)
    $test = GeneratorSpec $unit "test" (KnowledgeParameters $unit $acquire $practice $transfer $peers $domain "test" $examples)
    if ($unit.unit_id -match '^unit\.g1\.nl\.oral\.') {
        $independent = [ordered]@{
            generatorId = "generator.g1.language.speaking.basic_interaction"
            parameters = [ordered]@{
                grade = "1"
                roadmap_entry = "roadmap.g1.language.speaking.basic_interaction"
                phase = "guided"
                editorial_status = "approved"
            }
        }
        $test = [ordered]@{
            generatorId = "generator.g1.language.speaking.basic_interaction"
            parameters = [ordered]@{
                grade = "1"
                roadmap_entry = "roadmap.g1.language.speaking.basic_interaction"
                phase = "checkpoint"
                editorial_status = "approved"
            }
        }
    }
    $test.parameters.exclude_recent_exact_items = "true"
    $tags = @("g1.$($unit.subject_id).$($unit.domain_id).knowledge", "g1.$($unit.subject_id).$($unit.domain_id).skill")
    $theoryMinutes = if ($unit.subject_id -in @("math", "nl")) { 15 } else { 18 }
    $stages = @(
        (Stage "$lessonId.theory" "THEORY" 1 $false $theoryMinutes $blocks @()),
        (Stage "$lessonId.practise" "PRACTISE" 2 $true 30 @() @(
            (GeneratedSet "$lessonId.set.guided" "GUIDED" 5 $guided.generatorId $guided.parameters $tags "FULL" "IMMEDIATE_EXPLANATORY" $false),
            (GeneratedSet "$lessonId.set.fading" "FADING_SUPPORT" 5 $guided.generatorId $guided.parameters $tags "FADING" "IMMEDIATE_EXPLANATORY" $true),
            (GeneratedSet "$lessonId.set.independent" "INDEPENDENT_PRACTICE" 6 $independent.generatorId $independent.parameters $tags "NONE" "IMMEDIATE_MINIMAL" $true)
        )),
        (Stage "$lessonId.apply" "APPLY_DEMONSTRATE" 3 $true 10 @() @(
            (GeneratedSet "$lessonId.set.repetition" "NEAR_TRANSFER" 4 $independent.generatorId $independent.parameters $tags "NONE" "AFTER_SET" $true)
        )),
        (Stage "$lessonId.test" "MASTERY_TEST" 4 $true 10 @() @(
            (GeneratedSet "$lessonId.set.test" "MASTERY_TEST" 10 $test.generatorId $test.parameters $tags "NONE" "AFTER_TEST" $false)
        ))
    )
    if ($unit.unit_id -match '^unit\.g1\.nl\.handwriting\.') {
        $letterGroups = if ($unit.unit_id -match 'schrijfhouding-pengreep') {
            @("lowercase_a_e", "lowercase_f_j", "lowercase_k_p", "lowercase_q_t", "lowercase_u_z_review")
        } else {
            @("uppercase_a_f", "uppercase_g_l", "uppercase_m_r", "uppercase_s_z", "number_0_9")
        }
        $traceItemCounts = if ($unit.unit_id -match 'schrijfhouding-pengreep') { @(5, 5, 6, 4, 10) } else { @(6, 6, 6, 8, 10) }
        $traceSets = @($stages | ForEach-Object { @($_['exerciseSets']) } | Where-Object { $null -ne $_ })
        for ($traceIndex = 0; $traceIndex -lt $traceSets.Count; $traceIndex++) {
            $traceParameters = [ordered]@{}
            foreach ($parameterKey in $traceSets[$traceIndex]['parameters'].Keys) {
                $traceParameters[$parameterKey] = $traceSets[$traceIndex]['parameters'][$parameterKey]
            }
            $traceParameters['mode'] = "letter_tracing"
            $traceParameters['letter_group'] = $letterGroups[$traceIndex]
            $traceSets[$traceIndex]['parameters'] = $traceParameters
            $traceSets[$traceIndex]['itemCount'] = $traceItemCounts[$traceIndex]
        }
    }
    if ($domain -in @("MATH", "GEOMETRY")) {
        $lessonRepresentationIds = @($visuals | ForEach-Object { [string]$_['representationId'] } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)
        foreach ($set in @($stages | ForEach-Object { @($_['exerciseSets']) } | Where-Object { $null -ne $_ })) {
            $set['representationIds'] = $lessonRepresentationIds
        }
    }
    $previous = $previousBySubject[[string]$unit.subject_id]
    $definition = [ordered]@{
        id = $lessonId; unitId = [string]$unit.unit_id; grade = 1; sequence = [int]$unit.sequence_in_grade_subject
        title = $lessonTitle; summary = $lessonSummary
        legacyRouting = [ordered]@{ domain = $domain; subcategoryId = $subcategory; legacyLessonIds = @() }
        learningGoalIds = @("goal.$lessonId")
        masteryTags = $tags
        prerequisiteLessonIds = @($previous | Where-Object { $_ })
        publicationStatus = $(if ($lessonId -in @(
            "lesson.g1.math.addition.within10.meaning-and-model",
            "lesson.g1.reading.initial-sound.identify-and-compare",
            "lesson.g1.geometry.length.compare-and-order"
        )) { "ACTIVE" } else { "DRAFT_REVIEW_REQUIRED" })
        visuals = $visuals
        stages = $stages
        gatePolicy = [ordered]@{
            evidenceSource = "MASTERY_TEST_ONLY"; minimumCorrectRatio = 0.75; minimumIndependentItems = 10
            maximumHints = 0; requiredMasteryTags = $tags; criticalTagMinimumCorrect = 1
            unlockOnPass = $true; retentionCheckRequiredForMastered = $true
        }
        errorRepetitionPolicy = [ordered]@{
            enabled = $true; repeatConceptNotExactItem = $true; maxInjectedShare = 0.25
            reviewIntervalsDays = @(0, 1, 3, 7); resolveAfterConsecutiveCorrect = 2
        }
        definitionVersion = 2
    }
    Add-LessonEditorialMetadata $definition $curriculumCategoryId $curriculumSubcategoryId
    $generatedLessons += $definition
    $previousBySubject[[string]$unit.subject_id] = $lessonId
    $index += [ordered]@{
        lessonId = $lessonId; unitId = [string]$unit.unit_id; sourceLessonIds = @($unit.lesson_ids)
        subjectId = [string]$unit.subject_id; domain = $domain; subcategoryId = $subcategory
        title = [string]$unit.title; visualKey = $visualKey; palette = $palette
        sourceMinutes = [int]$unit.estimated_minutes_total
        runtimeMinutes = ($stages | ForEach-Object { [int]$_['estimatedMinutes'] } | Measure-Object -Sum).Sum
        exerciseItems = ($stages | ForEach-Object { @($_['exerciseSets']) } | ForEach-Object { [int]$_['itemCount'] } | Measure-Object -Sum).Sum
    }
}

function New-WithinFiveLesson(
    $Source,
    [string]$LessonId,
    [string]$UnitId,
    [int]$Sequence,
    [string]$Operation,
    [string[]]$Prerequisites
) {
    $cloneJson = $Source | ConvertTo-Json -Depth 100
    $clone = $cloneJson.Replace([string]$Source.id, $LessonId) | ConvertFrom-Json
    $clone.unitId = $UnitId
    $clone.sequence = $Sequence
    $clone.publicationStatus = "DRAFT_REVIEW_REQUIRED"
    $clone.prerequisiteLessonIds = @($Prerequisites)

    $isAddition = $Operation -eq "addition"
    $title = if ($isAddition) {
        Text "Optellen tot 5" "Additionner jusqu’à 5" "Add within 5"
    } else {
        Text "Aftrekken tot 5" "Soustraire jusqu’à 5" "Subtract within 5"
    }
    $summary = if ($isAddition) {
        Text "Voeg kleine hoeveelheden samen en oefen alle optellingen met een uitkomst tot 5." "Réunis de petites quantités et exerce toutes les additions dont le résultat va jusqu’à 5." "Join small quantities and practise all additions with a result up to 5."
    } else {
        Text "Neem kleine hoeveelheden weg en oefen alle aftrekkingen met getallen tot 5." "Retire de petites quantités et exerce toutes les soustractions avec des nombres jusqu’à 5." "Take away small quantities and practise all subtractions with numbers up to 5."
    }
    $clone.title = $title
    $clone.summary = $summary

    $tagRoot = if ($isAddition) { "g1.math.operations.addition.within5" } else { "g1.math.operations.subtraction.within5" }
    $tags = @("$tagRoot.knowledge", "$tagRoot.skill")
    $clone.masteryTags = $tags
    $clone.gatePolicy.requiredMasteryTags = $tags
    foreach ($set in @($clone.stages | ForEach-Object exerciseSets | Where-Object { $null -ne $_ })) {
        $set.parameters.max_result = "5"
        $set.parameters.operation = $Operation
        $set.masteryTags = $tags
    }

    $visual = $Source.visuals[0] | ConvertTo-Json -Depth 20 | ConvertFrom-Json
    $visual.id = if ($isAddition) { "visual.g1.math.addition.within5" } else { "visual.g1.math.subtraction.within5" }
    if ($isAddition) {
        $visual.representationId = "math.addition.counters.3_plus_2.within5.v1"
        $visual.altText = [ordered]@{
            "nl-BE" = "Drie gele bollen en twee blauwe bollen vormen samen vijf."
            "fr-BE" = "Trois jetons jaunes et deux jetons bleus forment cinq."
            "en-US" = "Three yellow counters and two blue counters make five."
        }
    } else {
        $visual.representationId = "math.subtraction.within5.v1"
        $visual.altText = [ordered]@{
            "nl-BE" = "Een eenvoudig schema om aftrekken tot vijf te tonen."
            "fr-BE" = "Un schéma simple pour montrer la soustraction jusqu’à cinq."
            "en-US" = "A simple diagram showing subtraction within five."
        }
    }
    $clone.visuals = @($visual)
    foreach ($set in @($clone.stages | ForEach-Object exerciseSets | Where-Object { $null -ne $_ })) {
        $set.representationIds = @($visual.representationId)
    }

    $theory = @($clone.stages | Where-Object type -eq "THEORY")[0]
    if ($isAddition) {
        $theory.contentBlocks = @(
            (Block "$LessonId.intent" "LEARNING_INTENT" (Text "Je leert hoeveel er samen zijn als de uitkomst niet groter is dan 5." "Tu apprends combien il y en a en tout lorsque le résultat ne dépasse pas 5." "You learn how many there are altogether when the result is no more than 5.")),
            (Block "$LessonId.words" "VOCABULARY" $null (Text "Kernwoorden" "Mots-clés" "Key words") @() @(
                [ordered]@{ term = Text "optellen" "additionner" "add"; definition = Text "Twee hoeveelheden samen nemen." "Réunir deux quantités." "Join two quantities." },
                [ordered]@{ term = Text "som" "somme" "sum"; definition = Text "Het antwoord van een optelling." "Le résultat d’une addition." "The answer to an addition." }
            )),
            (Block "$LessonId.how" "HOW_IT_WORKS" (Text "Leg het eerste aantal neer. Voeg het tweede aantal toe. Tel alle voorwerpen samen." "Place la première quantité. Ajoute la deuxième. Compte tous les objets ensemble." "Put down the first amount. Add the second amount. Count all objects together.") (Text "Zo werkt het" "Voici comment faire" "How it works") @($visual.id)),
            (Block "$LessonId.correct" "CORRECT_RESULT_RULE" (Text "De som is nooit groter dan 5 en is gelijk aan alle voorwerpen samen." "La somme ne dépasse jamais 5 et correspond à tous les objets réunis." "The sum is never greater than 5 and equals all objects together.")),
            (Block "$LessonId.example-1" "WORKED_EXAMPLE" (Text "Twee en één samen zijn drie: 2 + 1 = 3." "Deux et un font trois : 2 + 1 = 3." "Two and one make three: 2 + 1 = 3.") (Text "Voorbeeld 1" "Exemple 1" "Example 1")),
            (Block "$LessonId.example-2" "WORKED_EXAMPLE" (Text "Drie en twee samen zijn vijf: 3 + 2 = 5." "Trois et deux font cinq : 3 + 2 = 5." "Three and two make five: 3 + 2 = 5.") (Text "Voorbeeld 2" "Exemple 2" "Example 2") @($visual.id)),
            (Block "$LessonId.non-example" "NON_EXAMPLE" (Text "Tel beide delen samen; tel niet alleen wat erbij komt." "Compte les deux parties, pas seulement ce qui s’ajoute." "Count both parts, not only what is added."))
        )
    } else {
        $theory.contentBlocks = @(
            (Block "$LessonId.intent" "LEARNING_INTENT" (Text "Je leert iets wegnemen van een aantal dat niet groter is dan 5." "Tu apprends à retirer quelque chose d’une quantité qui ne dépasse pas 5." "You learn to take something away from an amount no greater than 5.")),
            (Block "$LessonId.words" "VOCABULARY" $null (Text "Kernwoorden" "Mots-clés" "Key words") @() @(
                [ordered]@{ term = Text "aftrekken" "soustraire" "subtract"; definition = Text "Een deel wegnemen van het geheel." "Retirer une partie du tout." "Take one part away from the whole." },
                [ordered]@{ term = Text "verschil" "différence" "difference"; definition = Text "Het antwoord van een aftrekking." "Le résultat d’une soustraction." "The answer to a subtraction." }
            )),
            (Block "$LessonId.how" "HOW_IT_WORKS" (Text "Leg het beginaantal neer. Neem het tweede aantal weg. Tel hoeveel er overblijven." "Place la quantité de départ. Retire la deuxième quantité. Compte ce qui reste." "Put down the starting amount. Take away the second amount. Count what remains.") (Text "Zo werkt het" "Voici comment faire" "How it works") @($visual.id)),
            (Block "$LessonId.correct" "CORRECT_RESULT_RULE" (Text "Het verschil is wat er overblijft en ligt tussen 0 en 5." "La différence est ce qui reste et se situe entre 0 et 5." "The difference is what remains and lies between 0 and 5.")),
            (Block "$LessonId.example-1" "WORKED_EXAMPLE" (Text "Vijf min twee is drie: 5 − 2 = 3." "Cinq moins deux font trois : 5 − 2 = 3." "Five minus two is three: 5 − 2 = 3.") (Text "Voorbeeld 1" "Exemple 1" "Example 1")),
            (Block "$LessonId.example-2" "WORKED_EXAMPLE" (Text "Vier min één is drie: 4 − 1 = 3." "Quatre moins un font trois : 4 − 1 = 3." "Four minus one is three: 4 − 1 = 3.") (Text "Voorbeeld 2" "Exemple 2" "Example 2") @($visual.id)),
            (Block "$LessonId.non-example" "NON_EXAMPLE" (Text "Begin bij het geheel en neem weg; tel niet verder alsof je optelt." "Pars du tout et retire ; ne continue pas à compter comme pour une addition." "Start with the whole and take away; do not count on as if adding."))
        )
    }
    return $clone
}

$additionSource = @($generatedLessons | Where-Object id -eq "lesson.g1.math.addition.within10.meaning-and-model")[0]
$subtractionSource = @($generatedLessons | Where-Object id -eq "lesson.g1.math.operations.aftrekken-tot-10-en-20")[0]
if ($null -eq $additionSource -or $null -eq $subtractionSource) {
    throw "Cannot create within-five lessons because an operation source lesson is missing."
}

foreach ($lesson in @($generatedLessons | Where-Object { $_.unitId -match '^unit\.g1\.math\.' })) {
    $currentSequence = [int]$lesson.sequence
    if ($currentSequence -eq 4) { $lesson.sequence = 5 }
    elseif ($currentSequence -ge 5) { $lesson.sequence = $currentSequence + 2 }
}

$additionWithinFive = New-WithinFiveLesson $additionSource `
    "lesson.g1.math.addition.within5.foundations" `
    "unit.g1.math.operations.optellen-tot-5" `
    4 `
    "addition" `
    @($additionSource.prerequisiteLessonIds)
$subtractionWithinFive = New-WithinFiveLesson $subtractionSource `
    "lesson.g1.math.subtraction.within5.foundations" `
    "unit.g1.math.operations.aftrekken-tot-5" `
    6 `
    "subtraction" `
    @($subtractionSource.prerequisiteLessonIds)

Add-LessonEditorialMetadata $additionWithinFive "number_sense" "math.addition_subtraction"
Add-LessonEditorialMetadata $subtractionWithinFive "number_sense" "math.addition_subtraction"

$generatedLessons += @($additionWithinFive, $subtractionWithinFive)
$index += @(
    [ordered]@{
        lessonId = $additionWithinFive.id; unitId = $additionWithinFive.unitId; sourceLessonIds = @($additionSource.id)
        subjectId = "math"; domain = "MATH"; subcategoryId = "addition"
        title = "Optellen tot 5"; visualKey = "math.addition.within5"; palette = "POINCARE_TRICOLOUR"
        sourceMinutes = 0; runtimeMinutes = 65; exerciseItems = 30
    },
    [ordered]@{
        lessonId = $subtractionWithinFive.id; unitId = $subtractionWithinFive.unitId; sourceLessonIds = @($subtractionSource.id)
        subjectId = "math"; domain = "MATH"; subcategoryId = "subtraction"
        title = "Aftrekken tot 5"; visualKey = "math.subtraction.within5"; palette = "POINCARE_TRICOLOUR"
        sourceMinutes = 0; runtimeMinutes = 65; exerciseItems = 30
    }
)

foreach ($lesson in $retainedGrade2) {
    foreach ($stage in @($lesson.stages)) {
        foreach ($set in @($stage.exerciseSets)) {
            if ([string]$set.generatorVersion -ne 'v2') {
                Set-ObjectProperty $set 'generatorImplementationVersion' ([int]$set.generatorVersion)
                Set-ObjectProperty $set 'generatorVersion' 'v2'
            }
            Set-ObjectProperty $set 'origin' $(if ([string]$set.sourceMode -eq 'CURATED') { 'FIXED' } else { 'GENERATED' })
        }
    }
    Add-LessonEditorialMetadata $lesson "world_orientation" "world.nature_living_world"
}

$catalog = [ordered]@{
    schemaVersion = 2
    definitionVersion = 2
    lessons = @($generatedLessons) + @($retainedGrade2)
}
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputPath))
$resolvedIndex = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $IndexPath))
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($resolvedOutput)) | Out-Null
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($resolvedIndex)) | Out-Null
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($resolvedOutput, ($catalog | ConvertTo-Json -Depth 100) + [Environment]::NewLine, $utf8)
[System.IO.File]::WriteAllText($resolvedIndex, ([ordered]@{
    generatedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
    sourceCurriculum = "poincare-vlaanderen-primary-g1-g6-v1"
    sourceUnitCount = $units.Count; sourceLessonFicheCount = $sourceLessons.Count
    generatedGrade1Lessons = $generatedLessons.Count
    retainedGrade2Pilots = $retainedGrade2.Count
    totalRuntimeExerciseItems = ($index | ForEach-Object { [int]$_['exerciseItems'] } | Measure-Object -Sum).Sum
    sourceMinutes = ($index | ForEach-Object { [int]$_['sourceMinutes'] } | Measure-Object -Sum).Sum
    runtimeMinutes = ($index | ForEach-Object { [int]$_['runtimeMinutes'] } | Measure-Object -Sum).Sum
    lessons = $index
} | ConvertTo-Json -Depth 20) + [Environment]::NewLine, $utf8)

Write-Output "Generated $($generatedLessons.Count) Grade 1 lessons from $($sourceLessons.Count) source fiches."
Write-Output $resolvedOutput
Write-Output $resolvedIndex
