package pl.herobrinebrain.core;

import java.util.*;

/**
 * Horror Director = Utility AI + model tempa (BUILD_UP / PEAK / RELAX) + uczenie per gracz (Thompson sampling).
 *
 * Każda decyzja: filtr dozwolonych strategii (poziom obecności, cooldown, sytuacja, bezpieczniki frustracji)
 * -> ocena użyteczności -> losowanie softmax (nie argmax, żeby uniknąć przewidywalności).
 *
 *   score = fit * (0.35*pace + 0.20*novelty + 0.30*learned + 0.15*deniability)
 *
 * fit        - dopasowanie do profilu/pamięci (np. gracz ignoruje tabliczki => SIGN_MESSAGE*0.3),
 * pace       - zgodność kosztu strategii z fazą tempa,
 * novelty    - kara za powtarzanie ostatnich strategii,
 * learned    - wylosowana skuteczność strategii dla TEGO gracza (bandyta Beta),
 * deniability- im mniej gracz wie, tym bardziej premiowana niejednoznaczność (i odwrotnie).
 */
public final class HorrorDirector {
    public enum Phase { BUILD_UP, PEAK, RELAX }

    private static final int RECENT_CAP = 6;
    private static final long PENDING_TTL = 6000;       // 5 min na zauważenie zdarzenia
    private static final long REF_COOLDOWN = 36000;     // ten sam fakt nie częściej niż co 30 min
    private static final double SOFTMAX_TEMP = 0.12;

    private final PlayerProfile profile;
    private final MemoryStore memory;
    private final Random rng;

    private final EnumMap<Strategy, double[]> beta = new EnumMap<>(Strategy.class);
    private final EnumMap<Strategy, Long> lastUsed = new EnumMap<>(Strategy.class);
    private final ArrayDeque<Strategy> recent = new ArrayDeque<>();
    private final Map<Integer, Pending> pending = new LinkedHashMap<>();
    private final Map<String, Long> lastReferenced = new HashMap<>();

    private double tension;
    private double presence = 0.5;       // ciągła "obecność" 0..6 -> poziom
    private long playTicks;
    private long lastTick = -1;
    private long nextDecision;
    private long phaseEnd;
    private Phase phase = Phase.BUILD_UP;
    private int nextId = 1;

    private record Pending(int id, Strategy strategy, long tick) { }

    public HorrorDirector(PlayerProfile profile, MemoryStore memory, long seed) {
        this.profile = profile;
        this.memory = memory;
        this.rng = new Random(seed);
        for (Strategy s : Strategy.values()) beta.put(s, new double[]{1, 1});
    }

    /* ------------------------------------------------------------------ API */

    public void observe(Observation o) {
        profile.observe(o);
        memory.record(o);
    }

    /** Wołać cyklicznie (np. co 20-100 ticków). Zwraca decyzję tylko gdy nadszedł czas na decyzję. */
    public Optional<DirectorAction> tick(Situation s) {
        advanceClock(s);
        if (s.tick() < nextDecision) return Optional.empty();
        nextDecision = s.tick() + decisionInterval();
        return Optional.of(commit(choose(s), s));
    }

    /** Wymusza decyzję poza harmonogramem (np. zdarzenie snu). Nadal działają wszystkie filtry. */
    public Optional<DirectorAction> decideNow(Situation s) {
        advanceClock(s);
        return Optional.of(commit(choose(s), s));
    }

    /** Adapter zgłasza, czy gracz zauważył skutek akcji i jak zareagował (-1 irytacja .. +1 silna reakcja). */
    public void reportOutcome(int actionId, boolean noticed, double reaction) {
        Pending p = pending.remove(actionId);
        if (p == null) return;
        // Niezauważone, ale subtelne = częściowy sukces. Niezauważone i głośne = porażka.
        double r = noticed ? clamp01(0.5 + 0.5 * reaction) : (p.strategy.deniability >= 0.7 ? 0.35 : 0.10);
        double[] ab = beta.get(p.strategy);
        ab[0] += r;
        ab[1] += 1 - r;
        if (ab[0] + ab[1] > 40) { ab[0] *= 0.9; ab[1] *= 0.9; }   // zapominanie => adaptacja do zmian gracza
        if (noticed) presence = Math.min(6, presence + 0.12);
    }

