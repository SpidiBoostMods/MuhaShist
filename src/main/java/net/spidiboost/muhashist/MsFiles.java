package net.spidiboost.muhashist;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

public final class MsFiles {
  private final Path file;

  public MsFiles(Path config) {
    file = config.resolve("ms.txt");
  }

  public Path file() {
    return file;
  }

  public static String format(HistoryCounter.Result result) {
    return "Ник: "
        + result.curator()
        + "\nБаллы: "
        + result.points()
        + "\nЗасчитано банов: "
        + result.counted()
        + "\nНики: "
        + String.join(" ", result.names())
        + "\n";
  }

  public void write(HistoryCounter.Result result) throws IOException {
    MsConfig.atomic(file, format(result));
  }

  public void ensure() throws IOException {
    if (!Files.exists(file)) {
      Files.createDirectories(file.getParent());
      Files.writeString(
          file,
          "Проверка ещё не запускалась.\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW);
    }
  }

  public void open() throws IOException {
    ensure();
    if (Files.isSymbolicLink(file))
      throw new IOException("ms.txt не должен быть символической ссылкой.");
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) {
      new ProcessBuilder("open", "-a", "TextEdit", file.toAbsolutePath().toString()).start();
      return;
    }
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
      Desktop.getDesktop().open(file.toFile());
      return;
    }
    if (os.startsWith("windows")) {
      new ProcessBuilder("notepad.exe", file.toAbsolutePath().toString()).start();
      return;
    }
    new ProcessBuilder("xdg-open", file.toAbsolutePath().toString()).start();
  }
}
