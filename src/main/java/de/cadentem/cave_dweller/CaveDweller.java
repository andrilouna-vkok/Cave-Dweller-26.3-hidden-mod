package de.cadentem.cave_dweller;

import com.mojang.logging.LogUtils;
import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.mimic.MimicManager;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

public class CaveDweller implements ModInitializer {
    public static final String MODID = "betterimprovements";
    public static final Logger LOG = LogUtils.getLogger();
    public static boolean doReload;
    public static CaveDweller instance;

    private final List<ServerPlayer> spelunkers = new ArrayList<>();
    private final Random random = new Random();
    private int calmTimer;
    private int noiseTimer;
    private int surfaceTimer;

    public void serverTick(MinecraftServer server) {
        if (doReload) {
            this.spelunkers.clear();
            this.resetNoiseTimer();
            this.resetCalmTimer();
            this.resetSurfaceTimer();
            MimicManager.reset();
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

        MimicManager.tick(server, overworld, dwellerExists.get());
        if (MimicManager.isActive()) {
            dwellerExists.set(true);
        }

        if (ServerConfig.SURFACE_STALKING && !dwellerExists.get() && this.surfaceTick(overworld)) {
            dwellerExists.set(true);
        }

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
                CaveDwellerEntity caveDweller = this.spawnFor(victim, false);
                if (caveDweller != null) {
                    this.resetCalmTimer();
                    this.resetNoiseTimer();
                } else {
                    LOG.debug("No valid spawn spot near {} - will retry", victim.getName().getString());
                }
            }
        }
    }

    private boolean listSpelunkers(ServerPlayer player) {
        if (this.isPlayerSpelunker(player)) this.spelunkers.add(player);
        return true;
    }

    public boolean playCaveSoundToSpelunkers(ServerPlayer player) {
        if (ServerConfig.SOUNDS_ONLY_WHILE_CHASING || !this.isPlayerSpelunker(player)) return false;
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
    @org.jetbrains.annotations.Nullable
    public CaveDwellerEntity spawnFor(ServerPlayer victim, boolean visible) {
        ServerLevel level = (ServerLevel) victim.level();
        CaveDwellerEntity dweller = new CaveDwellerEntity(ModEntityTypes.CAVE_DWELLER, level);
        dweller.setInvisible(!visible);
        Vec3 pos = dweller.generatePos(victim);
        if (pos == null) return null;
        dweller.setPos(pos);
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
        CaveDwellerEntity existing = null;
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof CaveDwellerEntity dweller) { existing = dweller; break; }
        }
        lines.add(flag(existing == null, "No Cave Dweller currently exists (one blocks new spawns and keeps resetting the timer)"));
        if (existing != null) {
            lines.add("  His position: " + (int) existing.getX() + " " + (int) existing.getY() + " " + (int) existing.getZ()
                    + " (" + (int) existing.distanceTo(player) + " blocks away, "
                    + (existing.isInvisible() ? "still hidden" : "visible") + ")");
        }
        lines.add("Spawn timer: " + Math.max(0, this.calmTimer / 20) + "s left"
                + " (once 0: " + String.format("%.1f", ServerConfig.SPAWN_CHANCE_PER_TICK * 100) + "% chance per tick)");
        lines.addAll(this.surfaceReport(player));
        if (player.level().getServer() != null) {
            lines.addAll(MimicManager.report(player, player.level().getServer()));
        }
        return lines;
    }

    private static String flag(boolean ok, String text) {
        return (ok ? "[OK] " : "[NO] ") + text;
    }

    /** Night-time surface visit: a stalker that follows at a distance and never attacks. Returns true if one was spawned. */
    private boolean surfaceTick(ServerLevel level) {
        if (!level.isDarkOutside()) return false;
        this.surfaceTimer--;
        if (this.surfaceTimer > 0) return false;
        this.resetSurfaceTimer();
        if (this.random.nextDouble() > ServerConfig.SURFACE_STALK_CHANCE) return false;

        List<ServerPlayer> candidates = new ArrayList<>();
        level.getPlayers(player -> {
            if (Utils.isValidPlayer(player) && level.canSeeSky(player.blockPosition())) candidates.add(player);
            return true;
        });
        if (candidates.isEmpty()) return false;

        ServerPlayer victim = candidates.get(this.random.nextInt(candidates.size()));
        Vec3 position = this.findSurfacePosition(level, victim);
        if (position == null) return false;

        this.createStalker(level, position);
        return true;
    }

    private void createStalker(ServerLevel level, Vec3 position) {
        CaveDwellerEntity stalker = new CaveDwellerEntity(ModEntityTypes.CAVE_DWELLER, level);
        stalker.makeSurfaceStalker();
        stalker.setInvisible(true);
        stalker.setPos(position);
        level.addFreshEntity(stalker);
    }

    public int getSurfaceTimer() {
        return this.surfaceTimer;
    }

    public void setSurfaceTimer(int ticks) {
        this.surfaceTimer = ticks;
    }

    /** Spawns a surface stalker near the victim right now. Returns null on success, otherwise the reason it failed. */
    @org.jetbrains.annotations.Nullable
    public String spawnSurfaceStalker(ServerPlayer victim) {
        ServerLevel level = (ServerLevel) victim.level();
        if (level.dimension() != Level.OVERWORLD) return "he only appears in the Overworld";
        if (!level.isDarkOutside()) return "it is not night (stalkers leave at dawn)";
        Vec3 position = this.findSurfacePosition(level, victim);
        if (position == null) return "no suitable ground 22-36 blocks away (water, steep cliffs or unloaded chunks) - try again from another spot";
        this.createStalker(level, position);
        return null;
    }

    private List<String> surfaceReport(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        ServerLevel level = (ServerLevel) player.level();
        lines.add("--- Night surface stalking ---");
        lines.add(flag(ServerConfig.SURFACE_STALKING, "SURFACE_STALKING is enabled in the config"));
        lines.add(flag(level.isDarkOutside(), "It is night (the surface timer only counts down at night)"));
        lines.add(flag(Utils.isValidPlayer(player) && level.canSeeSky(player.blockPosition()), "You are outside under open sky"));
        boolean exists = false;
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof CaveDwellerEntity) { exists = true; break; }
        }
        lines.add(flag(!exists, "No Cave Dweller currently exists"));
        lines.add(flag(this.findSurfacePosition(level, player) != null, "A spawn spot exists 22-36 blocks away (not water, not a cliff)"));
        lines.add("Surface timer: " + Math.max(0, this.surfaceTimer / 20) + "s left, then a "
                + (int) (ServerConfig.SURFACE_STALK_CHANCE * 100) + "% chance he comes. Use /BetterImprovements stalk to test now.");
        return lines;
    }

    private Vec3 findSurfacePosition(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double distance = 22 + this.random.nextInt(14);
            BlockPos probe = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(probe)) continue;

            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
            if (Math.abs(top.getY() - player.getY()) > 12) continue;
            if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.below()).isEmpty()) continue;
            if (!Utils.blocksMotion(level, top.below())) continue;
            return Vec3.atBottomCenterOf(top);
        }
        return null;
    }

    private void resetSurfaceTimer() {
        int min = Math.min(ServerConfig.SURFACE_STALK_COOLDOWN_MIN, ServerConfig.SURFACE_STALK_COOLDOWN_MAX);
        int max = Math.max(ServerConfig.SURFACE_STALK_COOLDOWN_MIN, ServerConfig.SURFACE_STALK_COOLDOWN_MAX);
        this.surfaceTimer = this.random.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
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
