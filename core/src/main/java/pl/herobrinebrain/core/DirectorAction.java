package pl.herobrinebrain.core;

/** Decyzja Directora przekazana adapterowi do wykonania. */
public record DirectorAction(int id, long tick, Strategy strategy, int level,
                             String targetPlace, FactKind referenceKind,
                             MessageIntent intent, double tensionAfter,
                             HorrorDirector.Phase phase) {
    public boolean visible() { return strategy.visible(); }
}
