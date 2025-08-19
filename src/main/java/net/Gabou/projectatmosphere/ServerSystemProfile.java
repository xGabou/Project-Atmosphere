package net.Gabou.projectatmosphere;

public class ServerSystemProfile extends ProjectAtmosphere.SystemProfile {

    @Override
    public String getGPUName() {
        return "N/A (Server)";
    }

    @Override
    public boolean isGoodEnoughGPU() {
        return true;
    }
}
