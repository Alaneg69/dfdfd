package pl.herobrinebrain.mod;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import pl.herobrinebrain.core.BrainState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cykl życia mózgów graczy: leniwe wczytywanie, zapis przy wylogowaniu / zatrzymaniu / co 5 min. */
public final class BrainManager {
    private static final Gson GSON = new Gson();
    private final Map<UUID, PlayerBrain> brains = new HashMap<>();
    private final AnomalyLedger ledger = new AnomalyLedger();

    public AnomalyLedger ledger() { return ledger; }

    public PlayerBrain get(ServerPlayer p) {
        return brains.computeIfAbsent(p.getUUID(), id -> load(p.getServer(), id));
    }

    private PlayerBrain load(MinecraftServer server, UUID id) {
        PlayerBrain b = new PlayerBrain(id);
        if (server == null) return b;
        String raw = BrainSavedData.get(server.overworld()).get(id.toString());
        if (raw != null) {
            try {
                BrainState s = GSON.fromJson(raw, BrainState.class);
                if (s != null) b.director.restore(s);
            } catch (JsonParseException e) {
                // uszkodzony zapis: zaczynamy od zera zamiast wywalać grę
            }
        }
        return b;
    }

    public void save(MinecraftServer server, UUID id) {
        PlayerBrain b = brains.get(id);
        if (b == null || server == null) return;
        BrainSavedData.get(server.overworld()).put(id.toString(), GSON.toJson(b.director.snapshot()));
    }

    public void saveAll(MinecraftServer server) {
        for (UUID id : brains.keySet()) save(server, id);
    }

    public void unload(MinecraftServer server, UUID id) {
        if (server != null) ledger.revertAll(server, id);
        save(server, id);
        brains.remove(id);
    }

    /** Pełny reset (komenda /hb reset). */
    public void reset(MinecraftServer server, UUID id) {
        brains.remove(id);
        if (server != null) ledger.revertAll(server, id);
        if (server != null) BrainSavedData.get(server.overworld()).remove(id.toString());
    }

    public void clear() { brains.clear(); }
}
