package net.Gabou.projectatmosphere.registry;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY
            = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProjectAtmosphere.MODID);

    public static final RegistryObject<CreativeModeTab> PROJECTATMO = REGISTRY.register(
            "thermometer",  // Changed registry name
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("item.projectatmosphere.thermometer"))
                    .icon(() -> new ItemStack(ModItems.THERMOMETRE.get()))
                    .displayItems((parameters, output) -> {
                        ModItems.ITEMS.getEntries().forEach(entry -> {
                            output.accept(new ItemStack(entry.get()));
                        });
                    })
                    .build()
    );

}
