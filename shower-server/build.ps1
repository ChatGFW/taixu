# Build shower-server.jar (classes.dex) for app_process — direct Surface pipeline.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$androidJar = "C:\Users\wangk\AppData\Local\Android\Sdk\platforms\android-35\android.jar"
$d8 = "C:\Users\wangk\AppData\Local\Android\Sdk\build-tools\36.0.0\d8.bat"
$javaHome = $env:JAVA_HOME
if (-not $javaHome) { $javaHome = "C:\Program Files\Android\Android Studio\jbr" }
$javac = Join-Path $javaHome "bin\javac.exe"
$jarTool = Join-Path $javaHome "bin\jar.exe"

$classes = Join-Path $root "build\classes"
$dexDir = Join-Path $root "build\dex"
$outJar = Join-Path $root "..\showerclient\src\main\assets\shower-server.jar"

Remove-Item $classes, $dexDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $dexDir | Out-Null

$sources = Get-ChildItem (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 --release 17 -classpath $androidJar -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$classFiles = Get-ChildItem $classes -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& $d8 --min-api 26 --output $dexDir @classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

$dex = Join-Path $dexDir "classes.dex"
if (-not (Test-Path $dex)) { throw "classes.dex missing" }

Push-Location $dexDir
& $jarTool cf $outJar classes.dex
Pop-Location
Write-Host "Wrote $outJar ($((Get-Item $outJar).Length) bytes)"
