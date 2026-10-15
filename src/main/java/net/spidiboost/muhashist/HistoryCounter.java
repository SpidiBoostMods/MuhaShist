package net.spidiboost.muhashist;

import java.math.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

public final class HistoryCounter {
  public interface Listener {
    void clearChat();

    void finished(Result result, String error);
  }

  public record Result(
      String curator,
      LocalDate since,
      BigInteger limit,
      long processed,
      long counted,
      BigInteger tenths,
      List<String> names,
      boolean complete) {
    public String points() {
      return new BigDecimal(tenths)
          .movePointLeft(1)
          .stripTrailingZeros()
          .toPlainString()
          .replace('.', ',');
    }
  }

  private static final int FLAGS =
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
  private static final Pattern FORMAT = Pattern.compile("§[0-9a-fk-orx]", FLAGS),
      TIME = Pattern.compile("^\\[\\d{1,2}:\\d{2}:\\d{2}]\\s*");
  private static final Pattern HEADER =
      Pattern.compile("^история персонала\\s+([A-Za-z0-9_]{1,16})(?:\\s|$)", FLAGS);
  private static final Pattern LIMIT = Pattern.compile("лимит\\s*:\\s*(\\d+)", FLAGS);
  private static final Pattern DATE = Pattern.compile("^[-—–]+\\s*\\[([^]]+)]\\s*[-—–]*$");
  private static final Pattern BAN =
      Pattern.compile(
          "^([A-Za-z0-9_]{1,16})\\s+был\\s+забанен\\s+(\\p{L}+)\\s+([A-Za-z0-9_]{1,16})(?=$|[\\s.,])",
          FLAGS);
  private static final Pattern REASON =
      Pattern.compile("^(?:по причине|причина|reason)\\s*:\\s*(.*)$", FLAGS);
  private static final Pattern UNBAN =
      Pattern.compile(
          "^([A-Za-z0-9_]{1,16})\\s+was\\s+unbanned\\s+by\\s+([A-Za-z0-9_]{1,16})[.!]?\\s*$",
          FLAGS);
  private static final Pattern STATUS =
      Pattern.compile("\\s*\\[\\s*(?:активный|ист[её]к|снят|неактивный)\\s*]\\s*$", FLAGS);
  private final Listener listener;
  private final LongSupplier clock;
  private final List<Ban> bans = new ArrayList<>();
  private final Map<String, NavigableMap<LocalDateTime, ArrayDeque<Ban>>> available =
      new HashMap<>();
  private final Map<String, NavigableMap<LocalDateTime, ArrayDeque<Event>>> waiting =
      new HashMap<>();
  private final Map<String, LocalDateTime> dates =
      new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, LocalDateTime> entry) {
          return size() > 64;
        }
      };
  private String curator = "", target = "";
  private LocalDate since;
  private BigInteger limit = BigInteger.ZERO, expected = BigInteger.ZERO, total = BigInteger.ZERO;
  private LocalDateTime date;
  private Ban current;
  private StringBuilder reason;
  private boolean active, header, readingReason;
  private long started, lastOwned, processed, counted, sinceClear, clearEvery = 20;
  private boolean clear = true;
  private long malformed;

  public HistoryCounter(Listener listener, LongSupplier clock) {
    this.listener = listener;
    this.clock = clock;
  }

  public void clearing(boolean enabled, long every) {
    if (every <= 0) throw new IllegalArgumentException("Интервал очистки должен быть больше нуля.");
    clear = enabled;
    clearEvery = every;
  }

  public boolean active() {
    return active;
  }

  public boolean headerSeen() {
    return header;
  }

  public String curator() {
    return curator;
  }

  public String target() {
    return target;
  }

  public LocalDate since() {
    return since;
  }

  public long processed() {
    return processed;
  }

  public long counted() {
    return counted;
  }

  public BigInteger expected() {
    return expected;
  }

  public String points() {
    return new BigDecimal(total)
        .movePointLeft(1)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',');
  }

  public void start(String curator, BigInteger limit, LocalDate since) {
    if (active) throw new IllegalStateException("Подсчёт уже идёт. Остановить: /ms stop.");
    if (!curator.matches("[A-Za-z0-9_]{1,16}") || limit.signum() <= 0 || since == null)
      throw new IllegalArgumentException("Неверные параметры проверки.");
    this.curator = curator;
    this.limit = limit;
    this.expected = limit;
    this.since = since;
    target = "";
    total = BigInteger.ZERO;
    bans.clear();
    available.clear();
    waiting.clear();
    current = null;
    reason = null;
    date = null;
    processed = counted = sinceClear = malformed = 0;
    header = readingReason = false;
    started = lastOwned = clock.getAsLong();
    active = true;
  }

  public void accept(String packet) {
    if (!active || packet == null) return;
    int start = 0;
    for (int i = 0; i <= packet.length() && active; i++)
      if (i == packet.length() || packet.charAt(i) == '\n' || packet.charAt(i) == '\r') {
        if (i > start) line(clean(packet.substring(start, i)));
        start = i + 1;
      }
  }

  private void line(String text) {
    if (text.isEmpty() || text.contains("»")) return;
    String lower = text.toLowerCase(Locale.ROOT);
    var heading = HEADER.matcher(text);
    if (lower.startsWith("история персонала") && heading.find()) {
      if (!heading.group(1).equalsIgnoreCase(curator)) {
        if (header) stop("Получена чужая история. Запустите /ms заново.");
        return;
      }
      if (header && (processed > 0 || current != null)) {
        stop("Сервер начал повторную историю. Запустите /ms заново.");
        return;
      }
      header = true;
      var value = LIMIT.matcher(text);
      if (value.find()) expected = new BigInteger(value.group(1));
      owned();
      return;
    }
    if (!header) {
      if (!text.contains("»")
          && (lower.startsWith("нет прав")
              || lower.startsWith("недостаточно прав")
              || lower.startsWith("you do not have permission")))
        stop("Нет прав на /shist на этом сервере.");
      return;
    }
    if (lower.matches("(?:нет|не найдено) (?:истории|записей|наказаний).*")) {
      owned();
      finish(true, "");
      return;
    }
    var timestamp = DATE.matcher(text);
    if ((text.charAt(0) == '-' || text.charAt(0) == '—' || text.charAt(0) == '–')
        && timestamp.matches()) {
      commit();
      String value = timestamp.group(1);
      date = dates.get(value);
      if (date == null) {
        date = timestamp(value);
        if (date != null) dates.put(value, date);
      }
      owned();
      return;
    }
    var ban = BAN.matcher(text);
    if (lower.contains(" был забанен ") && ban.find()) {
      commit();
      current =
          new Ban(
              ban.group(1),
              date,
              ban.group(2).equalsIgnoreCase("куратором") && ban.group(3).equalsIgnoreCase(curator));
      target = current.name;
      reason = new StringBuilder();
      readingReason = false;
      owned();
      return;
    }
    var unban = UNBAN.matcher(text);
    if (lower.contains(" was unbanned by ") && unban.matches()) {
      String key = unban.group(1).toLowerCase(Locale.ROOT);
      boolean manual = !unban.group(2).equalsIgnoreCase("ReallyWorld");
      if (current != null && current.key.equals(key)) {
        current.linked = true;
        current.manual |= manual;
      } else event(key, new Event(date, manual));
      readingReason = false;
      owned();
      return;
    }
    var why = REASON.matcher(text);
    if (current != null && why.matches()) {
      reason.setLength(0);
      reason.append(STATUS.matcher(why.group(1)).replaceFirst(""));
      readingReason = true;
      owned();
      return;
    }
    if (current != null
        && (lower.startsWith("окончание")
            || lower.startsWith("expires")
            || lower.startsWith("ip "))) {
      readingReason = false;
      owned();
      return;
    }
    if (current != null
        && readingReason
        && !text.contains("»")
        && !text.startsWith("[")
        && !text.contains(" -> ")
        && !lower.startsWith("ac ")
        && !lower.startsWith("rwac ")) {
      if (reason.length() < 4096) {
        reason.append(' ').append(STATUS.matcher(text).replaceFirst(""));
        owned();
      }
    }
  }

  private static LocalDateTime timestamp(String raw) {
    String[] tokens = raw.strip().split("\\s+", 2);
    try {
      LocalDate day = DateInput.parse(tokens[0]);
      LocalTime time =
          tokens.length == 1
              ? LocalTime.MIDNIGHT
              : LocalTime.parse(tokens[1].strip(), DateTimeFormatter.ISO_LOCAL_TIME);
      return day.atTime(time);
    } catch (IllegalArgumentException | DateTimeException e) {
      return null;
    }
  }

  private void commit() {
    if (current == null) return;
    Ban ban = current;
    current = null;
    readingReason = false;
    ban.base =
        ban.mine && ban.time != null && !ban.time.toLocalDate().isBefore(since)
            ? ScoreRules.tenths(reason.toString())
            : 0;
    if (ban.time == null || reason.isEmpty()) malformed++;
    reason = null;
    bans.add(ban);
    processed++;
    if (!ban.linked && ban.time != null) {
      var events = waiting.get(ban.key);
      var next = events == null ? null : events.ceilingEntry(ban.time);
      if (next != null) {
        Event e = next.getValue().removeFirst();
        if (next.getValue().isEmpty()) events.remove(next.getKey());
        ban.linked = true;
        ban.manual = e.manual;
      } else
        available
            .computeIfAbsent(ban.key, k -> new TreeMap<>())
            .computeIfAbsent(ban.time, k -> new ArrayDeque<>())
            .addLast(ban);
    }
    if (!ban.manual && ban.base > 0) {
      total = total.add(BigInteger.valueOf(ban.base));
      counted++;
    }
    if (++sinceClear >= clearEvery) {
      sinceClear = 0;
      if (clear) listener.clearChat();
    }
  }

  private void event(String key, Event event) {
    if (event.time == null) return;
    var byDate = available.get(key);
    var previous = byDate == null ? null : byDate.floorEntry(event.time);
    if (previous == null) {
      waiting
          .computeIfAbsent(key, k -> new TreeMap<>())
          .computeIfAbsent(event.time, k -> new ArrayDeque<>())
          .addLast(event);
      return;
    }
    Ban ban = previous.getValue().removeLast();
    if (previous.getValue().isEmpty()) byDate.remove(previous.getKey());
    ban.linked = true;
    if (event.manual) {
      ban.manual = true;
      if (ban.base > 0) {
        total = total.subtract(BigInteger.valueOf(ban.base));
        counted--;
      }
    }
  }

  private void owned() {
    lastOwned = clock.getAsLong();
  }

  public void tick() {
    if (!active) return;
    long now = clock.getAsLong();
    if (!header && now - started >= 10000) {
      stop("Нет ответа /shist за 10 секунд. Проверьте подключение и права.");
      return;
    }
    if (header && now - lastOwned >= (processed == 0 && current == null ? 500 : 1800))
      finish(true, "");
  }

  public void stop(String error) {
    if (active) finish(false, error);
  }

  public void finishNow() {
    if (active) finish(true, "");
  }

  private void finish(boolean complete, String error) {
    commit();
    active = false;
    List<String> names = new ArrayList<>();
    for (Ban ban : bans) if (!ban.manual && ban.base > 0) names.add(ban.name);
    Result result =
        new Result(curator, since, limit, processed, counted, total, List.copyOf(names), complete);
    bans.clear();
    available.clear();
    waiting.clear();
    reason = null;
    current = null;
    if (error.isEmpty() && malformed > 0)
      error = "Не разобрано записей: " + malformed + ". Не найдена дата или причина бана.";
    listener.finished(result, error);
  }

  public static String clean(String raw) {
    String text =
        FORMAT
            .matcher(raw)
            .replaceAll("")
            .replace('\u00a0', ' ')
            .replace('\u202f', ' ')
            .replace("\u200b", "")
            .replace("\ufeff", "")
            .strip();
    while (text.startsWith("[") && TIME.matcher(text).find())
      text = TIME.matcher(text).replaceFirst("").strip();
    return text;
  }

  private static final class Ban {
    final String name, key;
    final LocalDateTime time;
    final boolean mine;
    int base;
    boolean manual, linked;

    Ban(String name, LocalDateTime time, boolean mine) {
      this.name = name;
      key = name.toLowerCase(Locale.ROOT);
      this.time = time;
      this.mine = mine;
    }
  }

  private record Event(LocalDateTime time, boolean manual) {}
}
