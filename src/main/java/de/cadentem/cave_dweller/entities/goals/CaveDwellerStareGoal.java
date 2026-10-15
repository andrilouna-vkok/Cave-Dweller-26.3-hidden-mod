package de.cadentem.cave_dweller.entities.goals;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;

public class CaveDwellerStareGoal extends Goal {
   private final CaveDwellerEntity caveDweller;
   private boolean wasNotLookingPreviously;
   private int lookedAtCount;

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
         } else {
            this.caveDweller.pleaseStopMoving = true;
            this.caveDweller.getNavigation().stop();
            this.caveDweller.setDeltaMovement(Vec3.ZERO);
         }

         this.caveDweller.getLookControl().setLookAt(target);
      }

      this.wasNotLookingPreviously = isNotLooking;
   }
}
