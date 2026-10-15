package net.spidiboost.muhashist;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class MsHud {
  public record Bounds(int x, int y, int width, int height) {
    public boolean contains(double px, double py) {
      return px >= x && px < x + width && py >= y && py < y + height;
    }
  }

  private final MsConfig config;
  private boolean dragging;
  private double dx, dy;
  private int movingX, movingY;
  private final Text title = MsTheme.gradient("MuhaShist", true);

  public MsHud(MsConfig config) {
    this.config = config;
  }

  public Bounds bounds(int width, int height) {
    int w = Math.max(1, Math.min(264, width - 12)), h = Math.min(89, Math.max(1, height));
    int x = dragging ? movingX : config.x() < 0 ? (width - w) / 2 : config.x(),
        y = dragging ? movingY : config.y() < 0 ? height - h - 60 : config.y();
    return new Bounds(clamp(x, width - w), clamp(y, height - h), w, h);
  }

  private static int clamp(int value, int max) {
    return Math.max(0, Math.min(value, Math.max(0, max)));
  }

  public boolean press(double x, double y, int button, int width, int height) {
    var b = bounds(width, height);
    if (button != 0 || !b.contains(x, y)) return false;
    dragging = true;
    dx = x - b.x;
    dy = y - b.y;
    movingX = b.x;
    movingY = b.y;
    return true;
  }

  public boolean drag(double x, double y, int button, int width, int height) {
    if (!dragging || button != 0) return false;
    var b = bounds(width, height);
    movingX = clamp((int) Math.round(x - dx), width - b.width);
    movingY = clamp((int) Math.round(y - dy), height - b.height);
    return true;
  }

  public boolean release() throws IOException {
    if (!dragging) return false;
    dragging = false;
    config.position(movingX, movingY);
    return true;
  }

  public void draw(DrawContext c, HistoryCounter scan) {
    var client = MinecraftClient.getInstance();
    if (client.options.hudHidden
        || c.getScaledWindowWidth() < 180
        || c.getScaledWindowHeight() < 120) return;
    var b = bounds(c.getScaledWindowWidth(), c.getScaledWindowHeight());
    int x = b.x, y = b.y, w = b.width, h = b.height;
    c.fill(x + 5, y + 5, x + w - 2, y + h + 3, 0x38000000);
    c.fill(x + 3, y, x + w - 3, y + h, 0xf1151721);
    c.fill(x, y + 3, x + w, y + h - 3, 0xf1151721);
    c.fillGradient(x + 6, y, x + w - 6, y + 1, 0xffef659d, 0xffffd48d);
    c.fill(x + 9, y + 11, x + 11, y + 28, 0xffffb377);
    c.drawTextWithShadow(client.textRenderer, title, x + 18, y + 11, 0xffffff);
    String label = "БАНЫ";
    c.drawTextWithShadow(
        client.textRenderer,
        Text.literal(label),
        x + w - client.textRenderer.getWidth(label) - 10,
        y + 12,
        0x9aa3b6);
    String date =
        scan.since() == null
            ? ""
            : "с " + scan.since().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    c.drawTextWithShadow(
        client.textRenderer, Text.literal(fit(scan.curator(), w - 110)), x + 10, y + 33, 0xf9e6dd);
    if (w > 200)
      c.drawTextWithShadow(
          client.textRenderer,
          Text.literal(date),
          x + w - client.textRenderer.getWidth(date) - 10,
          y + 34,
          0xb6aab9);
    String detail =
        !scan.headerSeen()
            ? "Ожидаем /shist…"
            : scan.target().isEmpty() ? "Получаем историю…" : scan.target();
    c.drawTextWithShadow(
        client.textRenderer, Text.literal(fit(detail, w - 20)), x + 10, y + 47, 0xa8acbd);
    int length = w - 20;
    c.fill(x + 10, y + 61, x + w - 10, y + 64, 0xff31313e);
    double total = scan.expected().doubleValue(), ratio = total > 0 ? scan.processed() / total : 0;
    int filled = (int) (length * Math.max(0, Math.min(1, ratio)));
    if (!scan.headerSeen()) {
      int p = (int) (System.nanoTime() / 30_000_000 % Math.max(1, length));
      for (int i = Math.max(0, p - 22); i < p; i++)
        c.fill(x + 10 + i, y + 61, x + 11 + i, y + 64, 0xff805a7b);
    } else
      for (int i = 0; i < filled; i++)
        c.fill(
            x + 10 + i,
            y + 61,
            x + 11 + i,
            y + 64,
            0xff000000 | MsTheme.blend(0xff6a9e, 0xffd78b, (double) i / Math.max(1, length - 1)));
    String stats = "Проверено " + scan.processed() + " · баллы " + scan.points();
    c.drawTextWithShadow(
        client.textRenderer, Text.literal(fit(stats, w - 20)), x + 10, y + 73, 0xe4d6df);
    if (dragging) c.fill(x + 4, y + h - 2, x + w - 4, y + h - 1, 0x99ffbb86);
  }

  private static String fit(String value, int width) {
    return MinecraftClient.getInstance().textRenderer.trimToWidth(value, Math.max(1, width));
  }
}
