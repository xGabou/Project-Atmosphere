package net.Gabou.projectatmosphere.client.sound;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TornadoAudioClient {
    private static final Object2ObjectOpenHashMap<TornadoInstance, TornadoRoarLoop> ACTIVE = new Object2ObjectOpenHashMap<>();

    private TornadoAudioClient() {}

    /** Call each client tick to ensure the sound is playing and updated. */
    public static void ensure(TornadoInstance tornado, float baseVol, float maxDist) {
        var sm = Minecraft.getInstance().getSoundManager();
        TornadoRoarLoop loop = ACTIVE.get(tornado);
        if (loop == null) {
            loop = new TornadoRoarLoop(tornado, baseVol, maxDist);
            ACTIVE.put(tornado, loop);
            sm.play(loop);
        } else {
            loop.setBaseVolume(baseVol);
        }
    }

    /** Stop and remove the loop for the given tornado. */
    public static void stop(TornadoInstance tornado) {
        TornadoRoarLoop loop = ACTIVE.remove(tornado);
        if (loop != null) {
            loop.stop();
        }
    }

    /** Stop all active tornado roar loops. */
    public static void stopAll() {
        ACTIVE.values().forEach(TornadoRoarLoop::stop);
        ACTIVE.clear();
    }
}
