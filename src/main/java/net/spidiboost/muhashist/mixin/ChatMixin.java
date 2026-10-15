package net.spidiboost.muhashist.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.spidiboost.muhashist.MuhaClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ChatScreen.class)
public abstract class ChatMixin extends Screen {
  protected ChatMixin(Text title) {
    super(title);
  }

  @Inject(method = "render", at = @At("TAIL"))
  private void muhashist$panel(
      DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    var mod = MuhaClient.INSTANCE;
    if (mod != null && mod.hud() != null && mod.hudVisible())
      mod.hud().draw(context, mod.counter());
  }

  @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
  private void muhashist$command(String message, boolean addToHistory, CallbackInfo ci) {
    if (message.stripLeading().startsWith("/") && MuhaClient.handle(message)) {
      var client = MinecraftClient.getInstance();
      if (addToHistory) client.inGameHud.getChatHud().addToMessageHistory(message);
      client.setScreen(null);
      ci.cancel();
    }
  }

  @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
  private void muhashist$press(
      double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
    var mod = MuhaClient.INSTANCE;
    var c = MinecraftClient.getInstance();
    if (mod != null
        && mod.hud() != null
        && mod.hudVisible()
        && mod.hud()
            .press(x, y, button, c.getWindow().getScaledWidth(), c.getWindow().getScaledHeight()))
      cir.setReturnValue(true);
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    var mod = MuhaClient.INSTANCE;
    var c = MinecraftClient.getInstance();
    return mod != null
            && mod.hud() != null
            && mod.hud()
                .drag(x, y, button, c.getWindow().getScaledWidth(), c.getWindow().getScaledHeight())
        || super.mouseDragged(x, y, button, dx, dy);
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    return button == 0 && MuhaClient.INSTANCE != null && MuhaClient.INSTANCE.releaseHud()
        || super.mouseReleased(x, y, button);
  }

  @Inject(method = "removed", at = @At("HEAD"))
  private void muhashist$removed(CallbackInfo ci) {
    if (MuhaClient.INSTANCE != null) MuhaClient.INSTANCE.releaseHud();
  }
}
