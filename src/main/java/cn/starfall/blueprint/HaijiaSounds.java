package cn.starfall.blueprint;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Dedicated mix and captions using the game's bundled sound library. */
public final class HaijiaSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,StarfallBlueprint.ID);
    private static DeferredHolder<SoundEvent,SoundEvent> sound(String name){return SOUNDS.register("hj_"+name,()->SoundEvent.createVariableRangeEvent(StarfallBlueprint.id("hj_"+name)));}
    public static final DeferredHolder<SoundEvent,SoundEvent> ENGINE=sound("engine"),BOOST=sound("boost"),LASER=sound("laser"),MISSILE=sound("missile"),BOMB=sound("bomb_release"),SELECT=sound("weapon_select");
    private HaijiaSounds(){}
}
