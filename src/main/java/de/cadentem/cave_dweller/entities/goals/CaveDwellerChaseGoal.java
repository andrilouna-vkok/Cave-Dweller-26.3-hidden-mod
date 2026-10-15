package de.cadentem.cave_dweller.entities.goals;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import java.util.EnumSet;

public class CaveDwellerChaseGoal extends Goal {
   private final CaveDwellerEntity caveDweller;
   private final float ticksUntilChase;
   private final double speedModifier;
   private final int ticksToSqueeze;
   private final boolean canPenalize = false;
   private final boolean followTargetEvenIfNotSeen;
   private Path shortPath;
   private BlockPos nodePosition;
   private Vec3 vecNodePosition;
   private Vec3 vecMobPos;
   private Vec3 xPathStartVec;
   private Vec3 zPathStartVec;
   private Vec3 xPathTargetVec;
   private Vec3 zPathTargetVec;
   private Vec3 vecTargetPos;
   private Vec3 previousNodePosition;
   private double pathedTargetX;
   private double pathedTargetY;
   private double pathedTargetZ;
   private float currentTicksUntilChase;
   private long lastGameTimeCheck;
   private int ticksUntilNextPathRecalculation;
   private int ticksUntilNextAttack;
   private int failedPathFindingPenalty;
   private int currentTicksToSqueeze;
   private int ticksUntilLeave;
   private boolean squeezing;

   public CaveDwellerChaseGoal(CaveDwellerEntity caveDweller, double speedModifier, boolean followTargetEvenIfNotSeen, float ticksUntilChase) {
      this.caveDweller = caveDweller;
      this.speedModifier = speedModifier;
      this.followTargetEvenIfNotSeen = followTargetEvenIfNotSeen;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      this.ticksUntilChase = ticksUntilChase;
      this.currentTicksUntilChase = ticksUntilChase;
      this.ticksToSqueeze = 15;
      this.ticksUntilLeave = Utils.secondsToTicks(ServerConfig.TIME_UNTIL_LEAVE_CHASE);
   }

   public boolean canUse() {
      if (this.caveDweller.isInvisible()) {
         return false;
      }

      if (this.caveDweller.currentRoll == Roll.CHASE && this.caveDweller.isLookingAtMe(this.caveDweller.getTarget())) {
         long ticks = this.caveDweller.level().getGameTime();
         if (ticks - this.lastGameTimeCheck < 20L) {
            return false;
         } else {
            this.lastGameTimeCheck = ticks;
            LivingEntity target = this.caveDweller.getTarget();
            if (target == null) {
               return false;
            } else if (!target.isAlive()) {
               return false;
            } else {
               Path path = this.caveDweller.getNavigation().createPath(target, 0);
               if (path != null) {
                  return true;
               } else {
                  boolean canAttack = this.getAttackReachSqr(target)
                     >= this.caveDweller.distanceToSqr(target.getX(), target.getY(), target.getZ());
                  if (!canAttack) {
                     path = this.caveDweller.createShortPath(target);
                     return path != null;
                  } else {
                     return false;
                  }
               }
            }
         }
      } else {
         return false;
      }
   }

   public boolean canContinueToUse() {
      LivingEntity target = this.caveDweller.getTarget();
      if (target == null) {
         return false;
      } else if (!target.isAlive()) {
         this.caveDweller.discard();
         return false;
      } else if (!this.followTargetEvenIfNotSeen) {
         return !this.caveDweller.getNavigation().isDone();
      } else {
         return Utils.isValidPlayer(target);
      }
   }

   public void start() {
      this.caveDweller.setAggressive(true);
      this.ticksUntilNextPathRecalculation = 0;
      this.ticksUntilNextAttack = 0;
   }

