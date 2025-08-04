package net.Gabou.projectatmosphere;

import org.lwjgl.opengl.GL11;

public class ClientSystemProfile extends ProjectAtmosphere.SystemProfile {

    @Override
    public String getGPUName() {
        try {
            return GL11.glGetString(GL11.GL_RENDERER);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isGoodEnoughGPU() {
        String gpu = getGPUName();
        if (gpu == null) return false;

        String gpuUpper = gpu.toUpperCase();

        if (!gpuUpper.contains("RTX")) return false;

        if (gpuUpper.matches(".*RTX\\s*30([5-9][0-9]|[0-9]{3,}).*")) return true;
        if (gpuUpper.matches(".*RTX\\s*4[0-9]{2,}.*")) return true;
        if (gpuUpper.matches(".*RTX\\s*5[0-9]{2,}.*")) return true;
        if (gpuUpper.matches(".*RTX\\s*20(7[0-9]|8[0-9]|9[0-9])0?.*")) return true;

        return false;
    }
}
