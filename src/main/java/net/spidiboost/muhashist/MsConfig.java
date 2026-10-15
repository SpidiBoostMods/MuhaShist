package net.spidiboost.muhashist;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public final class MsConfig {
  private static final Pattern SETTING =
      Pattern.compile("^([A-Za-z][A-Za-z0-9_.-]*)\\s*=\\s*(.*)$");
  private final Path file;
  private boolean clear = true;
  private long every = 20, fishMinutes = 10;
  private int x = -1, y = -1;
  private ScoreRules rules = ScoreRules.standard();

  public MsConfig(Path directory) throws IOException {
    file = directory.resolve("ms.cfg");
    if (!Files.exists(file)) {
      atomic(file, defaults());
      return;
    }
    var rows = read();
    var settings = new HashMap<String, String>();
    var reasons = new ArrayList<String>();
    boolean section = false;
    for (int i = 0; i < rows.size(); i++) {
      String line = rows.get(i).strip();
      if (line.startsWith("#")) {
        section |= line.toLowerCase(Locale.ROOT).contains("причин");
        continue;
      }
      if (line.isEmpty()) continue;
      var setting = SETTING.matcher(line);
      if (setting.matches()) {
        if (settings.putIfAbsent(setting.group(1), setting.group(2).strip()) != null)
          throw new IOException("ms.cfg, строка " + (i + 1) + ": параметр повторяется.");
      } else {
        try {
          ScoreRules.parse(List.of(line));
        } catch (IllegalArgumentException e) {
          throw new IOException("ms.cfg, строка " + (i + 1) + ": " + e.getMessage());
        }
        reasons.add(line);
      }
    }
    String value = settings.getOrDefault("clear", "true");
    if (!Set.of("true", "false").contains(value))
      throw new IOException("ms.cfg: clear должен быть true или false.");
    clear = Boolean.parseBoolean(value);
    try {
      every = Long.parseLong(settings.getOrDefault("clearEvery", "20"));
      fishMinutes = Long.parseLong(settings.getOrDefault("autofishWindowMinutes", "10"));
      Math.multiplyExact(fishMinutes, 60000);
      x = Integer.parseInt(settings.getOrDefault("hudX", "-1"));
      y = Integer.parseInt(settings.getOrDefault("hudY", "-1"));
    } catch (NumberFormatException | ArithmeticException e) {
      throw new IOException("ms.cfg: неверное число в настройках GUI, очистки или autofish.");
    }
    if (every < 1 || fishMinutes < 1 || x < -1 || y < -1)
      throw new IOException("ms.cfg: интервалы > 0; координаты не меньше -1.");
    if (reasons.isEmpty() && !section) {
      var unknown = new StringBuilder();
      settings.forEach(
          (key, item) -> {
            if (!Set.of("clear", "clearEvery", "hudX", "hudY", "autofishWindowMinutes")
                .contains(key)) unknown.append(key).append('=').append(item).append('\n');
          });
      atomic(file, defaults() + unknown);
    } else {
      try {
        rules = ScoreRules.parse(reasons);
      } catch (IllegalArgumentException e) {
        throw new IOException("ms.cfg: " + e.getMessage());
      }
    }
  }

  private List<String> read() throws IOException {
    if (Files.isSymbolicLink(file) || Files.size(file) > 1048576)
      throw new IOException("ms.cfg: файл недоступен или больше 1 МБ.");
    return Files.readAllLines(file, StandardCharsets.UTF_8);
  }

  private String defaults() {
    return "# MuhaShist\n\n# Положение GUI. -1 — по центру над хотбаром.\nhudX="
        + x
        + "\nhudY="
        + y
        + "\n\n# Очистка чата\nclear="
        + clear
        + "\nclearEvery="
        + every
        + "\n\n"
        + "# Autofish: один засчитанный бан за окно; следующий — строго позже окна.\n"
        + "autofishWindowMinutes="
        + fishMinutes
        + "\n\n"
        + "# Причины и баллы\n"
        + "# Причины разделяются только ;. Запятые внутри причины сохраняются.\n"
        + "# причина1; причина2 - 0.7\n"
        + "# Точка и запятая в баллах равнозначны. Регистр и ё/е не важны.\n"
        + "# Читы сопоставляются точно; варианты тим-чита и autofish распознаются отдельно.\n"
        + String.join("\n\n", ScoreRules.defaults())
        + "\n";
  }

  public Path file() {
    return file;
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

  public ScoreRules rules() {
    return rules;
  }

  public long fishMillis() {
    return fishMinutes * 60000;
  }

  private void update(Map<String, String> values) throws IOException {
    var remaining = new LinkedHashMap<>(values);
    var rows = Files.exists(file) ? read() : new ArrayList<>(List.of(defaults().split("\n", -1)));
    var output = new StringBuilder();
    for (String line : rows) {
      var match = SETTING.matcher(line.strip());
      if (match.matches() && remaining.containsKey(match.group(1)))
        output.append(match.group(1)).append('=').append(remaining.remove(match.group(1)));
      else output.append(line);
      output.append('\n');
    }
    remaining.forEach((key, value) -> output.append(key).append('=').append(value).append('\n'));
    atomic(file, output.toString());
  }

  public void position(int x, int y) throws IOException {
    if (x < -1 || y < -1) throw new IOException("Координаты GUI не меньше -1.");
    update(Map.of("hudX", Integer.toString(x), "hudY", Integer.toString(y)));
    this.x = x;
    this.y = y;
  }

  public void clearing(boolean value) throws IOException {
    update(Map.of("clear", Boolean.toString(value)));
    clear = value;
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
    update(Map.of("clearEvery", Long.toString(count)));
    every = count;
  }

  public void save() throws IOException {
    update(
        Map.of(
            "hudX",
            Integer.toString(x),
            "hudY",
            Integer.toString(y),
            "clear",
            Boolean.toString(clear),
            "clearEvery",
            Long.toString(every),
            "autofishWindowMinutes",
            Long.toString(fishMinutes)));
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
