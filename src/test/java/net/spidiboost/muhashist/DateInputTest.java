package net.spidiboost.muhashist;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class DateInputTest {
  @Test
  void numericAndNamedFormats() {
    for (String input :
        List.of(
            "1.10.26",
            "01.10.2026",
            "1 10 26",
            "01 10 2026",
            "1/10/2026",
            "1-10-26",
            "2026-10-01",
            "2026/10/1",
            "1 октября 2026",
            "01 ОКТЯБРЯ 26",
            "1 Октябрь 2026",
            "1 окт. 26",
            "1октября2026",
            "1 October 2026",
            "01 OCT 26",
            "1 октября 2026 г.",
            "1.10.2026 года",
            "1\u00a010\u00a026",
            "1\t10\t2026",
            " 1 . 10 . 2026 "))
      assertEquals(LocalDate.of(2026, 10, 1), DateInput.parse(input), input);
  }

  @Test
  void everyMonthAndLeapYears() {
    String[] months = {
      "января",
      "ФЕВРАЛЬ",
      "марта",
      "АПРЕЛЯ",
      "май",
      "июня",
      "июль",
      "августа",
      "СЕНТЯБРЬ",
      "октября",
      "ноябрь",
      "декабря"
    };
    for (int i = 0; i < 12; i++)
      assertEquals(LocalDate.of(2026, i + 1, 1), DateInput.parse("1 " + months[i] + " 26"));
    assertEquals(LocalDate.of(2024, 2, 29), DateInput.parse("29 февраля 24"));
    assertEquals(LocalDate.of(2000, 1, 1), DateInput.parse("1.1.00"));
    assertEquals(LocalDate.of(2099, 12, 31), DateInput.parse("31.12.99"));
  }

  @Test
  void malformedDatesAreNeverLenient() {
    for (String input :
        List.of(
            "31.2.26",
            "29.2.2025",
            "0.10.26",
            "32.10.26",
            "1.0.26",
            "1.13.26",
            "1.1.1",
            "1.10",
            "hello",
            "1 октябрр 2026",
            "1.10.2026 garbage",
            "2026-10-01T23:59:00"))
      assertThrows(IllegalArgumentException.class, () -> DateInput.parse(input), input);
  }

  @Test
  void randomGregorianDatesRoundTrip() {
    var random = new Random(42);
    for (int i = 0; i < 10000; i++) {
      LocalDate day = LocalDate.of(2000, 1, 1).plusDays(random.nextInt(36525));
      assertEquals(day, DateInput.parse(day.toString()));
      assertEquals(
          day,
          DateInput.parse(day.getDayOfMonth() + "." + day.getMonthValue() + "." + day.getYear()));
    }
  }
}
