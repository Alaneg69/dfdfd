package pl.herobrinebrain.core;

/** Migawka sytuacji gracza dostarczana przez adapter (bez żadnych typów Minecrafta). */
public record Situation(long tick, int hourOfDay, boolean sleeping, boolean underground,
                        boolean dark, boolean night, boolean nearBase, boolean inCombat,
                        double healthFraction) { }
