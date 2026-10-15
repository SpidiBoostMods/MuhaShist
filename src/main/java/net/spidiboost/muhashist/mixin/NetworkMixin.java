package net.spidiboost.muhashist.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.spidiboost.muhashist.MuhaClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public final class NetworkMixin {
  @Inject(method = "onGameMessage", at = @At("HEAD"))
  private void muhashist$receive(GameMessageS2CPacket packet, CallbackInfo ci) {
    if (MinecraftClient.getInstance().isOnThread()
        && !packet.overlay()
        && MuhaClient.INSTANCE != null) MuhaClient.INSTANCE.receive(packet.content().getString());
  }

  @Inject(method = "onGameMessage", at = @At("TAIL"))
  private void muhashist$clear(GameMessageS2CPacket packet, CallbackInfo ci) {
    if (MinecraftClient.getInstance().isOnThread() && MuhaClient.INSTANCE != null)
      MuhaClient.INSTANCE.clearChat();
  }

  @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
  private void muhashist$command(String command, CallbackInfo ci) {
    if (MuhaClient.handle(command)) ci.cancel();
  }

  @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
  private void muhashist$chat(String message, CallbackInfo ci) {
    if (message.stripLeading().startsWith("/") && MuhaClient.handle(message)) ci.cancel();
  }
}
