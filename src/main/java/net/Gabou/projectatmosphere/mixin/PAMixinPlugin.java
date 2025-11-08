package net.Gabou.projectatmosphere.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class PAMixinPlugin implements IMixinConfigPlugin {
    private static Boolean SANDSTORMLOADED = null;
    private static Boolean AURORASLOADED = null;
    private static Boolean RAINBOWSLOADED = null;


    private boolean isSandStormLoaded() {
        if (SANDSTORMLOADED != null) return SANDSTORMLOADED;

        try {
            // Safer to check for a known class rather than the base package
            Class.forName("com.BreadRes.desertstormwarming.BurymodMain", false, getClass().getClassLoader());
            SANDSTORMLOADED = true;
        } catch (ClassNotFoundException e) {
            SANDSTORMLOADED = false;
        }

        System.out.println("[Project Atmosphere] SandStorms detected: " + SANDSTORMLOADED);
        return SANDSTORMLOADED;
    }

    private boolean isAurorasLoaded() {
        if (AURORASLOADED != null) return AURORASLOADED;
        AURORASLOADED = isClassPresent("auroras.Auroras");
        System.out.println("[Project Atmosphere] Auroras detected: " + AURORASLOADED);
        return AURORASLOADED;
    }

    private boolean isRainbowsLoaded() {
        if (RAINBOWSLOADED != null) return RAINBOWSLOADED;
        RAINBOWSLOADED = isClassPresent("rainbows.Rainbows");
        System.out.println("[Project Atmosphere] Rainbows detected: " + RAINBOWSLOADED);
        return RAINBOWSLOADED;
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onLoad(String s) {
        
    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("OverwriteDesertSound") && !isSandStormLoaded()) {
            return false;
        }
        if (mixinClassName.endsWith("MixinSandstormDebugBlocker") && !isSandStormLoaded()) {
            return false;
        }
        if (mixinClassName.contains("compat.auroras") && !isAurorasLoaded()) {
            return false;
        }
        if (mixinClassName.contains("compat.rainbows") && !isRainbowsLoaded()) {
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {

    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }
}
