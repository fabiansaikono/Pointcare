param(
    [string]$SourcePath = "tools/lesson_v2/source/grade2-canonical-source-v1.json",
    [string]$CatalogPath = "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json",
    [string]$CrosswalkPath = "docs/lesson_v2/reports/GRADE2_ATOMIC_CROSSWALK.json",
    [string]$IndexPath = "docs/lesson_v2/reports/GRADE2_CATALOG_INDEX.json",
    [string]$QuestionBankPath = "docs/lesson_v2/reports/GRADE2_EDITORIAL_QUESTION_BANK_V2.json"
)

$ErrorActionPreference = "Stop"
& node "tools/lesson_v2/Build-Grade2AtomicCatalogV2.mjs" `
    $SourcePath `
    $CatalogPath `
    $CrosswalkPath `
    $IndexPath `
    $QuestionBankPath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
