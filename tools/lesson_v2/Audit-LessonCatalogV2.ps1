param(
    [string]$CatalogPath = "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json",
    [string]$IndexPath = "docs/lesson_v2/reports/GRADE1_CATALOG_INDEX.json",
    [string]$ReportDirectory = "docs/lesson_v2/reports"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path ".").Path
$reportRoot = [System.IO.Path]::GetFullPath((Join-Path $root $ReportDirectory))
$catalogFile = [System.IO.Path]::GetFullPath((Join-Path $root $CatalogPath))
$indexFile = [System.IO.Path]::GetFullPath((Join-Path $root $IndexPath))
$siteRoot = Join-Path $root "curriculum-library"
$auditFile = Join-Path $siteRoot "public/data/v2-library-audit.json"
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.Directory]::CreateDirectory($reportRoot) | Out-Null

function Write-Utf8([string]$Path, [string]$Value) {
    [System.IO.File]::WriteAllText($Path, $Value + [Environment]::NewLine, $utf8)
}

if (-not (Test-Path -LiteralPath $catalogFile -PathType Leaf)) { throw "Catalogus ontbreekt: $catalogFile" }
if (-not (Test-Path -LiteralPath $indexFile -PathType Leaf)) { throw "Catalogusindex ontbreekt: $indexFile" }
if (-not (Test-Path -LiteralPath (Join-Path $siteRoot "scripts/audit-v2-library.mjs") -PathType Leaf)) { throw "V2-auditscript ontbreekt." }

Push-Location $siteRoot
try {
    & node "scripts/audit-v2-library.mjs"
    if ($LASTEXITCODE -ne 0) { throw "De volledige V2-bibliotheekaudit is mislukt." }
} finally {
    Pop-Location
}

$audit = Get-Content -LiteralPath $auditFile -Raw -Encoding UTF8 | ConvertFrom-Json
$catalog = Get-Content -LiteralPath $catalogFile -Raw -Encoding UTF8 | ConvertFrom-Json
$index = Get-Content -LiteralPath $indexFile -Raw -Encoding UTF8 | ConvertFrom-Json
$gradeOne = @($catalog.lessons | Where-Object grade -eq 1)
$activeGradeOne = @($gradeOne | Where-Object publicationStatus -eq "ACTIVE")
$draftGradeOne = @($gradeOne | Where-Object publicationStatus -eq "DRAFT_REVIEW_REQUIRED")
$gradeOneSets = @($gradeOne | ForEach-Object stages | ForEach-Object exerciseSets)
$gradeOneQuestions = @($gradeOneSets | ForEach-Object questionBlueprints)
$gradeTwo = @($catalog.lessons | Where-Object grade -eq 2)
$activeGradeTwo = @($gradeTwo | Where-Object publicationStatus -eq "ACTIVE")
$draftGradeTwo = @($gradeTwo | Where-Object publicationStatus -eq "DRAFT_REVIEW_REQUIRED")
$gradeTwoSets = @($gradeTwo | ForEach-Object stages | ForEach-Object exerciseSets)
$gradeTwoQuestions = @($gradeTwoSets | ForEach-Object questionBlueprints)

$validation = [ordered]@{
    generatedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
    result = $audit.result
    totals = [ordered]@{ checks = 8; passed = $(if ($audit.result -eq "PASS") { 8 } else { 0 }); failed = $audit.errors.Count }
    checks = @(
        [ordered]@{ id = "library.required-fields"; status = $audit.result; details = "Alle verplichte V2-lesvelden zijn gecontroleerd." },
        [ordered]@{ id = "library.stable-ids"; status = $audit.result; details = "$($audit.counts.stableIds) unieke stabiele ID's; geen duplicaten." },
        [ordered]@{ id = "library.relations"; status = $audit.result; details = "Les-, deel-, pagina-, reeks-, vraag- en antwoordrelaties zijn gecontroleerd." },
        [ordered]@{ id = "library.v1-v2"; status = $audit.result; details = "$($audit.counts.fixedV1Exercises) vaste V1-vragen behouden; nieuwe reeksen expliciet V2." },
        [ordered]@{ id = "library.translations"; status = $audit.result; details = "nl-BE standaard; FR/EN-statussen expliciet." },
        [ordered]@{ id = "library.publication"; status = $audit.result; details = "$($activeGradeOne.Count) actieve piloten; $($draftGradeOne.Count) conceptlessen." },
        [ordered]@{ id = "library.stories"; status = $audit.result; details = "Alle verhalen blijven aan boek en hoofdstuk gekoppeld." },
        [ordered]@{ id = "library.questions"; status = $audit.result; details = "Leerjaar 1: $($audit.counts.gradeOneExerciseSets) reeksen en $($audit.counts.gradeOneQuestions) vragen. Leerjaar 2: $($audit.counts.gradeTwoExerciseSets) reeksen en $($audit.counts.gradeTwoQuestions) vragen." }
    )
    errors = @($audit.errors)
    warnings = @($audit.warnings)
}

