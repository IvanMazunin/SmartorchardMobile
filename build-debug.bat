@echo off
setlocal
cd /d "%~dp0"
rem Foreground Gradle execution for Windows environments with loopback IPC issues.
set "JAVA_OPTS=%JAVA_OPTS% -Xms64m -Xmx3g -Dfile.encoding=UTF-8"
call gradlew.bat assembleDebug --no-daemon --console=plain "-Dorg.gradle.jvmargs=-Xms64m -Xmx3g -Dfile.encoding=UTF-8" "-Dorg.gradle.internal.instrumentation.agent=false"
exit /b %ERRORLEVEL%
