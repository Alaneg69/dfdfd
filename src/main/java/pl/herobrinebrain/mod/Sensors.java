package pl.herobrinebrain.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pl.herobrinebrain.core.Situation;

/** Zamienia stan Minecrafta na typy rdzenia (Situation + klucz miejsca). Żadnych decyzji tutaj. */
final class Sensors {
    private Sensors() { }

    static final double BASE_RADIUS = 48;

    /** Baza = punkt odrodzenia gracza (łóżko) albo spawn świata, w Overworldzie, w promieniu 48 bloków. */
    static boolean nearBase(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD) return false;
        BlockPos ref = p.getRespawnPosition();
        if (ref == null || p.getRespawnDimension() != Level.OVERWORLD) ref = p.serverLevel().getSharedSpawnPos();
        return p.blockPosition().distSqr(ref) < BASE_RADIUS * BASE_RADIUS;
    }

    /** "base" albo komórka 64x64 bloków. Prefiks "base" ma znaczenie w PlayerProfile (COMFORT). */
    static String placeKey(ServerPlayer p) {
        if (nearBase(p)) return "base";
        BlockPos pos = p.blockPosition();
        return "cell:" + (pos.getX() >> 6) + "," + (pos.getZ() >> 6);
    }

    static boolean underground(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        BlockPos pos = p.blockPosition();
        return pos.getY() < lvl.getSeaLevel() && !lvl.canSeeSky(pos);
    }

    static boolean dark(ServerPlayer p) {
        return p.serverLevel().getMaxLocalRawBrightness(p.blockPosition()) < 7;
    }

    static boolean night(ServerPlayer p) {
        return p.level().dimension() == Level.OVERWORLD && p.level().isNight();
    }

    static boolean inCombat(ServerPlayer p) {
        return p.getLastHurtByMob() != null && p.tickCount - p.getLastHurtByMobTimestamp() < 200;
    }

    /** Godzina 0..23 w konwencji "tick 0 = 06:00". */
    static int hourOfDay(ServerPlayer p) {
        long dayTime = p.level().getDayTime() % 24000L;
        return (int) ((dayTime / 1000 + 6) % 24);
    }

    static Situation situation(ServerPlayer p, boolean sleeping) {
        return new Situation(
                p.level().getGameTime(), hourOfDay(p), sleeping,
                underground(p), dark(p), night(p), nearBase(p), inCombat(p),
                p.getMaxHealth() <= 0 ? 1.0 : p.getHealth() / p.getMaxHealth());
    }

    /**
     * Czy gracz PATRZY na dany blok: w zasięgu, w stożku widzenia (cosThreshold: 0.9 ~ 25 stopni,
     * 0.5 ~ 60 stopni) i bez przeszkody na linii wzroku (raycast po kolizjach).
     */
    static boolean looking(ServerPlayer p, BlockPos target, double maxDist, double cosThreshold) {
        Vec3 eye = p.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);
        Vec3 d = center.subtract(eye);
        double dist = d.length();
        if (dist < 0.75) return true;
        if (dist > maxDist) return false;
        if (p.getLookAngle().dot(d.scale(1.0 / dist)) < cosThreshold) return false;
        BlockHitResult hit = p.level().clip(new ClipContext(eye, center,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }
}
