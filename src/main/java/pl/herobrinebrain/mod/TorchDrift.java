package pl.herobrinebrain.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import pl.herobrinebrain.core.DirectorAction;

import java.util.ArrayList;
import java.util.List;

/**
 * MICRO_ANOMALY: przesuwa jedną pochodnię o jeden blok. Wybiera pochodnię, której gracz TERAZ nie widzi,
 * żeby odkrył zmianę sam, gdy wróci wzrokiem. Nic nie niszczy, wszystko wraca przez AnomalyLedger.
 */
final class TorchDrift {
    private TorchDrift() { }

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final Direction[] ALL_SIDES = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};

    /** @return true, jeśli anomalia została faktycznie wykonana */
    static boolean run(ServerPlayer p, DirectorAction a, AnomalyLedger ledger) {
        ServerLevel lvl = p.serverLevel();
        RandomSource rnd = p.getRandom();
        BlockPos c = p.blockPosition();
        final int R = 16, RY = 6;

        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -R; dx <= R; dx++)
            for (int dy = -RY; dy <= RY; dy++)
                for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dz * dz < 25) continue;                 // nie tuż obok gracza
                    BlockPos pos = c.offset(dx, dy, dz);
                    BlockState st = lvl.getBlockState(pos);
                    if (!st.is(Blocks.TORCH) && !st.is(Blocks.WALL_TORCH)) continue;
                    if (ledger.isTouched(pos)) continue;
                    if (Sensors.looking(p, pos, 48, 0.5)) continue;       // poza polem widzenia
                    candidates.add(pos);
                }
        if (candidates.isEmpty()) return false;

        // kilka prób, bo nie każda pochodnia ma wolne sąsiednie miejsce
        for (int attempt = 0; attempt < 6; attempt++) {
            BlockPos from = candidates.get(rnd.nextInt(candidates.size()));
            BlockState st = lvl.getBlockState(from);
            Direction[] dirs = st.is(Blocks.TORCH) ? HORIZONTAL : ALL_SIDES;
            List<BlockPos> targets = new ArrayList<>();
            for (Direction d : dirs) {
                BlockPos np = from.relative(d);
                if (lvl.getBlockState(np).isAir() && st.canSurvive(lvl, np)) targets.add(np);
            }
            if (targets.isEmpty()) continue;
            BlockPos to = targets.get(rnd.nextInt(targets.size()));

            lvl.setBlock(from, Blocks.AIR.defaultBlockState(), 3);
            lvl.setBlock(to, st, 3);
            ledger.addMove(a.id(), p, from, to, st);
            return true;
        }
        return false;
    }
}
