param(
    [string]$ReferenceRoot = ".tmp/PoincareWorkPackageV2_20260730/Poincare_Codex_Work_Package_Lessonarchitecture_v2/curriculum_reference/Poincare_curriculum_vlaanderen",
    [string]$CatalogPath = "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json",
    [string]$OutputPath = "app/src/main/assets/poincare/curriculum/grade1-coverage-crosswalk-v1.json"
)

$ErrorActionPreference = "Stop"

function LessonIdFor($Unit) {
    $id = [string]$Unit.unit_id
    if ($id -eq "unit.g1.math.operations.optellen-tot-10-en-20") {
        return "lesson.g1.math.addition.within10.meaning-and-model"
    }
    if ($id -eq "unit.g1.nl.technical_reading.luisteren-naar-klanken-in-woorden") {
        return "lesson.g1.reading.initial-sound.identify-and-compare"
    }
    if ($id -eq "unit.g1.math.measurement.lengte-massa-en-inhoud-vergelijken") {
        return "lesson.g1.geometry.length.compare-and-order"
    }
    return $id -replace '^unit\.', 'lesson.'
}

function DeliveryFor($Unit, [string[]]$GeneratorIds) {
    if ($Unit.domain_id -eq "oral") {
        return @(
            "ADULT_MEDIATED",
            "HYBRID_LISTENING_SPEAKING_ADULT_VERIFIED",
            "adult verifies audible voice, relevant utterance, turn-taking and response to a partner"
        )
    }
    if ($Unit.domain_id -in @("handwriting", "composition")) {
        return @(
            "ADULT_MEDIATED",
            "HYBRID_DIGITAL_PRODUCTIVE_WRITING_ADULT_VERIFIED",
            "adult verifies letter formation, spacing, readability and independent written production"
        )
    }
    if ($Unit.subject_id -in @("arts", "pe")) {
        return @(
            "ADULT_MEDIATED",
            "ADULT_PRACTICE_WITH_DIGITAL_KNOWLEDGE_COMPONENT",
            "adult verifies the creative or physical performance using the lesson criteria"
        )
    }
    if ($Unit.subject_id -eq "attitudes") {
        $rubric = if ($Unit.domain_id -eq "citizenship_safety") {
            "adult verifies stopping at the edge, looking both ways, listening, safe crossing and visibility"
        } else {
            "adult verifies the social or self-regulation behaviour in a real interaction"
        }
        return @("ADULT_MEDIATED", "HYBRID_KNOWLEDGE_AND_REAL_WORLD_BEHAVIOUR", $rubric)
    }
    if ($Unit.subject_id -eq "science_tech" -or "generator.g1.world.environment_science" -in $GeneratorIds) {
        return @(
            "ADULT_MEDIATED",
            "HYBRID_DIGITAL_CONCEPT_AND_ADULT_OBSERVATION",
            "adult verifies the observation, experiment or safe real-world application"
        )
    }
    if ($Unit.domain_id -eq "create_communicate") {
        return @(
            "ADULT_MEDIATED",
            "HYBRID_DIGITAL_KNOWLEDGE_AND_DEVICE_TASK",
            "adult verifies the guided creation and sharing task on the device"
        )
    }
    return @(
        "IMPLEMENTED",
        "DIGITAL_THEORY_PRACTICE_TRANSFER_AND_MASTERY_TEST",
        ""
    )
}

$unitsPath = Join-Path $ReferenceRoot "data/units.json"
if (-not (Test-Path -LiteralPath $unitsPath -PathType Leaf)) {
    throw "Missing Grade 1 reference units: $unitsPath"
}
if (-not (Test-Path -LiteralPath $CatalogPath -PathType Leaf)) {
    throw "Missing v2 lesson catalog: $CatalogPath"
}

$units = @(
    (Get-Content -Raw -Encoding UTF8 -LiteralPath $unitsPath | ConvertFrom-Json) |
        Where-Object { $_.grade -eq 1 } |
        Sort-Object subject_id, sequence_in_grade_subject, unit_id
)
$catalog = Get-Content -Raw -Encoding UTF8 -LiteralPath $CatalogPath | ConvertFrom-Json
$gradeOneLessons = @($catalog.lessons | Where-Object { $_.grade -eq 1 })
$lessonsById = @{}
foreach ($lesson in $gradeOneLessons) {
    $lessonsById[[string]$lesson.id] = $lesson
}

