package net.Gabou.projectatmosphere.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;
import java.util.Set;

public class PAMixinPlugin implements IMixinConfigPlugin {
    private static Boolean SANDSTORMLOADED = null;
    private static Boolean AURORASLOADED = null;
    private static Boolean RAINBOWSLOADED = null;
    private static Boolean SIMPLECLOUDSLOADED = null;


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
        if (!FMLEnvironment.dist.isClient()) {
            return false;
        }
        if (AURORASLOADED != null) return AURORASLOADED;
        AURORASLOADED = isClassPresent("auroras.Auroras");
        System.out.println("[Project Atmosphere] Auroras detected: " + AURORASLOADED);
        return AURORASLOADED;
    }

    private boolean isRainbowsLoaded() {
        if (!FMLEnvironment.dist.isClient()) {
            return false;
        }
        if (RAINBOWSLOADED != null) return RAINBOWSLOADED;
        RAINBOWSLOADED = isClassPresent("rainbows.Rainbows");
        System.out.println("[Project Atmosphere] Rainbows detected: " + RAINBOWSLOADED);
        return RAINBOWSLOADED;
    }

    private boolean isSimpleCloudsLoaded() {
        if (SIMPLECLOUDSLOADED != null) return SIMPLECLOUDSLOADED;
        SIMPLECLOUDSLOADED = isClassPresent("dev.nonamecrackers2.simpleclouds.SimpleCloudsMod");
        System.out.println("[Project Atmosphere] Simple Clouds detected: " + SIMPLECLOUDSLOADED);
        return SIMPLECLOUDSLOADED;
    }

    private boolean isSimpleCloudsMixin(String mixinClassName) {
        return mixinClassName.contains("CloudGenerator")
                || mixinClassName.contains("CloudRegion")
                || mixinClassName.contains("SimpleClouds")
                || mixinClassName.contains("CloudMeshGenerator")
                || mixinClassName.contains("MultiRegionCloudMeshGenerator")
                || mixinClassName.contains("DefaultPipelineTornado")
                || mixinClassName.contains("DefaultPipelineHurricane")
                || mixinClassName.contains("ShaderSupportPipelineTornado")
                || mixinClassName.contains("ShaderSupportPipelineHurricane")
                || mixinClassName.contains("DhSupportPipeline")
                || mixinClassName.contains("InstanceableMesh")
                || mixinClassName.contains("InfoMixin")
                || mixinClassName.contains("BindingManager");
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
        return "projectatmosphere.refmap.json";
    }


    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!FMLEnvironment.dist.isClient() && mixinClassName.contains(".client.")) {
            return false;
        }
        if (isSimpleCloudsMixin(mixinClassName) && !isSimpleCloudsLoaded()) {
            return false;
        }
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
        if (mixinClassName.endsWith("ServerLevelWeatherCycleMixin") && isSimpleCloudsLoaded()) {
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
