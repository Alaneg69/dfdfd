@echo off
REM Wymaga: JDK 21 i Gradle 8.10+ w PATH. Jar trafi do build\libs\
gradle build
dir build\libs
pause
