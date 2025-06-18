package net.Gabou.projectatmosphere.client.renderer;


import net.Gabou.projectatmosphere.client.model.CloudModel5;
import net.Gabou.projectatmosphere.entity.CloudEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CloudRenderer5 extends GeoEntityRenderer<CloudEntity> {

    public CloudRenderer5(EntityRendererProvider.Context context) {
        super(context,new CloudModel5());
    }

}
