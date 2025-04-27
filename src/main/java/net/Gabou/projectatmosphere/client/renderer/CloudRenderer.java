package net.Gabou.projectatmosphere.client.renderer;

import net.Gabou.projectatmosphere.client.model.CloudModel;
import net.Gabou.projectatmosphere.entity.CloudEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CloudRenderer extends GeoEntityRenderer<CloudEntity> {

    public CloudRenderer(EntityRendererProvider.Context context) {
        super(context,new CloudModel());
    }

}
