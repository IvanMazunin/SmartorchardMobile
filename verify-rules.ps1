param([switch]$SkipCompile)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
$previousJavaOptions = $env:JAVA_OPTS
try {
    if (-not $SkipCompile) {
        $env:JAVA_OPTS = "$previousJavaOptions -Xms64m -Xmx3g -Dfile.encoding=UTF-8"
        & .\gradlew.bat compileDebugUnitTestKotlin --no-daemon --console=plain '-Dorg.gradle.jvmargs=-Xms64m -Xmx3g -Dfile.encoding=UTF-8' '-Dorg.gradle.internal.instrumentation.agent=false'
        if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    }
    $taskGradleRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $taskCache = Join-Path $taskGradleRoot 'caches\modules-2\files-2.1'
    $taskDependencies = @(
        @{G='junit'; A='junit'; V='4.13.2'},
        @{G='org.hamcrest'; A='hamcrest-core'; V='1.3'},
        @{G='org.jetbrains.kotlin'; A='kotlin-stdlib'; V='2.4.21'},
        @{G='org.jetbrains.kotlinx'; A='kotlinx-serialization-core-jvm'; V='1.9.0'},
        @{G='org.jetbrains.kotlinx'; A='kotlinx-serialization-json-jvm'; V='1.9.0'}
    )
    $taskJars = foreach ($dependency in $taskDependencies) {
        $dependencyPath = Join-Path $taskCache "$($dependency.G)\$($dependency.A)\$($dependency.V)"
        $jar = Get-ChildItem -LiteralPath $dependencyPath -Recurse -Filter '*.jar' |
            Where-Object { $_.Name -notmatch '(sources|javadoc)' } | Select-Object -First 1
        if (-not $jar) { throw "Dependency missing: $($dependency.A)" }
        $jar.FullName
    }
    $taskClasses = @(
        (Join-Path $PSScriptRoot 'app\build\intermediates\built_in_kotlinc\debugUnitTest\compileDebugUnitTestKotlin\classes'),
        (Join-Path $PSScriptRoot 'app\build\intermediates\built_in_kotlinc\debug\compileDebugKotlin\classes')
    )
    foreach ($path in $taskClasses) { if (-not (Test-Path -LiteralPath $path)) { throw 'Compile test classes first; omit -SkipCompile.' } }
    $taskJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
    $taskReport = Join-Path $PSScriptRoot 'app\build\reports\junit-direct.txt'
    New-Item -ItemType Directory -Force -Path (Split-Path $taskReport) | Out-Null
    & $taskJava '-Dfile.encoding=UTF-8' -cp (($taskClasses + $taskJars) -join ';') org.junit.runner.JUnitCore com.smartorchard.mobile.RulesTest | Tee-Object -FilePath $taskReport
    if ($LASTEXITCODE -ne 0) { throw 'Unit tests failed.' }
} finally {
    $env:JAVA_OPTS = $previousJavaOptions
    Pop-Location
}