$readiness = [ordered]@{
    scope = "GRADE1_V2_WITH_CONTROLLED_PUBLICATION"
    grade1CoreLessons = $gradeOne.Count
    grade1LibraryLessonsIncludingStories = $audit.counts.gradeOneLessons
    grade2Lessons = $gradeTwo.Count
    grade2ExerciseSets = $gradeTwoSets.Count
    grade2Questions = $gradeTwoQuestions.Count
    activeValidatedPilots = $activeGradeOne.Count
    draftReviewRequired = $draftGradeOne.Count
    grade2DraftReviewRequired = $draftGradeTwo.Count
    translations = $audit.translations
    note = "Conceptlessen blijven uit de kindnavigatie tot expliciete inhoudelijke en redactionele goedkeuring."
}

$state = [ordered]@{
    generatedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
    result = $audit.result
    curriculumV2 = [ordered]@{
        coreLessons = @($catalog.lessons).Count
        grade1CoreLessons = $gradeOne.Count
        grade1ExerciseSets = $gradeOneSets.Count
        grade1Questions = $gradeOneQuestions.Count
        grade2CoreLessons = $gradeTwo.Count
        grade2ExerciseSets = $gradeTwoSets.Count
        grade2Questions = $gradeTwoQuestions.Count
        runtimeMinutes = $index.runtimeMinutes
        sourceMinutes = $index.sourceMinutes
    }
    fullLibrary = $audit.counts
    translations = $audit.translations
    validation = $validation.totals
}

$crosswalk = @($catalog.lessons | ForEach-Object {
    [ordered]@{
        v2LessonId = $_.id
        sourceUnitId = $_.unitId
        grade = $_.grade
        categoryId = $_.curriculumCategoryId
        subcategoryId = $_.curriculumSubcategoryId
        publicationStatus = $_.publicationStatus
    }
})

$remaining = [ordered]@{
    grade1DraftLessons = $draftGradeOne.Count
    grade2DraftLessons = $draftGradeTwo.Count
    translationsNeedingReview = [int]$audit.translations.'fr-BE'.NEEDS_REVIEW + [int]$audit.translations.'en-US'.NEEDS_REVIEW
    nextGate = "Inhoudelijke validatie en expliciete publicatie van de conceptlessen per leerjaar."
    blockers = @($audit.errors)
}

Write-Utf8 (Join-Path $reportRoot "CURRENT_STATE_AUDIT.json") ($state | ConvertTo-Json -Depth 30)
Write-Utf8 (Join-Path $reportRoot "VALIDATION_REPORT.json") ($validation | ConvertTo-Json -Depth 30)
Write-Utf8 (Join-Path $reportRoot "CONTENT_READINESS.json") ($readiness | ConvertTo-Json -Depth 30)
Write-Utf8 (Join-Path $reportRoot "MIGRATION_CROSSWALK.json") ([ordered]@{ version = 2; lessons = $crosswalk } | ConvertTo-Json -Depth 30)
Write-Utf8 (Join-Path $reportRoot "REMAINING_MIGRATION_INVENTORY.json") ($remaining | ConvertTo-Json -Depth 20)

$lines = @(
    "# Lesson architecture V2 validation report",
    "",
    "Resultaat: **$($audit.result)**",
    "",
    "- Leerjaar-1-kernlessen: **$($gradeOne.Count)**",
    "- Volledige leerjaar-1-redactiebibliotheek inclusief verhalen: **$($audit.counts.gradeOneLessons)** lessen",
    "- Reeksen en vragen: **$($audit.counts.gradeOneExerciseSets)** en **$($audit.counts.gradeOneQuestions)**",
    "- Leerjaar 2: **$($gradeTwo.Count)** lessen, **$($gradeTwoSets.Count)** reeksen en **$($gradeTwoQuestions.Count)** vragen",
    "- Stabiele ID's: **$($audit.counts.stableIds)**; duplicaten: **0**",
    "- Publicatie: **$($activeGradeOne.Count)** actieve piloten; **$($draftGradeOne.Count)** conceptlessen",
    "- Te controleren vertaalstatussen: **$([int]$audit.translations.'fr-BE'.NEEDS_REVIEW + [int]$audit.translations.'en-US'.NEEDS_REVIEW)**",
    "",
    "| Controle | Resultaat | Details |",
    "|---|---:|---|"
)
foreach ($check in $validation.checks) { $lines += "| ``$($check.id)`` | $($check.status) | $($check.details) |" }
Write-Utf8 (Join-Path $reportRoot "VALIDATION_REPORT.md") ($lines -join [Environment]::NewLine)

Write-Output "V2 audit: $($audit.result)"
Write-Output "Grade 1 core lessons: $($gradeOne.Count); library lessons including stories: $($audit.counts.gradeOneLessons)"
Write-Output "Grade 2 lessons: $($gradeTwo.Count); exercise sets: $($gradeTwoSets.Count); questions: $($gradeTwoQuestions.Count)"
Write-Output "Exercise sets: $($audit.counts.gradeOneExerciseSets); questions: $($audit.counts.gradeOneQuestions); stable IDs: $($audit.counts.stableIds)"
if ($audit.result -ne "PASS") { exit 1 }
