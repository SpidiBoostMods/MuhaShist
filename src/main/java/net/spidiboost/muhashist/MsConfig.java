package net.spidiboost.muhashist;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public final class MsConfig {
  private final Path file;
  private final Properties extra = new Properties();
  private boolean clear = true;
  private long every = 20;
  private int x = -1, y = -1;

  public MsConfig(Path directory) throws IOException {
    file = directory.resolve("ms.cfg");
    if (Files.exists(file)) {
      if (Files.isSymbolicLink(file) || Files.size(file) > 65536)
        throw new IOException("ms.cfg: файл недоступен или слишком большой.");
      try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
        extra.load(input);
      }
      String value = extra.getProperty("clear", "true");
      if (!value.equals("true") && !value.equals("false"))
        throw new IOException("ms.cfg: clear должен быть true или false.");
      clear = Boolean.parseBoolean(value);
      try {
        every = Long.parseLong(extra.getProperty("clearEvery", "20"));
        x = Integer.parseInt(extra.getProperty("hudX", "-1"));
        y = Integer.parseInt(extra.getProperty("hudY", "-1"));
      } catch (NumberFormatException e) {
        throw new IOException("ms.cfg: неверное число. Проверьте clearEvery, hudX и hudY.");
      }
      if (every < 1 || x < -1 || y < -1)
        throw new IOException("ms.cfg: clearEvery > 0; координаты не меньше -1.");
    } else save();
  }

  public boolean clear() {
    return clear;
  }

  public long every() {
    return every;
  }

  public int x() {
    return x;
  }

  public int y() {
    return y;
  }

  public void position(int x, int y) throws IOException {
    int oldX = this.x, oldY = this.y;
    this.x = x;
    this.y = y;
    try {
      save();
    } catch (IOException e) {
      this.x = oldX;
      this.y = oldY;
      throw e;
    }
  }

  public void clearing(boolean value) throws IOException {
    boolean old = clear;
    clear = value;
    try {
      save();
    } catch (IOException e) {
      clear = old;
      throw e;
    }
  }

  public void interval(String value) throws IOException {
    long count;
    try {
      count = Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IOException(
          "Интервал очистки должен помещаться в целое число до " + Long.MAX_VALUE + ".");
    }
    if (count < 1) throw new IOException("Интервал очистки должен быть больше нуля.");
    long old = every;
    every = count;
    try {
      save();
    } catch (IOException e) {
      every = old;
      throw e;
    }
  }

  public void save() throws IOException {
    extra.setProperty("clear", Boolean.toString(clear));
    extra.setProperty("clearEvery", Long.toString(every));
    extra.setProperty("hudX", Integer.toString(x));
    extra.setProperty("hudY", Integer.toString(y));
    var text = new StringBuilder();
    extra.stringPropertyNames().stream()
        .sorted()
        .forEach(key -> text.append(key).append('=').append(extra.getProperty(key)).append('\n'));
    atomic(file, text.toString());
  }

  public static void atomic(Path file, String text) throws IOException {
    Path parent = file.toAbsolutePath().normalize().getParent();
    Files.createDirectories(parent);
    if (Files.isSymbolicLink(file))
      throw new IOException("Символические ссылки для файла настроек не поддерживаются.");
    Path temp = Files.createTempFile(parent, "ms-", ".tmp");
    try {
      Files.writeString(temp, text, StandardCharsets.UTF_8);
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