    public int level() { return (int) Math.min(Math.min(6.0, presence), levelCap()); }
    public double tension() { return tension; }
    public Phase phase() { return phase; }
    public double presence() { return presence; }
    public PlayerProfile profile() { return profile; }
    public MemoryStore memory() { return memory; }

    /** Poziom ograniczony czasem gry: min. ~50 min realnego czasu na poziom, niezależnie od zachowania gracza. */
    private double levelCap() { return 1 + (playTicks / 72000.0) * 1.2; }

    /* ------------------------------------------------------------ internals */

    private void advanceClock(Situation s) {
        long now = s.tick();
        if (lastTick < 0) {
            lastTick = now;
            nextDecision = now + 6000;                 // 5 min łaski na starcie
            phaseEnd = now + range(3600, 9600);
        }
        long dt = Math.max(0, now - lastTick);
        lastTick = now;
        playTicks += Math.min(dt, 200);                // ochrona przed AFK / skokami czasu
        profile.decay(now);
        memory.decay(now);
        tension *= Math.pow(0.5, dt / 1800.0);         // półtrwanie napięcia ~90 s
        expirePending(now);
        updatePhase(now);
    }

    private void updatePhase(long now) {
        if (now < phaseEnd) return;
        switch (phase) {
            case BUILD_UP -> {
                if (tension > 0.4) { phase = Phase.PEAK; phaseEnd = now + range(600, 1800); }
                else phaseEnd = now + range(2400, 6000);     // za mało napięcia - budujemy dalej
            }
            case PEAK -> { phase = Phase.RELAX; phaseEnd = now + range(2400, 6000); }
            case RELAX -> { phase = Phase.BUILD_UP; phaseEnd = now + range(3600, 9600); }
        }
    }

    private long decisionInterval() {
        return switch (phase) {
            case BUILD_UP -> range(800, 2400);
            case PEAK -> range(300, 900);
            case RELAX -> range(2400, 4800);
        };
    }

    private void expirePending(long now) {
        List<Integer> expired = new ArrayList<>();
        for (Pending p : pending.values()) if (now - p.tick > PENDING_TTL) expired.add(p.id);
        for (int id : expired) reportOutcome(id, false, 0);
    }

    private Strategy choose(Situation s) {
        int lvl = level();
        List<Strategy> cands = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (Strategy st : Strategy.values()) {
            if (!allowed(st, s, lvl)) continue;
            cands.add(st);
            scores.add(score(st, s));
        }
        if (cands.isEmpty()) return Strategy.DO_NOTHING;
        double max = Collections.max(scores);
        double[] w = new double[cands.size()];
        double sum = 0;
        for (int i = 0; i < w.length; i++) { w[i] = Math.exp((scores.get(i) - max) / SOFTMAX_TEMP); sum += w[i]; }
        double r = rng.nextDouble() * sum;
        for (int i = 0; i < w.length; i++) { r -= w[i]; if (r <= 0) return cands.get(i); }
        return cands.get(cands.size() - 1);
    }

    /** Twarde filtry: poziom, cooldown, kontekst i bezpieczniki "anty-frustracja". */
    private boolean allowed(Strategy st, Situation s, int lvl) {
        if (lvl < st.minLevel) return false;
        Long lu = lastUsed.get(st);
        if (lu != null && s.tick() - lu < st.cooldownTicks) return false;

        boolean safeOnly = s.inCombat() || s.healthFraction() < 0.3;
        if (safeOnly) return st == Strategy.DO_NOTHING || st == Strategy.OBSERVE_SILENTLY || st == Strategy.CALM_PERIOD;
        if (s.sleeping()) return st == Strategy.DREAM_INTERVENTION || st == Strategy.DO_NOTHING;

        return switch (st) {
            case DREAM_INTERVENTION -> false;
            case PERIPHERAL_SIGHTING, STALK, DIRECT_APPEARANCE -> s.dark() || s.underground() || s.night();
            case SIGN_MESSAGE -> !referenceCandidates().isEmpty();
            case STRUCTURE_ALTER, REALITY_DRIFT -> s.nearBase() || memory.strongest(FactKind.HOTSPOT).isPresent();
            case ISOLATE -> s.underground() || s.night();
            case CALM_PERIOD -> tension > 0.2;
            default -> true;
        };
    }

