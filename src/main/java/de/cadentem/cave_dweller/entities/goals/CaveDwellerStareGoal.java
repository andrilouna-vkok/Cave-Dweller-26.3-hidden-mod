package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Stalking: creeps toward the target while it is not looking, freezes completely while it is watched, and every few
 * seconds does something unsettling (noise, snuffing a light, a wave of darkness). After being looked at repeatedly
 * he eventually vanishes.
 */
public class CaveDwellerStareGoal extends Goal {
   private final CaveDwellerEntity caveDweller;
   private boolean wasNotLookingPreviously;
   private int lookedAtCount;
   private int creepTimer;

   public CaveDwellerStareGoal(CaveDwellerEntity caveDweller) {
      this.caveDweller = caveDweller;
   }

   public boolean canUse() {
      if (this.caveDweller.isInvisible()) {
         return false;
      } else {
         return this.caveDweller.getTarget() == null ? false : this.caveDweller.currentRoll == Roll.STARE;
      }
   }

   public boolean canContinueToUse() {
      return this.caveDweller.getTarget() == null ? false : this.caveDweller.currentRoll == Roll.STARE;
   }

   public boolean requiresUpdateEveryTick() {
      return true;
   }

   public void stop() {
      super.stop();
      this.lookedAtCount = 0;
      this.wasNotLookingPreviously = false;
      this.caveDweller.pleaseStopMoving = false;
      this.caveDweller.getEntityData().set(CaveDwellerEntity.SPOTTED_ACCESSOR, false);
   }

   public void tick() {
      LivingEntity target = this.caveDweller.getTarget();
      boolean isNotLooking = this.caveDweller.isTargetNotLooking();
      if (this.wasNotLookingPreviously && !isNotLooking) {
         this.lookedAtCount++;
      }

      if (this.lookedAtCount > 10 && isNotLooking && this.caveDweller.getRandom().nextDouble() < 0.1) {
         this.caveDweller.playDisappearSound();
         this.caveDweller.discard();
      }

      if (target != null) {
         if (isNotLooking) {
            this.caveDweller.pleaseStopMoving = false;
            this.caveDweller.getNavigation().moveTo(target, 1.0);
            this.creepyTick(target);
         } else {
            this.caveDweller.pleaseStopMoving = true;
            this.caveDweller.getNavigation().stop();
            this.caveDweller.setDeltaMovement(Vec3.ZERO);
         }

         this.caveDweller.getLookControl().setLookAt(target);
      }

      this.wasNotLookingPreviously = isNotLooking;
   }

   /** Only runs while the target is not looking at him: something unsettling roughly every 5-10 seconds. */
   private void creepyTick(LivingEntity target) {
      if (!(target instanceof ServerPlayer player)) return;
      this.creepTimer++;
      if (this.creepTimer < 100 || this.caveDweller.getRandom().nextDouble() >= 0.02) return;
      this.creepTimer = 0;

      RandomSource random = this.caveDweller.getRandom();
      switch (random.nextInt(3)) {
         case 0 -> {
            if (!ServerConfig.SOUNDS_ONLY_WHILE_CHASING) {
               SoundEvent[] noises = {ModSounds.CAVENOISE_1, ModSounds.CAVENOISE_2, ModSounds.CAVENOISE_3, ModSounds.CAVENOISE_4};
               player.playSound(noises[random.nextInt(noises.length)], 1.0F, 0.7F + random.nextFloat() * 0.3F);
            }
         }
         case 1 -> {
            if (ServerConfig.SNUFF_LIGHTS) this.snuffLight(player);
         }
         default -> {
            if (ServerConfig.CREEPY_DARKNESS && this.caveDweller.distanceTo(player) < 25.0F) {
               player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
            }
         }
      }
   }

   private void snuffLight(ServerPlayer player) {
      Level level = player.level();
      BlockPos center = player.blockPosition();
      RandomSource random = this.caveDweller.getRandom();
      for (int i = 0; i < 80; i++) {
         BlockPos pos = center.offset(random.nextInt(15) - 7, random.nextInt(7) - 3, random.nextInt(15) - 7);
         BlockState state = level.getBlockState(pos);
         if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
               || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)
               || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
            level.destroyBlock(pos, true);
            return;
         }
      }
   }
}
