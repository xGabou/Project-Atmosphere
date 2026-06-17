package dev.protomanly.pmweather.interfaces;

import java.util.List;
import net.minecraft.client.renderer.PostPass;

public interface PostChainData {
   List<PostPass> getPasses();
}
