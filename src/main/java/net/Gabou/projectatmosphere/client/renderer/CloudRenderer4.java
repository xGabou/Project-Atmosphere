package net.Gabou.projectatmosphere.client.renderer;


import net.Gabou.projectatmosphere.client.model.CloudModel4;
import net.Gabou.projectatmosphere.entity.CloudEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CloudRenderer4 extends GeoEntityRenderer<CloudEntity> {

    public CloudRenderer4(EntityRendererProvider.Context context) {
        super(context,new CloudModel4());
    }

}
