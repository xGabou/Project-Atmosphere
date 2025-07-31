package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.items.Balai;
import net.Gabou.projectatmosphere.items.Barometre;
import net.Gabou.projectatmosphere.items.Humidimeter;
import net.Gabou.projectatmosphere.items.Thermometre;
import net.Gabou.projectatmosphere.items.WindChecker;
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

    public static final RegistryObject<Item> THERMOMETRE = ITEMS.register("thermometer",
            () -> new Thermometre(new Item.Properties()
                    .stacksTo(1)));

    public static final RegistryObject<Item> BAROMETER = ITEMS.register("barometer",
            () -> new Barometre(new Item.Properties()));

    public static final RegistryObject<Item> HUMIDIMETER = ITEMS.register("humidimeter",
            () -> new Humidimeter(new Item.Properties()
                    .stacksTo(1)));

    public static final RegistryObject<Item> WIND_CHECKER = ITEMS.register("wind_checker",
            () -> new WindChecker(new Item.Properties()
                    .stacksTo(1)));

    public static final RegistryObject<Item> WOOD_BALAI = ITEMS.register("balai_bois",
            () -> new Balai(
                    1.0f,
                    -2.8f,
                    Tiers.WOOD,
                    BlockTags.DIRT,
                    new Item.Properties().durability(159) // bois ~ bois sword
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




    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static final RegistryObject<Item> DUST = blockUtilities(ModBlocks.DUST);

    private static RegistryObject<Item> blockUtilities(RegistryObject<Block> block) {
        return ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
