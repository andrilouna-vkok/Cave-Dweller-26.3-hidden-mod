package de.cadentem.cave_dweller.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {
    public static final SoundEvent CAVENOISE_1 = register("cavenoise_1");
    public static final SoundEvent CAVENOISE_2 = register("cavenoise_2");
    public static final SoundEvent CAVENOISE_3 = register("cavenoise_3");
    public static final SoundEvent CAVENOISE_4 = register("cavenoise_4");
    public static final SoundEvent CHASE_STEP_1 = register("chase_step_1");
    public static final SoundEvent CHASE_STEP_2 = register("chase_step_2");
    public static final SoundEvent CHASE_STEP_3 = register("chase_step_3");
    public static final SoundEvent CHASE_STEP_4 = register("chase_step_4");
    public static final SoundEvent CHASE_1 = register("chase_1");
    public static final SoundEvent CHASE_2 = register("chase_2");
    public static final SoundEvent CHASE_3 = register("chase_3");
    public static final SoundEvent CHASE_4 = register("chase_4");
    public static final SoundEvent FLEE_1 = register("flee_1");
    public static final SoundEvent FLEE_2 = register("flee_2");
    public static final SoundEvent SPOTTED = register("spotted");
    public static final SoundEvent DISAPPEAR = register("disappear");
    public static final SoundEvent DWELLER_HURT_1 = register("dweller_hurt_1");
    public static final SoundEvent DWELLER_HURT_2 = register("dweller_hurt_2");
    public static final SoundEvent DWELLER_HURT_3 = register("dweller_hurt_3");
    public static final SoundEvent DWELLER_HURT_4 = register("dweller_hurt_4");
    public static final SoundEvent DWELLER_DEATH = register("dweller_death");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath("betterimprovements", name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Touching this class registers all sounds. */
    public static void init() {}
}
