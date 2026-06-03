package net.Gabou.projectatmosphere.clouds;

import net.minecraft.world.phys.Vec3;

public final class CloudDebugSnapshotFactory {
    private CloudDebugSnapshotFactory() {}

    public static CloudRenderSnapshot createFakeSnapshot(){
        return createFakeSnapshot(Vec3.ZERO);
    }

    public static CloudRenderSnapshot createFakeSnapshot(Vec3 cameraPosition){

        return new CloudRenderSnapshot(
                true,
                "minecraft:overworld",
                0L,
                0.0f,
                cameraPosition != null ? cameraPosition : Vec3.ZERO,
                new Vec3(0.0D, 110.0D, 0.0D),
                96.0f,
                90.0f,
                130.0f,
                0.65f,
                0.80f,
                0.25f,
                0.0f,
                0.0f,
                0x88FFFFFF
        );
    }

    public static CloudRenderSnapshot createDebugSnapshot(CloudRenderSnapshot base, int debugColorOrTint) {
        if (base == null) {
            //TODO make it throw IllegalArgumentException
            base = createFakeSnapshot();
        }
        return new CloudRenderSnapshot(
                base.isEnabled(),
                base.getDimension(),
                base.getWorldTime(),
                base.getPartialTick(),
                base.getCameraPosition(),
                base.getRegionCenter(),
                base.getRegionRadius(),
                base.getCloudBaseY(),
                base.getCloudTopY(),
                base.getDensity(),
                base.getCoverage(),
                base.getEdgeSoftness(),
                base.getWindOffsetX(),
                base.getWindOffsetZ(),
                debugColorOrTint
        );
    }
}
