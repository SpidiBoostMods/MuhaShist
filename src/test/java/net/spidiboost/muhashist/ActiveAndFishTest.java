package net.spidiboost.muhashist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ActiveAndFishTest {
  @Test
  void unrelatedStaffChatCannotOverwriteOrAppendToACompletedHistoryReason() {
    var run = new CounterTest.Run();
    run.ban("Keep", "SpidiBoost", "Читы", "2026-10-02 12:00");
    run.counter.accept(
        "Администратор Someone замутил Player на 4 часа\n"
            + "По причине: Флуд [Истёк]\n"
            + "RWAC Somebody failed Timer\n"
            + "Сканирование Something [Онлайн]");
    run.ban("Next", "SpidiBoost", "Лив с проверки", "2026-10-02 12:01");
    run.counter.accept("По причине: Читы [Активный]");
    run.end();
    assertEquals("1,7", run.result.points());
    assertEquals(List.of("Keep", "Next"), run.result.names());
    assertTrue(run.requested.isEmpty());
  }

  @Test
  void scoringSnapshotCannotChangeDuringAHistoryRun() {
    var run = new CounterTest.Run();
    assertThrows(
        IllegalStateException.class,
        () -> run.counter.scoring(ScoreRules.parse(List.of("Читы - 100")), 1000));
    run.ban("Keep", "SpidiBoost", "Читы", "2026-10-02 12:00");
    run.end();
    assertEquals("0,7", run.result.points());
  }

  private void active(CounterTest.Run run, String name, String reason, String date) {
    run.counter.accept(
        "-- ["
            + date
            + "] --\n"
            + name
            + " был забанен куратором SpidiBoost\nПо причине: '"
            + reason
            + "' [Активный]\nОкончание в 10 дней");
  }

  @Test
  void autofishUsesChronologicalAnchorsNotNicknamesOrArrivalOrder() {
    for (boolean reversed : List.of(false, true)) {
      var run = new CounterTest.Run();
      var times =
          new ArrayList<>(
              List.of("12:00", "12:05", "12:10", "12:10:01", "12:19", "12:20:01", "12:20:02"));
      if (reversed) Collections.reverse(times);
      for (int i = 0; i < times.size(); i++)
        run.ban(
            "Fish" + i,
            "SpidiBoost",
            i % 2 == 0 ? "Читы (autofish)" : "cheat [auto-fish]",
            "2026-10-02 " + times.get(i));
      run.ban("Regular", "SpidiBoost", "Читы.", "2026-10-02 12:06");
      run.end();
      assertEquals("2,8", run.result.points());
      assertEquals(4, run.result.counted());
    }
  }

  @Test
  void excludedAutofishDoesNotOccupyTheWindowAndWeightsRemainExact() {
    var run =
        new CounterTest.Run(
            "10",
            ScoreRules.parse(
                List.of("Читы - 0.125", "Автофиш;Autofish - 0,1", "Помеха, предупреждение - 2.25")),
            300000);
    active(run, "Silent", "autofish", "2026-10-02 12:00");
    run.ban("Manual", "SpidiBoost", "Читы (автофиш)", "2026-10-02 12:01");
    run.unban("Manual", "Admin");
    run.ban("A", "SpidiBoost", "autofish", "2026-10-02 12:04");
    run.ban("B", "SpidiBoost", "autofish", "2026-10-02 12:08");
    run.ban("C", "SpidiBoost", "autofish", "2026-10-02 12:09:01");
    run.ban("Exact", "SpidiBoost", "Читы", "2026-10-02 12:04");
    run.ban("Comma", "SpidiBoost", "Помеха, предупреждение", "2026-10-02 12:04");
    run.end();
    assertEquals("2,575", run.result.points());
    assertEquals(List.of("Silent", "B", "Exact", "Comma"), run.result.names());
  }

  @Test
  void hundredThousandAutofishRecordsAreGroupedWithoutQuadraticWork() {
    long start = System.nanoTime();
    var run = new CounterTest.Run();
    for (int i = 0; i < 100000; i++)
      run.ban("Same" + (i % 17), "SpidiBoost", "Читы (автофиш)", "2026-10-02 12:00");
    run.end();
    assertEquals("0,7", run.result.points());
    assertEquals(1, run.result.counted());
    assertEquals(0, run.requested.size());
    assertTrue((System.nanoTime() - start) / 1000000 < 30000);
  }
}
