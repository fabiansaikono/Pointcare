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
    "app/src/main/java/be/saikono/poincare/data/story/StoryModels.kt" = @("data class Story(")
    "app/src/main/java/be/saikono/poincare/ui/story/StoryScreens.kt" = @("fun StoryLibraryScreen(")
    "app/src/test/java/be/saikono/poincare/data/story/StoryContentValidationTest.kt" = @("class StoryContentValidationTest")
    "curriculum-library/app/CurriculumStudio.tsx" = @("function visibleText(value: any")
    "curriculum-library/scripts/build-curriculum-data.mjs" = @("if (storyCatalog.stories.length > 0)")
}

$managedFiles = @(
    "app/src/main/assets/poincare/content/stories/poincare-stories-v1.json",
    "curriculum-library/public/data/lesson-editor-defaults-v1.json",
    "curriculum-library/public/data/lesson-health.json",
    "curriculum-library/public/data/library-meta.json",
    "curriculum-library/public/data/stories/poincare-stories-v1.json",
    "curriculum-library/public/data/stories/story-lessons-v1.json",
    "tools/story_v2/rewrite_stories_avi.mjs",
    "tools/story_v2/validate_stories_avi.mjs",
    "tools/story_v2/Build-UpdatePackage.ps1"
)

$generatedStoryFiles = @(Get-ChildItem -LiteralPath (Join-Path $root "story_import/generated/stories/g1") -File -Filter "*.json" | ForEach-Object {
    $_.FullName.Substring($root.Length + 1).Replace('\', '/')
})
$allFiles = @(@($anchored.Keys) + $managedFiles + $generatedStoryFiles | Sort-Object -Unique)

foreach ($relative in $allFiles) {
    $source = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Missing updater source: $relative"
    }
}

if (Test-Path -LiteralPath $payloadRoot) {
    $resolvedPayload = (Resolve-Path -LiteralPath $payloadRoot).Path
    if ($resolvedPayload -ne (Join-Path $root "poincare_update")) {
        throw "Refusing to remove unexpected payload directory: $resolvedPayload"
    }
    Get-ChildItem -LiteralPath $payloadRoot -Recurse -Force | ForEach-Object {
        if (-not $_.PSIsContainer -and $_.IsReadOnly) { $_.IsReadOnly = $false }
    }
    Remove-Item -LiteralPath $payloadRoot -Recurse -Force
}
[System.IO.Directory]::CreateDirectory($payloadRoot) | Out-Null

$manifestFiles = @()
foreach ($relative in $allFiles) {
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
    updateId = "poincare.stories.avi.20260808"
    files = $manifestFiles
    removedFiles = @()
    validationTasks = @(
        "node tools/story_v2/validate_stories_avi.mjs",
        "curriculum-library: npm run data:sync",
        "curriculum-library: npm run typecheck",
        ":app:testDebugUnitTest",
        ":app:assembleDebug"
    )
}
[System.IO.File]::WriteAllText(
    (Join-Path $payloadRoot "MANIFEST.json"),
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
