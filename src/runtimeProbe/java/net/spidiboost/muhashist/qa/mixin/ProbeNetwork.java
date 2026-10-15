package net.spidiboost.muhashist.qa.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.spidiboost.muhashist.qa.NativeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public final class ProbeNetwork {
  @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
  private void muhaqa$send(String command, CallbackInfo ci) {
    if (command.startsWith("shist ")) {
      NativeProbe.sent.add(command);
      ci.cancel();
    }
  }
}
