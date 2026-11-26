package cn.starfall.blueprint;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Server-triggered mechanical, propulsion, work and weapon audio feedback for Best mechs. */
public final class BestMechSounds {
    private BestMechSounds() {}

    private static void play(BestMechEntity mech, SoundEvent sound, float volume, float pitch) {
        if (!mech.level().isClientSide) {
            mech.level().playSound(null, mech.getX(), mech.getY() + mech.getBbHeight() * 0.4, mech.getZ(),
                    sound, SoundSource.PLAYERS, volume, pitch);
        }
    }

    public static void footstep(BestMechEntity mech) {
        if (!mech.legsKey().isEmpty()) {
            play(mech, SoundEvents.MINECART_RIDING, 0.22f, 0.72f);
            return;
        }
        float pitch = BestMechProgression.UNIVERSAL.equals(mech.platformType()) ? 0.56f
                : BestMechProgression.COMBAT.equals(mech.platformType()) ? 0.66f : 0.74f;
        play(mech, SoundEvents.IRON_GOLEM_STEP, 0.55f, pitch);
    }

    public static void jump(BestMechEntity mech) {
        play(mech, SoundEvents.PISTON_EXTEND, 0.65f, 0.65f);
        play(mech, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.45f, 0.70f);
    }

    public static void thruster(BestMechEntity mech) {
        play(mech, SoundEvents.FIRE_EXTINGUISH, 0.22f, 1.45f);
    }

    public static void drill(BestMechEntity mech, boolean area) {
        play(mech, SoundEvents.GRINDSTONE_USE, area ? 0.65f : 0.45f, area ? 0.62f : 0.85f);
    }

    public static void weapon(BestMechEntity mech, String weapon) {
        switch (weapon) {
            case BestMechCombatSystems.LASER -> play(mech, SoundEvents.WARDEN_SONIC_BOOM, 0.45f, 1.85f);
            case BestMechCombatSystems.SNIPER -> play(mech, SoundEvents.WARDEN_SONIC_BOOM, 0.75f, 1.25f);
            case BestMechCombatSystems.MACHINE_GUN -> play(mech, SoundEvents.CROSSBOW_SHOOT, 0.55f, 1.45f);
            case BestMechCombatSystems.MELEE -> {
                play(mech, SoundEvents.PLAYER_ATTACK_SWEEP, 0.70f, 0.72f);
                play(mech, SoundEvents.ANVIL_PLACE, 0.30f, 0.85f);
            }
            default -> play(mech, SoundEvents.GUARDIAN_ATTACK, 0.45f, 1.65f);
        }
    }

    public static void overheat(BestMechEntity mech) {
        play(mech, SoundEvents.FIRE_EXTINGUISH, 0.75f, 0.85f);
        play(mech, SoundEvents.NOTE_BLOCK_PLING.value(), 0.60f, 0.55f);
    }
}
