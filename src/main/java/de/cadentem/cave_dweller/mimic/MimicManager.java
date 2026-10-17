package de.cadentem.cave_dweller.mimic;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.registry.ModEntityTypes;
import de.cadentem.cave_dweller.registry.ModSounds;
import de.cadentem.cave_dweller.util.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Mixin(Mob.class)
interface MobAccessor {
    @Accessor("targetSelector")
    GoalSelector getTargetSelector();
}
/**
 * Player mimic: a "player" (skin + name of a real online player) that wanders or stares and creeps closer while
 * nobody is looking. Once the target gets within MIMIC_TRIGGER_DISTANCE blocks (or hits it) it turns into a
 * Cave Dweller that is already charging.
 *
 * Implementation: the skin is a vanilla mannequin entity (it renders any player's skin and name tag). A mannequin has
 * no AI, so an invisible, silent mob does the pathfinding and the mannequin is moved onto it every tick.
 */
public final class MimicManager {
    public static final String TAG = "bi_mimic";
    private static final Random RANDOM = new Random();
    private static final List<Mimic> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Integer> JOINED_AT = new HashMap<>();
    private static boolean joinScanReady;
    private static int tickCounter;
    private static int mimicTimer = -1;
    private static UUID preferIdentity;

    public static final class Mimic {
        public final UUID body;
        public final UUID skin;
        public final UUID victim;
        public final String name;
        public int ticksLeft;
        /** While above 0 the mimic wanders like a player; afterwards it stares and creeps closer. */
        public int wanderTicks;
        int missingTicks;

        Mimic(UUID body, UUID skin, UUID victim, String name, int ticksLeft, int wanderTicks) {
            this.body = body;
            this.skin = skin;
            this.victim = victim;
            this.name = name;
            this.ticksLeft = ticksLeft;
            this.wanderTicks = wanderTicks;
        }
    }

    private MimicManager() {}

    public static boolean isActive() {
        return !ACTIVE.isEmpty();
    }

    public static void reset() {
        mimicTimer = -1;
    }

    public static void tick(MinecraftServer server, ServerLevel overworld, boolean blocked) {
        tickCounter++;
        if (tickCounter % 20 == 0) scanJoins(server);
        updateMimics(server);
        if (tickCounter % 100 == 0) cleanOrphans(server);

        if (!ServerConfig.MIMIC_ENABLED || !ACTIVE.isEmpty() || blocked) return;
        if (mimicTimer < 0) resetTimer();
        mimicTimer--;
        if (mimicTimer > 0) return;
        resetTimer();
        if (RANDOM.nextDouble() > ServerConfig.MIMIC_CHANCE) return;

        ServerPlayer[] pair = pickPair(server, overworld);
        if (pair == null) return;
        String problem = spawn(server, pair[0], pair[1]);
        if (problem != null) CaveDweller.LOG.debug("Mimic not spawned: {}", problem);
    }

    private static void resetTimer() {
        int min = Math.min(ServerConfig.MIMIC_COOLDOWN_MIN, ServerConfig.MIMIC_COOLDOWN_MAX);
        int max = Math.max(ServerConfig.MIMIC_COOLDOWN_MIN, ServerConfig.MIMIC_COOLDOWN_MAX);
        mimicTimer = RANDOM.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
    }

