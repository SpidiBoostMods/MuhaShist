package net.spidiboost.muhashist;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

public final class DateInput {
  private static final Pattern NUMERIC =
      Pattern.compile("^(\\d{1,4})[.\\s/\\-_,]+(\\d{1,2})[.\\s/\\-_,]+(\\d{1,4})$");
  private static final Pattern WORDS =
      Pattern.compile(
          "^(\\d{1,2})[.\\s/\\-_,]*([\\p{L}.]+)[.\\s/\\-_,]*(\\d{2}|\\d{4})$",
          Pattern.UNICODE_CHARACTER_CLASS);
  private static final Map<String, Integer> MONTHS = months();
  private static final Pattern YEAR_SUFFIX =
      Pattern.compile("\\s*(?:года|год|г\\.?)$", Pattern.UNICODE_CHARACTER_CLASS);

  private DateInput() {}

  private static Map<String, Integer> months() {
    String[] names = {
      "январь января янв january jan",
      "февраль февраля фев февр february feb",
      "март марта мар march mar",
      "апрель апреля апр april apr",
      "май мая may",
      "июнь июня июн june jun",
      "июль июля июл july jul",
      "август августа авг august aug",
      "сентябрь сентября сен сент september sep sept",
      "октябрь октября окт october oct",
      "ноябрь ноября ноя нояб november nov",
      "декабрь декабря дек december dec"
    };
    var result = new HashMap<String, Integer>();
    for (int i = 0; i < names.length; i++)
      for (String name : names[i].split(" ")) result.put(name, i + 1);
    return Map.copyOf(result);
  }

  public static LocalDate parse(String raw) {
    String text =
        raw.toLowerCase(Locale.ROOT)
            .strip()
            .replace('\u00a0', ' ')
            .replace('\u202f', ' ')
            .replace('ё', 'е');
    text = YEAR_SUFFIX.matcher(text).replaceFirst("").strip();
    if (text.length() > 80) throw error();
    var numbers = NUMERIC.matcher(text);
    try {
      if (numbers.matches()) {
        int a = Integer.parseInt(numbers.group(1)),
            b = Integer.parseInt(numbers.group(2)),
            c = Integer.parseInt(numbers.group(3));
        if (numbers.group(1).length() == 4) return LocalDate.of(a, b, c);
        if (numbers.group(3).length() != 2 && numbers.group(3).length() != 4) throw error();
        return LocalDate.of(numbers.group(3).length() == 2 ? 2000 + c : c, b, a);
      }
      var words = WORDS.matcher(text);
      if (words.matches()) {
        Integer month = MONTHS.get(words.group(2).replace(".", ""));
        if (month == null) throw error();
        int year = Integer.parseInt(words.group(3));
        return LocalDate.of(
            words.group(3).length() == 2 ? 2000 + year : year,
            month,
            Integer.parseInt(words.group(1)));
      }
    } catch (DateTimeException | NumberFormatException e) {
      throw error();
    }
    throw error();
  }

  private static IllegalArgumentException error() {
    return new IllegalArgumentException("Дата неверна. Например: 1.10.2026 или 1 октября 26.");
  }
}
