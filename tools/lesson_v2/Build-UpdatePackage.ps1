param(
    [string]$DestinationZip = "poincare_update.zip"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path ".").Path
$payloadRoot = [System.IO.Path]::GetFullPath((Join-Path $root "poincare_update"))
$zipPath = [System.IO.Path]::GetFullPath((Join-Path $root $DestinationZip))
if ([System.IO.Path]::GetDirectoryName($payloadRoot) -ne $root) {
    throw "Refusing to replace a payload outside the project root: $payloadRoot"
}
if ([System.IO.Path]::GetDirectoryName($zipPath) -ne $root) {
    throw "Refusing to replace a ZIP outside the project root: $zipPath"
}

$anchored = [ordered]@{
    "app/build.gradle.kts" = @('namespace = "be.saikono.poincare"')
    "app/src/main/res/values/strings.xml" = @('<resources>')
    "app/src/main/java/be/saikono/poincare/data/local/Converters.kt" = @("class Converters")
    "app/src/main/java/be/saikono/poincare/data/local/DatabaseMigrations.kt" = @("object DatabaseMigrations")
    "app/src/main/java/be/saikono/poincare/data/local/PoincareDatabase.kt" = @("abstract class PoincareDatabase")
    "app/src/main/java/be/saikono/poincare/data/local/LessonProgressDao.kt" = @("interface LessonProgressDao")
    "app/src/main/java/be/saikono/poincare/data/local/LessonProgressEntities.kt" = @("data class LessonProgressEntity")
    "app/src/main/java/be/saikono/poincare/model/lesson/LessonProgressModels.kt" = @("data class LessonProgress(")
    "app/src/main/java/be/saikono/poincare/repository/LessonProgressRepository.kt" = @("class LessonProgressRepository")
    "app/src/main/java/be/saikono/poincare/data/sync/SyncDto.kt" = @("data class LessonProgressSyncDto(")
    "app/src/main/java/be/saikono/poincare/domain/curriculum/generation/Grade1AdditionSubtractionGenerator.kt" = @("class Grade1AdditionSubtractionGenerator")
    "app/src/main/java/be/saikono/poincare/domain/curriculum/generation/Grade1PhonologyLetterSoundGenerator.kt" = @("class Grade1PhonologyLetterSoundGenerator")
    "app/src/main/java/be/saikono/poincare/domain/curriculum/generation/Grade2IntegratedWorldFoundationsGenerator.kt" = @("class Grade2IntegratedWorldFoundationsGenerator")
    "app/src/main/java/be/saikono/poincare/ui/lesson/LessonRuntime.kt" = @("data class LessonStep(")
    "app/src/main/java/be/saikono/poincare/ui/lesson/LessonScreens.kt" = @("fun LessonHubScreen(")
    "app/src/main/java/be/saikono/poincare/ui/lesson/LessonViewModel.kt" = @("class LessonViewModel(")
}

$managedFiles = @(
    "app/schemas/README.md",
    "app/schemas/be.saikono.poincare.data.local.PoincareDatabase/10.json",
    "app/schemas/be.saikono.poincare.data.local.PoincareDatabase/11.json",
    "app/src/main/assets/poincare/curriculum/curriculum-manifest-v1.json",
    "app/src/main/assets/poincare/curriculum/grade1-coverage-crosswalk-v1.json",
    "app/src/main/assets/poincare/curriculum/lesson-catalog-v2.json",
    "app/src/main/resources/poincare/curriculum/grade1-unit-knowledge-profiles.json",
    "app/src/main/assets/poincare/visuals/world/seasons-cycle-v1.svg",
    "app/src/main/assets/poincare/visuals/world/seasons-cycle-v1.png",
    "app/src/main/java/be/saikono/poincare/data/local/LessonEvidenceEntitiesV2.kt",
    "app/src/main/java/be/saikono/poincare/model/lesson/LessonEvidenceModelsV2.kt",
    "app/src/main/java/be/saikono/poincare/domain/curriculum/v2/LessonCatalogV2Loader.kt",
    "app/src/main/java/be/saikono/poincare/domain/curriculum/v2/LessonCatalogV2Validator.kt",
    "app/src/main/java/be/saikono/poincare/domain/curriculum/v2/LessonExerciseResolverV2.kt",
    "app/src/main/java/be/saikono/poincare/domain/curriculum/v2/LessonStatusServiceV2.kt",
    "app/src/main/java/be/saikono/poincare/domain/curriculum/v2/LessonV2Models.kt",
    "app/src/main/java/be/saikono/poincare/ui/lesson/LessonRuntimeV2.kt",
    "app/src/test/java/be/saikono/poincare/data/local/DatabaseMigrationRegistryTest.kt",
    "app/src/test/java/be/saikono/poincare/data/local/RoomSchemaExportTest.kt",
    "app/src/test/java/be/saikono/poincare/data/sync/LessonSyncDtoTest.kt",
    "app/src/test/java/be/saikono/poincare/domain/curriculum/v2/LessonCatalogV2Test.kt",
    "app/src/test/java/be/saikono/poincare/domain/curriculum/v2/LessonStatusServiceV2Test.kt",
    "app/src/androidTest/java/be/saikono/poincare/data/local/PoincareDatabaseMigrationTest.kt",
    "tools/lesson_v2/Build-LessonCatalogV2.ps1",
    "tools/lesson_v2/Audit-LessonCatalogV2.ps1",
    "tools/lesson_v2/Build-UpdatePackage.ps1",
    "tools/lesson_v2/build_seasons_png.py",
    "outputs/g1-content-audit-20260731/AUDIT_RESULTAAT.md",
    "outputs/g1-content-audit-20260731/Poincare_Alle_Oefeningen_Leerjaar1.csv",
    "outputs/g1-content-audit-20260731/grade1-v2-validation-exercises.json",
    "deliverables/Poincare_Lessenhierarchie_zonder_verhalen.docx",
    "deliverables/lesson-hierarchy-source/grades-1-2-v2-validation-exercises.json",
    "curriculum-library/app/CurriculumStudio.tsx",
    "curriculum-library/lib/editor-contract.ts",
    "curriculum-library/package.json",
    "curriculum-library/public/data/lesson-catalog-v2.json",
    "curriculum-library/public/data/lesson-editor-defaults-v1.json",
    "curriculum-library/public/data/lesson-health.json",
    "curriculum-library/public/data/library-meta.json",
    "curriculum-library/public/data/stories/story-lessons-v1.json",
    "curriculum-library/public/data/v2-library-audit.json",
    "curriculum-library/scripts/audit-v2-library.mjs",
    "curriculum-library/scripts/build-curriculum-data.mjs",
    "curriculum-library/tests/editor-contract.test.mjs",
    "curriculum-library/tests/rendered-html.test.mjs"
)

