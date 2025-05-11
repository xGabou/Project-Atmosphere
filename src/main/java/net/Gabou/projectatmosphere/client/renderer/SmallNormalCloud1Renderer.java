package net.Gabou.projectatmosphere.client.renderer;

import net.Gabou.projectatmosphere.client.model.CloudModel;
import net.Gabou.projectatmosphere.client.model.SmallNormalCloud1Model;
import net.Gabou.projectatmosphere.entity.SmallNormalCloud1Entity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SmallNormalCloud1Renderer extends GeoEntityRenderer<SmallNormalCloud1Entity> {

    public SmallNormalCloud1Renderer(EntityRendererProvider.Context context) {
        super(context,new SmallNormalCloud1Model());
    }

}
