package de.cadentem.cave_dweller;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.mimic.MimicManager;
import de.cadentem.cave_dweller.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * /BetterImprovements spawn [player]   visible dweller near the player that immediately hunts them
 * /BetterImprovements scare [player]   cave noise + a hidden dweller that stalks the player like a natural spawn
 * /BetterImprovements status [player]  why a natural spawn near the player would / would not happen, plus the timer
 * /BetterImprovements stalk [player]  night-only: a stalker that follows at a distance and never attacks (leaves at dawn)
 * /BetterImprovements mimic [target [copy|steve]]  a fake copy of an online player (skin + name) near the target; turns into a charging dweller at 2 blocks
 * /BetterImprovements skip             sets the spawn timer to 0 so a natural spawn can happen right away
 * /BetterImprovements remove           removes every Cave Dweller in your dimension
 * /BetterImprovements reload           reloads config/cave_dweller.properties
 */
public final class CaveDwellerCommands {
    private CaveDwellerCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("BetterImprovements")
                // If this line does not compile on 26.3, delete it: the commands are then available to everyone.
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawn(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> spawn(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("scare")
                        .executes(ctx -> scare(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> scare(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("stalk")
                        .executes(ctx -> stalk(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> stalk(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("mimic")
                        .executes(ctx -> mimic(ctx.getSource(), ctx.getSource().getPlayerOrException(), null, false))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> mimic(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"), null, false))
                                .then(Commands.literal("steve")
                                        .executes(ctx -> mimic(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"), null, true)))
                                .then(Commands.argument("copy", EntityArgument.player())
                                        .executes(ctx -> mimic(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"),
                                                EntityArgument.getPlayer(ctx, "copy"), false)))))
                .then(Commands.literal("skip").executes(ctx -> skip(ctx.getSource())))
                .then(Commands.literal("remove").executes(ctx -> remove(ctx.getSource())))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource()))));
    }

    private static int spawn(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        if (CaveDweller.instance.spawnFor(target, true) == null) {
            source.sendFailure(Component.literal("No valid spot found near " + target.getName().getString() + " - try again"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Spawned a Cave Dweller near " + target.getName().getString()), true);
        return 1;
    }

    private static int scare(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        target.playSound(ModSounds.CAVENOISE_1, 2.0F, 1.0F);
        if (CaveDweller.instance.spawnFor(target, false) == null) {
            source.sendFailure(Component.literal("No valid spot found near " + target.getName().getString() + " - try again"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Something is watching " + target.getName().getString() + "..."), true);
        return 1;
    }

    private static int stalk(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        String problem = CaveDweller.instance.spawnSurfaceStalker(target);
        if (problem != null) {
            source.sendFailure(Component.literal("Couldn't spawn a surface stalker: " + problem));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Something is following " + target.getName().getString() + " at a distance (leaves at dawn)"), true);
        return 1;
    }

    private static int mimic(CommandSourceStack source, ServerPlayer target, ServerPlayer copy, boolean anonymous) {
        String problem = MimicManager.spawnNow(source.getServer(), target, copy, anonymous);
        if (problem != null) {
            source.sendFailure(Component.literal("Couldn't spawn a mimic: " + problem));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Someone is wandering near " + target.getName().getString() + "..."), true);
        return 1;
    }

    private static int status(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        List<String> lines = CaveDweller.instance.spelunkerReport(target);
        source.sendSuccess(() -> Component.literal("Cave Dweller spawn check for " + target.getName().getString() + ":"), false);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int skip(CommandSourceStack source) {
        CaveDweller.instance.setCalmTimer(0);
        CaveDweller.instance.setSurfaceTimer(0);
        source.sendSuccess(() -> Component.literal("Spawn timer set to 0 - he can arrive any moment"), true);
        return 1;
    }

    private static int remove(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<Entity> found = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof CaveDwellerEntity) found.add(entity);
        }
        found.forEach(Entity::discard);
        int count = found.size();
        MimicManager.removeAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Removed " + count + " Cave Dweller(s)"), true);
        return count;
    }

    private static int reload(CommandSourceStack source) {
        ServerConfig.load();
        CaveDweller.doReload = true;
        source.sendSuccess(() -> Component.literal("Cave Dweller config reloaded"), true);
        return 1;
    }
}
