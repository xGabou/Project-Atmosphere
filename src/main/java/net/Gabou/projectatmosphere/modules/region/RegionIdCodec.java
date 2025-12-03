package net.Gabou.projectatmosphere.modules.region;

import net.minecraft.network.FriendlyByteBuf;
import net.Gabou.projectatmosphere.util.RegionInstanceKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class RegionIdCodec {
    private RegionIdCodec() {}

    public static void write(FriendlyByteBuf buf, RegionInstanceKey key) {
        buf.writeVarInt(key.regionX());
        buf.writeVarInt(key.regionZ());
        buf.writeVarInt(key.regionSize());
    }

    public static RegionInstanceKey read(FriendlyByteBuf buf) {
        int rx = buf.readVarInt();
        int rz = buf.readVarInt();
        int size = buf.readVarInt();
        return new RegionInstanceKey(rx, rz, size);
    }

    public static RegionInstanceKey ofBlockPos(net.minecraft.core.BlockPos pos) {
        return RegionInstanceKey.from(pos);
    }
}
