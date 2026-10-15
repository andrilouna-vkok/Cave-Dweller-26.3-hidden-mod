package de.cadentem.cave_dweller.util;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Utils {
    public static int ticksToSeconds(int ticks) { return ticks / 20; }
    public static int secondsToTicks(int seconds) { return seconds * 20; }
    public static int minutesToTicks(int minutes) { return secondsToTicks(minutes * 60); }

    public static boolean isValidPlayer(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) return false;
        if (!player.isAlive()) return false;
        if (!ServerConfig.TARGET_INVISIBLE && player.isInvisible()) return false;
        return !player.isCreative() && !player.isSpectator();
    }

    public static LivingEntity getValidTarget(@NotNull CaveDwellerEntity caveDweller) {
        return caveDweller.level().getNearestPlayer(
                caveDweller.position().x, caveDweller.position().y, caveDweller.position().z, 128.0, Utils::isValidPlayer);
    }

    /** Replacement for BlockState#blocksMotion(): a block "blocks motion" if it has a collision shape. */
    public static boolean blocksMotion(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
