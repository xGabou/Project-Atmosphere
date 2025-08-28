package net.Gabou.projectatmosphere.client.sound;

import net.Gabou.projectatmosphere.modules.tornado.TornadoInstance;
import net.Gabou.projectatmosphere.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Looping tornado roar that follows the tornado instance and fades by distance.
 */
@OnlyIn(Dist.CLIENT)
public class TornadoRoarLoop extends AbstractTickableSoundInstance {
    private final TornadoInstance tornado;
    private final float maxHearDistance;
    private float baseVolume;

    public TornadoRoarLoop(TornadoInstance tornado, float baseVolume, float maxHearDistance) {
        super(ModSounds.TORNADO_ROAR.get(), SoundSource.WEATHER, Minecraft.getInstance().level.getRandom());
        this.tornado = tornado;
        this.baseVolume = baseVolume;
        this.maxHearDistance = maxHearDistance;
        this.looping = true;
        this.x = (float) tornado.position.x;
        this.y = (float) tornado.position.y;
        this.z = (float) tornado.position.z;
        this.volume = 0.0f;
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        if (this.tornado == null) {
            this.stop();
            return;
        }

        this.x = (float) tornado.position.x;
        this.y = (float) tornado.position.y;
        this.z = (float) tornado.position.z;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double d = mc.player.position().distanceTo(tornado.position);
        float fade = Mth.clamp(1.0f - (float) (d / maxHearDistance), 0.0f, 1.0f);
        this.volume = baseVolume * fade;

        this.pitch = 0.99f + 0.02f * Mth.sin((mc.level.getGameTime() % 100000) * 0.025f);
    }

    public void setBaseVolume(float v) {
        this.baseVolume = Mth.clamp(v, 0.0f, 1.0f);
    }

    public void stopSound(){
        this.stop();
    }
}