$crosswalk = foreach ($unit in $units) {
    $lessonId = LessonIdFor $unit
    $lesson = $lessonsById[$lessonId]
    if ($null -eq $lesson) {
        [ordered]@{
            referenceUnitId = [string]$unit.unit_id
            subjectId = [string]$unit.subject_id
            domainId = [string]$unit.domain_id
            title = [string]$unit.title
            sequence = [int]$unit.sequence_in_grade_subject
            sourceLessonIds = @($unit.lesson_ids)
            sourceCrosswalkTags = @($unit.source_crosswalk_tags)
            coverageStatus = "MISSING"
            deliveryMode = "NONE"
            adultEvidenceRequirement = ""
            lessonId = $null
            validationStatus = "NOT_IMPLEMENTED"
            evidence = $null
        }
        continue
    }

    $sets = @($lesson.stages | ForEach-Object { @($_.exerciseSets) } | Where-Object { $null -ne $_ })
    $generatorIds = @($sets | ForEach-Object { [string]$_.generatorId } | Sort-Object -Unique)
    $delivery = DeliveryFor $unit $generatorIds
    $theory = @($lesson.stages | Where-Object { $_.type -eq "THEORY" })[0]
    $exercises = @($lesson.stages | Where-Object { $_.type -eq "EXERCISES" })
    $test = @($lesson.stages | Where-Object { $_.type -eq "MASTERY_TEST" })[0]

    [ordered]@{
        referenceUnitId = [string]$unit.unit_id
        subjectId = [string]$unit.subject_id
        domainId = [string]$unit.domain_id
        title = [string]$unit.title
        sequence = [int]$unit.sequence_in_grade_subject
        sourceLessonIds = @($unit.lesson_ids)
        sourceCrosswalkTags = @($unit.source_crosswalk_tags)
        coverageStatus = [string]$delivery[0]
        deliveryMode = [string]$delivery[1]
        adultEvidenceRequirement = [string]$delivery[2]
        lessonId = [string]$lesson.id
        validationStatus = if ($lesson.publicationStatus -eq "ACTIVE") {
            "PILOT_ACTIVE"
        } else {
            "VALIDATION_REQUIRED"
        }
        evidence = [ordered]@{
            theoryStageId = [string]$theory.id
            exerciseStageIds = @($exercises | ForEach-Object { [string]$_.id })
            masteryTestStageId = [string]$test.id
            exerciseSetIds = @($sets | ForEach-Object { [string]$_.id })
            generatorIds = $generatorIds
            masteryTags = @($lesson.masteryTags)
            visualAssetIds = @($lesson.visuals | ForEach-Object { [string]$_.id })
            requiredLocales = @("nl-BE", "fr-BE", "en-US")
        }
    }
}

$allowedStatuses = @("IMPLEMENTED", "PARTIAL", "MISSING", "EXCLUDED", "ADULT_MEDIATED")
$invalidStatuses = @($crosswalk | Where-Object { $_.coverageStatus -notin $allowedStatuses })
if ($units.Count -ne 65) {
    throw "Expected 65 Grade 1 reference units, received $($units.Count)."
}
if ($crosswalk.Count -ne 65) {
    throw "Expected 65 Grade 1 crosswalk rows, received $($crosswalk.Count)."
}
if ($invalidStatuses.Count -gt 0) {
    throw "Crosswalk contains invalid coverage statuses."
}

$summary = [ordered]@{
    referenceUnitCount = $units.Count
    mappedLessonCount = @($crosswalk | Where-Object { $null -ne $_.lessonId }).Count
    implementedCount = @($crosswalk | Where-Object { $_.coverageStatus -eq "IMPLEMENTED" }).Count
    adultMediatedCount = @($crosswalk | Where-Object { $_.coverageStatus -eq "ADULT_MEDIATED" }).Count
    partialCount = @($crosswalk | Where-Object { $_.coverageStatus -eq "PARTIAL" }).Count
    missingCount = @($crosswalk | Where-Object { $_.coverageStatus -eq "MISSING" }).Count
    excludedCount = @($crosswalk | Where-Object { $_.coverageStatus -eq "EXCLUDED" }).Count
}

$mappedLessonIds = @($crosswalk | ForEach-Object { $_.lessonId } | Where-Object { $null -ne $_ })
$extraCatalogLessons = @(
    $gradeOneLessons |
        Where-Object { $_.id -notin $mappedLessonIds } |
        ForEach-Object {
            [ordered]@{
                lessonId = [string]$_.id
                unitId = [string]$_.unitId
                reason = "Product extension added after the 65-unit reference package."
            }
        }
)

$document = [ordered]@{
    schemaVersion = 1
    id = "poincare.grade1.coverage-crosswalk.v1"
    grade = 1
    referenceSource = "Poincare_curriculum_vlaanderen/data/units.json"
    allowedCoverageStatuses = $allowedStatuses
    summary = $summary
    units = @($crosswalk)
    extraCatalogLessons = $extraCatalogLessons
}

$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputPath))
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($resolvedOutput)) | Out-Null
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText(
    $resolvedOutput,
    ($document | ConvertTo-Json -Depth 30) + [Environment]::NewLine,
    $utf8
)

Write-Output "Generated Grade 1 coverage crosswalk: $($summary.referenceUnitCount) reference units, $($summary.missingCount) missing."
Write-Output $resolvedOutput
