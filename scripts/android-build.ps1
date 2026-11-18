param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Tasks = @(':app:assembleDebug')
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
$studioJbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
if (-not (Test-Path (Join-Path $studioJbr 'bin\java.exe'))) {
    throw 'Android Studio JBR was not found. Configure JAVA_HOME with JDK 25 and use gradlew.bat.'
}

$previousJavaHome = $env:JAVA_HOME
Push-Location $projectDir
try {
    $env:JAVA_HOME = $studioJbr
    # This checkout has Korean path components. Keep this override local to the invocation.
    & .\gradlew.bat '-Pandroid.overridePathCheck=true' @Tasks
    $buildExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
exit $buildExitCode
