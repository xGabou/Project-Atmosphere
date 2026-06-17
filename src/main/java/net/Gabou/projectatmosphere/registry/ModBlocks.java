package net.Gabou.projectatmosphere.registry;

import com.mojang.logging.LogUtils;
import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.blocks.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;

public class ModBlocks {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> REGISTRY =
            DeferredRegister.createBlocks(ProjectAtmosphere.MODID);

    public static final DeferredHolder<Block, Block> DUST = REGISTRY.register("dust", () ->
            new DustLayerBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.1f)
                    .sound(SoundType.SAND)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((state, getter, pos) -> false)
            ));

    public static final DeferredHolder<Block, Block> SAND_LAYER = REGISTRY.register("sand_layer", () ->
            new SandLayerBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.1f)
                    .sound(SoundType.SAND)
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((state, getter, pos) -> false)
            ));

    public static final DeferredHolder<Block, Block> WEATHER_VANE = REGISTRY.register("weather_vane", () ->
            new WeatherVaneBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final DeferredHolder<Block, Block> ANEMOMETER = REGISTRY.register("anemometer", () ->
            new AnemometerBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final DeferredHolder<Block, Block> THERMOMETER_BLOCK = REGISTRY.register("thermometer_block", () ->
            new ThermometerBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final DeferredHolder<Block, Block> BAROMETER_BLOCK = REGISTRY.register("barometer_block", () ->
            new BarometreBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final DeferredHolder<Block, Block> HUMIDIMETER_BLOCK = REGISTRY.register("humidimeter_block", () ->
            new HumidimeterBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final DeferredHolder<Block, Block> STORM_SIREN = REGISTRY.register("storm_siren", () ->
            new StormSirenBlock(BlockBehaviour.Properties
                    .of()
                    .strength(0.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static final DeferredHolder<Block, Block> STORM_SHIELD = REGISTRY.register("storm_shield", () ->
            new StormShieldBlock(BlockBehaviour.Properties
                    .of()
                    .strength(3.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            ));

    public static void register(IEventBus eventBus) {
        REGISTRY.register(eventBus);
    }
}
