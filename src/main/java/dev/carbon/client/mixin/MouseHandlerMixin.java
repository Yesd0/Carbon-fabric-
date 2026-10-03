package dev.carbon.client.mixin;

import dev.carbon.client.CarbonClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures actual mouse-button press edges so CPS is not inferred from held frames. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"))
    private void carbon$recordClick(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo callback) {
        int button = buttonInfo.button();
        if (action == GLFW.GLFW_PRESS) {
            CarbonClient.onMouseButton(button);
        }
        CarbonClient.onMouseInput(button, action, buttonInfo.modifiers());
    }
}
