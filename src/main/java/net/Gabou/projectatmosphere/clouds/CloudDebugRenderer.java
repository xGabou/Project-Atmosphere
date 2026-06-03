package net.Gabou.projectatmosphere.clouds;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

public final class CloudDebugRenderer {

    private CloudDebugRenderer() {}

    public static void render(CloudRenderSnapshot snapshot, PoseStack poseStack, Vec3 cameraPosition){
        if(snapshot == null || poseStack == null) return;
        if(cameraPosition == null) cameraPosition = Vec3.ZERO;

        Vec3 center = snapshot.getRegionCenter();
        float radius = snapshot.getRegionRadius();

        double minX = center.x() - radius - cameraPosition.x();
        double maxX = center.x() + radius - cameraPosition.x();

        double minY = snapshot.getCloudBaseY() - radius - cameraPosition.y();
        double maxY = snapshot.getCloudTopY() - cameraPosition.y();

        double minZ = center.z() - radius - cameraPosition.z();
        double maxZ = center.z() + radius - cameraPosition.z();



    }
}
