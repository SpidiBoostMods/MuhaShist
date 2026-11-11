package net.spidiboost.muhashist.qa;
import net.spidiboost.muhashist.*;

import java.math.*;
import java.time.LocalDate;
import java.util.List;
import net.minecraft.text.*;



public class ResultTextChecks {
  public static void run() {
    var checks = new ResultTextChecks();
    checks.exactThresholdAndFractionsUseTheStoredResult();
    checks.oldMessagesKeepTheirOwnCuratorDateAndScore();
  }
  private static void assertEquals(Object a,Object b){if(!java.util.Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
  private static void assertTrue(boolean value){if(!value)throw new AssertionError("result text mismatch");}
  private static void assertNotNull(Object value){if(value==null)throw new AssertionError("missing hover");}
  private HistoryCounter.Result result(String name, String score) {
    return new HistoryCounter.Result(name, LocalDate.of(2026, 11, 1), BigInteger.TEN,
        10, 10, new BigDecimal(score), List.of("Player"), true);
  }

  void exactThresholdAndFractionsUseTheStoredResult() {
    for (String value : List.of("0", "1", "2999.999999999999", "3000", "3000.000000000001")) {
      var result = result("SpidiBoost", value);
      Text text = MsTheme.result(result, "");
      assertEquals("[MuhaShist] Качественные баны: " + result.points(), text.getString());
      Text hint = text.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
      boolean allowed = result.score().compareTo(new BigDecimal("3000")) >= 0;
      assertEquals("Никнейм: SpidiBoost\nС даты: 01.11.2026\nПовышение: "
          + (allowed ? "Разрешено" : "Не разрешено"), hint.getString());
      text.visit((style, part) -> {
        assertNotNull(style.getHoverEvent());
        return java.util.Optional.empty();
      }, Style.EMPTY);
    }
  }

  void oldMessagesKeepTheirOwnCuratorDateAndScore() {
    var first = MsTheme.result(result("First", "3000"), "");
    var second = MsTheme.result(result("Second", "2.4"), "Частичный результат.");
    assertTrue(first.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT).getString().contains("First"));
    assertTrue(second.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT).getString().contains("Second"));
    assertTrue(second.getString().contains("Частичный результат. Качественные баны: 2,4"));
  }
}
