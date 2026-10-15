package net.spidiboost.muhashist;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class ScoreRules {
  private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;
  private static final Pattern MARKER =
      Pattern.compile("\\s*\\[(?:активный|истек|снят|неактивный)]\\s*$", FLAGS);
  private static final Pattern FORMAT = Pattern.compile("(?i)§[0-9a-fk-orx]");
  private static final Pattern SPACE = Pattern.compile("\\s+", FLAGS);
  private static final Pattern TEAM =
      Pattern.compile(
          "^(?:(?:тим\\p{L}*|темм?ейт\\p{L}*|команд\\p{L}*|игра|союз|буст)\\s*(?:с\\s*)?чит\\p{L}*|(?:team\\p{L}*|teaming|boosting|playing)\\s*(?:with\\s*)?(?:a\\s*)?cheat\\p{L}*)(?:\\s+(?:by|от)\\s+[a-z0-9_]+)?$",
          FLAGS);
  private static final Pattern FISH =
      Pattern.compile(
          "(?:[aа][\\s_\\-]*[uу][\\s_\\-]*[tт][\\s_\\-]*[oо0][\\s_\\-]*(?:f[\\s_\\-]*[iі][\\s_\\-]*[sс][\\s_\\-]*h(?:ing)?|phish)|а[вф][\\s_\\-]*то[\\s_\\-]*фиш(?:инг)?|авто[\\s_\\-]*рыбал\\p{L}*)",
          FLAGS);
  private static final Pattern FISH_REASON =
      Pattern.compile("^(?:читы|чит|читер|cheats?|cheater)(?=$|[\\s\\p{P}])", FLAGS);
  private static final Pattern ROW =
      Pattern.compile("^(.+)\\s+-\\s+([+]?(?:\\d+(?:[.,]\\d+)?|[.,]\\d+))\\s*$", FLAGS);
  private static final List<String> DEFAULTS =
      List.of(
          "Не истёк срок удаления читов; Не истёк срок использования читов; Читы при проверке;"
              + " Хранение читов; Отказ от прохождения проверки; Лив с проверки; Помеха проведению"
              + " проверки; Помеха проверке через AnyDesk, было выдано предупреждение; Неадекватное"
              + " поведение во время проведения проверки; Признание в использовании/хранении читов"
              + " при проверке; Ограничение доступа администрации к файлам/папкам во время"
              + " проверки; Сложился при проверке; Чистка перед проверкой; Использование батников"
              + " для удаления следов запрещённого ПО; Хранение батников для удаления следов"
              + " запрещённого ПО; Использование запрещённого мода; Хранение запрещённого мода -"
              + " 1.0",
          "Читы; Чит; Читер; Читерство; Читинг; Cheats; Cheat; Cheater; Cheating; Использование"
              + " читов; Тим чит; Тиммейт читера; Тим с читером; Команда с читером; Игра с читером;"
              + " Союз с читером; Буст с читером; Team cheat; Team with cheater; Автофиш; Autofish"
              + " - 0.7");
  private static final ScoreRules STANDARD = parse(DEFAULTS);
  private final Map<String, BigDecimal> values;
  private final Map<String, BigDecimal> cache =
      new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BigDecimal> entry) {
          return size() > 512;
        }
      };

  private ScoreRules(Map<String, BigDecimal> values) {
    this.values = Map.copyOf(values);
  }

  public static List<String> defaults() {
    return DEFAULTS;
  }

  public static ScoreRules standard() {
    return STANDARD;
  }

  public boolean previousDefaults() {
    var previous = new HashMap<>(STANDARD.values);
    for (String reason :
        List.of(
            "Помеха проверке через AnyDesk",
            "Уклон от проверки",
            "Уклон от проверки (время истекло)",
            "Отказ от проверки",
            "Сложился перед проверкой",
            "Ограничение доступа к файлам при проверке",
            "Признание при проверке",
            "Неадекватное поведение при проверке",
            "Использование батников для чистки следов ПО",
            "Хранение батников для чистки следов")) previous.put(normalize(reason), BigDecimal.ONE);
    return values.equals(previous);
  }

  public static ScoreRules parse(List<String> rows) {
    var values = new LinkedHashMap<String, BigDecimal>();
    for (String row : rows) {
      var match = ROW.matcher(row.strip());
      if (!match.matches()) throw new IllegalArgumentException("Причины: причина1; причина2 - 0.7");
      BigDecimal score;
      try {
        score = new BigDecimal(match.group(2).replace(',', '.')).stripTrailingZeros();
        if (score.signum() < 0 || score.scale() > 12 || score.precision() > 30)
          throw new NumberFormatException();
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Баллы: неотрицательное число, до 12 знаков после запятой.");
      }
      for (String item : match.group(1).split(";", -1)) {
        String reason = normalize(item);
        if (reason.isEmpty()) throw new IllegalArgumentException("Пустая причина между ;.");
        BigDecimal previous = values.putIfAbsent(reason, score);
        if (previous != null && previous.compareTo(score) != 0)
          throw new IllegalArgumentException(
              "Причина повторяется с разными баллами: " + item.strip());
      }
    }
    return new ScoreRules(values);
  }

  public static String normalize(String raw) {
    String text =
        FORMAT
            .matcher(Normalizer.normalize(raw, Normalizer.Form.NFKC))
            .replaceAll("")
            .toLowerCase(Locale.ROOT)
            .replace('ё', 'е');
    text = SPACE.matcher(MARKER.matcher(text).replaceFirst("")).replaceAll(" ").strip();
    while (!text.isEmpty() && "'\"‘’“”«»".indexOf(text.charAt(0)) >= 0)
      text = text.substring(1).stripLeading();
    while (!text.isEmpty() && ".! ' \"‘’“”«»".indexOf(text.charAt(text.length() - 1)) >= 0)
      text = text.substring(0, text.length() - 1).stripTrailing();
    return text;
  }

  public static boolean autofish(String reason) {
    return FISH.matcher(normalize(reason)).find();
  }

  public synchronized BigDecimal score(String reason) {
    BigDecimal found = cache.get(reason);
    if (found != null) return found;
    String key = normalize(reason);
    BigDecimal value = values.get(key);
    if (value == null && autofish(key)) {
      String rest = FISH.matcher(key).replaceAll("").replaceAll("[()\\[\\]{}.,!_-]+", " ").strip();
      if (rest.isEmpty()
          || FISH_REASON.matcher(key).find()
          || FISH.matcher(key).lookingAt()
          || Set.of("читы", "чит", "читер", "cheats", "cheat", "cheater").contains(rest))
        value = values.getOrDefault("автофиш", values.get("autofish"));
    }
    if (value == null && TEAM.matcher(key).matches()) value = values.get("тим чит");
    if (value == null) value = BigDecimal.ZERO;
    cache.put(reason, value);
    return value;
  }

  public static int tenths(String reason) {
    return STANDARD.score(reason).movePointRight(1).intValueExact();
  }
}
