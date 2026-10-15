package net.spidiboost.muhashist;

import net.minecraft.text.*;

public final class MsTheme {
  private MsTheme() {}

  public static int blend(int a, int b, double t) {
    int color = 0;
    for (int s = 0; s <= 16; s += 8)
      color |= (int) Math.round((a >> s & 255) + ((b >> s & 255) - (a >> s & 255)) * t) << s;
    return color;
  }

  public static MutableText gradient(String value, boolean bold) {
    int[] stops = {0xff669f, 0xff9b69, 0xffe7a2};
    var text = Text.empty();
    int[] points = value.codePoints().toArray();
    for (int i = 0; i < points.length; i++) {
      double at = (double) i / Math.max(1, points.length - 1) * 2;
      int segment = Math.min(1, (int) at),
          color = blend(stops[segment], stops[segment + 1], at - segment);
      text.append(
          Text.literal(new String(Character.toChars(points[i])))
              .setStyle(Style.EMPTY.withColor(color).withBold(bold)));
    }
    return text;
  }

  public static MutableText message(String text) {
    return gradient("[MuhaShist]", true).append(Text.literal(" " + text).withColor(0xf4e6df));
  }

  public static MutableText error(String text) {
    return gradient("[MuhaShist]", true).append(Text.literal(" " + text).withColor(0xffb3ba));
  }
}
