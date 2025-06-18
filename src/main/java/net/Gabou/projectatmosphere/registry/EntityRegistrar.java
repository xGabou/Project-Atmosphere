package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.entity.CloudEntity;
import net.Gabou.projectatmosphere.entity.SmallNormalCloud1Entity;
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
            () -> EntityType.Builder.of(CloudEntity::new, MobCategory.MISC)
                    .sized(2.0f, 1.0f)
                    .setUpdateInterval(20)
                    .build("cloud_entity")
    );
    public static final RegistryObject<EntityType<CloudEntity>> CLOUD_ENTITY1 = ENTITIES.register(
            "cloud1_entity",
            () -> EntityType.Builder.of(CloudEntity::new, MobCategory.MISC)
                    .sized(2.0f, 1.0f)
                    .setUpdateInterval(20)
                    .build("cloud1_entity")
    );
    public static final RegistryObject<EntityType<CloudEntity>> CLOUD_ENTITY2 = ENTITIES.register(
            "cloud2_entity",
            () -> EntityType.Builder.of(CloudEntity::new, MobCategory.MISC)
                    .sized(2.0f, 1.0f)
                    .setUpdateInterval(20)
                    .build("cloud2_entity")
    );
    public static final RegistryObject<EntityType<CloudEntity>> CLOUD_ENTITY3 = ENTITIES.register(
            "cloud3_entity",
            () -> EntityType.Builder.of(CloudEntity::new, MobCategory.MISC)
                    .sized(2.0f, 1.0f)
                    .setUpdateInterval(20)
                    .build("cloud3_entity")
    );
    public static final RegistryObject<EntityType<CloudEntity>> CLOUD_ENTITY4 = ENTITIES.register(
            "cloud4_entity",
            () -> EntityType.Builder.of(CloudEntity::new, MobCategory.MISC)
                    .sized(2.0f, 1.0f)
                    .setUpdateInterval(20)
                    .build("cloud4_entity")
    );
    public static final RegistryObject<EntityType<CloudEntity>> CLOUD_ENTITY5 = ENTITIES.register(
            "cloud5_entity",
            () -> EntityType.Builder.of(CloudEntity::new, MobCategory.MISC)
                    .sized(2.0f, 1.0f)
                    .setUpdateInterval(20)
                    .build("cloud5_entity")
    );

    public static final RegistryObject<EntityType<SmallNormalCloud1Entity>> SMALL_NORMAL_CLOUD_1_ENTITY = ENTITIES.register(
            "small_normal_cloud_1_entity",
            () -> EntityType.Builder.of(SmallNormalCloud1Entity::new, MobCategory.MISC)
                    .sized(2.0f, 1.0f)
                    .setUpdateInterval(20)
                    .build("small_normal_cloud_1_entity")
    );

    public static void registerEntities(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
