package net.spidiboost.muhashist;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigAndCommandTest {
  @TempDir Path root;

  @Test
  void commandDatesRemainGreedyAndLimitsHaveNoArtificialCap() {
    var command = MsCommand.parse("SpidiBoost 100000 1 ОКТЯБРЯ 26");
    assertEquals(MsCommand.Action.START, command.action());
    assertEquals(new BigInteger("100000"), command.limit());
    assertEquals(
        new BigInteger("9".repeat(100)),
        MsCommand.parse("SpidiBoost " + "9".repeat(100) + " 1 10 26").limit());
    for (String nick : new String[] {"open", "update", "clear", "stop", "config", "delay"})
      assertEquals(MsCommand.Action.START, MsCommand.parse(nick + " 100 1.10.26").action());
    assertEquals(MsCommand.Action.UPDATE, MsCommand.parse("update off").action());
    assertEquals("21", MsCommand.parse("clear 21").value());
    assertEquals(MsCommand.Action.HELP, MsCommand.parse("").action());
    for (String raw :
        new String[] {
          "A 0 1.1.26",
          "A -1 1.1.26",
          "A 1.5 1.1.26",
          "clear 0",
          "clear -2",
          "update nope",
          "A 5 31.2.26"
        }) assertThrows(IllegalArgumentException.class, () -> MsCommand.parse(raw), raw);
  }

  @Test
  void settingsRoundTripInUnicodeDirectoriesWithoutOverwritingOtherConfig() throws Exception {
    Path directory = root.resolve("Призма с пробелами/инстанс/.minecraft/config");
    var config = new MsConfig(directory);
    assertTrue(config.clear());
    assertEquals(20, config.every());
    config.position(21, 85);
    config.clearing(false);
    config.interval("7");
    var loaded = new MsConfig(directory);
    assertFalse(loaded.clear());
    assertEquals(7, loaded.every());
    assertEquals(21, loaded.x());
    assertEquals(85, loaded.y());
    Files.writeString(directory.resolve("other.cfg"), "untouched");
    loaded.save();
    assertEquals("untouched", Files.readString(directory.resolve("other.cfg")));
    assertTrue(Files.readString(directory.resolve("ms.cfg")).contains("# Положение GUI"));
    assertTrue(Files.readString(directory.resolve("ms.cfg")).contains("# Причины и баллы"));
  }

  @Test
  void invalidConfigAndFailedSaveAreExplicit() throws Exception {
    Files.writeString(root.resolve("ms.cfg"), "clearEvery=0\n");
    assertThrows(java.io.IOException.class, () -> new MsConfig(root));
    Files.writeString(root.resolve("ms.cfg"), "clearEvery=20\nclear=maybe\n");
    assertThrows(java.io.IOException.class, () -> new MsConfig(root));
    var fresh = new MsConfig(root.resolve("other"));
    assertThrows(java.io.IOException.class, () -> fresh.interval("9999999999999999999999999"));
    assertEquals(20, fresh.every());
  }

  @Test
  void resultContainsOnlyRequestedFieldsAndRepeatedNames() throws Exception {
    var run = new CounterTest.Run();
    run.ban("Same", "SpidiBoost", "Читы", "2026-10-02 12:00");
    run.ban("Same", "SpidiBoost", "Читы", "2026-10-02 12:00");
    run.end();
    var file = new MsFiles(root.resolve("config"));
    file.write(run.result);
    String text = Files.readString(file.file());
    assertEquals(4, text.lines().count());
    assertTrue(text.contains("Ник: SpidiBoost"));
    assertTrue(text.contains("Баллы: 1,4"));
    assertTrue(text.contains("Ники: Same Same"));
    assertFalse(text.contains("Читы"));
    assertFalse(text.contains("2026"));
  }
}
