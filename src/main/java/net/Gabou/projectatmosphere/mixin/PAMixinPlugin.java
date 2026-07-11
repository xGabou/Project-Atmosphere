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
    private static Boolean SERENESEASONSLOADED = null;


    private boolean isSandStormLoaded() {
        if (SANDSTORMLOADED != null) return SANDSTORMLOADED;
        SANDSTORMLOADED = isClassPresent("com.BreadRes.desertstormwarming.BurymodMain");

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
                || mixinClassName.contains("ShaderSupportPipelineTornado")
                || mixinClassName.contains("DhSupportPipeline")
                || mixinClassName.contains("InstanceableMesh")
                || mixinClassName.contains("InfoMixin")
                || mixinClassName.contains("BindingManager");
    }

    private boolean isSereneSeasonsLoaded() {
        if (SERENESEASONSLOADED != null) return SERENESEASONSLOADED;
        SERENESEASONSLOADED = isClassPresent("sereneseasons.season.SeasonHooks");
        System.out.println("[Project Atmosphere] Serene Seasons detected: " + SERENESEASONSLOADED);
        return SERENESEASONSLOADED;
    }

    private boolean isHurricaneRenderPipelineMixin(String mixinClassName) {
        return mixinClassName.contains("DefaultPipelineHurricane")
                || mixinClassName.contains("ShaderSupportPipelineHurricane");
    }

    private boolean isNativeCloudModuleMixin(String mixinClassName) {
        return mixinClassName.endsWith("client.MixinLevelRenderer")
                || mixinClassName.endsWith("client.MixinGameRenderer")
                || mixinClassName.endsWith("client.MixinMinecraftLevelLifecycle");
    }

    private boolean isCloudWeatherOwnershipMixin(String mixinClassName) {
        return mixinClassName.endsWith("ThrownTridentWeatherMixin")
                || mixinClassName.endsWith("WeatherCommandMixin")
                || mixinClassName.endsWith("WeatherStateMixin")
                || mixinClassName.endsWith("ServerLevelWeatherCycleMixin")
                || mixinClassName.endsWith("BiomeFreezingMixin")
                || mixinClassName.endsWith("client.ClientLevelWeatherMixin");
    }

    private boolean isCloudModuleMixin(String mixinClassName) {
        return isSimpleCloudsMixin(mixinClassName)
                || isNativeCloudModuleMixin(mixinClassName)
                || isCloudWeatherOwnershipMixin(mixinClassName);
    }

    private boolean isClassPresent(String className) {
        String resourceName = className.replace('.', '/') + ".class";
        return getClass().getClassLoader().getResource(resourceName) != null;
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
        boolean simpleCloudsLoaded = isSimpleCloudsLoaded();
        if (isSimpleCloudsMixin(mixinClassName) && !simpleCloudsLoaded) {
            return false;
        }
        if (mixinClassName.endsWith("SeasonHooksMixin") && !isSereneSeasonsLoaded()) {
            return false;
        }
        if (isSimpleCloudsMixin(mixinClassName)) {
            return true;
        }
        if (isHurricaneRenderPipelineMixin(mixinClassName)) {
            return simpleCloudsLoaded;
        }
        if ((isNativeCloudModuleMixin(mixinClassName) || isCloudWeatherOwnershipMixin(mixinClassName)) && simpleCloudsLoaded) {
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
