package de.cadentem.cave_dweller;

import com.mojang.logging.LogUtils;
import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.registry.ModEntityTypes;
import de.cadentem.cave_dweller.registry.ModItems;
import de.cadentem.cave_dweller.registry.ModSounds;
import de.cadentem.cave_dweller.util.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.slf4j.Logger;

public class CaveDweller implements ModInitializer {
    public static final String MODID = "cave_dweller";
    public static final Logger LOG = LogUtils.getLogger();
    public static boolean doReload;
    public static CaveDweller instance;

    private final List<ServerPlayer> spelunkers = new ArrayList<>();
    private final Random random = new Random();
    private int calmTimer;
    private int noiseTimer;

    public void serverTick(MinecraftServer server) {
        if (doReload) {
            this.spelunkers.clear();
            this.resetNoiseTimer();
            this.resetCalmTimer();
            doReload = false;
            LOG.info("Values have been reloaded");
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        AtomicBoolean dwellerExists = new AtomicBoolean(false);
        overworld.getAllEntities().forEach(entity -> {
            if (entity instanceof CaveDwellerEntity) {
                dwellerExists.set(true);
                this.resetCalmTimer();
            }
        });

        this.noiseTimer--;
        if (this.noiseTimer <= 0 && (dwellerExists.get() || this.calmTimer <= Utils.secondsToTicks(ServerConfig.RESET_CALM_MAX) / 2)) {
            overworld.getPlayers(this::playCaveSoundToSpelunkers);
            this.resetNoiseTimer();
        }

        boolean canSpawn = this.calmTimer <= 0;
        this.calmTimer--;
        if (canSpawn && !dwellerExists.get() && this.random.nextDouble() <= ServerConfig.SPAWN_CHANCE_PER_TICK) {
            this.spelunkers.clear();
            overworld.getPlayers(this::listSpelunkers);
            if (!this.spelunkers.isEmpty()) {
                ServerPlayer victim = this.spelunkers.get(this.random.nextInt(this.spelunkers.size()));
                overworld.getPlayers(this::playCaveSoundToSpelunkers);
                CaveDwellerEntity caveDweller = new CaveDwellerEntity(ModEntityTypes.CAVE_DWELLER, overworld);
                caveDweller.setInvisible(true);
                caveDweller.setPos(caveDweller.generatePos(victim));
                overworld.addFreshEntity(caveDweller);
                this.resetCalmTimer();
                this.resetNoiseTimer();
            }
        }
    }

    private boolean listSpelunkers(ServerPlayer player) {
        if (this.isPlayerSpelunker(player)) this.spelunkers.add(player);
        return true;
    }

    public boolean playCaveSoundToSpelunkers(ServerPlayer player) {
        if (!this.isPlayerSpelunker(player)) return false;
        SoundEvent soundEvent = switch (this.random.nextInt(4)) {
            case 1 -> ModSounds.CAVENOISE_2;
            case 2 -> ModSounds.CAVENOISE_3;
            case 3 -> ModSounds.CAVENOISE_4;
            default -> ModSounds.CAVENOISE_1;
        };
        player.playSound(soundEvent, 2.0F, 1.0F);
        return true;
    }

    /**
     * Simplified from the 1.20.1 version: the old code scaled sky light by sun angle / sky darkening via APIs that
     * no longer exist in the same form. Here raw sky light is used and treated as 0 at night.
     */
    public boolean isPlayerSpelunker(ServerPlayer player) {
        if (!Utils.isValidPlayer(player)) return false;
        if (player.position().y > ServerConfig.SPAWN_HEIGHT) return false;

        ServerLevel level = (ServerLevel) player.level();
        int sky = level.getBrightness(LightLayer.SKY, player.blockPosition());
        if (level.isDarkOutside()) sky = 0;
        sky = Math.max(0, Math.min(15, sky));
        if (sky > ServerConfig.SKY_LIGHT_LEVEL) return false;

        int block = level.getBrightness(LightLayer.BLOCK, player.blockPosition());
        if (block > ServerConfig.BLOCK_LIGHT_LEVEL) return false;

        return ServerConfig.ALLOW_SURFACE_SPAWN || !level.canSeeSky(player.blockPosition());
    }

    public int getCalmTimer() {
        return this.calmTimer;
    }

    public void setCalmTimer(int ticks) {
        this.calmTimer = ticks;
    }

    /** Spawns a Cave Dweller near the victim; invisible ones behave like natural spawns (they stalk until noticed). */
    public CaveDwellerEntity spawnFor(ServerPlayer victim, boolean visible) {
        ServerLevel level = (ServerLevel) victim.level();
        CaveDwellerEntity dweller = new CaveDwellerEntity(ModEntityTypes.CAVE_DWELLER, level);
        dweller.setInvisible(!visible);
        dweller.setPos(dweller.generatePos(victim));
        if (visible) dweller.setTarget(victim);
        level.addFreshEntity(dweller);
        return dweller;
    }

    /** Human-readable explanation of every condition for a natural spawn near this player. */
    public List<String> spelunkerReport(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        ServerLevel level = (ServerLevel) player.level();
        boolean overworld = level.dimension() == Level.OVERWORLD;
        lines.add(flag(overworld, "Dimension is the Overworld (natural spawns only happen there)"));
        lines.add(flag(Utils.isValidPlayer(player), "Valid target (survival/adventure, alive)"));
        lines.add(flag(player.position().y <= ServerConfig.SPAWN_HEIGHT,
                "Height Y=" + (int) player.position().y + " <= " + ServerConfig.SPAWN_HEIGHT));
        int sky = level.getBrightness(LightLayer.SKY, player.blockPosition());
        if (level.isDarkOutside()) sky = 0;
        lines.add(flag(sky <= ServerConfig.SKY_LIGHT_LEVEL, "Sky light " + sky + " <= " + ServerConfig.SKY_LIGHT_LEVEL));
        int block = level.getBrightness(LightLayer.BLOCK, player.blockPosition());
        lines.add(flag(block <= ServerConfig.BLOCK_LIGHT_LEVEL, "Block light " + block + " <= " + ServerConfig.BLOCK_LIGHT_LEVEL));
        boolean surface = level.canSeeSky(player.blockPosition());
        lines.add(flag(ServerConfig.ALLOW_SURFACE_SPAWN || !surface, "Not open to the sky (surface spawns "
                + (ServerConfig.ALLOW_SURFACE_SPAWN ? "allowed" : "disabled") + ")"));
        boolean exists = false;
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof CaveDwellerEntity) { exists = true; break; }
        }
        lines.add(flag(!exists, "No Cave Dweller currently exists"));
        lines.add("Spawn timer: " + Math.max(0, this.calmTimer / 20) + "s left"
                + " (once 0: " + String.format("%.1f", ServerConfig.SPAWN_CHANCE_PER_TICK * 100) + "% chance per tick)");
        return lines;
    }

    private static String flag(boolean ok, String text) {
        return (ok ? "[OK] " : "[NO] ") + text;
    }

    private void resetCalmTimer() {
        if (this.random.nextDouble() <= ServerConfig.RESET_CALM_COOLDOWN_CHANCE) {
            this.calmTimer = Utils.secondsToTicks(ServerConfig.RESET_CALM_COOLDOWN);
        } else {
            int min = ServerConfig.RESET_CALM_MIN;
            int max = ServerConfig.RESET_CALM_MAX;
            if (max < min) {
                int temp = min; min = max; max = temp;
                LOG.error("Configuration for `RESET_CALM` was wrong - values have been switched");
            }
            this.calmTimer = this.random.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
        }
    }

    private void resetNoiseTimer() {
        int min = ServerConfig.RESET_NOISE_MIN;
        int max = ServerConfig.RESET_NOISE_MAX;
        if (max < min) {
            int temp = min; min = max; max = temp;
            LOG.error("Configuration for `RESET_NOISE` was wrong - values have been switched");
        }
        this.noiseTimer = this.random.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
    }

    @Override
    public void onInitialize() {
        instance = this;
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ServerConfig.load());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> doReload = true);
        ServerTickEvents.START_SERVER_TICK.register(this::serverTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CaveDwellerCommands.register(dispatcher));
        ModSounds.init();
        ModEntityTypes.register();
        ModItems.register();
        FabricDefaultAttributeRegistry.register(ModEntityTypes.CAVE_DWELLER, CaveDwellerEntity.getAttributeBuilder());
    }
}