$docFiles = @(Get-ChildItem -LiteralPath (Join-Path $root "docs/lesson_v2") -Recurse -File | ForEach-Object {
    $_.FullName.Substring($root.Length + 1).Replace('\', '/')
})

$dynamicDirectories = @(
    "app/src/main/assets/poincare/visuals/grade1",
    "app/src/main/java/be/saikono/poincare/domain/curriculum/generation",
    "app/src/main/java/be/saikono/poincare/domain/curriculum/v2",
    "app/src/main/java/be/saikono/poincare/model/lesson",
    "app/src/main/java/be/saikono/poincare/ui/lesson",
    "app/src/test/java/be/saikono/poincare/domain/curriculum/generation",
    "app/src/test/java/be/saikono/poincare/domain/curriculum/v2",
    "app/src/test/java/be/saikono/poincare/ui/lesson",
    "tools/lesson_v2"
)
$dynamicFiles = @($dynamicDirectories | ForEach-Object {
    $directory = Join-Path $root $_
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        throw "Missing updater source directory: $_"
    }
    Get-ChildItem -LiteralPath $directory -Recurse -File | ForEach-Object {
        $_.FullName.Substring($root.Length + 1).Replace('\', '/')
    }
})

$allFiles = @(@($anchored.Keys) + $managedFiles + $docFiles + $dynamicFiles | Sort-Object -Unique)
$duplicates = @($allFiles | Group-Object | Where-Object { $_.Count -gt 1 })
if ($duplicates.Count -gt 0) {
    throw "Duplicate updater targets: $($duplicates.Name -join ', ')"
}

foreach ($relative in $allFiles) {
    $source = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Missing updater source: $relative"
    }
}

if (Test-Path -LiteralPath $payloadRoot) {
    Get-ChildItem -LiteralPath $payloadRoot -Recurse -Force | ForEach-Object {
        if (-not $_.PSIsContainer -and $_.IsReadOnly) { $_.IsReadOnly = $false }
    }
    Remove-Item -LiteralPath $payloadRoot -Recurse -Force
}
[System.IO.Directory]::CreateDirectory($payloadRoot) | Out-Null

$manifestFiles = @()
foreach ($relative in $allFiles | Sort-Object) {
    $source = Join-Path $root $relative
    $destination = Join-Path $payloadRoot $relative
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($destination)) | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination
    $info = Get-Item -LiteralPath $source
    $anchors = if ($anchored.Contains($relative)) { @($anchored[$relative]) } else { @() }
    $manifestFiles += [ordered]@{
        path = $relative.Replace('\', '/')
        sha256 = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
        bytes = $info.Length
        anchors = $anchors
        managedArtifact = ($anchors.Count -eq 0)
    }
}

$manifest = [ordered]@{
    formatVersion = 1
    updateId = "poincare.feedback-extrapolation.l1-l2.20260810"
    generatedFromBranch = (git branch --show-current).Trim()
    generatedFromCommit = (git rev-parse HEAD).Trim()
    files = $manifestFiles
    removedFiles = @()
    validationTasks = @(
        ":app:testDebugUnitTest",
        ":app:compileDebugAndroidTestKotlin",
        ":app:assembleDebug --no-daemon --no-configuration-cache",
        ":app:lintDebug",
        "curriculum-library: npm run audit:v2",
        "curriculum-library: npm run typecheck",
        "curriculum-library: npm run lint",
        "curriculum-library: npm test"
    )
}
$manifestPath = Join-Path $payloadRoot "MANIFEST.json"
[System.IO.File]::WriteAllText(
    $manifestPath,
    ($manifest | ConvertTo-Json -Depth 20) + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -LiteralPath $payloadRoot -Recurse -File | Sort-Object FullName) {
        $relative = $file.FullName.Substring($payloadRoot.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive,
            $file.FullName,
            "poincare_update/$relative",
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
    foreach ($rootFile in @("applychanges.py", "UPDATE_NOTES.md")) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive,
            (Join-Path $root $rootFile),
            $rootFile,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
} finally {
    $archive.Dispose()
}

$zip = Get-Item -LiteralPath $zipPath
Write-Output "Created $($zip.FullName)"
Write-Output "Payload files: $($manifestFiles.Count)"
Write-Output "ZIP bytes: $($zip.Length)"
Write-Output "ZIP SHA-256: $((Get-FileHash -LiteralPath $zip.FullName -Algorithm SHA256).Hash.ToLowerInvariant())"
