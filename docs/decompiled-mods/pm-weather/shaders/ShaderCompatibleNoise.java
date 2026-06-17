//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package dev.protomanly.pmweather.util;

import net.minecraft.util.Mth;
import org.joml.Vector2f;
import org.joml.Vector3f;

public class ShaderCompatibleNoise {
    public static final Sampler2D noiseSampler = new Sampler2D("assets/minecraft/textures/effect/pmweather/noise.png");
    public static final Sampler2D noiseXSampler = new Sampler2D("assets/minecraft/textures/effect/pmweather/noisex.png");

    public static float noise2D(Vector2f x) {
        x = x.div(512.0F);
        return (noiseXSampler.sample(x.x, x.y) - 0.5F) * 2.0F;
    }

    public static float noise(Vector3f x) {
        x = x.div(300.0F, 540.0F, 300.0F);
        x.y = fract(x.y) * 512.0F;
        float iz = (float)Mth.floor(x.y);
        float fz = fract(x.y);
        Vector2f a_off = (new Vector2f(23.0F, 29.0F)).mul(iz).div(512.0F);
        Vector2f b_off = (new Vector2f(23.0F, 29.0F)).mul(iz + 1.0F).div(512.0F);
        float a = noiseSampler.sample(x.x + a_off.x, x.z + a_off.y);
        float b = noiseSampler.sample(x.x + b_off.x, x.z + b_off.y);
        return (mix(a, b, fz) - 0.5F) * 2.0F;
    }

    public static float fract(float n) {
        return n - (float)Mth.floor(n);
    }

    public static Vector3f fract(Vector3f n) {
        return new Vector3f(fract(n.x), fract(n.y), fract(n.z));
    }

    public static Vector3f floor(Vector3f n) {
        return new Vector3f((float)Mth.floor(n.x), (float)Mth.floor(n.y), (float)Mth.floor(n.z));
    }

    public static float mix(float s, float e, float a) {
        return Mth.lerp(a, s, e);
    }

    public static Vector3f mix(Vector3f s, Vector3f e, float a) {
        return s.lerp(e, a);
    }

    public static float hash(float p) {
        p = fract(p * 0.1031F);
        p *= p + 33.33F;
        p *= p + p;
        return fract(p);
    }

    public static float onoise(Vector3f pos) {
        Vector3f x = pos.mul(2.0F);
        Vector3f p = floor(x);
        Vector3f f = fract(x);
        f = f.mul(f).mul((new Vector3f(3.0F, 3.0F, 3.0F)).sub(f.mul(2.0F)));
        float n = p.x + p.y * 57.0F + 113.0F * p.z;
        return mix(mix(mix(hash(n + 0.0F), hash(n + 1.0F), f.x), mix(hash(n + 57.0F), hash(n + 58.0F), f.x), f.y), mix(mix(hash(n + 113.0F), hash(n + 114.0F), f.x), mix(hash(n + 170.0F), hash(n + 171.0F), f.x), f.y), f.z);
    }

    public static float fbm(Vector3f x, int octaves, float lacunarity, float gain, float amplitude) {
        float y = 0.0F;

        for(int i = 0; i < Math.max(octaves, 1); ++i) {
            y += amplitude * noise(x);
            x = x.mul(lacunarity);
            amplitude *= gain;
        }

        return y;
    }
}
