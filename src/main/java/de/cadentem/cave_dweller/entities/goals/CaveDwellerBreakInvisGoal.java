package de.cadentem.cave_dweller.entities.goals;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;

public class CaveDwellerBreakInvisGoal extends Goal {
   private final CaveDwellerEntity caveDweller;

   public CaveDwellerBreakInvisGoal(CaveDwellerEntity caveDweller) {
      this.caveDweller = caveDweller;
   }

   public boolean canUse() {
      LivingEntity target = Utils.getValidTarget(this.caveDweller);
      return this.caveDweller.isInvisible() && (!this.caveDweller.inLineOfSight(target) || !this.caveDweller.isLookingAtMe(target));
   }

   public void start() {
      super.start();
      this.caveDweller.setInvisible(false);
   }
}
