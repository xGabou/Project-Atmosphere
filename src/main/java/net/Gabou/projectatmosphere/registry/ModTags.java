package net.Gabou.projectatmosphere.registry;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

public class ModTags {
    public static final TagKey<Block> GLASS_LIKE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("projectatmosphere", "glass_like"));
}
