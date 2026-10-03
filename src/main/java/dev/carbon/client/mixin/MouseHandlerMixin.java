package dev.carbon.client.mixin;

import dev.carbon.client.CarbonClient;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures actual mouse-button press edges so CPS is not inferred from held frames. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void carbon$recordClick(long window, int button, int action, int modifiers, CallbackInfo callback) {
        if (action == GLFW.GLFW_PRESS) {
            CarbonClient.onMouseButton(button);
        }
        CarbonClient.onMouseInput(button, action, modifiers);
    }
}
