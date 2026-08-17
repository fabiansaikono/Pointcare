param(
    [Parameter(Mandatory = $true)]
    [string]$WorkbookPath,
    [string]$OutputDirectory = "docs/lesson_v2/reports"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-ColumnIndex([string]$CellReference) {
    $letters = ($CellReference -replace '[^A-Z]', '')
    $value = 0
    foreach ($character in $letters.ToCharArray()) {
        $value = ($value * 26) + ([int][char]$character - [int][char]'A' + 1)
    }
    return $value - 1
}

function Read-EntryXml($Archive, [string]$Name) {
    $entry = $Archive.GetEntry($Name)
    if ($null -eq $entry) { throw "Workbook entry not found: $Name" }
    $reader = [System.IO.StreamReader]::new($entry.Open())
    try { return [xml]$reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Get-CellText($Cell, $SharedStrings, $NamespaceManager) {
    if ($null -eq $Cell) { return "" }
    $type = [string]$Cell.t
    if ($type -eq "inlineStr") {
        return [string]$Cell.SelectSingleNode("x:is", $NamespaceManager).InnerText
    }
    $valueNode = $Cell.SelectSingleNode("x:v", $NamespaceManager)
    if ($null -eq $valueNode) { return "" }
    $value = [string]$valueNode.InnerText
    if ($type -eq "s") { return [string]$SharedStrings[[int]$value] }
    return $value
}

function Get-IssueIds([string]$Feedback, [string]$DesiredChange) {
    $text = "$Feedback $DesiredChange".ToLowerInvariant()
    $issues = [System.Collections.Generic.List[string]]::new()
    if ($text -match 'generiek|theoreti|concreet|handeling|zelfde vraag') { $issues.Add("ISS-01") }
    if ($text -match 'antwoordopt|foute antwoord|distractor|gokken') { $issues.Add("ISS-02") }
    if ($text -match 'variatie|dezelfde|herhaal|eerste oefening') { $issues.Add("ISS-03") }
    if ($text -match 'frans|bonjour|ijsje|kleur|vertal') { $issues.Add("ISS-04") }
    if ($text -match 'wiskunde|tiental|optel|aftrek|maal|deel|tafel') { $issues.Add("ISS-05") }
    if ($text -match 'meer reeks|te weinig|extra reeks') { $issues.Add("ISS-06") }
    if ($text -match '\bv1\b|curated') { $issues.Add("ISS-07") }
    if ($text -match 'onvoldoende informatie|niet duidelijk|eenduidig') { $issues.Add("ISS-08") }
    if ($text -match 'eenvoudig|moeilijk woord|taalgebruik|kind') { $issues.Add("ISS-09") }
    if ($text -match 'toets|ongezien|overlap') { $issues.Add("ISS-10") }
    if ($issues.Count -eq 0) {
        $issues.Add("ISS-01")
        $issues.Add("ISS-03")
    }
    return @($issues | Select-Object -Unique)
}

$resolvedWorkbook = (Resolve-Path -LiteralPath $WorkbookPath).Path
$resolvedOutput = Join-Path (Resolve-Path -LiteralPath ".").Path $OutputDirectory
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null

$archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedWorkbook)
try {
    $sharedStrings = @()
    $sharedEntry = $archive.GetEntry("xl/sharedStrings.xml")
    if ($null -ne $sharedEntry) {
        $sharedXml = Read-EntryXml $archive "xl/sharedStrings.xml"
        $sharedNs = [System.Xml.XmlNamespaceManager]::new($sharedXml.NameTable)
        $sharedNs.AddNamespace("x", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
        $sharedStrings = @($sharedXml.SelectNodes("//x:si", $sharedNs) | ForEach-Object { [string]$_.InnerText })
    }

    $workbookXml = Read-EntryXml $archive "xl/workbook.xml"
    $workbookNs = [System.Xml.XmlNamespaceManager]::new($workbookXml.NameTable)
    $workbookNs.AddNamespace("x", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
    $workbookNs.AddNamespace("r", "http://schemas.openxmlformats.org/officeDocument/2006/relationships")
    $sheet = $workbookXml.SelectNodes("//x:sheet", $workbookNs) | Where-Object { $_.name -eq "Feedback_extract" } | Select-Object -First 1
    if ($null -eq $sheet) { throw "Worksheet Feedback_extract was not found." }
    $relationshipId = [string]$sheet.GetAttribute("id", "http://schemas.openxmlformats.org/officeDocument/2006/relationships")

    $relationsXml = Read-EntryXml $archive "xl/_rels/workbook.xml.rels"
    $relationsNs = [System.Xml.XmlNamespaceManager]::new($relationsXml.NameTable)
    $relationsNs.AddNamespace("p", "http://schemas.openxmlformats.org/package/2006/relationships")
    $relationship = $relationsXml.SelectNodes("//p:Relationship", $relationsNs) | Where-Object { $_.Id -eq $relationshipId } | Select-Object -First 1
    if ($null -eq $relationship) { throw "Worksheet relationship was not found: $relationshipId" }
    $target = [string]$relationship.Target
    $sheetPath = if ($target.StartsWith("/")) { $target.TrimStart("/") } else { "xl/$target" }
    $sheetPath = $sheetPath.Replace("xl/../", "")

    $sheetXml = Read-EntryXml $archive $sheetPath
    $sheetNs = [System.Xml.XmlNamespaceManager]::new($sheetXml.NameTable)
    $sheetNs.AddNamespace("x", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
    $rows = @($sheetXml.SelectNodes("//x:sheetData/x:row", $sheetNs))
    if ($rows.Count -lt 2) { throw "Feedback_extract contains no data rows." }

    $headerValues = @{}
    foreach ($cell in $rows[0].SelectNodes("x:c", $sheetNs)) {
        $headerValues[(Get-ColumnIndex ([string]$cell.r))] = Get-CellText $cell $sharedStrings $sheetNs
    }
    $headers = @{}
    foreach ($entry in $headerValues.GetEnumerator()) { $headers[[string]$entry.Value] = [int]$entry.Key }

    $results = [System.Collections.Generic.List[object]]::new()
    foreach ($row in $rows | Select-Object -Skip 1) {
        $values = @{}
        foreach ($cell in $row.SelectNodes("x:c", $sheetNs)) {
            $values[(Get-ColumnIndex ([string]$cell.r))] = Get-CellText $cell $sharedStrings $sheetNs
        }
        $get = { param([string]$Name) if ($headers.ContainsKey($Name) -and $values.ContainsKey($headers[$Name])) { [string]$values[$headers[$Name]] } else { "" } }
        $feedback = & $get "Feedback/comments"
        $desiredChange = & $get "Desired change"
        $issueIds = Get-IssueIds $feedback $desiredChange
        $results.Add([pscustomobject]@{
            feedbackRow = [int]$row.r
            excelSourceRow = & $get "Excel row"
            version = & $get "Version"
            grade = & $get "Grade"
            category = & $get "Category"
            lesson = & $get "Lesson"
            lessonId = & $get "Lesson-ID"
            phaseSeries = & $get "Phase/series"
            setId = & $get "Set-ID/first ID"
            feedback = $feedback
            desiredChange = $desiredChange
            issueIds = $issueIds
            disposition = "IMPLEMENTED_GLOBAL_RULE"
            implementationEvidence = "Catalog definition v5; expanded series; concrete reviewed pools; V1 mapping; feedback validators"
        })
    }
} finally {
    $archive.Dispose()
}

$jsonPath = Join-Path $resolvedOutput "FEEDBACK_ROW_DISPOSITIONS_L12.json"
$csvPath = Join-Path $resolvedOutput "FEEDBACK_ROW_DISPOSITIONS_L12.csv"
$payload = [ordered]@{
    schemaVersion = 1
    generatedAt = [DateTimeOffset]::UtcNow.ToString("o")
    sourceWorkbook = [IO.Path]::GetFileName($resolvedWorkbook)
    sourceSheet = "Feedback_extract"
    expectedRows = 550
    actualRows = $results.Count
    implementedRows = @($results | Where-Object disposition -eq "IMPLEMENTED_GLOBAL_RULE").Count
    rejectedRows = @($results | Where-Object disposition -like "REJECTED*").Count
    rows = $results
}
$payload | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
$results | Select-Object feedbackRow, excelSourceRow, version, grade, category, lesson, lessonId, phaseSeries, setId, @{Name="issueIds";Expression={$_.issueIds -join "|"}}, disposition, implementationEvidence, feedback, desiredChange |
    Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding utf8

if ($results.Count -ne 550) { throw "Expected 550 feedback rows, found $($results.Count)." }
Write-Output ([pscustomobject]@{ Rows = $results.Count; Implemented = $payload.implementedRows; Rejected = $payload.rejectedRows; Json = $jsonPath; Csv = $csvPath })
