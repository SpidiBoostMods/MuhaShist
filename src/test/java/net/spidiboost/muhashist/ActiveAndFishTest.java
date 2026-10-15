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

  private void response(CounterTest.Run run, String name, String staff, String silent) {
    answer(run, name, staff, silent);
    run.time += 100;
    run.counter.tick();
  }

  private void answer(CounterTest.Run run, String name, String staff, String silent) {
    run.counter.accept(
        "Цель ["
            + name
            + "]: забанена.\nЗабанил: "
            + staff
            + "\nIP бан: no, Тихий: "
            + silent
            + ", Навсегда: no");
  }

  private void seal(CounterTest.Run run) {
    run.time += 2000;
    run.counter.tick();
  }

  @Test
  void hundredMillisAreMeasuredBetweenSendsAndSlowRepliesAddNoExtraPause() {
    var run = new CounterTest.Run();
    for (String name : List.of("Fast", "Slow", "Last"))
      active(run, name, "Уклон от проверки", "2026-10-02 12:00");
    seal(run);
    run.time += 20;
    answer(run, "Fast", "SpidiBoost", "no");
    run.counter.tick();
    assertEquals(List.of(2000L), run.requestedAt);
    run.time += 79;
    run.counter.tick();
    assertEquals(1, run.requested.size());
    run.time++;
    run.counter.tick();
    assertEquals(List.of(2000L, 2100L), run.requestedAt);
    run.time += 350;
    answer(run, "Slow", "SpidiBoost", "no");
    assertEquals(2, run.requested.size());
    run.counter.tick();
    assertEquals(List.of(2000L, 2100L, 2450L), run.requestedAt);
    answer(run, "Last", "SpidiBoost", "no");
    run.counter.tick();
    assertNotNull(run.result);
    assertEquals("3", run.result.points());
    assertEquals(2450, run.time);
  }

  @Test
  void elapsedSendIntervalNeverAllowsAnotherPlayerBeforeCompleteReply() {
    var run = new CounterTest.Run();
    active(run, "Pending", "Уклон от проверки", "2026-10-02 12:00");
    active(run, "Later", "Уклон от проверки", "2026-10-02 12:00");
    seal(run);
    run.counter.accept("Цель [Pending]: забанена.\nЗабанил: SpidiBoost");
    run.time += 500;
    run.counter.tick();
    assertEquals(List.of("Pending"), run.requested);
    assertEquals("0", run.counter.points());
    run.counter.accept("Тихий: no\nНавсегда: no");
    assertEquals(1, run.requested.size());
    run.counter.tick();
    assertEquals(List.of("Pending", "Later"), run.requested);
    answer(run, "Pending", "SpidiBoost", "no");
    assertEquals("1", run.counter.points());
    answer(run, "Later", "SpidiBoost", "yes");
    run.counter.tick();
    assertEquals(List.of("Pending"), run.result.names());
  }

  @Test
  void spacingUsesRetrySendTimeNotTheOriginalRequestTime() {
    var run = new CounterTest.Run();
    active(run, "Retry", "Уклон от проверки", "2026-10-02 12:00");
    active(run, "Next", "Уклон от проверки", "2026-10-02 12:00");
    seal(run);
    run.time += 6000;
    run.counter.tick();
    assertEquals(List.of("Retry", "Retry"), run.requested);
    run.time += 25;
    answer(run, "Retry", "SpidiBoost", "no");
    run.counter.tick();
    run.time += 74;
    run.counter.tick();
    assertEquals(2, run.requested.size());
    run.time++;
    run.counter.tick();
    assertEquals(List.of(2000L, 8000L, 8100L), run.requestedAt);
    answer(run, "Next", "SpidiBoost", "no");
    run.counter.tick();
    assertEquals("2", run.result.points());
  }

  @Test
  void hundredThousandChecksKeepSendSpacingAcrossVariableLatency() {
    var run = new CounterTest.Run();
    int[] delays = {0, 5, 20, 50, 99, 100, 101, 350, 1000, 5000};
    for (int i = 0; i < 100000; i++)
      active(run, "Same" + (i % 17), "Уклон от проверки", "2026-10-02 12:00");
    seal(run);
    long expectedSent = run.time;
    for (int i = 0; i < 100000; i++) {
      assertEquals(i + 1, run.requested.size());
      assertEquals(expectedSent, run.requestedAt.get(i));
      int delay = delays[i % delays.length];
      run.time += delay;
      answer(run, "Same" + (i % 17), "SpidiBoost", i % 4 == 0 ? "yes" : "no");
      assertEquals(i + 1, run.requested.size());
      run.counter.tick();
      if (i < 99999 && delay < 100) {
        assertEquals(i + 1, run.requested.size());
        run.time = expectedSent + 99;
        run.counter.tick();
        assertEquals(i + 1, run.requested.size());
        run.time++;
        run.counter.tick();
      }
      expectedSent += Math.max(100, delay);
    }
    assertNotNull(run.result);
    assertTrue(run.result.complete());
    assertEquals("75000", run.result.points());
    assertEquals(75000, run.result.counted());
    assertEquals(100000, run.counter.checked());
    assertEquals(100000, run.requested.size());
  }

  @Test
  void onlyActiveRecordsAreCheckedAndEachReplyIsBoundToItsPlayer() {
    var run = new CounterTest.Run();
    run.ban("Expired", "SpidiBoost", "Хранение читов", "2026-10-02 12:00");
    active(run, "First", "Читы", "2026-10-02 12:00");
    active(run, "Second", "Лив с проверки", "2026-10-02 12:00");
    active(run, "Third", "Читы", "2026-10-02 12:00");
    seal(run);
    assertEquals(List.of("First"), run.requested);
    run.counter.accept("Цель [Other]: забанена.\nЗабанил: SpidiBoost\nТихий: no");
    assertEquals(List.of("First"), run.requested);
    assertEquals("1", run.counter.points());
    response(run, "First", "SpidiBoost", "yes");
    assertEquals(List.of("First", "Second"), run.requested);
    response(run, "Second", "Another", "no");
    response(run, "Third", "sPiDiBoOsT", "no");
    assertEquals(List.of("First", "Second", "Third"), run.requested);
    assertEquals("1,7", run.result.points());
    assertEquals(List.of("Expired", "Third"), run.result.names());
    assertEquals(3, run.counter.checked());
  }

  @Test
  void manualUnbansAndForeignCuratorsDoNotTriggerActiveChecks() {
    var run = new CounterTest.Run();
    active(run, "Manual", "Читы", "2026-10-02 12:00");
    run.unban("Manual", "Admin");
    run.counter.accept(
        "-- [2026-10-02 12:00] --\n"
            + "Foreign был забанен куратором Other\n"
            + "По причине: Читы [Активный]");
    active(run, "ExpiredDate", "Читы", "2026-09-30 12:00");
    active(run, "Keep", "Читы", "2026-10-02 12:00");
    run.unban("Keep", "ReallyWorld");
    seal(run);
    assertEquals(List.of("Keep"), run.requested);
    response(run, "Keep", "SpidiBoost", "no");
    assertEquals("0,7", run.result.points());
  }

  @Test
  void incompleteRepliesRetrySamePlayerThenStopWithoutInventingSilentNo() {
    var run = new CounterTest.Run();
    run.ban("Expired", "SpidiBoost", "Читы", "2026-10-02 12:00");
    active(run, "Pending", "Читы", "2026-10-02 12:00");
    active(run, "Later", "Читы", "2026-10-02 12:00");
    seal(run);
    for (int i = 0; i < 3; i++) {
      run.counter.accept("Цель [Pending]: забанена.\nЗабанил: SpidiBoost");
      run.time += 6001;
      run.counter.tick();
    }
    assertEquals(List.of("Pending", "Pending", "Pending"), run.requested);
    assertFalse(run.result.complete());
    assertEquals("0,7", run.result.points());
    assertTrue(run.error.contains("Неполный ответ"));
    run.counter.accept("Тихий: no");
    assertEquals(List.of("Expired"), run.result.names());
  }

  @Test
  void stopCheckPhaseKeepsOnlyConfirmedAndExpiredScores() {
    var run = new CounterTest.Run();
    run.ban("Expired", "SpidiBoost", "Хранение читов", "2026-10-02 12:00");
    active(run, "Confirmed", "Читы", "2026-10-02 12:00");
    active(run, "Pending", "Читы", "2026-10-02 12:00");
    seal(run);
    response(run, "Confirmed", "SpidiBoost", "no");
    run.counter.stop("stop");
    assertEquals("1,7", run.result.points());
    assertEquals(List.of("Expired", "Confirmed"), run.result.names());
    assertFalse(run.result.complete());
  }

  @Test
  void duplicateActiveBanRecordsAreNotDeduplicatedAndChecksRespectClearInterval() {
    var run = new CounterTest.Run();
    run.counter.clearing(true, 20);
    for (int i = 0; i < 41; i++) active(run, "Same", "Читы", "2026-10-02 12:00");
    seal(run);
    int historyClears = run.clears;
    for (int i = 0; i < 41; i++) response(run, "Same", "SpidiBoost", "no");
    assertEquals(41, run.requested.size());
    assertEquals(41, run.result.counted());
    assertEquals("28,7", run.result.points());
    assertEquals(historyClears + 2, run.clears);
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
    seal(run);
    response(run, "Silent", "SpidiBoost", "yes");
    assertEquals("2,575", run.result.points());
    assertEquals(List.of("A", "C", "Exact", "Comma"), run.result.names());
  }

  @Test
  void hundredThousandActiveBansAreSequentialAndHaveExactVerdicts() {
    long start = System.nanoTime();
    var run = new CounterTest.Run();
    for (int i = 0; i < 100000; i++) active(run, "Same" + (i % 17), "Читы", "2026-10-02 12:00");
    seal(run);
    for (int i = 0; i < 100000; i++) {
      assertEquals(i + 1, run.requested.size());
      response(
          run, "Same" + (i % 17), i % 4 == 0 ? "Other" : "SpidiBoost", i % 4 == 1 ? "yes" : "no");
    }
    assertEquals(100000, run.counter.checked());
    assertEquals("35000", run.result.points());
    assertEquals(50000, run.result.counted());
    long elapsed = (System.nanoTime() - start) / 1000000;
    System.out.println("100000 active bans and checkban replies: " + elapsed + " ms");
    assertTrue(elapsed < 30000, "check queue exceeded 30 seconds");
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
