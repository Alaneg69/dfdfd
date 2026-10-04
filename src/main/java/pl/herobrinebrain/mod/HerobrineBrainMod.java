package pl.herobrinebrain.mod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Punkt wejścia. Cała logika decyzyjna jest w pakiecie core; tu tylko podpinamy adapter. */
@Mod(HerobrineBrainMod.MODID)
public final class HerobrineBrainMod {
    public static final String MODID = "herobrinebrain";

    public HerobrineBrainMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(new BrainEvents());
    }
}
