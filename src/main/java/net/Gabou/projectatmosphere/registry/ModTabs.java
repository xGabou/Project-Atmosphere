package net.Gabou.projectatmosphere.registry;


import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModTabs {
    // Register container for creative tabs under your modid
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProjectAtmosphere.MODID);

    // Register the Project Atmosphere tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PROJECTATMO =
            CREATIVE_MODE_TABS.register("projectatmo_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.projectatmosphere")) // lang key
                    .icon(() -> new ItemStack(ModItems.THERMOMETER.get())) // tab icon
                    .displayItems((parameters, output) -> {
                        // add all registered items from your mod
                        ModItems.ITEMS.getEntries().forEach(entry -> {
                            output.accept(entry.get().getDefaultInstance());
                        });
                    })
                    .build());


}
