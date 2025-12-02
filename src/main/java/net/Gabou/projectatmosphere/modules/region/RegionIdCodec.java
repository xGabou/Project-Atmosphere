package net.Gabou.projectatmosphere.modules.region;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class RegionIdCodec {
    private RegionIdCodec() {}

    public static void write(FriendlyByteBuf buf, ForecastRegionId id) {
        buf.writeVarInt(id.rx());
        buf.writeVarInt(id.rz());
        buf.writeResourceLocation(id.dimension().location());
    }

    public static ForecastRegionId read(FriendlyByteBuf buf) {
        int rx = buf.readVarInt();
        int rz = buf.readVarInt();
        ResourceLocation dimLoc = buf.readResourceLocation();
        ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc);
        return new ForecastRegionId(rx, rz, dim);
    }

    /**
     * Convenience for deriving a region id from a block position (using 8x8 chunk regions).
     */
    public static ForecastRegionId ofBlockPos(net.minecraft.core.BlockPos pos, ResourceKey<Level> dim) {
        return ForecastRegionId.ofChunk(pos.getX() >> 4, pos.getZ() >> 4, dim);
    }

    /**
     * Legacy helper when dimension is unknown; defaults to overworld.
     */
    public static ForecastRegionId ofChunk(net.minecraft.core.BlockPos pos) {
        ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"));
        return ofBlockPos(pos, dim);
    }
}
