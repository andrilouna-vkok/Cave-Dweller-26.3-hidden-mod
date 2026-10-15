package de.cadentem.cave_dweller.entities.goals;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;

public class CaveDwellerTargetSeesMeGoal extends NearestAttackableTargetGoal<Player> {
   private final CaveDwellerEntity caveDweller;

   public CaveDwellerTargetSeesMeGoal(CaveDwellerEntity mob) {
      super(mob, Player.class, false);
      this.caveDweller = mob;
   }

   public boolean canUse() {
      if (this.caveDweller.isInvisible()) {
         return false;
      }

      this.target = Utils.getValidTarget(this.caveDweller);
      return !Utils.isValidPlayer(this.target) ? false : this.caveDweller.inLineOfSight(this.target) && this.caveDweller.isLookingAtMe(this.target);
   }

   public void start() {
      this.caveDweller.setTarget(this.target);
      this.caveDweller.spottedByPlayer = true;
      this.caveDweller.getEntityData().set(CaveDwellerEntity.SPOTTED_ACCESSOR, true);
      this.caveDweller.reRoll();
      super.start();
   }

   public void stop() {
      super.stop();
   }

   public boolean canContinueToUse() {
      return Utils.isValidPlayer(this.target);
   }

   public void tick() {
      super.tick();
   }
}
