# HEROBRINE BRAIN — dokument projektowy (v0.1)

> Cel: gracz po kilku godzinach mówi „nie wiem, czy to było zaprogramowane… ale coś mnie obserwowało".
> Największym przeciwnikiem nie jest Herobrine, tylko **utrata zaufania do świata**.

---

## 1. Analiza koncepcji

### Mocne strony
- **Director zamiast potwora** to właściwy kierunek. Horror robi się z oczekiwania i niepewności, nie z HP moba.
- **Cisza jako ruch** (DO_NOTHING) jest najcenniejszym pomysłem w całej koncepcji. Zostaje w rdzeniu jako pełnoprawna strategia z własnym scoringiem.
- **Personalizacja snów** to unikalny wyróżnik. Nic w Minecrafcie nie daje tak naturalnego „bezpiecznego momentu do podważenia" jak łóżko.
- **Niejednoznaczność przyczyny** (A–E z Twojej listy) jest rdzeniem paranoi. Zamieniam ją na mierzalną wielkość: *deniability* każdej akcji.

### Słabe strony i moje korekty (wprost)
| Problem | Dlaczego to ryzyko | Korekta |
|---|---|---|
| **Liniowe poziomy 0–6** | Gracz „przeskakuje" poziomy i czuje skrypt. | Ciągła zmienna `presence` 0–6; poziom = `floor(presence)` **ograniczony czasem gry** (nie da się dojechać do L6 w godzinę). Poziom bramkuje *dostępność* strategii, nie ich kolejność. |
| **„Ignoruje tabliczki → zmień strategię"** zakłada, że wiemy, że je ignoruje | Nie wiemy, bo gracz mógł ich nie zauważyć. | Wprowadzam **detekcję zauważenia** (gaze-cone + raycast + czas w zasięgu) i raport `Outcome(noticed, reaction)` do Directora. Bez tego adaptacja to iluzja. |
| **Zmienianie domu gracza** | Griefing = frustracja, wyłączenie moda. | **Phantom World Layer**: fałszywe bloki wysyłane pakietem *tylko do jednego klienta*, bez zmiany świata. Prawdziwe zmiany tylko przez **Reversible Ledger** (wszystko cofalne, TTL, nigdy nie dotyka skrzyń i przedmiotów gracza). |
| **LLM jako „mózg"** | Latencja, koszt, halucynacje, wyciek danych, niestabilny ton. | LLM nigdy nie decyduje. Dostaje abstrakcyjny `MessageIntent`, zwraca jedną linię, która przechodzi `MessageGate`. Zawsze istnieje fallback szablonowy. |
| **Wiadomości odnoszące się do faktów** („jedna pochodnia mniej") | Jeśli to kłamstwo, gracz sprawdzi i magia pęka. | **Claim Ledger**: tekst może twierdzić tylko to, co faktycznie zostało zrobione w świecie (albo co jest prawdą w pamięci). |
| **12+ kategorii strategii** | Niemożliwe do przetestowania i zbalansowania. | Jeden wspólny interfejs `Strategy` + symulator headless (zrobiony, patrz `Sim`) do strojenia bez uruchamiania gry. |
| **Cechy „realnego świata"** (nick, godzina systemowa) | Skuteczne, ale granica creepy/naruszenie zaufania. | Domyślnie wyłączone; opt-in w configu. Rdzeń ich nie potrzebuje. |

### Technika zaawansowana, której nie było w briefie
**Contextual bandit per gracz (Thompson sampling)**: Director uczy się, które strategie *u tego gracza* dają zauważenie + reakcję. Ukryta nagroda dla subtelności: niezauważone-ale-subtelne to częściowy sukces, niezauważone-i-głośne to porażka. Dzięki temu system zbiega do „tego, co działa na Ciebie", a nie do „głośnego".

---

## 2. Mechaniki wyróżniające mod (8)

1. **Deniability Ledger**: każda akcja ma wagę niejednoznaczności; Director preferuje niską pewność przyczyny, dopóki gracz nie jest świadomy (potem odwraca preferencję).
2. **Dream Archive**: sen budowany z migawek miejsc gracza (jego baza, jaskinia, miejsce śmierci) z kontrolowanymi *błędami* (brak jednej pochodni, drzwi do złego miejsca).
3. **Phantom World Layer**: świat kłamie tylko Tobie (pakiety per-klient). Zero griefingu, pełna odwracalność.
4. **Chekhov's Sign**: tabliczka bez sensu, której znaczenie ujawnia się godzinami później (Director zapisuje „nasiona" i spłaca je, gdy warunek się spełni).
5. **Peripheral Observer**: sylwetka istnieje tylko poza stożkiem widzenia; patrząc wprost, gracz nigdy jej nie złapie. Zaprzecza się sama.
6. **Echo of Self**: dźwięki kroków/kopania gracza odtwarzane z opóźnieniem i z innej strony (z nagrania własnej trasy).
7. **Withdrawal (wycofanie)**: gracz-łowca dostaje *mniej* Herobrine'a, a więcej fałszywych tropów. Brak zjawiska staje się zjawiskiem.
8. **False Safety Cycle**: po CALM_PERIOD rośnie COMFORT, a Director czeka na jego szczyt, żeby podważyć właśnie to poczucie.

---

## 3. Architektura techniczna

**Wersja:** Minecraft **1.21.1** + **NeoForge** (Java 21).
Uzasadnienie: największa baza dokumentacji i modów, stabilne API `SavedData`, eventów i pakietów, prosta własna dimension (dream). Fabric byłby lżejszy, ale chcę gotowych, stabilnych punktów zaczepienia eventów i magazynu danych. *Sprawdź przed startem, czy jest nowsza docelowa wersja; rdzeń (`core`) nie zależy od wersji Minecrafta.*

**AI:** Utility AI + model tempa (L4D-style: BUILD_UP/PEAK/RELAX) + bandyta Beta.
Nie behavior tree (decyzje są wyborem *co robić w skali minut*, nie sterowaniem ruchem), nie czysta FSM (eksplozja stanów). Behavior tree/skrypt użyję *wewnątrz* pojedynczych akcji (np. choreografia obserwatora).

```
herobrine-brain/
├─ core/                      (czysta Java, bez MC; testowalna headless)  ← ZROBIONE
│  ├─ Trait, PlayerProfile    profil psychologiczny z dryfem
│  ├─ ObsType, Observation    zdarzenia z adaptera
│  ├─ MemoryStore, Fact       pamięć krótko-/długoterminowa
│  ├─ Strategy, Situation     katalog strategii, migawka sytuacji
│  ├─ HorrorDirector          utility + tempo + bandyta
│  ├─ MessageIntent/Gate/Composer/TemplateBank   hybryda LLM
│  └─ test/Sim                symulacja 3 typów graczy + asercje
├─ adapter-neoforge/          ← NASTĘPNY KROK
│  ├─ sensors/                zbieranie obserwacji (ticki, eventy, raycast)
│  ├─ executors/              po jednym ActionExecutor na Strategy
│  ├─ ledger/                 AnomalyLedger (odwracalne zmiany, TTL, detekcja zauważenia)
│  ├─ persist/                SavedData per-gracz (JSON przez Gson)
│  ├─ net/                    pakiety: phantom blocks, dźwięk, efekty klienta
│  └─ dream/                  dimension + archiwum migawek
└─ content/                   dźwięki, tekstury, lang
```

**Przepływ:** `Sensors → Observation → Director(profile+memory) → DirectorAction → ActionExecutor → (zmiana w świecie + AnomalyLedger) → detekcja zauważenia → Outcome → Director`.

**Synchronizacja:** serwer autorytatywny (cały Director). Klient tylko kosmetycznie: dźwięk, post-process, fałszywe bloki z pakietu. Mod działa w singleplayer (zintegrowany serwer) i multi (profil per UUID).

**Zapis:** `SavedData` per-gracz; serializacja stanu rdzenia przez Gson (MC dołącza Gson). Zapis przy zapisie świata + po wydarzeniach (sen, śmierć).

**LLM (hybryda):**
- Director decyduje *co/kiedy/dla jakiego faktu* → `MessageIntent`.
- `MessageComposer` buduje abstrakcyjny prompt (zero współrzędnych/nicku) i woła LLM **asynchronicznie** z twardym timeoutem.
- `MessageGate` waliduje wynik (długość, brak linków/komend/formatowania, brak meta-słów, brak liczb), inaczej szablon.
- Rekomendacja: LLM jest **opcjonalny**; deterministyczny rdzeń daje 95% efektu. LLM dodaje wariację brzmienia, nie logikę. Lokalnie: Ollama przez HTTP na osobnym executorze.

---

## 4. Systemy: CO / DLACZEGO / HORROR / WSPÓŁPRACA / JAK / POWTARZALNOŚĆ / FRUSTRACJA / UKRYCIE

### 4.1 Percepcja
- **Co:** zbiera obserwacje (dwell, sen, śmierć, patrzenie na anomalię, ucieczka, czytanie wiadomości, szukanie Herobrine'a).
- **Dlaczego:** bez danych nie ma „wiedzy o graczu".
- **Horror:** każda późniejsza „wiedza" jest uprawniona prawdziwymi danymi.
- **Współpraca:** zasila profil i pamięć; odbiera Outcome od AnomalyLedger.
- **Jak:** adapter wysyła `Observation` (próbkowanie co 5 s dla dwell, eventy dla reszty).
- **Powtarzalność:** n/d. **Frustracja:** brak (pasywne). **Ukrycie:** zero widocznych efektów.

### 4.2 Pamięć
- **Co:** krótkoterminowy ring buffer (64) + fakty długoterminowe (HOTSPOT, FEARED_PLACE, SLEEP_ROUTINE, …) z siłą 0–1.
- **Dlaczego:** pozwala odnosić się do przeszłości i wygaszać nieaktualne.
- **Horror:** „zapamiętał" brzmi wiarygodnie tylko, gdy pamięć jest selektywna i zanika.
- **Współpraca:** Director filtruje akcje faktami (np. IGNORES_MESSAGES wycisza tabliczki); komunikacja i sny cytują fakty.
- **Jak:** wzmocnienie z malejącymi przyrostami, półtrwanie ~10 h gry; fakt nie jest usuwany, póki świeżo wzmacniany.
- **Powtarzalność:** cooldown 30 min na odwołanie do tego samego faktu.
- **Frustracja:** zanik = system „wybacza". **Ukrycie:** gracz nie widzi pamięci, tylko jej skutki.

### 4.3 Profil gracza
- **Co:** 9 cech: FEAR, CURIOSITY, AGGRESSION, PARANOIA, CONFIDENCE, EXPLORATION, ISOLATION, AWARENESS, COMFORT + histogram snu i dwell.
- **Dlaczego:** zamiana zachowania w liczby, na których operuje scoring.
- **Horror:** COMFORT jest celem (podważanie bezpieczeństwa), AWARENESS przełącza stosunek do subtelności.
- **Współpraca:** wejście dla `fit()` w Directorze.
- **Jak:** nudge z saturacją (nic nie przykleja się do 1.0) + dryf do wartości bazowych o różnych półtrwaniach.
- **Powtarzalność:** dryf resetuje „wyuczony" stan, gdy gracz się zmienia.
- **Frustracja:** FEAR>0.85 tłumi bezpośrednie pojawienia. **Ukrycie:** całkowite.

### 4.4 Horror Director
- **Co:** wybiera strategię; mierzy napięcie; zmienia fazy BUILD_UP → PEAK → RELAX.
- **Dlaczego:** horror to tempo, nie częstotliwość.
- **Horror:** `DO_NOTHING` i `CALM_PERIOD` są pełnoprawnymi wyborami.
- **Współpraca:** czyta profil/pamięć, produkuje `DirectorAction`, uczy się z `Outcome`.
- **Jak:** `score = fit·(0.35·pace + 0.20·novelty + 0.30·learned + 0.15·deniability)`, wybór softmax (nie argmax).
- **Powtarzalność:** novelty (kara za ostatnie 6), cooldowny, softmax, kara za niedawno użyty fakt.
- **Frustracja:** w walce / <30% HP dostępne tylko DO_NOTHING/OBSERVE/CALM; 5 min łaski na starcie.
- **Ukrycie:** losowe odstępy (13 s – 4 min zależnie od fazy).

### 4.5 Eskalacja (presence)
- **Co:** ciągła `presence` 0–6 → poziom; strategie mają `minLevel`.
- **Dlaczego:** powolne budowanie obecności zamiast skoków.
- **Horror:** gracz nie wie, *kiedy* przekroczył próg.
- **Współpraca:** rośnie od „studiowania" i od zauważenia (`noticed`); cap = `1 + 1.2·godziny_gry`.
- **Jak:** AFK-owe skoki czasu nie liczą się do czasu gry (max 200 ticków na wywołanie).
- **Frustracja:** twardy cap czasowy. **Powtarzalność:** poziom bramkuje *dostępność*, nie kolejność.

### 4.6 Sny (kluczowy)
- **Co:** po położeniu się spać gracz może wejść w „dream state" zbudowany z archiwum migawek jego miejsc.
- **Dlaczego:** łóżko to jedyny moment, w którym podważenie bezpieczeństwa nie jest „losowym wydarzeniem".
- **Horror:** „prawie identyczne, ale błędne" (brak jednej pochodni, drzwi do złego miejsca, skrzynia z notatką).
- **Współpraca:** bierze fakty z pamięci (HOTSPOT, DEATH_SPOT, SLEEP_ROUTINE); `MessageIntent` dla głosu; kończy się w tym samym łóżku.
- **Jak (MVP):** dimension z pustym generatorem; wklejanie `StructureTemplate` z migawek (48×32×48 wokół łóżka, skompresowane paletą); lista *mutacji* (usuń blok, odbij, podmień drzwi). Osobny ekwipunek snu (zapis/przywrócenie).
- **Powtarzalność:** losowy zestaw mutacji + różne miejsca z archiwum; cooldown 20 min.
- **Frustracja:** ekwipunek nietykalny, sen trwa 60–120 s, zawsze wybudzenie w bezpiecznym miejscu, opcja „wyłącz sny" w configu. Sen **nie** zawsze się dzieje (Director może wybrać DO_NOTHING).
- **Ukrycie:** pierwsze sny są niemal normalne; zmiany tylko wtedy, gdy `sleepRegularity > 0`.

### 4.7 Komunikacja
- **Co:** tabliczki, książki, nazwy przedmiotów, czat, głos we śnie.
- **Dlaczego:** ewolucja od „…" do konkretu buduje relację.
- **Horror:** poziom 3 = kontakt bez konkretów, 4 = konkret, 5+ = intymna wiedza.
- **Współpraca:** `MessageIntent` → `MessageComposer` → `MessageGate`; lokalizacja wg faktu; Claim Ledger weryfikuje twierdzenia.
- **Jak:** miejsce tabliczki z *znaczenia* (HOTSPOT/FEARED_PLACE), nie z losowania; wiadomość „opóźniona" (Chekhov's Sign) trafia w nasiona.
- **Powtarzalność:** szablony + opcjonalny LLM; cooldown faktu 30 min.
- **Frustracja:** wiadomości nie blokują, nigdy nie na czacie spamem; jeśli gracz ignoruje, kanał wygasa.
- **Ukrycie:** ~1–2 wiadomości na godzinę gry na wysokim poziomie.

### 4.8 Stalking
- **Co:** Peripheral Observer: sylwetka widoczna tylko poza stożkiem widzenia.
- **Dlaczego:** niewidzialność przy bezpośrednim spojrzeniu to źródło paranoi.
- **Horror:** gracz „prawie" ją widzi; łowca dostaje mniej (Withdrawal).
- **Współpraca:** Director wybiera moment/miejsce; AnomalyLedger mierzy czy patrzył.
- **Jak:** encja bez AI-goali, sterowana skryptem; test: kąt względem wektora patrzenia + raycast; znika, gdy patrzy.
- **Powtarzalność:** różne choreografie (stoi, idzie równolegle, stoi odwrócona).
- **Frustracja:** bez obrażeń na L≤4; unika walki i niskiego HP.
- **Ukrycie:** pierwsze obserwacje poza zasięgiem rozpoznania.

### 4.9 Anomalie i wydarzenia
- **Co:** drobne, odwracalne zmiany (przesunięta pochodnia, otwarte drzwi), dźwięki, fałszywe tropy.
- **Dlaczego:** „jedna pochodnia" > jumpscare.
- **Horror:** gracz sam zaczyna sprawdzać świat.
- **Współpraca:** wykonywane przez `ActionExecutor`; rejestr w `AnomalyLedger` (TTL, cofnięcie, detekcja zauważenia).
- **Jak:** Phantom Layer (pakiety per-klient) jako domyślny kanał; prawdziwe zmiany tylko odwracalne.
- **Powtarzalność:** kombinatoryczny generator (typ × miejsce × czas × „czy wróci do normy").
- **Frustracja:** nic nie niszczy, nic nie zabiera, wszystko się cofa.
- **Ukrycie:** większość anomalii *nigdy nie jest zauważona*; to feature.

### 4.10 Warstwa LLM
- **Co:** opcjonalne generowanie brzmienia wiadomości.
- **Dlaczego:** naturalność bez oddawania kontroli.
- **Horror:** konkretne, ale oszczędne, spójne w tonie.
- **Współpraca:** wyłącznie `MessageIntent` → tekst; zero dostępu do świata.
- **Jak:** prompt abstrakcyjny, async + timeout, walidacja, fallback.
- **Powtarzalność:** to główny jej zysk. **Frustracja:** brak (fallback natychmiast). **Ukrycie:** tekst bez meta.

---

## 5. Stan implementacji (v0.2)
- ✅ `core` skompilowany (JDK 21) i uruchomiony: `Sim` (5 asercji adaptacji) + `SnapshotTest` (7 asercji round-tripu stanu).
- ✅ **M1 napisany**: szkielet NeoForge (`build.gradle`, `neoforge.mods.toml`), sensory (dwell, jaskinie, noc, sen, śmierć, czat), zapis `SavedData` + Gson, komendy `/hb debug` i `/hb reset`, router akcji (jedyny działający efekt: `AMBIENT_SOUND`).
- ⚠️ **Kod adaptera nie był kompilowany przeciw Minecraftowi** (brak dostępu do repozytoriów NeoForge/Mojang w moim środowisku). Sprawdziłem tylko składnię: jedyne błędy to brakujące klasy MC. Spodziewaj się drobnych poprawek nazw API; wersje w `gradle.properties` i `build.gradle` oznaczone „UWAGA" trzeba zweryfikować.
- ⏳ Akcje poza `AMBIENT_SOUND` to *dry-run* (wpis w logu `/hb debug`) i wołają `discard()`, żeby nie uczyć bandyty bez pomiaru zauważenia.

### Mapa drogowa
| Etap | Zakres |
|---|---|
| M1 ✅ | Szkielet moda NeoForge + sensory + zapis `SavedData` + komenda debug `/hb debug` (profil, fakty, napięcie). |
| M2 | Executory: MICRO_ANOMALY (pochodnie), AMBIENT_SOUND, SIGN_MESSAGE + AnomalyLedger z detekcją zauważenia. |
| M3 | Phantom Layer (pakiety) + Peripheral Observer. |
| M4 | Sny: archiwum migawek, dimension, mutacje. |
| M5 | Claim Ledger, Chekhov's Sign, Echo of Self. |
| M6 | Opcjonalny LLM (Ollama) + config intensywności + tryb „bezpieczny". |

## 6. Strojenie
Wszystkie progi są stałymi w `HorrorDirector`, szybkimi do wyciągnięcia do configu (`pace`, `softmaxTemp`, `levelCap`, `graceTicks`). Do strojenia używaj `Sim` (18 dni gry liczy się w ułamku sekundy).
