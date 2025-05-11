package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.entity.CloudEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityRegistrar {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ProjectAtmosphere.MODID);

    public static final RegistryObject<EntityType<CloudEntity>> CLOUD_ENTITY = ENTITIES.register(
            "cloud_entity",
            () -> EntityType.Builder.<CloudEntity>of(CloudEntity::new, MobCategory.MISC)
                    .sized(2.0f, 1.0f)
                    .setUpdateInterval(20)
                    .build("cloud_entity")
    );

    public static final RegistryObject<EntityType<>>

    public static void registerEntities(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
