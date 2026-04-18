@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "REPO_ROOT=%SCRIPT_DIR%.."
set "JAR=%REPO_ROOT%\ddsl-cli\target\ddsl-cli-0.1.0-SNAPSHOT-all.jar"

if not exist "%JAR%" (
  pushd "%REPO_ROOT%" || exit /b 1
  call mvn -q -pl ddsl-cli -am package
  if errorlevel 1 exit /b %errorlevel%
  popd
)

java -jar "%JAR%" %*
