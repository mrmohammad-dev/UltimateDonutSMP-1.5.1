@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "GEYSER_REPO=https://github.com/Pooyan3748/GeyserDetector.git"
set "GEYSER_COMMIT=8a080acecd0d55353b159118496cbe224f0f9845"
set "EXPECTED_JAR=target\UltimateDonutSmp-1.5.1.jar"

echo [1/5] Checking build tools...
where java >nul 2>nul || goto :missing_java
where mvn >nul 2>nul || goto :missing_maven
where git >nul 2>nul || goto :missing_git
where jar >nul 2>nul || goto :missing_jar
java -version
mvn -version

echo.
echo [2/5] Preparing the pinned GeyserDetector API source...
if not exist "GeyserDetector\.git" (
    if exist "GeyserDetector" (
        echo ERROR: GeyserDetector exists but is not a Git checkout.
        exit /b 2
    )
    git clone "%GEYSER_REPO%" GeyserDetector || goto :failed
) else (
    git -C GeyserDetector fetch origin || goto :failed
)
git -C GeyserDetector checkout --detach "%GEYSER_COMMIT%" || goto :failed

echo.
echo [3/5] Installing the exact GeyserDetector API dependency locally...
call mvn --batch-mode --no-transfer-progress -f GeyserDetector\pom.xml -pl geyserdetector-api -am install -DskipTests
if errorlevel 1 goto :failed

echo.
echo [4/5] Running project tests and building the shaded plugin...
call mvn --batch-mode --no-transfer-progress clean verify
if errorlevel 1 goto :failed

echo.
echo [5/5] Verifying the final plugin JAR...
if not exist "%EXPECTED_JAR%" (
    echo ERROR: Maven completed but %EXPECTED_JAR% was not created.
    exit /b 3
)
jar tf "%EXPECTED_JAR%" | findstr /x "plugin.yml" >nul
if errorlevel 1 (
    echo ERROR: plugin.yml is missing from the JAR.
    exit /b 4
)
echo.
echo SUCCESS: %EXPECTED_JAR%
echo Copy this JAR to the server plugins folder only after testing it on a staging server.
exit /b 0

:missing_java
echo ERROR: Java was not found. Install a JDK (JDK 25 is used by the project's CI) and add it to PATH.
exit /b 2
:missing_maven
echo ERROR: Maven was not found. Install Apache Maven 3.9.x or newer and add mvn to PATH.
exit /b 2
:missing_git
echo ERROR: Git was not found. Install Git and add it to PATH; the build needs the pinned GeyserDetector API source.
exit /b 2
:missing_jar
echo ERROR: The JDK jar tool was not found. Check that a full JDK is installed and on PATH.
exit /b 2
:failed
echo.
echo ERROR: A dependency-install, test, or package step failed. Review the Maven output above; no JAR is approved.
exit /b 1
