package pl.herobrinebrain.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import pl.herobrinebrain.core.ObsType;
import pl.herobrinebrain.core.Observation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Rejestr odwracalnych zmian świata. Daje trzy gwarancje:
 *  1) każda zmiana jest zapamiętana wraz ze stanem oryginalnym i da się ją cofnąć,
 *  2) wykrywamy, czy gracz ją zauważył (patrzył) i raportujemy to Directorowi (uczenie),
 *  3) zmiany są cofane po oknie czasowym, przy wylogowaniu i przy zatrzymaniu serwera.
 */
final class AnomalyLedger {
    /** Okno zauważenia < PENDING_TTL w Directorze (6000), żeby raport "zauważone" zdążył dotrzeć. */
    static final long NOTICE_WINDOW = 5400;
    static final long REVERT_GRACE = 1200;     // dodatkowy czas, jeśli gracz akurat patrzy w chwili cofania

    private static final class Entry {
        final int actionId; final UUID player; final ResourceKey<Level> dim;
        final BlockPos from, to; final BlockState state; final long created;
        boolean noticed;
        Entry(int actionId, UUID player, ResourceKey<Level> dim, BlockPos from, BlockPos to, BlockState state, long created) {
            this.actionId = actionId; this.player = player; this.dim = dim;
            this.from = from; this.to = to; this.state = state; this.created = created;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    void addMove(int actionId, ServerPlayer p, BlockPos from, BlockPos to, BlockState state) {
        entries.add(new Entry(actionId, p.getUUID(), p.level().dimension(), from, to, state, p.level().getGameTime()));
    }

    boolean isTouched(BlockPos pos) {
        for (Entry e : entries) if (e.from.equals(pos) || e.to.equals(pos)) return true;
        return false;
    }

    boolean hasActive(UUID player) {
        for (Entry e : entries) if (e.player.equals(player)) return true;
        return false;
    }

    /** Wołać często (co ~10 ticków), gdy gracz ma aktywne anomalie. */
    void tick(ServerPlayer p, PlayerBrain brain) {
        long now = p.level().getGameTime();
        MinecraftServer server = p.getServer();
        if (server == null) return;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (!e.player.equals(p.getUUID())) continue;
            ServerLevel lvl = server.getLevel(e.dim);
            if (lvl == null) { it.remove(); continue; }
            if (!lvl.isLoaded(e.to)) continue;                       // poczekaj, aż chunk się wczyta

            if (!lvl.getBlockState(e.to).is(e.state.getBlock())) {   // gracz sam to zmienił/zniszczył
                it.remove();
                continue;
            }
            if (!e.noticed && p.level().dimension() == e.dim && Sensors.looking(p, e.to, 24, 0.9)) {
                e.noticed = true;
                brain.director.observe(Observation.of(ObsType.SAW_ANOMALY, now, Sensors.placeKey(p), 0.3));
                brain.director.reportOutcome(e.actionId, true, 0.4);
            }
            long age = now - e.created;
            if (age >= NOTICE_WINDOW) {
                boolean watching = p.level().dimension() == e.dim && Sensors.looking(p, e.to, 40, 0.5);
                if (!watching || age >= NOTICE_WINDOW + REVERT_GRACE) {   // nie cofamy "na oczach" gracza
                    revert(lvl, e);
                    it.remove();
                }
            }
        }
    }

    /** Cofa wszystko danego gracza (wylogowanie). */
    void revertAll(MinecraftServer server, UUID player) {
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (!e.player.equals(player)) continue;
            ServerLevel lvl = server.getLevel(e.dim);
            if (lvl != null && lvl.isLoaded(e.to)) revert(lvl, e);
            it.remove();
        }
    }

    void revertEverything(MinecraftServer server) {
        for (Entry e : new ArrayList<>(entries)) {
            ServerLevel lvl = server.getLevel(e.dim);
            if (lvl != null && lvl.isLoaded(e.to)) revert(lvl, e);
        }
        entries.clear();
    }

    private static void revert(ServerLevel lvl, Entry e) {
        if (!lvl.getBlockState(e.to).is(e.state.getBlock())) return;
        lvl.setBlock(e.to, Blocks.AIR.defaultBlockState(), 3);
        if (lvl.getBlockState(e.from).isAir() && e.state.canSurvive(lvl, e.from))
            lvl.setBlock(e.from, e.state, 3);
    }
}
