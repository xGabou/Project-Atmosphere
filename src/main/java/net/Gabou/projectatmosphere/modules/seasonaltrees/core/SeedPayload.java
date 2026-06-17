package net.Gabou.projectatmosphere.modules.seasonaltrees.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record SeedPayload(TreeType treeType, ResourceLocation speciesId, BlockPos sourcePos) {
}
