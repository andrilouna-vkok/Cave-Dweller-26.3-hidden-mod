package de.cadentem.cave_dweller.entities.goals;

import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.util.Utils;
import java.util.function.Predicate;

public class CaveDwellerBreakDoorGoal extends BreakDoorGoal {
   public CaveDwellerBreakDoorGoal(Mob mob, Predicate<Difficulty> validDifficulties) {
      super(mob, validDifficulties);
   }

   protected int getDoorBreakTime() {
      return Utils.secondsToTicks(ServerConfig.BREAK_DOOR_TIME);
   }
}
