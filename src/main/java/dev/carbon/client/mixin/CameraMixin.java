package dev.carbon.client.mixin;

import dev.carbon.client.modules.visual.ZoomModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the enabled Zoom module to the camera's computed field of view. */
@Mixin(targets = "net.minecraft.client.Camera")
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void carbon$applyZoom(float partialTicks, CallbackInfoReturnable<Float> callback) {
        float originalFov = callback.getReturnValueF();
        float zoomedFov = ZoomModule.applyFov(originalFov);
        if (zoomedFov != originalFov) {
            callback.setReturnValue(zoomedFov);
        }
    }
}
