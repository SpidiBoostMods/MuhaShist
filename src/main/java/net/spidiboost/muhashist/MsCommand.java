package net.spidiboost.muhashist;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Locale;

public record MsCommand(
    Action action, String nickname, BigInteger limit, LocalDate since, String value) {
  public enum Action {
    HELP,
    START,
    STOP,
    OPEN,
    UPDATE,
    CLEAR
  }

  public static MsCommand parse(String arguments) {
    String text = arguments.strip();
    if (text.isEmpty()) return simple(Action.HELP, "");
    String[] parts = text.split("\\s+", 3);
    String first = parts[0].toLowerCase(Locale.ROOT);
    if (parts.length == 1) {
      var action =
          switch (first) {
            case "open" -> Action.OPEN;
            case "stop" -> Action.STOP;
            case "update" -> Action.UPDATE;
            case "clear" -> Action.CLEAR;
            default -> null;
          };
      if (action != null) return simple(action, "");
    }
    if (parts.length == 2 && (first.equals("update") || first.equals("clear"))) {
      String value = parts[1].toLowerCase(Locale.ROOT);
      if (!value.equals("on")
          && !value.equals("off")
          && !(first.equals("clear")
              && value.matches("[0-9]+")
              && new BigInteger(value).signum() > 0))
        throw new IllegalArgumentException(
            first.equals("clear")
                ? "Очистка: /ms clear on, off или положительное число."
                : "Обновления: /ms update on или off.");
      return simple(first.equals("clear") ? Action.CLEAR : Action.UPDATE, value);
    }
    if (parts.length != 3)
      throw new IllegalArgumentException(
          "Проверка: /ms ник количество дата. Например: /ms SpidiBoost 1000 1.10.2026.");
    if (!parts[0].matches("[A-Za-z0-9_]{1,16}"))
      throw new IllegalArgumentException("Ник: 1–16 латинских букв, цифр или _.");
    if (!parts[1].matches("[0-9]+"))
      throw new IllegalArgumentException("Количество должно быть положительным целым числом.");
    BigInteger limit = new BigInteger(parts[1]);
    if (limit.signum() <= 0)
      throw new IllegalArgumentException("Количество должно быть больше нуля.");
    return new MsCommand(Action.START, parts[0], limit, DateInput.parse(parts[2]), "");
  }

  private static MsCommand simple(Action action, String value) {
    return new MsCommand(action, "", BigInteger.ZERO, null, value);
  }
}
