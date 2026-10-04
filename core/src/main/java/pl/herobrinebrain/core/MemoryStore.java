package pl.herobrinebrain.core;

import java.util.*;

/**
 * Pamięć dwupoziomowa:
 *  - krótkoterminowa: ring buffer ostatnich obserwacji (kontekst "co się właśnie stało"),
 *  - długoterminowa: fakty o graczu wyprowadzone z obserwacji, wzmacniane i zanikające.
 */
public final class MemoryStore {
    private static final int SHORT_CAP = 64;
    private static final double HALF_LIFE_MIN = 600;   // ~10h gry
    private static final double PRUNE_BELOW = 0.03;

    private final ArrayDeque<Observation> shortTerm = new ArrayDeque<>();
    private final Map<String, Fact> longTerm = new HashMap<>();
    private long lastDecayTick = -1;

    public void record(Observation o) {
        shortTerm.addLast(o);
        while (shortTerm.size() > SHORT_CAP) shortTerm.removeFirst();
        long t = o.tick();
        switch (o.type()) {
            case DWELL -> reinforce(FactKind.HOTSPOT, o.place(), Math.min(0.08, o.magnitude() / 600.0), t);
            case DEATH -> { reinforce(FactKind.DEATH_SPOT, o.place(), 0.5, t); reinforce(FactKind.FEARED_PLACE, o.place(), 0.3, t); }
            case FLED_FROM_SIGHT -> { reinforce(FactKind.FLED_FROM_SIGHT, o.place(), 0.4, t); reinforce(FactKind.FEARED_PLACE, o.place(), 0.35, t); }
            case SLEEP -> reinforce(FactKind.SLEEP_ROUTINE, "h" + (int) o.magnitude(), 0.2, t);
            case IGNORED_MESSAGE -> reinforce(FactKind.IGNORES_MESSAGES, "", 0.25, t);
            case READ_MESSAGE -> weaken(FactKind.IGNORES_MESSAGES, "", 0.3);
            case DESTROYED_ARTIFACT -> reinforce(FactKind.DESTROYED_ARTIFACT, o.place(), 0.5, t);
            case SEARCHED_FOR_HEROBRINE -> reinforce(FactKind.SEEKS_HEROBRINE, "", 0.35, t);
            default -> { }
        }
    }

    private void reinforce(FactKind k, String place, double amount, long tick) {
        Fact f = longTerm.computeIfAbsent(k + ":" + place, x -> new Fact(k, place));
        f.strength += (1 - f.strength) * Math.min(1, amount);
        f.count++;
        f.lastTick = tick;
    }

    private void weaken(FactKind k, String place, double amount) {
        Fact f = longTerm.get(k + ":" + place);
        if (f != null) f.strength = Math.max(0, f.strength - amount);
    }

    public void decay(long nowTick) {
        if (lastDecayTick < 0) { lastDecayTick = nowTick; return; }
        double dtMin = (nowTick - lastDecayTick) / 1200.0;
        lastDecayTick = nowTick;
        if (dtMin <= 0) return;
        double k = Math.pow(0.5, dtMin / HALF_LIFE_MIN);
        long decayNow = nowTick;
        for (Iterator<Fact> it = longTerm.values().iterator(); it.hasNext(); ) {
            Fact f = it.next();
            f.strength *= k;
            // nie usuwamy faktów świeżo wzmacnianych (inaczej powolne, powtarzalne sygnały nigdy by się nie zsumowały)
            if (f.strength < PRUNE_BELOW && decayNow - f.lastTick > 24000) it.remove();
        }
    }

    /** Siła najsilniejszego faktu danego rodzaju (0 jeśli brak). */
    public double strengthOf(FactKind kind) {
        return strongest(kind).map(f -> f.strength).orElse(0.0);
    }

    public Optional<Fact> strongest(FactKind kind) {
        return longTerm.values().stream().filter(f -> f.kind == kind).max(Comparator.comparingDouble(f -> f.strength));
    }

    public Collection<Fact> all() { return Collections.unmodifiableCollection(longTerm.values()); }

    public int countRecent(ObsType type, long sinceTick) {
        int n = 0;
        for (Observation o : shortTerm) if (o.type() == type && o.tick() >= sinceTick) n++;
        return n;
    }

    /* ---- serializacja (pamięć krótkoterminowa jest celowo ulotna) ---- */
    void exportTo(BrainState s) {
        s.facts = new ArrayList<>();
        for (Fact f : longTerm.values()) {
            BrainState.FactState fs = new BrainState.FactState();
            fs.kind = f.kind.name(); fs.place = f.place; fs.strength = f.strength;
            fs.lastTick = f.lastTick; fs.count = f.count;
            s.facts.add(fs);
        }
    }

    void restoreFrom(BrainState s) {
        longTerm.clear();
        shortTerm.clear();
        lastDecayTick = -1;
        if (s.facts == null) return;
        for (BrainState.FactState fs : s.facts) {
            try {
                Fact f = new Fact(FactKind.valueOf(fs.kind), fs.place == null ? "" : fs.place);
                f.strength = fs.strength; f.lastTick = fs.lastTick; f.count = fs.count;
                longTerm.put(f.key(), f);
            } catch (IllegalArgumentException ignored) { /* fakt z nowszej/starszej wersji - pomijamy */ }
        }
    }
}
