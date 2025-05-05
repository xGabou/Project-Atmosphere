//// src/main/java/net/Gabou/projectatmosphere/modules/pressure/system/CellPos.java
//package net.Gabou.projectatmosphere.modules.pressure.system;
//
//import net.minecraft.resources.ResourceLocation;
//
//import java.util.Objects;
//
//public record CellPos(ResourceLocation dimension, int chunkX, int chunkZ) {
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (!(o instanceof CellPos other)) return false;
//        return chunkX == other.chunkX &&
//                chunkZ == other.chunkZ &&
//                Objects.equals(dimension, other.dimension);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(dimension, chunkX, chunkZ);
//    }
//
//    @Override
//    public String toString() {
//        return "CellPos[" + dimension + " @ " + chunkX + "," + chunkZ + "]";
//    }
//    public CellPos getNeighbor() {
//        return new CellPos(dimension, chunkX + dx, chunkZ + dz);
//    }
//}
