package de.cadentem.cave_dweller.mimic;

import de.cadentem.cave_dweller.util.Utils;
import java.util.EnumSet;
import java.util.Random;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Drives the invisible body of a mimic. First it wanders around like a player (walks somewhere, stops, looks
 * around, sometimes looks at the target, sometimes jumps). Then it stares at the target and creeps closer, but only
 * while the target is not looking at it.
 */
public class MimicGoal extends Goal {
    private static final Random RANDOM = new Random();

    private final PathfinderMob mob;
    private final MimicManager.Mimic mimic;
    private int pause;
    private int repathDelay;
    private int lookDelay;
    private Vec3 lookPoint;

    public MimicGoal(PathfinderMob mob, MimicManager.Mimic mimic) {
        this.mob = mob;
        this.mimic = mimic;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Player victim = this.mob.level().getPlayerByUUID(this.mimic.victim);
        if (victim == null) {
            return;
        }

        if (this.mimic.wanderTicks > 0) {
            this.wander(victim);
        } else {
            this.stalk(victim);
        }
    }

    private void wander(Player victim) {
        if (this.mob.onGround() && RANDOM.nextInt(400) == 0) {
            this.mob.getJumpControl().jump();
        }

        if (this.pause > 0) {
            this.pause--;
            this.mob.getNavigation().stop();
            this.lookAround(victim);
            return;
        }

        if (this.mob.getNavigation().isDone()) {
            if (RANDOM.nextInt(3) == 0) {
                this.pause = 40 + RANDOM.nextInt(100);
                return;
            }
            Vec3 spot = DefaultRandomPos.getPos(this.mob, 10, 4);
            if (spot != null) {
                this.mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9);
            } else {
                this.pause = 20;
            }
        }
    }

    private void lookAround(Player victim) {
        this.lookDelay--;
        if (this.lookPoint == null || this.lookDelay <= 0) {
            this.lookDelay = 30 + RANDOM.nextInt(70);
            if (RANDOM.nextInt(10) < 4) {
                this.lookPoint = victim.getEyePosition();
            } else {
                double angle = RANDOM.nextDouble() * Math.PI * 2.0;
                this.lookPoint = new Vec3(this.mob.getX() + Math.cos(angle) * 8.0, this.mob.getEyeY() + RANDOM.nextDouble() * 2.0 - 1.0,
                        this.mob.getZ() + Math.sin(angle) * 8.0);
            }
        }
        this.mob.getLookControl().setLookAt(this.lookPoint.x, this.lookPoint.y, this.lookPoint.z);
    }

    private void stalk(Player victim) {
        Vec3 eyes = victim.getEyePosition();
        this.mob.getLookControl().setLookAt(eyes.x, eyes.y, eyes.z);

        if (Utils.isWatching(victim, this.mob)) {
            // Frozen while watched
            this.mob.getNavigation().stop();
            return;
        }

        this.repathDelay--;
        if (this.repathDelay <= 0) {
            this.repathDelay = 5;
            this.mob.getNavigation().moveTo(victim, 1.0);
        }
    }
}