    private double score(Strategy st, Situation s) {
        double aw = profile.get(Trait.AWARENESS);
        double fit = fit(st, s);
        double pace = pace(st);
        double cnt = 0;
        for (Strategy r : recent) if (r == st) cnt++;
        double novelty = 1 - 0.7 * cnt / Math.max(1, recent.size());
        double[] ab = beta.get(st);
        double learned = sampleBeta(ab[0], ab[1]);
        double deny = st.deniability * (1 - aw) + (1 - st.deniability) * aw;
        return fit * (0.35 * pace + 0.20 * novelty + 0.30 * learned + 0.15 * deny);
    }

    private double fit(Strategy st, Situation s) {
        double aw = p(Trait.AWARENESS), fear = p(Trait.FEAR), comfort = p(Trait.COMFORT),
               par = p(Trait.PARANOIA), conf = p(Trait.CONFIDENCE), cur = p(Trait.CURIOSITY),
               iso = p(Trait.ISOLATION), expl = p(Trait.EXPLORATION);
        double hot = memory.strengthOf(FactKind.HOTSPOT);
        boolean seeking = memory.strengthOf(FactKind.SEEKS_HEROBRINE) > 0.5;   // gracz poluje => wycofujemy się
        double v = switch (st) {
            case DO_NOTHING -> 0.30 + 0.40 * tension + (phase == Phase.RELAX ? 0.20 : 0) + (fear > 0.8 ? 0.15 : 0);
            case OBSERVE_SILENTLY -> 0.35 + 0.30 * (1 - Math.min(1, presence / 3));
            case CALM_PERIOD -> 0.30 + 0.50 * tension + (fear > 0.7 ? 0.20 : 0);
            case MICRO_ANOMALY -> 0.35 + 0.35 * comfort + (s.nearBase() ? 0.20 : 0) + (comfort > 0.7 ? 0.10 : 0);
            case AMBIENT_SOUND -> s.underground() ? 0.40 + 0.40 * fear : 0.30 + 0.20 * iso;
            case FALSE_TRAIL -> 0.20 + 0.50 * cur + 0.20 * expl + (seeking ? 0.25 : 0);
            case PERIPHERAL_SIGHTING -> (0.30 + 0.40 * (1 - conf) + (s.dark() ? 0.20 : 0)) * (seeking ? 0.4 : 1);
            case SIGN_MESSAGE -> (0.20 + 0.60 * bestReferenceStrength())
                                 * (memory.strengthOf(FactKind.IGNORES_MESSAGES) > 0.5 ? 0.3 : 1);
            case DREAM_INTERVENTION -> 0.40 + 0.50 * profile.sleepRegularity() + 0.10 * comfort;
            case STALK -> (0.30 + 0.40 * aw + 0.20 * iso) * (seeking ? 0.4 : 1);
            case STRUCTURE_ALTER -> 0.20 + 0.60 * hot;
            case ISOLATE -> 0.20 + 0.40 * fear + 0.30 * iso;
            case DIRECT_APPEARANCE -> (0.15 + 0.50 * aw) * (fear > 0.85 ? 0.3 : 1) * (seeking ? 0.4 : 1);
            case REALITY_DRIFT -> 0.20 + 0.60 * par + 0.20 * aw;
        };
        return clamp01(v);
    }

    private double pace(Strategy st) {
        double c = st.tensionCost;
        return switch (phase) {
            case BUILD_UP -> clamp01(1 - Math.abs(c - 0.2) * 2.5);
            case PEAK -> clamp01(0.2 + c * 1.6);
            case RELAX -> c <= 0.1 ? 1.0 : 0.1;
        };
    }

