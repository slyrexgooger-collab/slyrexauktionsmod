@echo off
chcp 65001 >nul
title Auktions-Mod bauen
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java wurde nicht gefunden!
  echo Bitte installiere Java 21 von https://adoptium.net/temurin/releases/?version=21
  echo und starte diese Datei danach erneut.
  start https://adoptium.net/temurin/releases/?version=21
  pause
  exit /b 1
)
echo Baue die Mod... (beim ersten Mal dauert das einige Minuten)
call gradlew.bat build --no-daemon
if errorlevel 1 (
  echo.
  echo FEHLER beim Bauen. Schick die Meldung oben an Claude.
  pause
  exit /b 1
)
copy /y build\libs\auktionsmod-1.0.0.jar auktionsmod-1.0.0.jar >nul
echo.
echo FERTIG! Die Mod liegt jetzt hier: %cd%\auktionsmod-1.0.0.jar
echo Kopiere sie in deinen Ordner .minecraft\mods  (zusammen mit der Fabric API)
explorer /select,"%cd%\auktionsmod-1.0.0.jar"
pause
