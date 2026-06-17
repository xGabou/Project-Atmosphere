package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.items.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;


public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.createItems(ProjectAtmosphere.MODID);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    // === Block items ===
    public static final DeferredHolder<Item, Item> WEATHER_VANE =
            blockUtilities(ModBlocks.WEATHER_VANE);

    public static final DeferredHolder<Item, Item> THERMOMETER =
            ITEMS.register("thermometer_block",
                    () -> new Thermometre(ModBlocks.THERMOMETER_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> ANEMOMETER =
            ITEMS.register("anemometer",
                    () -> new Anemometer(ModBlocks.ANEMOMETER.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> BAROMETER =
            ITEMS.register("barometer",
                    () -> new Barometre(ModBlocks.BAROMETER_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> HUMIDIMETER =
            ITEMS.register("humidimeter_block",
                    () -> new Humidimeter(ModBlocks.HUMIDIMETER_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> WEATHER_RADAR =
            ITEMS.register("weather_radar",
                    () -> new WeatherRadarItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> CLOUD_PROBE =
            ITEMS.register("cloud_probe",
                    () -> new CloudProbeItem(new Item.Properties()));

    // === Custom tools (Balais) ===
    public static final DeferredHolder<Item, Item> WOOD_BALAI =
            ITEMS.register("balai_bois",
                    () -> new Balai(1.0f, -2.8f, Tiers.WOOD, BlockTags.DIRT,
                            new Item.Properties().durability(159)));

    public static final DeferredHolder<Item, Item> STONE_BALAI =
            ITEMS.register("balai_pierre",
                    () -> new Balai(1.0f, -2.8f, Tiers.STONE, BlockTags.DIRT,
                            new Item.Properties().durability(250)));

    public static final DeferredHolder<Item, Item> IRON_BALAI =
            ITEMS.register("balai_fer",
                    () -> new Balai(1.0f, -2.8f, Tiers.IRON, BlockTags.DIRT,
                            new Item.Properties().durability(550)));

    public static final DeferredHolder<Item, Item> DIAMOND_BALAI =
            ITEMS.register("balai_diamant",
                    () -> new Balai(1.0f, -2.8f, Tiers.DIAMOND, BlockTags.DIRT,
                            new Item.Properties().durability(1561)));

    public static final DeferredHolder<Item, Item> NETHERITE_BALAI =
            ITEMS.register("balai_netherite",
                    () -> new Balai(1.0f, -2.8f, Tiers.NETHERITE, BlockTags.DIRT,
                            new Item.Properties().durability(2031).fireResistant()));

    // === Misc block utilities ===
    public static final DeferredHolder<Item, Item> DUST =
            blockUtilities(ModBlocks.DUST);

    public static final DeferredHolder<Item, Item> SAND_LAYER =
            blockUtilities(ModBlocks.SAND_LAYER);

    public static final DeferredHolder<Item, Item> STORM_SIREN =
            blockUtilities(ModBlocks.STORM_SIREN);

    public static final DeferredHolder<Item, Item> STORM_SHIELD =
            blockUtilities(ModBlocks.STORM_SHIELD);

    // === Utility method ===
    private static DeferredHolder<Item, Item> blockUtilities(DeferredHolder<Block, Block> block) {
        return ITEMS.register(block.getId().getPath(),
                () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
