package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * While chasing, if the Cave Dweller is stuck behind blocks, it breaks the 1x3 column of blocks directly in front of
 * it (toward the target). Bedrock and anything harder than BREAK_BLOCK_MAX_HARDNESS are left alone.
 */
public class CaveDwellerBreakBlocksGoal extends Goal {
    private final CaveDwellerEntity dweller;
    private int stuckTicks;

    public CaveDwellerBreakBlocksGoal(CaveDwellerEntity dweller) {
        this.dweller = dweller;
    }

    @Override
    public boolean canUse() {
        if (!ServerConfig.CAN_BREAK_BLOCKS || this.dweller.isInvisible()) return false;
        LivingEntity target = this.dweller.getTarget();
        return target != null && target.isAlive() && this.dweller.getEntityData().get(CaveDwellerEntity.AGGRO_ACCESSOR);
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        this.stuckTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.dweller.getTarget();
        if (target == null) return;

        boolean stuck = this.dweller.horizontalCollision
                || (this.dweller.getNavigation().isDone() && this.dweller.distanceToSqr(target) > 4.0);
        if (!stuck) {
            this.stuckTicks = 0;
            return;
        }

        this.stuckTicks++;
        if (this.stuckTicks < Math.max(1, Utils.secondsToTicks(ServerConfig.BREAK_BLOCK_TIME))) return;
        this.stuckTicks = 0;

        double dx = target.getX() - this.dweller.getX();
        double dz = target.getZ() - this.dweller.getZ();
        int stepX = 0;
        int stepZ = 0;
        if (Math.abs(dx) > Math.abs(dz)) stepX = dx > 0 ? 1 : -1;
        else stepZ = dz > 0 ? 1 : -1;

        Level level = this.dweller.level();
        BlockPos front = BlockPos.containing(this.dweller.getX() + stepX, this.dweller.getY(), this.dweller.getZ() + stepZ);
        for (int up = 0; up < 3; up++) {
            this.breakBlock(level, front.above(up));
        }
        // Target is clearly above: open the ceiling so it can climb out
        if (target.getY() > this.dweller.getY() + 2.0) {
            this.breakBlock(level, BlockPos.containing(this.dweller.getX(), this.dweller.getY() + 3.0, this.dweller.getZ()));
        }
    }

    private void breakBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !Utils.blocksMotion(level, pos)) return;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > ServerConfig.BREAK_BLOCK_MAX_HARDNESS) return;
        level.destroyBlock(pos, true, this.dweller);
    }
}
