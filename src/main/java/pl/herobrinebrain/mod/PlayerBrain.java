package pl.herobrinebrain.mod;

import pl.herobrinebrain.core.HorrorDirector;
import pl.herobrinebrain.core.MemoryStore;
import pl.herobrinebrain.core.PlayerProfile;

import java.util.ArrayDeque;
import java.util.UUID;

/** Mózg jednego gracza + stan czysto-adapterowy (nie zapisywany). */
public final class PlayerBrain {
    public final HorrorDirector director;
    final ArrayDeque<String> actionLog = new ArrayDeque<>();
    boolean wasSleeping;
    long lastSaveTick;

    PlayerBrain(UUID id) {
        // Seed z UUID: zachowanie powtarzalne dla gracza w obrębie sesji, różne między graczami.
        this.director = new HorrorDirector(new PlayerProfile(), new MemoryStore(),
                id.getMostSignificantBits() ^ id.getLeastSignificantBits());
    }

    void log(String line) {
        actionLog.addLast(line);
        while (actionLog.size() > 15) actionLog.removeFirst();
    }
}