   public void stop() {
      LivingEntity target = this.caveDweller.getTarget();
      if (!Utils.isValidPlayer(target)) {
         this.caveDweller.setTarget(null);
      }

      this.caveDweller.squeezeCrawling = false;
      this.caveDweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, false);
      this.caveDweller.refreshDimensions();
      this.currentTicksUntilChase = this.ticksUntilChase;
      this.caveDweller.getNavigation().stop();
   }

   public boolean requiresUpdateEveryTick() {
      return true;
   }

   public void tick() {
      this.caveDweller.squeezeCrawling = this.squeezing;
      LivingEntity target = this.caveDweller.getTarget();
      this.tickAggroClock();
      if (!this.squeezing && target != null) {
         if (this.caveDweller.isAggressive()) {
            this.caveDweller.getLookControl().setLookAt(target, 90.0F, 90.0F);
         } else {
            this.caveDweller.getLookControl().setLookAt(target, 180.0F, 1.0F);
         }
      }

      if ((Boolean)this.caveDweller.getEntityData().get(CaveDwellerEntity.AGGRO_ACCESSOR)) {
         if (this.squeezing) {
            this.squeezingTick();
         } else {
            this.aggroTick();
         }
      }

      this.ticksUntilLeave--;
      if (this.ticksUntilLeave <= 0 && (!this.caveDweller.isLookingAtMe(target) || !this.inPlayerLineOfSight())) {
         this.caveDweller.discard();
      }
   }

   private void tickAggroClock() {
      this.currentTicksUntilChase--;
      if (this.currentTicksUntilChase <= 0.0F) {
         this.caveDweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, true);
      }

      this.caveDweller.refreshDimensions();
   }

   private Path getShortPath(LivingEntity target) {
      this.shortPath = this.caveDweller.createShortPath(target);
      return this.shortPath;
   }

   private void squeezingTick() {
      Path path = this.caveDweller.getNavigation().getPath();
      if (path != null && !path.isDone()) {
         this.nodePosition = path.getNextNodePos();
      }

      this.caveDweller.getNavigation().stop();
      if (this.nodePosition == null) {
         this.stopSqueezing();
      } else {
         if (this.vecNodePosition == null) {
            this.vecNodePosition = new Vec3(this.nodePosition.getX(), this.nodePosition.getY(), this.nodePosition.getZ());
         }

         this.previousNodePosition = this.vecNodePosition;
         Vec3 vecOldMobPos = this.caveDweller.getPosition(1.0F);
         if (this.xPathStartVec == null) {
            if (vecOldMobPos.x < this.vecNodePosition.x) {
               this.xPathStartVec = new Vec3(
                  this.vecNodePosition.x - 1.0, this.vecNodePosition.y - 1.0, this.vecNodePosition.z + 0.5
               );
               this.xPathTargetVec = new Vec3(
                  this.vecNodePosition.x + 1.0, this.vecNodePosition.y - 1.0, this.vecNodePosition.z + 0.5
               );
            } else {
               this.xPathStartVec = new Vec3(
                  this.vecNodePosition.x + 1.0, this.vecNodePosition.y - 1.0, this.vecNodePosition.z + 0.5
               );
               this.xPathTargetVec = new Vec3(
                  this.vecNodePosition.x - 1.0, this.vecNodePosition.y - 1.0, this.vecNodePosition.z + 0.5
               );
            }
         }

         if (this.zPathStartVec == null) {
            if (vecOldMobPos.z < this.vecNodePosition.z) {
               this.zPathStartVec = new Vec3(
                  this.vecNodePosition.x + 0.5, this.vecNodePosition.y - 1.0, this.vecNodePosition.z - 1.0
               );
               this.zPathTargetVec = new Vec3(
                  this.vecNodePosition.x + 0.5, this.vecNodePosition.y - 1.0, this.vecNodePosition.z + 1.0
               );
            } else {
               this.zPathStartVec = new Vec3(
                  this.vecNodePosition.x + 0.5, this.vecNodePosition.y - 1.0, this.vecNodePosition.z + 1.0
               );
               this.zPathTargetVec = new Vec3(
                  this.vecNodePosition.x + 0.5, this.vecNodePosition.y - 1.0, this.vecNodePosition.z - 1.0
               );
            }
         }

         BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(this.xPathTargetVec.x, this.xPathTargetVec.y, this.xPathTargetVec.z);
         boolean xBlocked = Utils.blocksMotion(this.caveDweller.level(), blockpos$mutableblockpos);
         blockpos$mutableblockpos = new BlockPos.MutableBlockPos(this.zPathTargetVec.x, this.zPathTargetVec.y, this.zPathTargetVec.z);
         boolean zBlocked = Utils.blocksMotion(this.caveDweller.level(), blockpos$mutableblockpos);
         if (xBlocked) {
            this.vecMobPos = this.zPathStartVec;
            this.vecTargetPos = this.zPathTargetVec;
         }

         if (zBlocked) {
            this.vecMobPos = this.xPathStartVec;
            this.vecTargetPos = this.xPathTargetVec;
         }

         if (this.vecTargetPos != null && this.vecMobPos != null) {
            this.currentTicksToSqueeze++;
            float tickF = (float)this.currentTicksToSqueeze / this.ticksToSqueeze;
            Vec3 vecCurrentMobPos = new Vec3(
               lerp(this.vecMobPos.x, this.vecTargetPos.x, tickF),
               this.vecMobPos.y,
               lerp(this.vecMobPos.z, this.vecTargetPos.z, tickF)
            );
            Vec3 rotAxis = new Vec3(
               this.vecTargetPos.x - this.vecMobPos.x, 0.0, this.vecTargetPos.z - this.vecMobPos.z
            );
            rotAxis = rotAxis.normalize();
            double rotAngle = Math.toDegrees(Math.atan2(-rotAxis.x, rotAxis.z));
            this.caveDweller.setYHeadRot((float)rotAngle);
            this.caveDweller
               .snapTo(vecCurrentMobPos.x, vecCurrentMobPos.y, vecCurrentMobPos.z, (float)rotAngle, (float)rotAngle);
            if (tickF >= 1.0F) {
               this.caveDweller.setPos(this.vecTargetPos.x, this.vecTargetPos.y, this.vecTargetPos.z);
               this.stopSqueezing();
            }
         } else {
            this.stopSqueezing();
         }
      }
   }

   private void stopSqueezing() {
      this.squeezing = false;
      this.caveDweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, false);
   }

   private void startSqueezing() {
      this.vecNodePosition = null;
      this.vecMobPos = null;
      this.xPathStartVec = null;
      this.zPathStartVec = null;
      this.xPathTargetVec = null;
      this.zPathTargetVec = null;
      this.vecTargetPos = null;
      this.currentTicksToSqueeze = 0;
      this.squeezing = true;
      this.caveDweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, true);
      this.nodePosition = null;
   }

   private boolean shouldSqueeze(Path pathToCheck) {
      if (pathToCheck == null) {
         return false;
      }

      if (!pathToCheck.isDone()) {
         BlockPos blockPosition = pathToCheck.getNextNodePos();
         if (this.previousNodePosition != null
            && blockPosition.getX() == (int)this.previousNodePosition.x
            && blockPosition.getY() == (int)this.previousNodePosition.y
            && blockPosition.getZ() == (int)this.previousNodePosition.z) {
            return false;
         }

         return Utils.blocksMotion(this.caveDweller.level(), blockPosition.above());
      } else {
         return false;
      }
   }

   private void aggroTick() {
      this.caveDweller.playChaseSound();
      LivingEntity target = this.caveDweller.getTarget();
      boolean shouldUseShortPath = true;
      Path path = this.caveDweller.getNavigation().getPath();
      if (path != null) {
         Node finalPathPoint = path.getEndNode();
         if (finalPathPoint != null && !path.isDone()) {
            shouldUseShortPath = false;
         }
      }

      if (shouldUseShortPath) {
         path = this.getShortPath(target);
      }

      if (this.shouldSqueeze(path)) {
         this.startSqueezing();
         this.squeezing = true;
         this.caveDweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, true);
      } else if (target != null) {
         double distance = this.caveDweller.distanceToSqr(target);
         this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
         if (this.ticksUntilNextPathRecalculation == 0
            && (this.followTargetEvenIfNotSeen || this.caveDweller.getSensing().hasLineOfSight(target))
            && (
               this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0
                  || target.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                  || this.caveDweller.getRandom().nextFloat() < 0.05
            )) {
            this.pathedTargetX = target.getX();
            this.pathedTargetY = target.getY();
            this.pathedTargetZ = target.getZ();
            this.ticksUntilNextPathRecalculation = 2;
            if (distance > 1024.0) {
               this.ticksUntilNextPathRecalculation += 10;
            } else if (distance > 256.0) {
               this.ticksUntilNextPathRecalculation += 5;
            }

            boolean canMoveTo;
            if (shouldUseShortPath) {
               canMoveTo = this.caveDweller.getNavigation().moveTo(this.shortPath, this.speedModifier);
            } else {
               canMoveTo = this.caveDweller.getNavigation().moveTo(target, this.speedModifier);
            }

            if (!canMoveTo) {
               this.ticksUntilNextPathRecalculation += 8;
            }

            this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
         }

         this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
         this.checkAndPerformAttack(target, distance);
      }
   }

   private boolean inPlayerLineOfSight() {
      LivingEntity target = this.caveDweller.getTarget();
      return target != null && target.hasLineOfSight(this.caveDweller);
   }

   private void checkAndPerformAttack(LivingEntity target, double distanceToTarget) {
      double attackReach = this.getAttackReachSqr(target);
      if (distanceToTarget <= attackReach && this.ticksUntilNextAttack <= 0) {
         this.resetAttackCooldown();
         if (this.caveDweller.level() instanceof ServerLevel serverLevel) {
            this.caveDweller.doHurtTarget(serverLevel, target);
         }
      }
   }

   private void resetAttackCooldown() {
      this.ticksUntilNextAttack = this.adjustedTickDelay(20);
   }

   private double getAttackReachSqr(LivingEntity target) {
      return this.caveDweller.getBbWidth() * 4.0F * this.caveDweller.getBbWidth() * 4.0F + target.getBbWidth();
   }

   private static double lerp(double a, double b, double f) {
      return (b - a) * f + a;
   }
}
