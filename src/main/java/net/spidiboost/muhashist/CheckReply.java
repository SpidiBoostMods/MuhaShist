package net.spidiboost.muhashist;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CheckReply {
  public enum State {
    WAITING,
    ACCEPT,
    REJECT,
    ERROR,
    RETRY
  }

  private static final int FLAGS =
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
  private static final Pattern TARGET =
      Pattern.compile(
          "^(?:цель|target)\\s+(?:\\[([a-z0-9_]{1,16})]|([a-z0-9_]{1,16})(?=\\s))", FLAGS);
  private static final Pattern AUTHOR =
      Pattern.compile("^(?:забанил|banned by)\\s*:\\s*([a-z0-9_]{1,16})(?:\\s|$|[.!])", FLAGS);
  private static final Pattern SILENT =
      Pattern.compile(
          "(?:^|[\\s,])(?:тихий|silent)\\s*:\\s*(yes|no|да|нет|true|false|вкл|выкл)(?=$|[\\s,.!])",
          FLAGS);
  private final String target, curator;
  private boolean owned;
  private String author, error = "";
  private Boolean silent;
  private State state = State.WAITING;

  public CheckReply(String target, String curator) {
    this.target = target;
    this.curator = curator;
  }

  public State state() {
    return state;
  }

  public String error() {
    return error;
  }

  public void accept(String packet) {
    if (state != State.WAITING) return;
    for (String raw : packet.split("[\\r\\n]+")) {
      if (state != State.WAITING) break;
      String line = HistoryCounter.clean(raw), lower = line.toLowerCase(Locale.ROOT);
      if (line.isEmpty() || line.contains("»")) continue;
      var header = TARGET.matcher(line);
      if (header.find()) {
        String name = header.group(1) == null ? header.group(2) : header.group(1);
        owned = name.equalsIgnoreCase(target);
        author = null;
        silent = null;
        if (owned
            && (lower.contains("не забан")
                || lower.contains("not banned")
                || lower.contains("не найден"))) state = State.REJECT;
        continue;
      }
      if (lower.startsWith("нет прав")
          || lower.startsWith("недостаточно прав")
          || lower.startsWith("you do not have permission")) {
        state = State.ERROR;
        error = "Нет прав на /checkban.";
        continue;
      }
      if (lower.startsWith("ошибка: игрок не найден") || lower.startsWith("игрок не найден")) {
        state = State.REJECT;
        continue;
      }
      if (lower.startsWith("подождите")
          || lower.startsWith("слишком быстро")
          || lower.startsWith("не так быстро")) {
        state = State.RETRY;
        continue;
      }
      if (!owned) continue;
      var staff = AUTHOR.matcher(line);
      if (staff.find()) author = staff.group(1);
      var flag = SILENT.matcher(line);
      if (flag.find())
        silent =
            switch (flag.group(1).toLowerCase(Locale.ROOT)) {
              case "yes", "да", "true", "вкл" -> true;
              default -> false;
            };
      if (author != null && silent != null)
        state = author.equalsIgnoreCase(curator) && !silent ? State.ACCEPT : State.REJECT;
    }
  }
}
