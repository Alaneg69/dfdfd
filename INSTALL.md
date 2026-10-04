# Jak dostać gotowy mod (.jar)

Mod wymaga: **Minecraft 1.21.1 + NeoForge 21.1.x + Java 21**.

## Sposób A — bez instalowania czegokolwiek (GitHub, ~3 minuty)
1. Załóż darmowe konto na github.com i utwórz **nowe puste repozytorium**.
2. Wgraj do niego całą zawartość tego folderu (przycisk „Add file → Upload files”, przeciągnij pliki
   razem z folderem `.github`; jeśli przeglądarka go ukrywa, użyj `git`: `git init && git add -A && git commit -m init && git push`).
3. Wejdź w zakładkę **Actions** → workflow **Build mod jar** uruchomi się sam (albo „Run workflow”).
4. Po zakończeniu (zielony ✓) otwórz przebieg i pobierz artefakt **herobrinebrain-jar**.
5. Rozpakuj zip: w środku jest `herobrinebrain-0.1.0.jar`.

## Sposób B — lokalnie
1. Zainstaluj JDK 21 i Gradle 8.10+.
2. Uruchom `build.sh` (Linux/macOS) lub `build.bat` (Windows). Jar: `build/libs/herobrinebrain-0.1.0.jar`.
3. Testowe uruchomienie gry z modem: `gradle runClient`.

## Instalacja w grze
1. Zainstaluj NeoForge 1.21.1 (https://neoforged.net) i uruchom grę raz.
2. Wrzuć `herobrinebrain-0.1.0.jar` do folderu `mods` (`%appdata%\.minecraft\mods`).
3. Wejdź do świata (najlepiej nowy, tryb Survival).

## Co zobaczysz / jak testować
- Przez pierwsze ~5 minut mod nic nie robi (okres łaski). Potem rzadko przesuwa pojedyncze pochodnie
  w Twojej okolicy (poza polem widzenia) i odtwarza dźwięki jaskini. Po kilku minutach wszystko wraca.
- Komendy (wymagają OP / cheatów): `/hb debug` — profil, fakty, napięcie i log decyzji; `/hb reset`.
- Chcesz szybciej? W `HorrorDirector.advanceClock` zmień `now + 6000` (okres łaski) na `now + 200`.

## Gdy build się wywali
Najczęstsze przyczyny: numer wersji NeoForge (`neo_version` w `gradle.properties`) albo pluginu
(`net.neoforged.moddev` w `build.gradle`) — sprawdź aktualne na https://projects.neoforged.net.
Błąd kompilacji (np. nazwa klasy Minecrafta) wklej mi — poprawię.
