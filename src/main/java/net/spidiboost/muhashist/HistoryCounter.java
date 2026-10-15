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

    default void check(String nickname) {}
  }

  public record Result(
      String curator,
      LocalDate since,
      BigInteger limit,
      long processed,
      long counted,
      BigDecimal score,
      List<String> names,
      boolean complete) {
    public String points() {
      return score.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public BigInteger tenths() {
      return score.movePointRight(1).toBigIntegerExact();
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
      Pattern.compile(
          "\\s*\\[\\s*(активный|ист[её]к|снят|неактивный|active|expired)\\s*]\\s*$", FLAGS);
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
  private BigInteger limit = BigInteger.ZERO, expected = BigInteger.ZERO;
  private BigDecimal total = BigDecimal.ZERO;
  private ScoreRules rules = ScoreRules.standard();
  private long fishWindow = 600000;
  private final ArrayDeque<Ban> checks = new ArrayDeque<>();
  private Ban checking;
  private CheckReply reply;
  private boolean historyDone;
  private long checkStarted, nextCheckAt, checked, retries;
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
    return total.stripTrailingZeros().toPlainString().replace('.', ',');
  }

  public void scoring(ScoreRules rules, long window) {
    if (active) throw new IllegalStateException("Причины применяются со следующей проверки.");
    this.rules = Objects.requireNonNull(rules);
    if (window < 1) throw new IllegalArgumentException("Окно autofish должно быть больше нуля.");
    fishWindow = window;
  }

  public boolean checking() {
    return historyDone && active;
  }

  public long checked() {
    return checked;
  }

  public long checkCount() {
    return checked + checks.size() + (checking == null ? 0 : 1);
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
    total = BigDecimal.ZERO;
    bans.clear();
    available.clear();
    waiting.clear();
    current = null;
    reason = null;
    date = null;
    processed = counted = sinceClear = malformed = checked = retries = 0;
    checks.clear();
    checking = null;
    reply = null;
    historyDone = false;
    nextCheckAt = 0;
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
    if (historyDone) {
      checkLine(text);
      return;
    }
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
      endHistory();
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
      if (!reason.isEmpty()) return;
      reason.setLength(0);
      reason.append(reasonPart(why.group(1)));
      readingReason = !reasonClosed(why.group(1));
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
        reason.append(' ').append(reasonPart(text));
        readingReason = !reasonClosed(text);
        owned();
      }
    }
  }

  private String reasonPart(String text) {
    var marker = STATUS.matcher(text);
    if (marker.find()) {
      if (marker.group(1).equalsIgnoreCase("активный")
          || marker.group(1).equalsIgnoreCase("active")) current.activeBan = true;
      return text.substring(0, marker.start());
    }
    return text;
  }

  private static boolean reasonClosed(String value) {
    String text = value.strip();
    return STATUS.matcher(text).find()
        || text.length() > 1
            && (text.startsWith("'") && text.endsWith("'")
                || text.startsWith("\"") && text.endsWith("\""));
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
            ? rules.score(reason.toString())
            : BigDecimal.ZERO;
    ban.fish = ScoreRules.autofish(reason.toString());
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
    count(ban);
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
      if (ban.counted) {
        total = total.subtract(ban.base);
        counted--;
        ban.counted = false;
      }
    }
  }

  private void owned() {
    lastOwned = clock.getAsLong();
  }

  public void tick() {
    if (!active) return;
    long now = clock.getAsLong();
    if (historyDone) {
      if (checking == null) {
        if (checks.isEmpty() || now >= nextCheckAt) nextCheck();
      } else if (now - checkStarted >= 6000)
        retryCheck("Неполный ответ /checkban " + checking.name + ".");
      return;
    }
    if (!header && now - started >= 10000) {
      stop("Нет ответа /shist за 10 секунд. Проверьте подключение и права.");
      return;
    }
    if (header && now - lastOwned >= (processed == 0 && current == null ? 500 : 1800)) endHistory();
  }

  public void stop(String error) {
    if (active) finish(false, error);
  }

  public void finishNow() {
    if (active && !historyDone) endHistory();
  }

  private void count(Ban ban) {
    if (!ban.counted
        && !ban.fish
        && !ban.manual
        && ban.base.signum() > 0
        && (!ban.activeBan || ban.verified)) {
      total = total.add(ban.base);
      counted++;
      ban.counted = true;
    }
  }

  private void endHistory() {
    commit();
    historyDone = true;
    for (Ban ban : bans)
      if (ban.activeBan
          && ban.mine
          && !ban.manual
          && ban.time != null
          && !ban.time.toLocalDate().isBefore(since)) checks.addLast(ban);
    nextCheck();
  }

  private void nextCheck() {
    if (!active) return;
    checking = checks.pollFirst();
    if (checking == null) {
      finish(true, "");
      return;
    }
    target = checking.name;
    reply = new CheckReply(checking.name, curator);
    retries = 0;
    sendCheck();
  }

  private void retryCheck(String problem) {
    if (++retries > 2) {
      stop(problem + " Проверка остановлена; неподтверждённые баны не засчитаны.");
      return;
    }
    reply = new CheckReply(checking.name, curator);
    sendCheck();
  }

  private void sendCheck() {
    checkStarted = clock.getAsLong();
    nextCheckAt = checkStarted + 100;
    listener.check(checking.name);
  }

  private void checkLine(String text) {
    if (checking == null || reply == null) return;
    reply.accept(text);
    switch (reply.state()) {
      case WAITING -> {}
      case ERROR -> stop(reply.error());
      case RETRY -> {
        checkStarted = clock.getAsLong() - 5000;
        reply = new CheckReply(checking.name, curator);
      }
      case ACCEPT, REJECT -> {
        checking.verified = reply.state() == CheckReply.State.ACCEPT;
        count(checking);
        checked++;
        checking = null;
        reply = null;
        if (clear && checked % clearEvery == 0) listener.clearChat();
      }
    }
  }

  private void finish(boolean complete, String error) {
    commit();
    LocalDateTime fishSince = null;
    for (Ban ban :
        bans.stream()
            .filter(b -> b.fish && !b.manual && b.base.signum() > 0 && (!b.activeBan || b.verified))
            .sorted(Comparator.comparing(b -> b.time))
            .toList()) {
      if (fishSince == null
          || java.time.Duration.between(fishSince, ban.time).toMillis() > fishWindow) {
        total = total.add(ban.base);
        counted++;
        ban.counted = true;
        fishSince = ban.time;
      }
    }
    active = false;
    List<String> names = new ArrayList<>();
    for (Ban ban : bans) if (ban.counted) names.add(ban.name);
    Result result =
        new Result(curator, since, limit, processed, counted, total, List.copyOf(names), complete);
    bans.clear();
    available.clear();
    waiting.clear();
    reason = null;
    current = null;
    checks.clear();
    checking = null;
    reply = null;
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
    BigDecimal base = BigDecimal.ZERO;
    boolean manual, linked, activeBan, verified, fish, counted;

    Ban(String name, LocalDateTime time, boolean mine) {
      this.name = name;
      key = name.toLowerCase(Locale.ROOT);
      this.time = time;
      this.mine = mine;
    }
  }

  private record Event(LocalDateTime time, boolean manual) {}
}
