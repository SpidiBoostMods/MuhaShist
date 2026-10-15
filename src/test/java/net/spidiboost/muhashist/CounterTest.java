package net.spidiboost.muhashist;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CounterTest {
  static final class Run implements HistoryCounter.Listener {
    long time;
    int clears;
    String error = "";
    HistoryCounter.Result result;
    final HistoryCounter counter = new HistoryCounter(this, () -> time);

    Run() {
      this("100000");
    }

    Run(String count) {
      counter.start("SpidiBoost", new BigInteger(count), LocalDate.of(2026, 10, 1));
      counter.accept(
          "§6[12:00:00] История персонала SpidiBoost (Лимит: " + count + "): [CopyFull]");
    }

    void ban(String nick, String staff, String reason, String date) {
      counter.accept(
          "-- ["
              + date
              + "] --\n"
              + nick
              + " был забанен куратором "
              + staff
              + "\nПо причине: '"
              + reason
              + "' [Истёк]\nОкончание в 1 день");
    }

    void unban(String nick, String staff) {
      counter.accept(nick + " was unbanned by " + staff + ".");
    }

    void end() {
      time += 2000;
      counter.tick();
      assertNotNull(result, error);
    }

    public void clearChat() {
      clears++;
    }

    public void finished(HistoryCounter.Result result, String error) {
      this.result = result;
      this.error = error;
    }
  }

  @Test
  void repeatedNicknamesAndIdenticalMinuteBansAreAllSeparate() {
    var run = new Run("3");
    run.ban("Same", "SpidiBoost", "Читы", "2026-10-01 00:00");
    run.ban("Same", "SpidiBoost", "Читы", "2026-10-01 00:00");
    run.ban("Same", "SpidiBoost", "Хранение читов", "2026-10-01 00:00");
    run.end();
    assertEquals("2,4", run.result.points());
    assertEquals(List.of("Same", "Same", "Same"), run.result.names());
    assertEquals(3, run.result.counted());
  }

  @Test
  void exactCuratorAndInclusiveDateRegardlessOfStatusOrColor() {
    var run = new Run();
    run.ban("Cutoff", "sPiDiBoOsT", "§bЧиты", "2026-10-01 00:00");
    run.ban("Other", "Different", "Читы", "2026-10-02 12:00");
    run.ban("Old", "SpidiBoost", "Читы", "2026-09-30 23:59:59");
    run.counter.accept(
        "-- [2026-10-02 10:00] --\n"
            + "Active был забанен куратором SpidiBoost\n"
            + "По причине: Читы [Активный]\n"
            + "Окончание в 10 дней");
    run.counter.accept(
        "-- [2026-10-02 10:00] --\n"
            + "Role был забанен модератором SpidiBoost\n"
            + "По причине: Читы [Активный]");
    run.end();
    assertEquals(List.of("Cutoff", "Active"), run.result.names());
    assertEquals("1,4", run.result.points());
    assertEquals(5, run.result.processed());
  }

  @Test
  void manualUnbanExcludesOnlyItsOwnBan() {
    var run = new Run();
    run.ban("Same", "SpidiBoost", "Читы", "2026-10-03 12:00");
    run.unban("Same", "Moderator");
    run.ban("Same", "SpidiBoost", "Читы", "2026-10-02 12:00");
    run.ban("Same", "SpidiBoost", "Хранение читов", "2026-10-01 12:00");
    run.unban("Same", "Admin");
    run.end();
    assertEquals("0,7", run.result.points());
    assertEquals(List.of("Same"), run.result.names());
  }

  @Test
  void reallyWorldNeverAddsOrChangesPoints() {
    var run = new Run();
    for (int i = 0; i < 2; i++) {
      run.ban("Same", "SpidiBoost", "Читы", "2026-10-03 12:00");
      run.unban("Same", "rEALLyWoRlD");
    }
    run.ban("Same", "SpidiBoost", "Хранение читов", "2026-10-02 12:00");
    run.unban("Same", "ReallyWorld");
    run.end();
    assertEquals("2,4", run.result.points());
    assertEquals(3, run.result.counted());
  }

  @Test
  void standaloneReversalsPairByPlayerAndTimestampInEitherOrder() {
    for (boolean descending : List.of(true, false)) {
      var run = new Run();
      String event = "-- [2026-10-02 12:30] --\nSame was unbanned by Moderator.";
      if (descending) run.counter.accept(event);
      run.ban("Same", "SpidiBoost", "Читы", "2026-10-02 12:00");
      if (!descending) run.counter.accept(event);
      run.ban("Same", "SpidiBoost", "Читы", "2026-10-03 12:00");
      run.end();
      assertEquals(List.of("Same"), run.result.names());
      assertEquals("0,7", run.result.points());
    }
  }

  @Test
  void otherStaffBanConsumesItsOwnUnban() {
    var run = new Run();
    run.ban("Same", "SpidiBoost", "Читы", "2026-10-02 12:00");
    run.ban("Same", "Other", "Читы", "2026-10-03 12:00");
    run.counter.accept("-- [2026-10-03 12:30] --\nSame was unbanned by Moderator.");
    run.end();
    assertEquals("0,7", run.result.points());
  }

  @Test
  void wrappedReasonsAndMultiplePacketLayouts() {
    var run = new Run();
    run.counter.accept(
        "-- [2026-10-02 12:00] --\r\n"
            + "Nick был забанен куратором SpidiBoost\r\n"
            + "По причине: Ограничение доступа");
    run.counter.accept("к файлам при проверке [Истёк]\nОкончание в 5 дней");
    run.end();
    assertEquals("1", run.result.points());
  }

  @Test
  void historyClearingCanBeDisabledOrChanged() {
    var run = new Run();
    run.counter.clearing(true, 20);
    for (int i = 0; i < 41; i++) run.ban("Nick" + i, "SpidiBoost", "Читы", "2026-10-02 12:00");
    run.end();
    assertEquals(2, run.clears);
    assertEquals(41, run.result.counted());
    var disabled = new Run();
    disabled.counter.clearing(false, 1);
    disabled.ban("A", "SpidiBoost", "Читы", "2026-10-02 12:00");
    disabled.end();
    assertEquals(0, disabled.clears);
  }

  @Test
  void hugeLimitIsNeverClampedToAnInt() {
    String huge = "9".repeat(100);
    var run = new Run(huge);
    assertEquals(new BigInteger(huge), run.counter.expected());
    run.ban("A", "SpidiBoost", "Читы", "2026-10-02 12:00");
    run.end();
    assertEquals(new BigInteger(huge), run.result.limit());
  }

  @Test
  void missingHeaderAndForeignHistoryDoNotProduceBogusSuccess() {
    var run = new Run();
    run.counter.accept("История персонала Other (Лимит: 1):");
    assertNotNull(run.result);
    assertFalse(run.result.complete());
    assertTrue(run.error.contains("чужая"));
    var clock = new java.util.concurrent.atomic.AtomicLong();
    boolean[] ended = {false};
    var empty =
        new HistoryCounter(
            new HistoryCounter.Listener() {
              public void clearChat() {}

              public void finished(HistoryCounter.Result r, String e) {
                ended[0] = true;
                assertFalse(r.complete());
                assertTrue(e.contains("10 секунд"));
              }
            },
            clock::get);
    empty.start("Staff", BigInteger.ONE, LocalDate.of(2026, 1, 1));
    clock.set(10001);
    empty.tick();
    assertTrue(ended[0]);
  }

  @Test
  void unrelatedChatDoesNotExtendOwnedReplyTimeoutAndStopDropsLatePackets() {
    var run = new Run();
    run.ban("A", "SpidiBoost", "Читы", "2026-10-02 12:00");
    run.time = 2000;
    run.counter.accept("[Игрок] Person » hello");
    run.counter.tick();
    assertNotNull(run.result);
    assertEquals("0,7", run.result.points());
    run.counter.accept(
        "-- [2026-10-02 12:00] --\nLate был забанен куратором SpidiBoost\nПо причине: Читы");
    assertEquals(1, run.result.counted());
    var stop = new Run();
    stop.ban("A", "SpidiBoost", "Читы", "2026-10-02 12:00");
    stop.counter.stop("Остановлено.");
    assertFalse(stop.result.complete());
    assertEquals("0,7", stop.result.points());
  }

  @Test
  void malformedRecordIsNotCountedAndGivesAnActionableError() {
    var run = new Run();
    run.counter.accept("Nick был забанен куратором SpidiBoost\nПо причине: Читы");
    run.end();
    assertEquals("0", run.result.points());
    assertTrue(run.error.contains("дата"));
  }

  @Test
  void hundredThousandMixedBanRecords() {
    long started = System.nanoTime();
    var run = new Run("100000");
    long wanted = 0, counted = 0;
    for (int i = 0; i < 100000; i++) {
      int kind = i % 8;
      String name = "Repeat" + (i % 17),
          staff = kind == 4 ? "Foreign" : "SpidiBoost",
          date = kind == 5 ? "2026-09-30 23:59" : "2026-10-02 12:00";
      String reason =
          kind == 1
              ? "Тиммейт читера"
              : kind == 2 ? "Хранение читов" : kind == 3 ? "Уклон от проверки" : "Читы";
      run.ban(name, staff, reason, date);
      if (kind == 6) run.unban(name, "Moderator");
      if (kind == 7) run.unban(name, "ReallyWorld");
      if (kind != 4 && kind != 5 && kind != 6) {
        wanted += kind == 2 || kind == 3 ? 10 : 7;
        counted++;
      }
    }
    run.end();
    assertEquals(BigInteger.valueOf(wanted), run.result.tenths());
    assertEquals(counted, run.result.counted());
    assertEquals(100000, run.result.processed());
    assertEquals(counted, run.result.names().size());
    assertEquals(5000, run.clears);
    long millis = (System.nanoTime() - started) / 1_000_000;
    System.out.println(
        "100000 mixed records: "
            + millis
            + " ms; points="
            + run.result.points()
            + "; matched="
            + counted);
    assertTrue(millis < 30000, "100000-record scan exceeded 30 seconds");
  }

  @Test
  void hundredThousandStandaloneUnbansDoNotBecomeQuadratic() {
    long started = System.nanoTime();
    var run = new Run("100000");
    LocalDateTime first = LocalDateTime.of(2026, 10, 2, 0, 0);
    var fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    for (int i = 99999; i >= 0; i--)
      run.counter.accept(
          "-- ["
              + first.plusSeconds(i * 2L + 1).format(fmt)
              + "] --\nSame was unbanned by "
              + (i % 2 == 0 ? "ReallyWorld" : "Moderator")
              + ".");
    for (int i = 99999; i >= 0; i--)
      run.ban("Same", "SpidiBoost", "Читы", first.plusSeconds(i * 2L).format(fmt));
    run.end();
    assertEquals(50000, run.result.counted());
    assertEquals("35000", run.result.points());
    long millis = (System.nanoTime() - started) / 1_000_000;
    System.out.println("100000 standalone unbans + 100000 bans: " + millis + " ms");
    assertTrue(millis < 30000);
  }
}
