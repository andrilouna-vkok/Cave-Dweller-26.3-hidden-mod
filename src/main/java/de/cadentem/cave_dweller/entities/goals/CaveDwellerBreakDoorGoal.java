package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.util.Utils;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class CaveDwellerBreakDoorGoal extends BreakDoorGoal {
   public CaveDwellerBreakDoorGoal(Mob mob, Predicate<Difficulty> validDifficulties) {
      super(mob, validDifficulties);
   }

   protected int getDoorBreakTime() {
      return Utils.secondsToTicks(ServerConfig.BREAK_DOOR_TIME);
   }

   /**
    * Vanilla removes the door with removeBlock(pos, false), which drops nothing.
    * Watch for that removal and drop the door's loot ourselves.
    */
   @Override
   public void tick() {
      Level level = this.mob.level();
      BlockPos pos = this.doorPos;
      BlockState before = level.getBlockState(pos);
      boolean isDoor = before.getBlock() instanceof DoorBlock;

      // Only the lower half of a door has the drop in its loot table
      BlockPos lowerPos = isDoor && before.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
      BlockState lowerState = level.getBlockState(lowerPos);

      super.tick();

      if (isDoor && level.getBlockState(pos).isAir()) {
         Block.dropResources(lowerState, level, lowerPos);
      }
   }
}
