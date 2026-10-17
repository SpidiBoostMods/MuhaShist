package net.spidiboost.muhashist;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DelayTest {
  @TempDir Path directory;

  private void active(CounterTest.Run run, String name) {
    run.counter.accept(
        "-- [2026-10-02 12:00] --\n"
            + name
            + " был забанен куратором SpidiBoost\nПо причине: Уклон от проверки [Активный]");
  }

  private CounterTest.Run checking(int count) {
    var run = new CounterTest.Run();
    for (int i = 0; i < count; i++) active(run, "Player" + i);
    run.counter.finishNow();
    return run;
  }

  private void answer(CounterTest.Run run, String name, String silent) {
    run.counter.accept("Цель [" + name + "]: забанена.\nЗабанил: SpidiBoost\nТихий: " + silent);
  }

  @Test
  void delayCommandAcceptsWholeMillisAndKeepsReservedNicknameUsable() {
    assertEquals(MsCommand.Action.DELAY, MsCommand.parse("delay").action());
    assertEquals("", MsCommand.parse("delay").value());
    for (String value : List.of("0", "1", "100", "250", Long.toString(Long.MAX_VALUE))) {
      var parsed = MsCommand.parse("DeLaY " + value);
      assertEquals(MsCommand.Action.DELAY, parsed.action());
      assertEquals(value, parsed.value());
    }
    assertEquals("100", MsCommand.parse("delay 00100").value());
    assertEquals(MsCommand.Action.START, MsCommand.parse("delay 100 1.10.26").action());
    for (String value : List.of("-1", "1.5", "1,5", "on", "NaN", "9223372036854775808")) {
      var error =
          assertThrows(IllegalArgumentException.class, () -> MsCommand.parse("delay " + value));
      assertTrue(error.getMessage().startsWith("Задержка"));
    }
  }

  @Test
  void delayDefaultsToHundredAndRoundTripsWithoutTouchingReasons() throws Exception {
    String custom = "# Положение GUI\nhudX=12\nhudY=34\n# Причины — мои\nЧиты - 2.25\n# оставить\n";
    Path file = directory.resolve("ms.cfg");
    Files.writeString(file, custom);
    var config = new MsConfig(directory);
    assertEquals(100, config.delay());
    assertEquals(custom, Files.readString(file));
    for (long millis : new long[] {250, 0, Long.MAX_VALUE, 100}) {
      config.delay(millis);
      config.position(56, 78);
      config.save();
      var loaded = new MsConfig(directory);
      assertEquals(millis, loaded.delay());
      assertEquals("2.25", loaded.rules().score("Читы").toPlainString());
      assertTrue(Files.readString(file).contains("# оставить"));
      assertEquals(
          1, Files.readAllLines(file).stream().filter(s -> s.startsWith("checkbanDelay=")).count());
    }
    var fresh = new MsConfig(directory.resolve("new"));
    assertEquals(100, fresh.delay());
    assertTrue(Files.readString(fresh.file()).contains("checkbanDelay=100"));
  }

  @Test
  void settingsOnlyMigrationKeepsDelayAndDoesNotDuplicateItsKey() throws Exception {
    Files.writeString(directory.resolve("ms.cfg"), "hudX=5\nclear=false\ncheckbanDelay=375\n");
    var config = new MsConfig(directory);
    assertEquals(375, config.delay());
    assertEquals(375, new MsConfig(directory).delay());
    assertEquals(
        1,
        Files.readAllLines(config.file()).stream()
            .filter(s -> s.startsWith("checkbanDelay="))
            .count());
  }

  @Test
  void badDelayDoesNotRewriteConfigOrChangeWorkingState() throws Exception {
    for (String value : List.of("-1", "1.5", "none", "9223372036854775808")) {
      String text = "# Причины\ncheckbanDelay=" + value + "\nЧиты - 0.7\n";
      Files.writeString(directory.resolve("ms.cfg"), text);
      assertThrows(IOException.class, () -> new MsConfig(directory));
      assertEquals(text, Files.readString(directory.resolve("ms.cfg")));
    }
    var config = new MsConfig(directory.resolve("working"));
    String text = Files.readString(config.file());
    assertThrows(IOException.class, () -> config.delay(-1));
    assertEquals(100, config.delay());
    assertEquals(text, Files.readString(config.file()));
    Files.move(config.file(), directory.resolve("backup.cfg"));
    Files.createDirectory(config.file());
    assertThrows(IOException.class, () -> config.delay(250));
    assertEquals(100, config.delay());
    assertEquals(text, Files.readString(directory.resolve("backup.cfg")));
  }

  @Test
  void configuredDelayCountsFromSendNotFromAnswer() {
    var run = checking(3);
    assertEquals(100, run.counter.delay());
    run.counter.delay(250);
    run.time = 75;
    answer(run, "Player0", "no");
    run.counter.tick();
    run.time = 249;
    run.counter.tick();
    assertEquals(List.of(0L), run.requestedAt);
    run.time = 250;
    run.counter.tick();
    assertEquals(List.of(0L, 250L), run.requestedAt);
    run.time = 1000;
    answer(run, "Player1", "no");
    run.counter.tick();
    assertEquals(List.of(0L, 250L, 1000L), run.requestedAt);
    answer(run, "Player2", "no");
    run.counter.tick();
    assertEquals("3", run.result.points());
  }

  @Test
  void changingDelayUsesLastSendAndDoesNotRestartWaitingFromTheCommandTime() {
    var run = checking(3);
    run.counter.delay(1000);
    run.time = 50;
    answer(run, "Player0", "no");
    run.time = 200;
    run.counter.delay(250);
    run.counter.tick();
    assertEquals(1, run.requested.size());
    run.time = 250;
    run.counter.tick();
    run.time = 260;
    answer(run, "Player1", "no");
    run.counter.delay(500);
    run.time = 749;
    run.counter.tick();
    assertEquals(2, run.requested.size());
    run.time++;
    run.counter.tick();
    assertEquals(List.of(0L, 250L, 750L), run.requestedAt);
    answer(run, "Player2", "no");
    run.counter.tick();
    assertEquals("3", run.result.points());
  }

  @Test
  void zeroDelayStillWaitsForOwnedCompleteReplyAndNeverSendsABatch() {
    var run = checking(3);
    run.counter.delay(0);
    run.counter.accept("Цель [Player0]: забанена.\nЗабанил: SpidiBoost");
    for (int i = 0; i < 10; i++) run.counter.tick();
    assertEquals(List.of("Player0"), run.requested);
    run.counter.accept("Тихий: no\nНавсегда: no");
    assertEquals(1, run.requested.size());
    run.counter.tick();
    assertEquals(List.of("Player0", "Player1"), run.requested);
    answer(run, "Player0", "no");
    run.counter.tick();
    assertEquals(2, run.requested.size());
    answer(run, "Player1", "no");
    run.counter.tick();
    assertEquals(3, run.requested.size());
    answer(run, "Player2", "no");
    run.counter.tick();
    assertEquals("3", run.result.points());
    assertThrows(IllegalArgumentException.class, () -> run.counter.delay(-1));
    assertEquals(0, run.counter.delay());
  }

  @Test
  void retriesAlsoRespectConfiguredDelayAndResetTheLastSend() {
    var run = checking(2);
    run.counter.delay(7000);
    run.time = 6000;
    run.counter.tick();
    assertEquals(1, run.requested.size());
    run.time = 6999;
    run.counter.tick();
    assertEquals(1, run.requested.size());
    run.time++;
    run.counter.tick();
    assertEquals(List.of(0L, 7000L), run.requestedAt);
    run.time = 7025;
    answer(run, "Player0", "no");
    run.time = 13999;
    run.counter.tick();
    assertEquals(2, run.requested.size());
    run.time++;
    run.counter.tick();
    assertEquals(List.of(0L, 7000L, 14000L), run.requestedAt);
    answer(run, "Player1", "no");
    run.counter.tick();
    assertEquals("2", run.result.points());
  }

  @Test
  void maximumDelayAndLargeClockValuesCannotOverflowIntoAnImmediateSend() {
    var run = new CounterTest.Run();
    active(run, "A");
    active(run, "B");
    run.time = Long.MAX_VALUE - 1000;
    run.counter.delay(Long.MAX_VALUE);
    run.counter.finishNow();
    answer(run, "A", "no");
    run.time += 500;
    run.counter.tick();
    assertEquals(1, run.requested.size());
    run.counter.delay(500);
    run.counter.tick();
    assertEquals(2, run.requested.size());
    answer(run, "B", "no");
    run.counter.tick();
    assertEquals("2", run.result.points());
  }

  @Test
  void hundredThousandChecksRespectChangingConfiguredDelayWithoutExtraResponsePause() {
    var run = new CounterTest.Run();
    for (int i = 0; i < 100000; i++) active(run, "Same" + (i % 17));
    run.counter.finishNow();
    int[] settings = {0, 1, 50, 100, 250, 1000, 10000};
    int[] latency = {0, 5, 25, 100, 350, 1000, 5000};
    long sent = 0;
    for (int i = 0; i < 100000; i++) {
      assertEquals(i + 1, run.requested.size());
      assertEquals(sent, run.requestedAt.get(i));
      int delay = settings[i % settings.length];
      int response = latency[(i / settings.length) % latency.length];
      run.counter.delay(delay);
      run.time = sent + response;
      answer(run, "Same" + (i % 17), i % 4 == 0 ? "yes" : "no");
      assertEquals(i + 1, run.requested.size());
      run.counter.tick();
      if (i < 99999 && response < delay) {
        assertEquals(i + 1, run.requested.size());
        run.time = sent + delay - 1;
        run.counter.tick();
        assertEquals(i + 1, run.requested.size());
        run.time++;
        run.counter.tick();
      }
      sent += Math.max(delay, response);
    }
    assertTrue(run.result.complete());
    assertEquals("75000", run.result.points());
    assertEquals(75000, run.result.counted());
    assertEquals(100000, run.counter.checked());
  }
}
