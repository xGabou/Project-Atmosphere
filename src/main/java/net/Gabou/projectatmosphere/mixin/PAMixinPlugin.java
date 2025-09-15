package net.Gabou.projectatmosphere.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class PAMixinPlugin implements IMixinConfigPlugin {
    private static Boolean SANDSTORMLOADED =null;


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
        else return !mixinClassName.endsWith("MixinSandstormDebugBlocker") || isSandStormLoaded();
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
