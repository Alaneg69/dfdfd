package pl.herobrinebrain.core;

/** Fakt z pamięci długoterminowej. strength 0..1 rośnie z powtórzeniami (malejące przyrosty) i zanika w czasie. */
public final class Fact {
    public final FactKind kind;
    public final String place;
    public double strength;
    public long lastTick;
    public int count;

    Fact(FactKind kind, String place) { this.kind = kind; this.place = place; }

    public String key() { return kind + ":" + place; }

    @Override public String toString() {
        return String.format("%s@%s s=%.2f n=%d", kind, place, strength, count);
    }
}
