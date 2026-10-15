package net.spidiboost.muhashist.qa.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
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
    if (command.startsWith("checkban ")) {
      if (!NativeProbe.pendingChecks.isEmpty()
          || NativeProbe.checkTimes.size()
              != net.spidiboost.muhashist.MuhaClient.INSTANCE.counter().checked())
        throw new AssertionError("checkban sent before previous reply completed");
      NativeProbe.checkTimes.add(System.nanoTime() / 1_000_000);
      NativeProbe.sent.add(command);
      NativeProbe.pendingChecks.addLast(command.substring("checkban ".length()));
      ci.cancel();
    }
  }

  @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
  private void muhaqa$route(GameMessageS2CPacket packet, CallbackInfo ci) {
    if (NativeProbe.routeMessages
        && net.minecraft.client.MinecraftClient.getInstance().isOnThread()) {
      if ((NativeProbe.primaryMessages + NativeProbe.staffMessages) % 2 == 0)
        NativeProbe.primaryMessages++;
      else NativeProbe.staffMessages++;
      ci.cancel();
    }
  }
}
