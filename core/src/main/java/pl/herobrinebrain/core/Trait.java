package pl.herobrinebrain.core;

/**
 * Wymiary profilu gracza (0..1). Każdy wymiar dryfuje z powrotem do wartości bazowej
 * (okres półtrwania w minutach czasu gry), więc profil jest "żywy" - strach opada,
 * ale świadomość Herobrine'a (AWARENESS) zapomina się bardzo wolno.
 */
public enum Trait {
    FEAR(0.10, 20),
    CURIOSITY(0.50, 60),
    AGGRESSION(0.30, 30),
    PARANOIA(0.05, 120),
    CONFIDENCE(0.50, 90),
    EXPLORATION(0.40, 60),
    ISOLATION(0.30, 45),
    AWARENESS(0.00, 600),   // ile gracz "wie", że coś go obserwuje
    COMFORT(0.50, 30);      // poczucie bezpieczeństwa - cel numer 1 do podważania

    public final double baseline;
    public final double halfLifeMinutes;

    Trait(double baseline, double halfLifeMinutes) {
        this.baseline = baseline;
        this.halfLifeMinutes = halfLifeMinutes;
    }
}
