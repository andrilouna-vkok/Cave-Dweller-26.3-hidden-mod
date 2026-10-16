package de.cadentem.cave_dweller.entities;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.goals.*;
import de.cadentem.cave_dweller.registry.ModSounds;
import de.cadentem.cave_dweller.util.Utils;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CaveDwellerEntity extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation CHASE = RawAnimation.begin().thenLoop("animation.cave_dweller.new_run");
    private static final RawAnimation CHASE_IDLE = RawAnimation.begin().thenLoop("animation.cave_dweller.run_idle");
    private static final RawAnimation CROUCH_RUN = RawAnimation.begin().thenLoop("animation.cave_dweller.crouch_run_new");
    private static final RawAnimation CROUCH_IDLE = RawAnimation.begin().thenLoop("animation.cave_dweller.crouch_idle");
    private static final RawAnimation CALM_RUN = RawAnimation.begin().thenLoop("animation.cave_dweller.calm_move");
    private static final RawAnimation CALM_STILL = RawAnimation.begin().thenLoop("animation.cave_dweller.calm_idle");
    private static final RawAnimation IS_SPOTTED = RawAnimation.begin().thenPlayAndHold("animation.cave_dweller.spotted");
    private static final RawAnimation CRAWL = RawAnimation.begin().thenPlayAndHold("animation.cave_dweller.crawl");
    private static final RawAnimation FLEE = RawAnimation.begin().thenLoop("animation.cave_dweller.flee");

    public static final EntityDataAccessor<Boolean> FLEEING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CROUCHING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> AGGRO_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SQUEEZING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> SPOTTED_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Boolean> CLIMBING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);

    private final float twoBlockSpaceCooldown;
    public Roll currentRoll = Roll.STROLL;
    public boolean fakeSize;
    public boolean isFleeing;
    public boolean spottedByPlayer;
    public boolean squeezeCrawling;
    public boolean pleaseStopMoving;
    private float twoBlockSpaceTimer;
    private int ticksTillRemove;
    private int chaseSoundClock;
    private boolean inTwoBlockSpace;
    private boolean alreadyPlayedFleeSound;
    private boolean alreadyPlayedSpottedSound;
    private boolean startedPlayingChaseSound;
    private boolean alreadyPlayedDeathSound;

    public CaveDwellerEntity(EntityType<? extends CaveDwellerEntity> entityType, Level level) {
        super(entityType, level);
        this.refreshDimensions();
        this.twoBlockSpaceCooldown = 5.0F;
        this.ticksTillRemove = Utils.secondsToTicks(ServerConfig.TIME_UNTIL_LEAVE);
        // Start out stalking instead of aimlessly wandering
        this.currentRoll = this.getRandom().nextDouble() < ServerConfig.STALK_CHANCE ? Roll.STARE : Roll.STROLL;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty,
                                        @NotNull EntitySpawnReason reason, @Nullable SpawnGroupData spawnData) {
        this.setAttribute(this.getAttribute(Attributes.MAX_HEALTH), ServerConfig.MAX_HEALTH);
        this.setAttribute(this.getAttribute(Attributes.ATTACK_DAMAGE), ServerConfig.ATTACK_DAMAGE);
        this.setAttribute(this.getAttribute(Attributes.ATTACK_SPEED), ServerConfig.ATTACK_SPEED);
        this.setAttribute(this.getAttribute(Attributes.MOVEMENT_SPEED), ServerConfig.MOVEMENT_SPEED);
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    private void setAttribute(@Nullable AttributeInstance attribute, double value) {
        if (attribute == null) return;
        attribute.setBaseValue(value);
        if (attribute.getAttribute().equals(Attributes.MAX_HEALTH)) {
            this.setHealth((float) value);
        } else if (attribute.getAttribute().equals(Attributes.MOVEMENT_SPEED)) {
            this.setSpeed((float) value);
        }
    }

    public static AttributeSupplier.Builder getAttributeBuilder() {
        // Depth Strider was an enchantment-based travel() tweak in 1.20.1; it is now an attribute (clamped to 0..1).
        double waterEfficiency = Math.max(0.0, Math.min(1.0, ServerConfig.DEPTH_STRIDER_BONUS));
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ATTACK_SPEED, 0.35)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 100.0)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, waterEfficiency);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLEEING_ACCESSOR, false);
        builder.define(CROUCHING_ACCESSOR, false);
        builder.define(AGGRO_ACCESSOR, false);
        builder.define(SQUEEZING_ACCESSOR, false);
        builder.define(SPOTTED_ACCESSOR, false);
        builder.define(CLIMBING_ACCESSOR, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new CaveDwellerChaseGoal(this, 0.85F, true, 20.0F));
        this.goalSelector.addGoal(1, new CaveDwellerFleeGoal(this, 20.0F, 1.0));
        this.goalSelector.addGoal(2, new CaveDwellerBreakInvisGoal(this));
        this.goalSelector.addGoal(2, new CaveDwellerStareGoal(this));
        if (ServerConfig.CAN_BREAK_DOOR) {
            this.goalSelector.addGoal(2, new CaveDwellerBreakDoorGoal(this, difficulty -> true));
        }
        if (ServerConfig.CAN_BREAK_BLOCKS) {
            this.goalSelector.addGoal(2, new CaveDwellerBreakBlocksGoal(this));
        }
        this.goalSelector.addGoal(3, new CaveDwellerStrollGoal(this, 0.7));
        this.targetSelector.addGoal(1, new CaveDwellerTargetTooCloseGoal(this, 12.0F));
        this.targetSelector.addGoal(2, new CaveDwellerTargetSeesMeGoal(this));
    }

    /**
     * Finds a standable spot (3 free blocks of height, solid floor) 16-30 blocks from the player, within 20 blocks
     * below / 6 above. Returns null when nothing suitable is found, so the caller can skip the spawn instead of
     * placing him inside rock or below the world.
     */
    @Nullable
    public Vec3 generatePos(Entity player) {
        Vec3 playerPos = player.position();
        Random rand = new Random();
        Level level = this.level();
        for (int attempt = 0; attempt < 60; attempt++) {
            int x = (int) Math.floor(playerPos.x) + rand.nextInt(61) - 30;
            int z = (int) Math.floor(playerPos.z) + rand.nextInt(61) - 30;
            double dx = x + 0.5 - playerPos.x;
            double dz = z + 0.5 - playerPos.z;
            if (dx * dx + dz * dz < 16.0 * 16.0) continue;
            for (int y = (int) Math.floor(playerPos.y) + 6; y >= (int) Math.floor(playerPos.y) - 20; y--) {
                if (!Utils.blocksMotion(level, new BlockPos(x, y, z))
                        && !Utils.blocksMotion(level, new BlockPos(x, y + 1, z))
                        && !Utils.blocksMotion(level, new BlockPos(x, y + 2, z))
                        && Utils.blocksMotion(level, new BlockPos(x, y - 1, z))) {
                    // Final safety net: his real hitbox must not touch any block or liquid at this spot
                    this.setPos(x + 0.5, y, z + 0.5);
                    if (!level.noCollision(this) || level.containsAnyLiquid(this.getBoundingBox())) break;
                    return new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    @Override
    protected boolean canRide(@NotNull Entity vehicle) {
        return ServerConfig.ALLOW_RIDING && super.canRide(vehicle);
    }

    /** He squeezes through tight gaps and breaks blocks, so being inside a block must never hurt him. */
    @Override
    public boolean isInvulnerableTo(@NotNull ServerLevel level, @NotNull DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(level, source);
    }

    /** Replaces the old MixinLivingEntity#travel(HEAD) cancel. */
    @Override
    public void travel(@NotNull Vec3 travelVector) {
        if (this.pleaseStopMoving) return;
        super.travel(travelVector);
    }

    @Override
    public void tick() {
        this.ticksTillRemove--;
        if (this.ticksTillRemove <= 0) {
            this.playDisappearSound();
            this.discard();
        }

        BlockPos above = BlockPos.containing(this.position().x, this.position().y + 2.0, this.position().z);
        boolean blocksMotion = Utils.blocksMotion(this.level(), above);
        if (blocksMotion) {
            this.twoBlockSpaceTimer = this.twoBlockSpaceCooldown;
            this.inTwoBlockSpace = true;
        } else {
            this.twoBlockSpaceTimer--;
            if (this.twoBlockSpaceTimer <= 0.0F) this.inTwoBlockSpace = false;
        }

        if (this.isAggressive() || this.isFleeing) {
            this.spottedByPlayer = false;
            this.getEntityData().set(SPOTTED_ACCESSOR, false);
        }

        super.tick();
        this.getEntityData().set(CROUCHING_ACCESSOR, this.inTwoBlockSpace);
        if (this.getEntityData().get(SPOTTED_ACCESSOR)) this.playSpottedSound();

        if (!this.level().isClientSide()) this.setClimbing(this.horizontalCollision);

        if (this.getTarget() == null) {
            this.setTarget(this.level().getNearestPlayer(
                    this.position().x, this.position().y, this.position().z, 128.0, Utils::isValidPlayer));
        }
    }

    @NotNull
    @Override
    public EntityDimensions getDefaultDimensions(@NotNull Pose pose) {
        return this.fakeSize ? EntityDimensions.fixed(0.5F, 0.9F) : EntityDimensions.fixed(0.5F, 1.9F);
    }

    private boolean isMoving() {
        Vec3 velocity = this.getDeltaMovement();
        float avg = (float) (Math.abs(velocity.x) + Math.abs(velocity.z)) / 2.0F;
        return avg > 0.03F;
    }

    public void reRoll() {
        // 25% chase, 50% keep stalking, 25% flee
        int[] table = {0, 1, 1, 2};
        this.currentRoll = Roll.fromValue(table[new Random().nextInt(table.length)]);
    }

    public void pickRoll(@NotNull List<Roll> rolls) { this.currentRoll = rolls.get(new Random().nextInt(rolls.size())); }

    public Path createShortPath(LivingEntity target) {
        this.fakeSize = true;
        this.refreshDimensions();
        Path shortPath = this.getNavigation().createPath(target, 0);
        this.fakeSize = false;
        this.refreshDimensions();
        return shortPath;
    }

    public Path createShortPath(@NotNull Vec3 position) {
        this.fakeSize = true;
        this.refreshDimensions();
        Path shortPath = this.getNavigation().createPath(position.x, position.y, position.z, 0);
        this.fakeSize = false;
        this.refreshDimensions();
        return shortPath;
    }

    @Override
    public boolean onClimbable() { return this.isClimbing(); }

    public boolean isClimbing() {
        if (!ServerConfig.CAN_CLIMB) return false;
        LivingEntity target = this.getTarget();
        return target != null && target.getPosition(1.0F).y > this.getY() && this.getEntityData().get(CLIMBING_ACCESSOR);
    }

    public void setClimbing(boolean isClimbing) { this.getEntityData().set(CLIMBING_ACCESSOR, isClimbing); }

    @NotNull
    @Override
    protected PathNavigation createNavigation(@NotNull Level level) {
        // Replaces MixinGroundPathNavigation#canUpdatePath: allow path updates while using the short "fake size" path.
        return new WallClimberNavigation(this, level) {
            @Override
            protected boolean canUpdatePath() {
                return super.canUpdatePath() || CaveDwellerEntity.this.fakeSize;
            }
        };
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(state -> {
        SynchedEntityData data = this.getEntityData();
        if (data.get(AGGRO_ACCESSOR)) {
            if (data.get(SQUEEZING_ACCESSOR)) return state.setAndContinue(CRAWL);
            if (data.get(CROUCHING_ACCESSOR)) return state.isMoving() ? state.setAndContinue(CROUCH_RUN) : state.setAndContinue(CROUCH_IDLE);
            return state.isMoving() ? state.setAndContinue(CHASE) : state.setAndContinue(CHASE_IDLE);
        } else if (data.get(FLEEING_ACCESSOR)) {
            return state.isMoving() ? state.setAndContinue(FLEE) : state.setAndContinue(CHASE_IDLE);
        } else if (data.get(SPOTTED_ACCESSOR) && !state.isMoving()) {
            return state.setAndContinue(IS_SPOTTED);
        }
        return state.isMoving() ? state.setAndContinue(CALM_RUN) : state.setAndContinue(CALM_STILL);
    }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        super.playStepSound(pos, state);
        this.playEntitySound(this.chooseStep());
    }

    private void playEntitySound(SoundEvent soundEvent) { this.playEntitySound(soundEvent, 1.0F, 1.0F); }

    private void playEntitySound(SoundEvent soundEvent, float volume, float pitch) {
        this.level().playSound(null, this, soundEvent, SoundSource.HOSTILE, volume, pitch);
    }

    private void playBlockPosSound(SoundEvent soundEvent, float volume, float pitch) {
        if (this.level() instanceof ServerLevel serverLevel) {
            int radiusSq = 3600;
            serverLevel.getPlayers(player -> player.distanceToSqr(this) <= radiusSq)
                    .forEach(player -> player.playSound(soundEvent, volume, pitch));
        }
    }

    public void playChaseSound() {
        if (this.startedPlayingChaseSound || this.isMoving()) {
            if (this.chaseSoundClock <= 0) {
                SoundEvent sound = switch (new Random().nextInt(4)) {
                    case 1 -> ModSounds.CHASE_2;
                    case 2 -> ModSounds.CHASE_3;
                    case 3 -> ModSounds.CHASE_4;
                    default -> ModSounds.CHASE_1;
                };
                this.playEntitySound(sound, 3.0F, 1.0F);
                this.startedPlayingChaseSound = true;
                this.chaseSoundClock = Utils.secondsToTicks(5);
            }
            this.chaseSoundClock--;
        }
    }

    public void playDisappearSound() { this.playBlockPosSound(ModSounds.DISAPPEAR, 3.0F, 1.0F); }

    public void playFleeSound() {
        if (!this.alreadyPlayedFleeSound) {
            this.playEntitySound(new Random().nextInt(2) == 0 ? ModSounds.FLEE_1 : ModSounds.FLEE_2, 3.0F, 1.0F);
            this.alreadyPlayedFleeSound = true;
        }
    }

    private void playSpottedSound() {
        if (!this.alreadyPlayedSpottedSound) {
            this.playEntitySound(ModSounds.SPOTTED, 3.0F, 1.0F);
            this.alreadyPlayedSpottedSound = true;
        }
    }

    private SoundEvent chooseStep() {
        return switch (new Random().nextInt(4)) {
            case 1 -> ModSounds.CHASE_STEP_2;
            case 2 -> ModSounds.CHASE_STEP_3;
            case 3 -> ModSounds.CHASE_STEP_4;
            default -> ModSounds.CHASE_STEP_1;
        };
    }

    private SoundEvent chooseHurtSound() {
        return switch (new Random().nextInt(4)) {
            case 1 -> ModSounds.DWELLER_HURT_2;
            case 2 -> ModSounds.DWELLER_HURT_3;
            case 3 -> ModSounds.DWELLER_HURT_4;
            default -> ModSounds.DWELLER_HURT_1;
        };
    }

    @Override
    protected void playHurtSound(@NotNull DamageSource source) { this.playEntitySound(this.chooseHurtSound(), 2.0F, 1.0F); }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (!this.alreadyPlayedDeathSound) {
            this.playBlockPosSound(ModSounds.DWELLER_DEATH, 2.0F, 1.0F);
            this.alreadyPlayedDeathSound = true;
        }
    }

    public boolean isLookingAtMe(@Nullable Entity entity) {
        if (entity instanceof Player player && !player.isSpectator() && player.isAlive()) {
            return player.getEyePosition(1.0F).distanceTo(this.getPosition(1.0F)) <= ServerConfig.SPOTTING_RANGE && this.isLooking(player);
        }
        return false;
    }

    public boolean isTargetNotLooking() {
        LivingEntity target = this.getTarget();
        return target != null && !this.isLooking(target);
    }

    private boolean isLooking(LivingEntity target) {
        Vec3 viewVector = target.getViewVector(1.0F).normalize();
        Vec3 difference = new Vec3(this.getX() - target.getX(), this.getEyeY() - target.getEyeY(), this.getZ() - target.getZ()).normalize();
        return viewVector.dot(difference) > 0.0 && target.hasLineOfSight(this);
    }

    public boolean inTargetLineOfSight() { return this.getTarget() != null && this.getTarget().hasLineOfSight(this); }

    public boolean inLineOfSight(@Nullable LivingEntity target) { return target != null && target.hasLineOfSight(this); }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) { return this.chooseHurtSound(); }

    @Override
    protected SoundEvent getDeathSound() { return ModSounds.DWELLER_DEATH; }

    @Override
    protected float getSoundVolume() { return 0.4F; }
}
