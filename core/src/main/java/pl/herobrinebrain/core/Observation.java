package pl.herobrinebrain.core;

/** Pojedyncza obserwacja. 'place' to nieprzezroczysty klucz (np. "cx,cz" chunka albo "base"). */
public record Observation(ObsType type, long tick, String place, double magnitude) {
    public static Observation of(ObsType t, long tick, String place, double mag) {
        return new Observation(t, tick, place == null ? "" : place, mag);
    }
}
