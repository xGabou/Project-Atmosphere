package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.Gabou.projectatmosphere.items.Balai;
import net.Gabou.projectatmosphere.items.Barometre;
import net.Gabou.projectatmosphere.items.Thermometre;
import net.minecraft.world.item.Item;
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

    public static final RegistryObject<Item> BALAI = ITEMS.register("balai",
            () -> new Balai(new Item.Properties()));


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
