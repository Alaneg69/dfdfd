package pl.herobrinebrain.core;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

/**
 * Symulacja bez Minecrafta: wirtualny gracz żyje według rytmu dnia, a Director podejmuje decyzje.
 * Cel: zweryfikować, że system ADAPTUJE się (a nie tylko losuje) i że poziomy eskalują wolno.
 */
public final class Sim {
    static final int DAY = 24000;

    record Result(Map<Strategy, Integer> hist, int finalLevel, PlayerProfile profile, MemoryStore mem,
                  List<String> log) { }

    static Result run(String name, boolean readsSigns, boolean hunts, int days, long seed, boolean verbose) {
        PlayerProfile profile = new PlayerProfile();
        MemoryStore memory = new MemoryStore();
        HorrorDirector dir = new HorrorDirector(profile, memory, seed);
        MessageComposer composer = new MessageComposer(null, ForkJoinPool.commonPool(), Duration.ofMillis(500), seed);
        Random world = new Random(seed * 31 + 7);
        Map<Strategy, Integer> hist = new EnumMap<>(Strategy.class);
        List<String> log = new ArrayList<>();

        for (int day = 0; day < days; day++) {
            for (int t = 0; t < DAY; t += 100) {
                long tick = (long) day * DAY + t;
                int hour = (t / 1000 + 6) % 24;                       // tick 0 = 06:00
                boolean cave = hour >= 17 && hour < 21;
                boolean sleepingStep = hour == 22 && (t % 1000) < 300;
                String place = cave ? "cave1" : "base";
                boolean night = hour >= 20 || hour < 5;

                // --- zachowanie gracza => obserwacje
                dir.observe(Observation.of(ObsType.DWELL, tick, place, 5));
                if (cave) dir.observe(Observation.of(ObsType.UNDERGROUND_TIME, tick, place, 5));
                if (hunts && hour == 12 && t % 1000 == 0 && day % 2 == 0)
                    dir.observe(Observation.of(ObsType.SEARCHED_FOR_HEROBRINE, tick, "", 1));

                Situation s = new Situation(tick, hour, sleepingStep, cave, cave || night, night, !cave, false, 1.0);

                Optional<DirectorAction> act;
                if (hour == 22 && t % 1000 == 0) {                    // zdarzenie snu - wymuszona decyzja
                    dir.observe(Observation.of(ObsType.SLEEP, tick, "base", hour));
                    act = dir.decideNow(new Situation(tick, hour, true, false, true, true, true, false, 1.0));
                } else {
                    act = dir.tick(s);
                }

                if (act.isEmpty()) continue;
                DirectorAction a = act.get();
                hist.merge(a.strategy(), 1, Integer::sum);
                if (!a.visible()) continue;

                // --- reakcja gracza (symulowana): im mniej "deniability", tym większa szansa zauważenia
                double pNotice = 0.2 + 0.6 * (1 - a.strategy().deniability);
                boolean noticed = world.nextDouble() < pNotice;
                String msg = "";
                if (a.intent() != null) {
                    msg = composer.compose(a.intent()).join();
                    if (a.strategy() == Strategy.SIGN_MESSAGE) {
                        boolean read = readsSigns && world.nextDouble() < 0.85;
                        dir.observe(Observation.of(read ? ObsType.READ_MESSAGE : ObsType.IGNORED_MESSAGE, tick, a.targetPlace(), 1));
                        noticed = read;
                    }
                }
                if (noticed && a.strategy() != Strategy.SIGN_MESSAGE)
                    dir.observe(Observation.of(ObsType.SAW_ANOMALY, tick, a.targetPlace(), a.strategy().tensionCost));
                dir.reportOutcome(a.id(), noticed, noticed ? world.nextDouble() : 0);

                if (a.strategy() != Strategy.MICRO_ANOMALY && a.strategy() != Strategy.AMBIENT_SOUND || verbose)
                    log.add(String.format("dzień %2d %02d:00  L%d  %-19s @%-6s %s", day + 1, hour, a.level(),
                            a.strategy(), a.targetPlace(), msg.isEmpty() ? "" : "\"" + msg + "\""));
            }
        }
        return new Result(hist, dir.level(), profile, memory, log);
    }

    static void report(String name, Result r, int logLines) {
        System.out.println("\n===== " + name + " =====");
        System.out.println("poziom końcowy: " + r.finalLevel());
        System.out.println("profil: " + r.profile());
        System.out.println("fakty:  " + r.mem().all().stream().filter(f -> f.strength > 0.15)
                .sorted((x, y) -> Double.compare(y.strength, x.strength)).limit(6).toList());
        System.out.println("strategie: " + r.hist());
        System.out.println("-- fragment dziennika --");
        r.log().stream().skip(Math.max(0, r.log().size() - logLines)).forEach(System.out::println);
    }

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("FAIL: " + msg);
        System.out.println("OK   " + msg);
    }

    public static void main(String[] args) {
        int days = 18;
        Result reader = run("czytelnik", true, false, days, 1, false);
        Result ignorer = run("ignorant", false, false, days, 1, false);
        Result hunter = run("łowca", true, true, days, 1, false);
        report("Gracz CZYTA tabliczki", reader, 12);
        report("Gracz IGNORUJE tabliczki", ignorer, 8);
        report("Gracz POLUJE na Herobrine'a", hunter, 8);

        System.out.println("\n===== asercje =====");
        int sr = reader.hist().getOrDefault(Strategy.SIGN_MESSAGE, 0);
        int si = ignorer.hist().getOrDefault(Strategy.SIGN_MESSAGE, 0);
        check(si < sr, "ignorant dostaje mniej tabliczek (" + si + ") niż czytelnik (" + sr + ")");
        int dn = reader.hist().getOrDefault(Strategy.DO_NOTHING, 0);
        int total = reader.hist().values().stream().mapToInt(i -> i).sum();
        check(dn > total * 0.10, "cisza to realna strategia: DO_NOTHING = " + dn + "/" + total);
        int dirR = reader.hist().getOrDefault(Strategy.DIRECT_APPEARANCE, 0);
        check(dirR <= 4, "bezpośrednie pojawienia są rzadkie (" + dirR + ")");
        int appH = hunter.hist().getOrDefault(Strategy.PERIPHERAL_SIGHTING, 0) + hunter.hist().getOrDefault(Strategy.STALK, 0);
        int appR = reader.hist().getOrDefault(Strategy.PERIPHERAL_SIGHTING, 0) + reader.hist().getOrDefault(Strategy.STALK, 0);
        check(appH <= appR, "łowca widzi Herobrine'a rzadziej (" + appH + ") niż zwykły gracz (" + appR + ")");
        check(reader.finalLevel() <= 6 && reader.finalLevel() >= 1, "eskalacja mieści się w 1..6: " + reader.finalLevel());
    }
}
