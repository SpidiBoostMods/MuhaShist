package net.spidiboost.muhashist;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class ScoreRules {
  private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;
  private static final Pattern MARKER =
      Pattern.compile("\\[[^]]*(?:активный|истек|снят|неактивный)[^]]*]\\s*$", FLAGS);
  private static final Pattern PUNCTUATION = Pattern.compile("[\\p{P}\\p{S}]+", FLAGS),
      SPACE = Pattern.compile("\\s+", FLAGS);
  private static final Map<String, Integer> CACHE =
      Collections.synchronizedMap(
          new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Integer> entry) {
              return size() > 512;
            }
          });
  private static final List<Pattern> ONE =
      List.of(
              "хранени[ея]\\s+чит\\p{L}*",
              "чит\\p{L}*\\s+(?:при|на|во время)\\s+проверк\\p{L}*",
              "не\\s+истек\\p{L}*\\s+срок\\s+удалени[яе]\\s+чит\\p{L}*",
              "помех\\p{L}*\\s+(?:проверк\\p{L}*|проведени\\p{L}*\\s+проверк\\p{L}*)",
              "(?:уклон\\p{L}*|уход|лив|отказ(?:\\s+от\\s+прохождени[яе])?)\\s+(?:(?:от|с|со)\\s+)?проверк\\p{L}*",
              "(?:чистк\\p{L}*|очистк\\p{L}*|сложил\\p{L}*|сложен\\p{L}*)\\s+перед\\s+проверк\\p{L}*",
              "ограничени\\p{L}*\\s+доступ\\p{L}*\\s+(?:к\\s+)?файл\\p{L}*.*проверк\\p{L}*",
              "признани\\p{L}*\\s+(?:при|на|во время)\\s+проверк\\p{L}*",
              "неадекватн\\p{L}*\\s+поведени\\p{L}*\\s+(?:при|на|во время)\\s+проверк\\p{L}*",
              "(?:использовани[ея]|хранени[ея])\\s+батник\\p{L}*.*(?:чистк\\p{L}*|очистк\\p{L}*|след\\p{L}*)",
              "(?:использовани[ея]|хранени[ея])\\s+запрещенн\\p{L}*\\s+мод\\p{L}*",
              "(?:stored?\\s+cheats?|cheats?\\s+during\\s+(?:check|inspection)|(?:evading|refusing|leaving)\\s+(?:a\\s+)?(?:check|inspection)|(?:using|storing)\\s+(?:a\\s+)?(?:forbidden|banned)\\s+mods?)")
          .stream()
          .map(rule -> Pattern.compile("^(?:" + rule + ")(?=$|\\W)", FLAGS))
          .toList();
  private static final Pattern TEAM =
      Pattern.compile(
          "^(?:(?:тим\\p{L}*|темм?ейт\\p{L}*|команд\\p{L}*|игра|союз|буст)\\s*(?:с\\s*)?(?:чит\\p{L}*)|(?:team\\p{L}*|teaming|boosting|playing)\\s*(?:with\\s*)?(?:a\\s*)?cheat\\p{L}*)(?=$|\\W)",
          FLAGS);
  private static final Pattern CHEATS =
      Pattern.compile(
          "^(?:(?:использовани[ея]|использовал|use"
              + " of)\\s+)?(?:чит(?:ы|ов|ами|ах)?|читер(?:ство)?|читинг|cheats?|cheater|cheating)(?=$|\\W)",
          FLAGS);

  private ScoreRules() {}

  public static String normalize(String raw) {
    String text =
        Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё', 'е');
    text = MARKER.matcher(text).replaceFirst("");
    text = PUNCTUATION.matcher(text).replaceAll(" ");
    return SPACE.matcher(text).replaceAll(" ").strip();
  }

  public static int tenths(String reason) {
    Integer cached = CACHE.get(reason);
    if (cached != null) return cached;
    String text = normalize(reason);
    int score = 0;
    for (Pattern rule : ONE)
      if (rule.matcher(text).find()) {
        score = 10;
        break;
      }
    if (score == 0 && (TEAM.matcher(text).find() || CHEATS.matcher(text).find())) score = 7;
    CACHE.put(reason, score);
    return score;
  }
}
