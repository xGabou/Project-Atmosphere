package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ProjectAtmosphere.MODID);

    // ---------------------------------------------------------------------
    // Registered sounds
    // ---------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> WEATHER_SIREN = registerSound("weather_siren");

    public static final RegistryObject<SoundEvent> TORNADO_ROAR = registerSound("tornado_roar");

    public static final RegistryObject<SoundEvent> THUNDER_IN_CLOUDS = registerSound("thunder.in_clouds");
    public static final RegistryObject<SoundEvent> THUNDER_HIT_DISTANT = registerSound("thunder.hit_distant");
    public static final RegistryObject<SoundEvent> THUNDER_HIT_SEMI_DISTANT = registerSound("thunder.hit_semi_distant");
    public static final RegistryObject<SoundEvent> THUNDER_RUMBLING_CLOSE = registerSound("thunder.rumbling_close");
    public static final RegistryObject<SoundEvent> THUNDER_RUMBLING_DISTANT = registerSound("thunder.rumbling_distant");

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------
    private static RegistryObject<SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(ProjectAtmosphere.MODID, name)));
    }
}
