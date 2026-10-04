package pl.herobrinebrain.core;

import java.util.*;

/** Round-trip stanu: snapshot -> restore -> snapshot musi dać te same dane. */
public final class SnapshotTest {
    public static void main(String[] args) {
        PlayerProfile p1 = new PlayerProfile();
        MemoryStore m1 = new MemoryStore();
        HorrorDirector d1 = new HorrorDirector(p1, m1, 5);
        for (int i = 0; i < 400; i++) d1.observe(Observation.of(ObsType.DWELL, i * 100L, i % 3 == 0 ? "cave" : "base", 5));
        for (int h : new int[]{22, 22, 23, 22}) d1.observe(Observation.of(ObsType.SLEEP, 1000, "base", h));
        d1.observe(Observation.of(ObsType.SAW_ANOMALY, 2000, "base", 0.5));
        for (long t = 0; t < 200000; t += 100) d1.tick(new Situation(t, 12, false, false, false, false, true, false, 1));

        BrainState a = d1.snapshot();
        PlayerProfile p2 = new PlayerProfile();
        MemoryStore m2 = new MemoryStore();
        HorrorDirector d2 = new HorrorDirector(p2, m2, 99);
        d2.restore(a);
        BrainState b = d2.snapshot();

        Sim.check(Arrays.equals(a.traits, b.traits), "cechy profilu identyczne po restore");
        Sim.check(Arrays.equals(a.sleepHist, b.sleepHist), "histogram snu identyczny");
        Sim.check(a.presence == b.presence && a.playTicks == b.playTicks && a.tension == b.tension, "presence/tension/playTicks identyczne");
        Sim.check(a.facts.size() == b.facts.size() && a.facts.size() > 0, "fakty zachowane (" + a.facts.size() + ")");
        boolean betaOk = true;
        for (var e : a.beta.entrySet()) betaOk &= Arrays.equals(e.getValue(), b.beta.get(e.getKey()));
        Sim.check(betaOk, "parametry bandyty zachowane");
        Sim.check(Math.abs(p1.sleepRegularity() - p2.sleepRegularity()) < 1e-9, "regularność snu zachowana");

        // discard nie uczy: po discard stan bandyty bez zmian
        Optional<DirectorAction> act = d2.decideNow(new Situation(300000, 12, false, false, true, true, true, false, 1));
        BrainState before = d2.snapshot();
        act.ifPresent(x -> d2.discard(x.id()));
        BrainState after = d2.snapshot();
        boolean same = true;
        for (var e : before.beta.entrySet()) same &= Arrays.equals(e.getValue(), after.beta.get(e.getKey()));
        Sim.check(same, "discard() nie zmienia parametrów uczenia");
    }
}
