package net.Gabou.projectatmosphere.registry;

import com.mojang.logging.LogUtils;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.blocks.*;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

public class ModBlocks {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> REGISTRY =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ProjectAtmosphere.MODID);

    public static final RegistryObject<Block> DUST = REGISTRY.register("dust", () ->
            new DustLayerBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.1f)
                    .sound(SoundType.SAND)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((state, getter, pos) -> false)
            ));
    public static final RegistryObject<Block> WEATHER_VANE = REGISTRY.register("weather_vane", () ->
            new WeatherVaneBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> ANEMOMETER = REGISTRY.register("anemometer", () ->
            new AnemometerBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> THERMOMETER_BLOCK = REGISTRY.register("thermometer_block", () ->
            new ThermometerBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> BAROMETER_BLOCK = REGISTRY.register("barometer_block", () ->
            new BarometreBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> HUMIDIMETER_BLOCK = REGISTRY.register("humidimeter_block", () ->
            new HumidimeterBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

}
