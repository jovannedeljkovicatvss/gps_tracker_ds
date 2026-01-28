# export.ps1 - Android Studio Project Export
$outputFile = "projekat_export.txt"
$projectRoot = Get-Location

# Lista foldera i fajlova koje treba izbeći
$excludePaths = @(
    "build", 
    ".gradle", 
    ".idea", 
    "generated",
    "*.iml",
    "local.properties"
)

Write-Host "Pokrećem export Android Studio projekta..." -ForegroundColor Green

if (Test-Path $outputFile) { 
    Remove-Item $outputFile 
    Write-Host "Obrisan postojeći $outputFile" -ForegroundColor Yellow
}

# Pronađi sve relevantne fajlove
$files = Get-ChildItem -Recurse -File | Where-Object {
    $file = $_
    $relativePath = $file.FullName.Replace("$projectRoot\", "")
    
    # Proveri da li je fajl u isključenom folderu
    $shouldInclude = $true
    foreach ($exclude in $excludePaths) {
        if ($relativePath -like "*$exclude*") {
            $shouldInclude = $false
            break
        }
    }
    
    # Proveri ekstenziju
    if ($shouldInclude) {
        $validExtensions = @(".java", ".kt", ".xml", ".gradle", ".txt", ".md", ".json", ".properties", ".pro")
        $validExtensions -contains $file.Extension
    } else {
        $false
    }
}

Write-Host "Pronađeno $($files.Count) fajlova za export..." -ForegroundColor Green

# Exportuj svaki fajl
foreach ($file in $files) {
    $relativePath = $file.FullName.Replace("$projectRoot\", "")
    Write-Host "Dodajem: $relativePath"
    
    Add-Content -Path $outputFile -Value "=" * 50
    Add-Content -Path $outputFile -Value "FILE: $relativePath"
    Add-Content -Path $outputFile -Value "=" * 50
    Add-Content -Path $outputFile -Value ""
    
    try {
        $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
        Add-Content -Path $outputFile -Value $content
    } catch {
        Add-Content -Path $outputFile -Value "[ERROR READING FILE: $($_.Exception.Message)]"
    }
    
    Add-Content -Path $outputFile -Value ""
    Add-Content -Path $outputFile -Value ""
}

Write-Host "Export ZAVRŠEN! Fajl: $outputFile" -ForegroundColor Green
Write-Host "Ukupno fajlova: $($files.Count)" -ForegroundColor Cyan