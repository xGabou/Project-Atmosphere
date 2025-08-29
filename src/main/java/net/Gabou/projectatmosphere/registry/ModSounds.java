package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModSounds {
    // Main DeferredRegister for SoundEvents
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, ProjectAtmosphere.MODID);

    // Example sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> WEATHER_SIREN =
            registerSound("weather_siren");

    public static final DeferredHolder<SoundEvent, SoundEvent> TORNADO_ROAR =
            registerSound("tornado_roar");

    // Hook into mod event bus
    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, name)));
    }
}
