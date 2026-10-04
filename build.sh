#!/usr/bin/env bash
# Wymaga: JDK 21 i Gradle 8.10+ (https://gradle.org/install). Jar trafi do build/libs/
set -e
gradle build
ls -la build/libs/