    /** Remembers who is online; a player who just joined can trigger a mimic of themselves a little later. */
    private static void scanJoins(MinecraftServer server) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            if (!JOINED_AT.containsKey(player.getUUID())) {
                JOINED_AT.put(player.getUUID(), tickCounter);
                if (joinScanReady && ServerConfig.MIMIC_ENABLED && RANDOM.nextDouble() <= ServerConfig.MIMIC_ON_JOIN_CHANCE) {
                    preferIdentity = player.getUUID();
                    int delay = Utils.secondsToTicks(30 + RANDOM.nextInt(91));
                    if (mimicTimer < 0 || mimicTimer > delay) mimicTimer = delay;
                }
            }
        }
        JOINED_AT.keySet().retainAll(online);
        joinScanReady = true;
    }

    /** Returns {victim, identity} or null. Prefers copying somebody other than the victim. */
    @Nullable
    private static ServerPlayer[] pickPair(MinecraftServer server, ServerLevel overworld) {
        List<ServerPlayer> victims = new ArrayList<>();
        for (ServerPlayer player : overworld.players()) {
            if (Utils.isValidPlayer(player)) victims.add(player);
        }
        if (victims.isEmpty()) return null;

        ServerPlayer identity = null;
        if (preferIdentity != null) {
            identity = server.getPlayerList().getPlayer(preferIdentity);
            preferIdentity = null;
        }
        if (identity != null) {
            ServerPlayer copied = identity;
            victims.removeIf(player -> player == copied);
        }

        ServerPlayer victim;
        if (!victims.isEmpty()) {
            victim = victims.get(RANDOM.nextInt(victims.size()));
        } else if (identity != null && ServerConfig.MIMIC_ALLOW_SELF && Utils.isValidPlayer(identity)
                && identity.level() == overworld) {
            victim = identity;
        } else {
            return null;
        }

        if (identity == null) {
            List<ServerPlayer> others = new ArrayList<>(server.getPlayerList().getPlayers());
            ServerPlayer target = victim;
            others.removeIf(player -> player == target);
            if (!others.isEmpty()) {
                identity = others.get(RANDOM.nextInt(others.size()));
            } else if (ServerConfig.MIMIC_ALLOW_SELF) {
                identity = victim;
            }
            // else: nobody to copy -> identity stays null and the mimic is an anonymous Steve
        }
        return new ServerPlayer[] {victim, identity};
    }

    /** Spawns a mimic of {@code identity} near {@code victim} (identity may be null: someone else, else the victim). */
    @Nullable
    public static String spawnNow(MinecraftServer server, ServerPlayer victim, @Nullable ServerPlayer identity, boolean anonymous) {
        if (anonymous) {
            return spawn(server, victim, null);
        }
        if (identity == null) {
            List<ServerPlayer> others = new ArrayList<>(server.getPlayerList().getPlayers());
            others.removeIf(player -> player == victim);
            identity = others.isEmpty() ? victim : others.get(RANDOM.nextInt(others.size()));
        }
        return spawn(server, victim, identity);
    }

    /** Returns null on success, otherwise a human-readable reason. */
    @Nullable
    private static String spawn(MinecraftServer server, ServerPlayer victim, @Nullable ServerPlayer identity) {
        if (!ACTIVE.isEmpty()) return "a mimic is already active (/BetterImprovements remove clears it)";
        if (!(victim.level() instanceof ServerLevel level)) return "the target is not in a loaded level";

        // Fallback when there is nobody to copy (or the name is unusable): anonymous default Steve, no name tag
        String name = identity == null ? null : identity.getName().getString();
        if (name != null && !name.matches("[A-Za-z0-9_.\\-]{1,24}")) {
            CaveDweller.LOG.info("Mimic: name \"{}\" cannot be copied, using the default Steve skin instead", name);
            name = null;
        }

        Vec3 pos = findSpawnPos(level, victim);
        if (pos == null) return "no suitable ground " + ServerConfig.MIMIC_MIN_SPAWN_DISTANCE + "-" + ServerConfig.MIMIC_MAX_SPAWN_DISTANCE
                + " blocks away (water, cliffs or unloaded chunks) - try again from another spot";

        float yaw = (float) (Mth.atan2(victim.getZ() - pos.z, victim.getX() - pos.x) * 57.29577951308232) - 90.0F;

        // The player model: a vanilla mannequin wearing the copied player's skin and name.
        // Attempts, in order: the real player's profile -> the default Steve texture -> no profile at all.
        UUID skinId = UUID.randomUUID();
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withPosition(pos).withSuppressedOutput();
        Entity skin = null;
        for (int attempt = name == null ? 1 : 0; attempt < 3 && skin == null; attempt++) {
            String command = summonCommand(pos, skinId, attempt == 0 ? name : null, attempt);
            server.getCommands().performPrefixedCommand(source, command);
            skin = level.getEntity(skinId);
            if (skin == null && attempt == 0) {
                CaveDweller.LOG.info("Mimic: could not use the skin of {}, falling back to the default Steve skin", name);
            }
        }
        if (skin == null) return "the player model (mannequin) could not be created - see the server log";
        if (name == null) name = "";

        // The invisible body that does the walking
        PathfinderMob body = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        if (body == null) {
            skin.discard();
            return "the mimic's body could not be created";
        }
        body.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        body.setInvisible(true);
        body.setSilent(true);
        body.setPersistenceRequired();
        body.addTag(TAG);
        if (body.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            body.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0);
        }
        
        body.getGoalSelector().getAvailableGoals().clear();
     ((MobAccessor) body).getTargetSelector().getAvailableGoals().clear();

        int wander = 0;
        if (RANDOM.nextDouble() >= ServerConfig.MIMIC_STARE_CHANCE) {
            int min = Math.min(ServerConfig.MIMIC_WANDER_MIN_TIME, ServerConfig.MIMIC_WANDER_MAX_TIME);
            int max = Math.max(ServerConfig.MIMIC_WANDER_MIN_TIME, ServerConfig.MIMIC_WANDER_MAX_TIME);
            wander = Utils.secondsToTicks(min + RANDOM.nextInt(max - min + 1));
        }
        Mimic mimic = new Mimic(body.getUUID(), skinId, victim.getUUID(), name,
                Utils.secondsToTicks(Math.max(10, ServerConfig.MIMIC_TIME)), wander);
        body.getGoalSelector().addGoal(0, new FloatGoal(body));
        body.getGoalSelector().addGoal(1, new MimicGoal(body, mimic));
        level.addFreshEntity(body);
        ACTIVE.add(mimic);

        CaveDweller.LOG.info("Mimic ({}) spawned near {}", name.isEmpty() ? "anonymous Steve" : name, victim.getName().getString());
        return null;
    }

    private static void updateMimics(MinecraftServer server) {
        Iterator<Mimic> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Mimic mimic = iterator.next();
            ServerPlayer victim = server.getPlayerList().getPlayer(mimic.victim);
            if (victim == null || !Utils.isValidPlayer(victim) || !(victim.level() instanceof ServerLevel level)) {
                removeEntities(server, mimic);
                iterator.remove();
                continue;
            }

            Entity bodyEntity = level.getEntity(mimic.body);
            Entity skinEntity = level.getEntity(mimic.skin);
            if (!(bodyEntity instanceof LivingEntity body)) {
                if (++mimic.missingTicks > 100) {
                    removeEntities(server, mimic);
                    iterator.remove();
                }
                continue;
            }
            mimic.missingTicks = 0;

            // Killed: it still turns into the Cave Dweller right where it died
            if (!body.isAlive()) {
                transform(level, victim, body.position(), body.getYRot());
                removeEntities(server, mimic);
                iterator.remove();
                continue;
            }

            boolean hurt = body.getHealth() < body.getMaxHealth() - 0.01F
                    || (skinEntity instanceof LivingEntity model && model.getHealth() < model.getMaxHealth() - 0.01F);
            boolean close = Math.sqrt(body.distanceToSqr(victim)) <= ServerConfig.MIMIC_TRIGGER_DISTANCE;
            if (hurt || close) {
                transform(level, victim, body.position(), body.getYRot());
                removeEntities(server, mimic);
                iterator.remove();
                continue;
            }

            if (--mimic.ticksLeft <= 0) {
                level.sendParticles(ParticleTypes.SMOKE, body.getX(), body.getY() + 1.0, body.getZ(), 12, 0.3, 0.8, 0.3, 0.01);
                removeEntities(server, mimic);
                iterator.remove();
                continue;
            }
            mimic.wanderTicks--;

            if (skinEntity == null) {
                if (++mimic.missingTicks > 100) {
                    removeEntities(server, mimic);
                    iterator.remove();
                }
                continue;
            }
            // Put the player model on the invisible body
            skinEntity.setPos(body.getX(), body.getY(), body.getZ());
            skinEntity.setYRot(body.getYRot());
            skinEntity.setXRot(body.getXRot());
            if (skinEntity instanceof LivingEntity model) {
                model.setYHeadRot(body.getYHeadRot());
                model.setYBodyRot(body.getYRot());
            }
        }
    }

    private static void transform(ServerLevel level, ServerPlayer victim, Vec3 pos, float yaw) {
        CaveDwellerEntity dweller = new CaveDwellerEntity(ModEntityTypes.CAVE_DWELLER, level);
        dweller.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        dweller.startImmediateCharge(victim);
        level.addFreshEntity(dweller);
        level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 1.0, pos.z, 25, 0.3, 0.9, 0.3, 0.03);
        level.playSound((net.minecraft.world.entity.player.Player) null, pos.x, pos.y, pos.z, ModSounds.SPOTTED, SoundSource.HOSTILE, 3.0F, 1.0F);
    }

    private static void removeEntities(MinecraftServer server, Mimic mimic) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity body = level.getEntity(mimic.body);
            if (body != null) body.discard();
            Entity skin = level.getEntity(mimic.skin);
            if (skin != null) skin.discard();
        }
    }

    public static void removeAll(MinecraftServer server) {
        for (Mimic mimic : ACTIVE) removeEntities(server, mimic);
        ACTIVE.clear();
        cleanOrphans(server);
    }

    /** Mimic parts left behind by a restart or crash (they carry the tag but nobody controls them) are removed. */
    private static void cleanOrphans(MinecraftServer server) {
        Set<UUID> known = new HashSet<>();
        for (Mimic mimic : ACTIVE) {
            known.add(mimic.body);
            known.add(mimic.skin);
        }
        List<Entity> orphans = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!known.contains(entity.getUUID()) && Utils.hasTag(entity, TAG)) orphans.add(entity);
            }
        }
        orphans.forEach(Entity::discard);
    }

    /** Standable spot MIMIC_MIN_SPAWN_DISTANCE-MIMIC_MAX_SPAWN_DISTANCE blocks from the victim, or null. */
    @Nullable
    private static Vec3 findSpawnPos(ServerLevel level, ServerPlayer victim) {
        int min = Math.min(ServerConfig.MIMIC_MIN_SPAWN_DISTANCE, ServerConfig.MIMIC_MAX_SPAWN_DISTANCE);
        int max = Math.max(ServerConfig.MIMIC_MIN_SPAWN_DISTANCE, ServerConfig.MIMIC_MAX_SPAWN_DISTANCE);
        boolean surface = level.canSeeSky(victim.blockPosition());
        int victimY = victim.blockPosition().getY();

        for (int attempt = 0; attempt < 60; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double distance = min + RANDOM.nextDouble() * (max - min);
            int x = (int) Math.floor(victim.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(victim.getZ() + Math.sin(angle) * distance);
            BlockPos probe = new BlockPos(x, victimY, z);
            if (!level.hasChunkAt(probe)) continue;

            if (surface) {
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
                if (Math.abs(top.getY() - victimY) <= 14 && isStandable(level, top)) return Vec3.atBottomCenterOf(top);
            } else {
                for (int y = victimY + 8; y >= victimY - 16; y--) {
                    BlockPos feet = new BlockPos(x, y, z);
                    if (isStandable(level, feet)) return Vec3.atBottomCenterOf(feet);
                }
            }
        }
        return null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet) {
        return !Utils.blocksMotion(level, feet)
                && !Utils.blocksMotion(level, feet.above())
                && !Utils.blocksMotion(level, feet.above(2))
                && Utils.blocksMotion(level, feet.below())
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty()
                && level.getFluidState(feet.below()).isEmpty();
    }

    /**
     * attempt 0: copied player's profile + visible name; 1: default Steve texture, no name; 2: no profile, no name.
     * Unknown keys are ignored by /summon, so a wrong field name degrades to the next fallback rather than failing.
     */
    private static String summonCommand(Vec3 pos, UUID id, @Nullable String name, int attempt) {
        String common = "UUID:" + uuidToNbt(id) + ",Tags:[\"" + TAG + "\"],hide_description:1b,immovable:1b,NoGravity:1b,Silent:1b";
        String appearance;
        if (attempt == 0 && name != null) {
            appearance = ",CustomName:\"" + name + "\",CustomNameVisible:1b,profile:\"" + name + "\"";
        } else if (attempt <= 1) {
            appearance = ",CustomNameVisible:0b,profile:{texture:\"minecraft:entity/player/wide/steve\",model:\"wide\"}";
        } else {
            appearance = ",CustomNameVisible:0b";
        }
        return String.format(Locale.ROOT, "summon minecraft:mannequin %.3f %.3f %.3f {%s%s}", pos.x, pos.y, pos.z, common, appearance);
    }

    private static String uuidToNbt(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return "[I;" + (int) (most >> 32) + "," + (int) most + "," + (int) (least >> 32) + "," + (int) least + "]";
    }

    public static List<String> report(ServerPlayer player, MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        lines.add("--- Player mimic ---");
        lines.add(flag(ServerConfig.MIMIC_ENABLED, "MIMIC_ENABLED is on in the config"));
        lines.add(flag(ACTIVE.isEmpty(), "No mimic is active right now"));
        int online = server.getPlayerList().getPlayers().size();
        lines.add(flag(online > 1 || ServerConfig.MIMIC_ALLOW_SELF,
                online + " player(s) online" + (online > 1 ? "" : " (alone: MIMIC_ALLOW_SELF must be true)")));
        lines.add("Mimic timer: " + (mimicTimer < 0 ? "not started" : Math.max(0, mimicTimer / 20) + "s left")
                + ", then a " + (int) (ServerConfig.MIMIC_CHANCE * 100) + "% chance. Use /BetterImprovements mimic to test now.");
        return lines;
    }

    private static String flag(boolean ok, String text) {
        return (ok ? "[OK] " : "[NO] ") + text;
    }
}
