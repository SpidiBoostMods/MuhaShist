package net.spidiboost.muhashist.qa.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientConnection.class)
public interface PacketAccess {
  @Invoker("handlePacket")
  static void muhaqa$dispatch(Packet<?> packet, PacketListener listener) {
    throw new AssertionError();
  }
}
