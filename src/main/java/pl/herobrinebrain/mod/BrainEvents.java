package pl.herobrinebrain.mod;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import pl.herobrinebrain.core.*;

import java.util.Comparator;
import java.util.Locale;

/** Wszystkie podpięcia do eventów NeoForge w jednym miejscu (sensory + cykl życia + komendy). */
public final class BrainEvents {
    private static final int SAMPLE_EVERY = 100;       // co 5 s
    private static final int AUTOSAVE_EVERY = 6000;    // co 5 min
    private final BrainManager manager = new BrainManager();

    /* ------------------------------------------------------------ próbkowanie */

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        // szybsze próbkowanie spojrzenia (co 0.5 s) tylko gdy gracz ma aktywne anomalie
        if (p.tickCount % 10 == 0 && manager.ledger().hasActive(p.getUUID())) manager.ledger().tick(p, manager.get(p));
        if (p.tickCount % SAMPLE_EVERY != 0) return;

        PlayerBrain brain = manager.get(p);
        HorrorDirector dir = brain.director;
        long now = p.level().getGameTime();
        String place = Sensors.placeKey(p);
        boolean sleeping = p.isSleeping();

        // --- obserwacje ciągłe
        dir.observe(Observation.of(ObsType.DWELL, now, place, SAMPLE_EVERY / 20.0));
        boolean underground = Sensors.underground(p);
        if (underground) dir.observe(Observation.of(ObsType.UNDERGROUND_TIME, now, place, SAMPLE_EVERY / 20.0));
        else if (Sensors.night(p) && p.level().canSeeSky(p.blockPosition()))
            dir.observe(Observation.of(ObsType.SURFACE_TIME_NIGHT, now, place, SAMPLE_EVERY / 20.0));
        if (Sensors.inCombat(p)) dir.observe(Observation.of(ObsType.COMBAT, now, place, 1));

        // --- zdarzenie snu: przejście "nie śpi" -> "śpi" (odporne na nieudane próby położenia się)
        java.util.Optional<DirectorAction> action;
        if (sleeping && !brain.wasSleeping) {
            dir.observe(Observation.of(ObsType.SLEEP, now, place, Sensors.hourOfDay(p)));
            action = dir.decideNow(Sensors.situation(p, true));
        } else {
            action = dir.tick(Sensors.situation(p, sleeping));
        }
        brain.wasSleeping = sleeping;
        action.ifPresent(a -> ActionRouter.execute(p, brain, a, manager.ledger()));

        if (now - brain.lastSaveTick > AUTOSAVE_EVERY) {
            brain.lastSaveTick = now;
            manager.save(p.getServer(), p.getUUID());
        }
    }

    /* ---------------------------------------------------------------- zdarzenia */

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        manager.get(p).director.observe(Observation.of(ObsType.DEATH, p.level().getGameTime(), Sensors.placeKey(p), 1));
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent e) {
        String text = e.getMessage().getString().toLowerCase(Locale.ROOT);
        if (text.contains("herobrine") || text.contains("herobrajn")) {
            ServerPlayer p = e.getPlayer();
            manager.get(p).director.observe(Observation.of(ObsType.SEARCHED_FOR_HEROBRINE, p.level().getGameTime(), "", 1));
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) manager.unload(p.getServer(), p.getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        manager.ledger().revertEverything(e.getServer());   // zawsze oddajemy świat nienaruszony
        manager.saveAll(e.getServer());
        manager.clear();
    }

    /* ------------------------------------------------------------------ komendy */

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("hb")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("debug").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    reply(ctx.getSource(), debugText(manager.get(p)));
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    manager.reset(p.getServer(), p.getUUID());
                    reply(ctx.getSource(), "Mózg zresetowany.");
                    return 1;
                })));
    }

    private static void reply(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static String debugText(PlayerBrain b) {
        HorrorDirector d = b.director;
        StringBuilder sb = new StringBuilder();
        sb.append(d).append('\n').append(d.profile()).append("\nFakty:");
        d.memory().all().stream()
                .sorted(Comparator.comparingDouble((Fact f) -> f.strength).reversed()).limit(6)
                .forEach(f -> sb.append("\n  ").append(f));
        sb.append("\nOstatnie akcje:");
        b.actionLog.forEach(l -> sb.append("\n  ").append(l));
        return sb.toString();
    }
}
