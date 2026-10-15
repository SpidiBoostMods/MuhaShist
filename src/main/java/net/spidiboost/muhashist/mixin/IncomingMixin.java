package net.spidiboost.muhashist.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.spidiboost.muhashist.MuhaClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public final class IncomingMixin {
  @Inject(method = "handlePacket", at = @At("HEAD"))
  private static void muhashist$beforeDispatch(
      Packet<?> packet, PacketListener listener, CallbackInfo ci) {
    if (!(packet instanceof GameMessageS2CPacket message)
        || message.overlay()
        || !(listener instanceof ClientPlayNetworkHandler)
        || MuhaClient.INSTANCE == null) return;
    var client = MinecraftClient.getInstance();
    String text = message.content().getString();
    client.execute(
        () -> {
          if (client.getNetworkHandler() == listener && MuhaClient.INSTANCE != null)
            MuhaClient.INSTANCE.receive(text);
        });
  }
}
