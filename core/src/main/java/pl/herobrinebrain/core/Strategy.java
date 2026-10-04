package pl.herobrinebrain.core;

/**
 * Katalog strategii Directora.
 * tensionCost  - ile napięcia dokłada (ujemne = rozładowuje),
 * deniability  - jak bardzo gracz może uznać zdarzenie za przypadek/błąd świata (1 = idealnie niejednoznaczne),
 * minLevel     - minimalny poziom obecności, od którego strategia jest dostępna,
 * cooldownTicks- minimalny odstęp między użyciami tej samej strategii.
 */
public enum Strategy {
    DO_NOTHING(0.00, 1.00, 0, 0),
    OBSERVE_SILENTLY(0.00, 1.00, 0, 1200),
    CALM_PERIOD(-0.20, 1.00, 0, 6000),
    MICRO_ANOMALY(0.08, 0.95, 1, 2400),
    AMBIENT_SOUND(0.10, 0.85, 1, 3600),
    FALSE_TRAIL(0.12, 0.80, 2, 6000),
    PERIPHERAL_SIGHTING(0.25, 0.55, 2, 12000),
    SIGN_MESSAGE(0.22, 0.35, 3, 9000),
    DREAM_INTERVENTION(0.30, 0.50, 3, 24000),
    STALK(0.30, 0.40, 4, 12000),
    STRUCTURE_ALTER(0.35, 0.50, 4, 18000),
    ISOLATE(0.35, 0.40, 5, 24000),
    DIRECT_APPEARANCE(0.60, 0.00, 5, 48000),
    REALITY_DRIFT(0.50, 0.70, 6, 24000);

    public final double tensionCost, deniability;
    public final int minLevel;
    public final long cooldownTicks;

    Strategy(double cost, double deniability, int minLevel, long cooldownTicks) {
        this.tensionCost = cost; this.deniability = deniability;
        this.minLevel = minLevel; this.cooldownTicks = cooldownTicks;
    }

    /** Czy strategia ma efekt widoczny w świecie (i wymaga raportu "czy zauważone"). */
    public boolean visible() {
        return this != DO_NOTHING && this != OBSERVE_SILENTLY && this != CALM_PERIOD;
    }
}
