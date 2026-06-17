package net.Gabou.projectatmosphere.mixin.client;

import net.Gabou.projectatmosphere.client.crash.ProjectAtmosphereCrashHandler;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftCrashHandlerMixin {
    @Shadow
    protected abstract void runTick(boolean renderLevel);

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"))
    private void projectatmosphere$wrapRunTick(Minecraft minecraft, boolean renderLevel) {
        try {
            this.runTick(renderLevel);
        } catch (ReportedException reportedException) {
            if (!ProjectAtmosphereCrashHandler.handleCrashReport(minecraft, reportedException.getReport())) {
                throw reportedException;
            }
        } catch (Throwable throwable) {
            if (!ProjectAtmosphereCrashHandler.handleThrowable(minecraft, throwable, "Unexpected error")) {
                throw throwable;
            }
        }
    }

    @Inject(method = {"delayCrash", "delayCrashRaw"}, at = @At("HEAD"), cancellable = true)
    private void projectatmosphere$handleDelayedCrash(CrashReport report, CallbackInfo ci) {
        if (ProjectAtmosphereCrashHandler.handleCrashReport((Minecraft) (Object) this, report)) {
            ci.cancel();
        }
    }
}
