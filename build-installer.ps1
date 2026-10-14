# =============================================================================
#  Tenant Management System - Windows Installer Build Script
# =============================================================================

$ErrorActionPreference = "Stop"

# ---------- Configuration ----------
$AppName     = "TenantManagementSystem"
$AppVersion  = "1.0.0"
$Vendor      = "Property Management Systems"
$Description = "Tenant and Property Management System for landlords"
$MainClass   = "com.propertymanager.Main"
$MainJar     = "tenant-management-system-1.0.0.jar"

# ---------- Paths ----------
$Root       = Split-Path -Parent $MyInvocation.MyCommand.Path
$TargetDir  = Join-Path $Root "target"
$StageDir   = Join-Path $Root "target\staging"
$DistDir    = Join-Path $Root "dist"
$IconPath   = Join-Path $Root "app.ico"
$JarPath    = Join-Path $TargetDir $MainJar
$LibDir     = Join-Path $StageDir "lib"
$ResDir     = Join-Path $StageDir "resources"
$JfxModsDir = Join-Path $StageDir "jfx-mods"

# ---------- Auto-detect JDK ----------
function Find-Jdk {
    $candidates = @(
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot",
        "C:\Program Files\Java\jdk-25.0.2"
    )
    foreach ($path in $candidates) {
        if (Test-Path (Join-Path $path "bin\jpackage.exe")) {
            return $path
        }
    }
    $cmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($cmd) {
        return Split-Path -Parent (Split-Path -Parent $cmd.Source)
    }
    throw "Could not find a JDK with jpackage. Install JDK 21+ and add to PATH."
}

$JdkPath  = Find-Jdk
$JPackage = Join-Path $JdkPath "bin\jpackage.exe"

Write-Host ""
Write-Host "=============================================================" -ForegroundColor Cyan
Write-Host "  Building $AppName $AppVersion" -ForegroundColor Cyan
Write-Host "=============================================================" -ForegroundColor Cyan
Write-Host "Project root : $Root"
Write-Host "JDK          : $JdkPath"
Write-Host ""

# ---------- Clean dist folder only ----------
if (Test-Path $DistDir) { Remove-Item $DistDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

# ---------- Maven build ----------
Write-Host "[1/4] Running Maven build..." -ForegroundColor Yellow
Push-Location $Root
try {
    & mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
} finally {
    Pop-Location
}

if (-not (Test-Path $JarPath)) {
    throw "Main JAR not found at $JarPath - did the Maven build succeed?"
}
Write-Host "      Main JAR: $MainJar" -ForegroundColor Green

# ---------- Create staging folders AFTER Maven has run ----------
Write-Host "[2/4] Staging runtime resources..." -ForegroundColor Yellow

if (Test-Path $StageDir) { Remove-Item $StageDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $StageDir   | Out-Null
New-Item -ItemType Directory -Force -Path $LibDir     | Out-Null
New-Item -ItemType Directory -Force -Path $ResDir     | Out-Null
New-Item -ItemType Directory -Force -Path $JfxModsDir | Out-Null

Copy-Item $JarPath $LibDir

Push-Location $Root
try {
    & mvn dependency:copy-dependencies "-DoutputDirectory=$LibDir" -DincludeScope=runtime -q
    if ($LASTEXITCODE -ne 0) { throw "Failed to copy dependencies." }
} finally {
    Pop-Location
}

$resourceSrc = Join-Path $Root "src\main\resources"
Copy-Item "$resourceSrc\*" $ResDir -Recurse -Force

Write-Host "      Staged JARs: $((Get-ChildItem $LibDir -Filter *.jar).Count)" -ForegroundColor Green

# ---------- Copy JavaFX JARs into a dedicated module path folder ----------
Get-ChildItem $LibDir -Filter "javafx-*.jar" | ForEach-Object {
    Copy-Item $_.FullName $JfxModsDir
}
$jfxCount = (Get-ChildItem $JfxModsDir -Filter *.jar).Count
Write-Host "      JavaFX modules staged: $jfxCount" -ForegroundColor Green

# ---------- Icon check ----------
if (-not (Test-Path $IconPath)) {
    Write-Host "  WARNING: app.ico not found. Building without an icon." -ForegroundColor Yellow
    $useIcon = $false
} else {
    $useIcon = $true
}

# ---------- jpackage ----------
Write-Host "[3/4] Running jpackage (this may take a minute)..." -ForegroundColor Yellow

$jpArgs = @(
    "--type", "exe",
    "--name", $AppName,
    "--app-version", $AppVersion,
    "--vendor", $Vendor,
    "--description", $Description,
    "--input", $LibDir,
    "--main-jar", $MainJar,
    "--main-class", $MainClass,
    "--dest", $DistDir,
    "--win-dir-chooser",
    "--win-menu",
    "--win-shortcut",
    "--win-menu-group", $AppName,
    "--module-path", $JfxModsDir,
    "--add-modules", "java.base,javafx.controls,javafx.fxml,javafx.swing",
    "--java-options", "-Xmx512m"
)

if ($useIcon) {
    $jpArgs += "--icon"
    $jpArgs += $IconPath
}

& $JPackage @jpArgs
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed. See output above."
}

# ---------- Done ----------
$installer = Get-ChildItem $DistDir -Filter "*.exe" | Select-Object -First 1

if (-not $installer) {
    throw "No installer was produced in $DistDir"
}

Write-Host ""
Write-Host "=============================================================" -ForegroundColor Green
Write-Host "  BUILD SUCCESSFUL" -ForegroundColor Green
Write-Host "=============================================================" -ForegroundColor Green
Write-Host "  Installer: $($installer.FullName)" -ForegroundColor Green
Write-Host "  Size     : $([math]::Round($installer.Length / 1MB, 1)) MB" -ForegroundColor Green
Write-Host "=============================================================" -ForegroundColor Green
Write-Host ""
