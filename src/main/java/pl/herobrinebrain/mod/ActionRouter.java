package pl.herobrinebrain.mod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import pl.herobrinebrain.core.DirectorAction;
import pl.herobrinebrain.core.Strategy;

/**
 * Router akcji Directora -> efekty w świecie.
 *  - MICRO_ANOMALY: przesunięta pochodnia + pomiar zauważenia (AnomalyLedger) => UCZY Directora.
 *  - AMBIENT_SOUND: dźwięk słyszany tylko przez gracza (discard: brak pomiaru reakcji).
 *  - reszta: dry-run (wpis do /hb debug) i discard() - bez efektu nie ma czego uczyć.
 */
final class ActionRouter {
    private ActionRouter() { }

    static void execute(ServerPlayer p, PlayerBrain brain, DirectorAction a, AnomalyLedger ledger) {
        String line = String.format("t=%d L%d %s phase=%s T=%.2f target=%s%s",
                a.tick(), a.level(), a.strategy(), a.phase(), a.tensionAfter(), a.targetPlace(),
                a.referenceKind() == null ? "" : " ref=" + a.referenceKind());
        boolean learnsFromOutcome = false;

        switch (a.strategy()) {
            case MICRO_ANOMALY -> {
                boolean done = TorchDrift.run(p, a, ledger);
                line += done ? "  [EXEC torch drift]" : "  [skip: brak pochodni]";
                learnsFromOutcome = done;      // wynik zgłosi AnomalyLedger (zauważone) lub wygaśnięcie w Directorze
            }
            case AMBIENT_SOUND -> {
                p.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.55f, 0.7f);
                line += "  [EXEC]";
            }
            default -> line += a.visible() ? "  [dry-run]" : "  [silent]";
        }
        if (a.strategy() != Strategy.DO_NOTHING) brain.log(line);
        if (a.visible() && !learnsFromOutcome) brain.director.discard(a.id());
    }
}
