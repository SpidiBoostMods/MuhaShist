import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class Icon {
  public static void main(String[] args) throws Exception {
    var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
    var g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setPaint(new GradientPaint(0, 0, new Color(28, 23, 39), 256, 256, new Color(15, 20, 30)));
    g.fill(new RoundRectangle2D.Double(4, 4, 248, 248, 62, 62));
    g.setStroke(new BasicStroke(5));
    g.setPaint(
        new GradientPaint(20, 20, new Color(255, 102, 159), 230, 236, new Color(255, 223, 150)));
    g.draw(new RoundRectangle2D.Double(11, 11, 234, 234, 53, 53));
    var m = new Path2D.Double();
    m.moveTo(45, 174);
    m.lineTo(45, 78);
    m.lineTo(69, 78);
    m.lineTo(105, 125);
    m.lineTo(141, 78);
    m.lineTo(165, 78);
    m.lineTo(165, 174);
    m.lineTo(142, 174);
    m.lineTo(142, 113);
    m.lineTo(105, 160);
    m.lineTo(68, 113);
    m.lineTo(68, 174);
    m.closePath();
    g.setPaint(
        new LinearGradientPaint(
            40,
            85,
            205,
            185,
            new float[] {0, .5f, 1},
            new Color[] {
              new Color(255, 102, 159), new Color(255, 156, 105), new Color(255, 231, 162)
            }));
    g.fill(m);
    g.setStroke(new BasicStroke(13, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    var s = new Path2D.Double();
    s.moveTo(212, 91);
    s.curveTo(173, 70, 168, 121, 199, 126);
    s.curveTo(230, 132, 219, 183, 184, 164);
    g.draw(s);
    g.dispose();
    Path target = Path.of(args[0]);
    Files.createDirectories(target.getParent());
    ImageIO.write(image, "png", target.toFile());
  }
}