    private DirectorAction commit(Strategy st, Situation s) {
        long now = s.tick();
        int lvl = level();
        tension = clamp01(tension + st.tensionCost);
        presence = Math.min(6, presence + 0.015);       // samo "studiowanie" gracza podnosi obecność
        if (st != Strategy.DO_NOTHING) {
            lastUsed.put(st, now);
            recent.addLast(st);
            while (recent.size() > RECENT_CAP) recent.removeFirst();
        }

        Fact ref = null;
        MessageIntent intent = null;
        if (st == Strategy.SIGN_MESSAGE || st == Strategy.DREAM_INTERVENTION) {
            ref = pickReferenceFact().orElse(null);
            if (ref != null) lastReferenced.put(ref.key(), now);
            intent = new MessageIntent(
                    st == Strategy.SIGN_MESSAGE ? MessageIntent.Channel.SIGN : MessageIntent.Channel.DREAM_VOICE,
                    ref == null ? null : ref.kind, ref == null ? "" : ref.place, lvl,
                    st == Strategy.SIGN_MESSAGE ? 60 : 80);
        }
        String target = ref != null ? ref.place
                : memory.strongest(FactKind.HOTSPOT).map(f -> f.place).orElse("");

        int id = nextId++;
        if (st.visible()) pending.put(id, new Pending(id, st, now));
        return new DirectorAction(id, now, st, lvl, target, ref == null ? null : ref.kind, intent, tension, phase);
    }

    /* ---------------------------------------------------- reference facts */

    private static final EnumSet<FactKind> REFERENCEABLE = EnumSet.of(
            FactKind.HOTSPOT, FactKind.FEARED_PLACE, FactKind.SLEEP_ROUTINE, FactKind.FLED_FROM_SIGHT,
            FactKind.DESTROYED_ARTIFACT, FactKind.SEEKS_HEROBRINE, FactKind.DEATH_SPOT);

    private double refWeight(Fact f, long now) {
        Long lr = lastReferenced.get(f.key());
        double cooldownFactor = (lr != null && now - lr < REF_COOLDOWN) ? 0.2 : 1.0;
        return f.strength * cooldownFactor;
    }

    private List<Fact> referenceCandidates() {
        List<Fact> out = new ArrayList<>();
        for (Fact f : memory.all()) if (REFERENCEABLE.contains(f.kind) && f.strength >= 0.25) out.add(f);
        return out;
    }

    private double bestReferenceStrength() {
        double best = 0;
        for (Fact f : referenceCandidates()) best = Math.max(best, refWeight(f, lastTick));
        return best;
    }

    private Optional<Fact> pickReferenceFact() {
        List<Fact> c = referenceCandidates();
        if (c.isEmpty()) return Optional.empty();
        double sum = 0;
        for (Fact f : c) sum += refWeight(f, lastTick);
        double r = rng.nextDouble() * sum;
        for (Fact f : c) { r -= refWeight(f, lastTick); if (r <= 0) return Optional.of(f); }
        return Optional.of(c.get(c.size() - 1));
    }

    /* -------------------------------------------------------------- utils */

    private double p(Trait t) { return profile.get(t); }
    private long range(int lo, int hi) { return lo + rng.nextInt(hi - lo + 1); }
    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    /** Aproksymacja próbkowania Thompsona z rozkładu Beta(a,b) (średnia + szum wg wariancji). */
    private double sampleBeta(double a, double b) {
        double m = a / (a + b);
        double sd = Math.sqrt(a * b / ((a + b) * (a + b) * (a + b + 1)));
        return clamp01(m + sd * rng.nextGaussian());
    }

    @Override public String toString() {
        return String.format("Director[lvl=%d presence=%.2f tension=%.2f phase=%s]", level(), presence, tension, phase);
    }

    /* ---- serializacja i kontrola ---- */

    /** Porzuca oczekujący wynik BEZ uczenia (np. akcja nie miała jeszcze efektu w świecie). */
    public void discard(int actionId) { pending.remove(actionId); }

    public BrainState snapshot() {
        BrainState s = new BrainState();
        profile.exportTo(s);
        memory.exportTo(s);
        s.tension = tension; s.presence = presence; s.playTicks = playTicks;
        for (Map.Entry<Strategy, double[]> e : beta.entrySet()) s.beta.put(e.getKey().name(), e.getValue().clone());
        return s;
    }

    public void restore(BrainState s) {
        profile.restoreFrom(s);
        memory.restoreFrom(s);
        tension = s.tension; presence = s.presence; playTicks = s.playTicks;
        for (Map.Entry<String, double[]> e : s.beta.entrySet()) {
            try {
                double[] ab = e.getValue();
                if (ab != null && ab.length == 2 && ab[0] > 0 && ab[1] > 0) beta.put(Strategy.valueOf(e.getKey()), ab.clone());
            } catch (IllegalArgumentException ignored) { }
        }
        lastTick = -1;           // ponowna inicjalizacja zegara (+ okres łaski po wczytaniu)
        pending.clear();
    }
}
