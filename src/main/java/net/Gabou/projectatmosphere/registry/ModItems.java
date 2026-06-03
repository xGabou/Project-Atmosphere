package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.items.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ProjectAtmosphere.MODID);
    // ---------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    // ---------------------------------------------------------------------
    // Block-linked items
    // ---------------------------------------------------------------------
    public static final RegistryObject<Item> WEATHER_VANE = blockUtilities(ModBlocks.WEATHER_VANE);
    public static final RegistryObject<Item> THERMOMETER = ITEMS.register("thermometer_block", () ->
            new Thermometre(ModBlocks.THERMOMETER_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> ANEMOMETER = ITEMS.register("anemometer",
            () -> new Anemometer(ModBlocks.ANEMOMETER.get(), new Item.Properties()));

    public static final RegistryObject<Item> BAROMETER = ITEMS.register("barometer",
            () -> new Barometre(ModBlocks.BAROMETER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> HUMIDIMETER = ITEMS.register("humidimeter_block",
            () -> new Humidimeter(ModBlocks.HUMIDIMETER_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> WEATHER_RADAR = ITEMS.register("weather_radar",
            () -> new WeatherRadarItem(new Item.Properties()));

    public static final RegistryObject<Item> CLOUD_PROBE = ITEMS.register("cloud_probe",
            () -> new CloudProbeItem(new Item.Properties().stacksTo(1)));



    public static final RegistryObject<Item> WOOD_BALAI = ITEMS.register("balai_bois",
            () -> new Balai(
                    1.0f,
                    -2.8f,
                    Tiers.WOOD,
                    BlockTags.DIRT,
                    new Item.Properties().durability(159) 
            )
    );

    public static final RegistryObject<Item> STONE_BALAI = ITEMS.register("balai_pierre",
            () -> new Balai(
                    1.0f,
                    -2.8f,
                    Tiers.STONE,
                    BlockTags.DIRT,
                    new Item.Properties().durability(250)
            )
    );

    public static final RegistryObject<Item> IRON_BALAI = ITEMS.register("balai_fer",
            () -> new Balai(
                    1.0f,
                    -2.8f,
                    Tiers.IRON,
                    BlockTags.DIRT,
                    new Item.Properties().durability(550)
            )
    );

    public static final RegistryObject<Item> DIAMOND_BALAI = ITEMS.register("balai_diamant",
            () -> new Balai(
                    1.0f,
                    -2.8f,
                    Tiers.DIAMOND,
                    BlockTags.DIRT,
                    new Item.Properties().durability(1561)
            )
    );
    public static final RegistryObject<Item> NETHERITE_BALAI = ITEMS.register("balai_netherite",
            () -> new Balai(
                    1.0f,
                    -2.8f,
                    Tiers.NETHERITE,
                    BlockTags.DIRT,
                    new Item.Properties().durability(2031).fireResistant()
            )
    );






    public static final RegistryObject<Item> DUST = blockUtilities(ModBlocks.DUST);
    public static final RegistryObject<Item> SAND_LAYER = blockUtilities(ModBlocks.SAND_LAYER);

    public static final RegistryObject<Item> STORM_SIREN = blockUtilities(ModBlocks.STORM_SIREN);
    public static final RegistryObject<Item> STORM_SHIELD = blockUtilities(ModBlocks.STORM_SHIELD);

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------
    private static RegistryObject<Item> blockUtilities(RegistryObject<Block> block) {
        return ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
