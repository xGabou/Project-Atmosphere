package net.Gabou.projectatmosphere.client.model;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class CloudModel extends DefaultedEntityGeoModel {
    public CloudModel() {
        super(new ResourceLocation(ProjectAtmosphere.MODID, "smallnormalclouds1"),false);
    }

}



