package de.cadentem.cave_dweller.entities.goals;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;

public class CaveDwellerFleeGoal extends Goal {
   private final CaveDwellerEntity caveDweller;
   private final double speedModifier;
   private float ticksUntilLeave;
   private float ticksUntilFlee;
   private boolean shouldLeave;
   private Path fleePath;
   private int ticksUntilNextPathRecalculation;

   public CaveDwellerFleeGoal(CaveDwellerEntity caveDweller, float ticksUntilLeave, double speedModifier) {
      this.caveDweller = caveDweller;
      this.ticksUntilLeave = ticksUntilLeave;
      this.ticksUntilFlee = 10.0F;
      this.speedModifier = speedModifier;
   }

   public boolean canUse() {
      if (this.caveDweller.isInvisible()) {
         return false;
      } else {
         return this.caveDweller.currentRoll != Roll.FLEE ? false : this.caveDweller.getTarget() != null;
      }
   }

   public boolean canContinueToUse() {
      return this.caveDweller.currentRoll != Roll.FLEE ? false : this.caveDweller.getTarget() != null;
   }

   public void start() {
      this.setFleePath();
      this.caveDweller.spottedByPlayer = false;
      this.shouldLeave = false;
   }

   public void tick() {
      LivingEntity target = this.caveDweller.getTarget();
      if (this.shouldLeave && (!this.caveDweller.isLookingAtMe(target) || !this.caveDweller.inTargetLineOfSight())) {
         this.caveDweller.playDisappearSound();
         this.caveDweller.discard();
      }

      this.ticksUntilFlee--;
      this.tickStareClock();
      if (this.ticksUntilFlee <= 0.0F) {
         this.fleeTick();
         this.caveDweller.isFleeing = true;
         this.caveDweller.getEntityData().set(CaveDwellerEntity.FLEEING_ACCESSOR, true);
      } else if (target != null) {
         this.caveDweller.getLookControl().setLookAt(target, 180.0F, 1.0F);
      }
   }

   private void setFleePath() {
      LivingEntity target = this.caveDweller.getTarget();
      if (target != null) {
         Vec3 fleePosition = DefaultRandomPos.getPosAway(this.caveDweller, 32, 7, target.position());
         if (fleePosition != null) {
            this.fleePath = this.caveDweller.getNavigation().createPath(fleePosition.x, fleePosition.y, fleePosition.z, 0);
            if (this.fleePath == null) {
               this.fleePath = this.caveDweller.createShortPath(fleePosition);
            }
         }
      }
   }

   public void tickStareClock() {
      this.ticksUntilLeave--;
      if (this.ticksUntilLeave < 0.0F) {
         this.shouldLeave = true;
      }
   }

   public void fleeTick() {
      if (this.fleePath == null || this.fleePath.isDone()) {
         this.setFleePath();
      }

      this.caveDweller.playFleeSound();
      this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
      if (this.ticksUntilNextPathRecalculation == 0) {
         this.ticksUntilNextPathRecalculation = 2;
         if (!this.caveDweller.getNavigation().moveTo(this.fleePath, this.speedModifier)) {
            this.ticksUntilNextPathRecalculation += 2;
         }

         this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
      }
   }
}
