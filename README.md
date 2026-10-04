# Herobrine Brain

## Rdzeń (czysta Java 21, testowalny bez Minecrafta)
    mkdir out && javac -encoding UTF-8 -d out $(find core -name '*.java')
    java -Dstdout.encoding=UTF-8 -cp out pl.herobrinebrain.core.Sim
    java -Dstdout.encoding=UTF-8 -cp out pl.herobrinebrain.core.SnapshotTest

## Mod (NeoForge 1.21.1, Java 21)
    ./gradlew runClient      # (najpierw wygeneruj wrapper: gradle wrapper)

Przed pierwszym buildem sprawdź wersje w `gradle.properties` (neo_version) i `build.gradle` (plugin net.neoforged.moddev).
W grze (OP): `/hb debug` pokazuje profil, fakty, napięcie i ostatnie akcje Directora; `/hb reset` zeruje mózg.


Dokument projektowy: HEROBRINE_BRAIN_design.md

Jak zbudować jar: patrz INSTALL.md (najprościej: GitHub Actions, workflow już jest w .github/workflows).
