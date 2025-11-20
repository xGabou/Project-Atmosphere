package net.Gabou.projectatmosphere.mixin;

final class TornadoUpload {
    final float typeIndex;
    final float centerX;
    final float centerZ;
    final float radius;
    final float bottom;
    final float height;

    TornadoUpload(float typeIndex, float centerX, float centerZ, float radius, float bottom, float height) {
        this.typeIndex = typeIndex;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.bottom = bottom;
        this.height = height;
    }
}
