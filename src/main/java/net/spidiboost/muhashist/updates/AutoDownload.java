package net.spidiboost.muhashist.updates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public final class AutoDownload {
  private AutoDownload() {}

  private static Path file(Path game) {
    return game.resolve("config/muhashist-update.properties");
  }

  public static boolean enabled(Path game) {
    Path file = file(game);
    if (!Files.exists(file)) return true;
    if (Files.isSymbolicLink(file)) return false;
    try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      var values = new Properties();
      values.load(input);
      return "true".equalsIgnoreCase(values.getProperty("autoDownload", "true").strip());
    } catch (IOException | IllegalArgumentException e) {
      return false;
    }
  }

  public static void set(Path game, boolean enabled) throws IOException {
    Path file = file(game);
    Files.createDirectories(file.getParent());
    if (Files.isSymbolicLink(file) || !file.getParent().toRealPath().startsWith(game.toRealPath()))
      throw new IOException("Foreign preference path");
    Path temp = Files.createTempFile(file.getParent(), "muhashist-update-", ".tmp");
    try {
      Files.writeString(temp, "autoDownload=" + enabled + "\n", StandardCharsets.UTF_8);
      try {
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}
