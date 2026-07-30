package net.Gabou.projectatmosphere.tools.debug;

import net.Gabou.projectatmosphere.ProjectAtmosphere;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = ProjectAtmosphere.MODID, value = Dist.CLIENT)
public class HudRenderTest {

    @SubscribeEvent
    public static void onRenderGuiOverlayPost(RenderGuiEvent.Post event) {











    }
}
