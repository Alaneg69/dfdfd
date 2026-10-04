package pl.herobrinebrain.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Ewoluujący model psychologiczny konkretnego gracza. */
public final class PlayerProfile {
    private final double[] v = new double[Trait.values().length];
    private final double[] sleepHist = new double[24];
    private final Map<String, Double> dwell = new HashMap<>();
    private long lastDecayTick = -1;

    public PlayerProfile() {
        for (Trait t : Trait.values()) v[t.ordinal()] = t.baseline;
    }

    public double get(Trait t) { return v[t.ordinal()]; }

    public void nudge(Trait t, double delta) {
        double cur = v[t.ordinal()];
        // przyrosty maleją przy nasyceniu (nic nie "przykleja się" do 1.0); spadki liniowe
        double next = delta > 0 ? cur + delta * (1 - cur) : cur + delta;
        v[t.ordinal()] = Math.max(0, Math.min(1, next));
    }

    /** Dryf do wartości bazowych. Wołać okresowo. */
    public void decay(long nowTick) {
        if (lastDecayTick < 0) { lastDecayTick = nowTick; return; }
        double dtMin = (nowTick - lastDecayTick) / 1200.0;   // 20 tps * 60 s
        lastDecayTick = nowTick;
        if (dtMin <= 0) return;
        for (Trait t : Trait.values()) {
            double k = Math.pow(0.5, dtMin / t.halfLifeMinutes);
            v[t.ordinal()] = t.baseline + (v[t.ordinal()] - t.baseline) * k;
        }
    }

    /** Zamienia obserwację w zmianę profilu. Wartości są celowo małe - profil ma się budować godzinami. */
    public void observe(Observation o) {
        switch (o.type()) {
            case DWELL -> {
                dwell.merge(o.place(), o.magnitude(), Double::sum);
                if (o.place().startsWith("base")) nudge(Trait.COMFORT, Math.min(0.05, o.magnitude() / 600 * 0.05));
                else nudge(Trait.EXPLORATION, Math.min(0.03, o.magnitude() / 600 * 0.03));
            }
            case UNDERGROUND_TIME -> {
                nudge(Trait.ISOLATION, Math.min(0.04, o.magnitude() / 600 * 0.04));
                nudge(Trait.COMFORT, -Math.min(0.02, o.magnitude() / 600 * 0.02));
            }
            case SURFACE_TIME_NIGHT -> nudge(Trait.CONFIDENCE, Math.min(0.03, o.magnitude() / 600 * 0.03));
            case SLEEP -> { sleepHist[((int) o.magnitude()) % 24] += 1; nudge(Trait.COMFORT, 0.05); }
            case DEATH -> { nudge(Trait.FEAR, 0.15); nudge(Trait.CONFIDENCE, -0.15); }
            case COMBAT -> nudge(Trait.AGGRESSION, 0.02);
            case SAW_ANOMALY -> {
                nudge(Trait.AWARENESS, 0.04 + 0.04 * o.magnitude());
                nudge(Trait.PARANOIA, 0.05);
                nudge(Trait.FEAR, 0.03 + 0.05 * o.magnitude());
                nudge(Trait.COMFORT, -0.06);
            }
            case IGNORED_ANOMALY -> { nudge(Trait.PARANOIA, -0.01); nudge(Trait.AWARENESS, -0.005); }
            case FLED_FROM_SIGHT -> {
                nudge(Trait.FEAR, 0.20); nudge(Trait.CONFIDENCE, -0.10);
                nudge(Trait.AWARENESS, 0.10); nudge(Trait.COMFORT, -0.15);
            }
            case READ_MESSAGE -> {
                nudge(Trait.AWARENESS, 0.08); nudge(Trait.CURIOSITY, 0.03); nudge(Trait.FEAR, 0.03);
            }
            case IGNORED_MESSAGE -> { nudge(Trait.CONFIDENCE, 0.02); nudge(Trait.PARANOIA, -0.02); }
            case DESTROYED_ARTIFACT -> { nudge(Trait.AGGRESSION, 0.10); nudge(Trait.CONFIDENCE, 0.05); }
            case SEARCHED_FOR_HEROBRINE -> {
                nudge(Trait.CURIOSITY, 0.10); nudge(Trait.AWARENESS, 0.10); nudge(Trait.AGGRESSION, 0.03);
            }
        }
    }

    /**
     * Jak bardzo regularny jest sen gracza (0..1): udział snów w oknie ±1h wokół najczęstszej godziny,
     * znormalizowany względem losowego rozkładu (3/24). Poniżej 3 snów zwraca 0.
     */
    public double sleepRegularity() {
        double total = 0;
        for (double d : sleepHist) total += d;
        if (total < 3) return 0;
        double best = 0;
        for (int h = 0; h < 24; h++) {
            double w = sleepHist[(h + 23) % 24] + sleepHist[h] + sleepHist[(h + 1) % 24];
            best = Math.max(best, w);
        }
        double conc = best / total;
        return Math.max(0, Math.min(1, (conc - 3.0 / 24) / (1 - 3.0 / 24)));
    }

    public int typicalSleepHour() {
        int bh = 0;
        for (int h = 1; h < 24; h++) if (sleepHist[h] > sleepHist[bh]) bh = h;
        return bh;
    }

    public Optional<String> topHotspot() {
        return dwell.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Trait t : Trait.values()) sb.append(String.format("%s=%.2f ", t, get(t)));
        return sb.toString().trim();
    }

    /* ---- serializacja ---- */
    void exportTo(BrainState s) {
        s.traits = v.clone();
        s.sleepHist = sleepHist.clone();
        s.dwell = new HashMap<>(dwell);
    }

    void restoreFrom(BrainState s) {
        if (s.traits != null && s.traits.length == v.length) System.arraycopy(s.traits, 0, v, 0, v.length);
        if (s.sleepHist != null && s.sleepHist.length == sleepHist.length)
            System.arraycopy(s.sleepHist, 0, sleepHist, 0, sleepHist.length);
        dwell.clear();
        if (s.dwell != null) dwell.putAll(s.dwell);
        lastDecayTick = -1;
    }
}
