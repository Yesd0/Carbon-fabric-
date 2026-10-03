package dev.carbon.client.mixin;

import dev.carbon.client.CarbonClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes keyboard press and release edges for module keybinds and hold controls. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"))
    private void carbon$publishKeyInput(long window, int action, KeyEvent event, CallbackInfo callback) {
        CarbonClient.onKeyInput(event.key(), event.scancode(), action, event.modifiers());
    }
}
