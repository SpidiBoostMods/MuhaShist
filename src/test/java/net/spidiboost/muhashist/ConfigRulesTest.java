package net.spidiboost.muhashist;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigRulesTest {
  @TempDir Path directory;

  @Test
  void untouchedPreviousDefaultsMigrateButUserWeightsAndCommentsArePreserved() throws Exception {
    String extras =
        "Помеха проверке через AnyDesk; Уклон от проверки; Уклон от проверки (время истекло); Отказ"
            + " от проверки; Сложился перед проверкой; Ограничение доступа к файлам при проверке;"
            + " Признание при проверке; Неадекватное поведение при проверке; Использование батников"
            + " для чистки следов ПО; Хранение батников для чистки следов";
    String old =
        "# GUI\nhudX=12\nhudY=34\nclear=false\nclearEvery=7\n# Причины\n"
            + ScoreRules.defaults().getFirst().replace(" - 1.0", "; " + extras + " - 1.0")
            + "\n"
            + ScoreRules.defaults().get(1)
            + "\n# мой комментарий\n";
    Files.writeString(directory.resolve("ms.cfg"), old);
    var migrated = new MsConfig(directory);
    assertEquals(12, migrated.x());
    assertEquals(34, migrated.y());
    assertFalse(migrated.clear());
    assertEquals(7, migrated.every());
    assertEquals(BigDecimal.ZERO, migrated.rules().score("Уклон от проверки"));
    assertTrue(Files.readString(migrated.file()).contains("# мой комментарий"));
    String custom = old.replace(" - 1.0", " - 2.0");
    Files.writeString(directory.resolve("ms.cfg"), custom);
    var kept = new MsConfig(directory);
    assertEquals(new BigDecimal("2"), kept.rules().score("Уклон от проверки"));
    assertEquals(custom, Files.readString(kept.file()));
  }

  @Test
  void reasonsUseSemicolonsAndKeepInternalCommasAndDecimalPrecision() throws Exception {
    Path file = directory.resolve("ms.cfg");
    String text =
        "# Причины\n"
            + "reason1; reason2 - 1\n"
            + "reason3;reason4 - 0.7\n"
            + "reason5; reason6 - 0,1\n"
            + "reason7; reason8 - 2.0\n"
            + "Помеха проверке через AnyDesk, было выдано предупреждение - 1.25\n";
    Files.writeString(file, text);
    var config = new MsConfig(directory);
    assertEquals(new BigDecimal("0.7"), config.rules().score("reason3"));
    assertEquals(new BigDecimal("0.1"), config.rules().score("reason6"));
    assertEquals(new BigDecimal("2"), config.rules().score("reason8"));
    assertEquals(
        new BigDecimal("1.25"),
        config.rules().score("Помеха проверке через AnyDesk, было выдано предупреждение"));
    assertEquals(BigDecimal.ZERO, config.rules().score("Помеха проверке через AnyDesk"));
    config.position(23, 48);
    assertTrue(Files.readString(file).contains(text));
    var reloaded = new MsConfig(directory);
    assertEquals(23, reloaded.x());
    assertEquals(
        new BigDecimal("1.25"),
        reloaded.rules().score("Помеха проверке через AnyDesk, было выдано предупреждение"));
  }

  @Test
  void guiAutosavePreservesUnreloadedEditsAndComments() throws Exception {
    var config = new MsConfig(directory);
    Path file = config.file();
    Files.writeString(
        file, "# Положение GUI\nhudX=1\nhudY=2\n# Причины — мои\nЧиты - 3.25\n# не удалять\n");
    config.position(70, 90);
    config.clearing(false);
    config.interval("7");
    String saved = Files.readString(file);
    assertTrue(saved.contains("# не удалять"));
    assertTrue(saved.contains("Читы - 3.25"));
    assertEquals(new BigDecimal("3.25"), new MsConfig(directory).rules().score("Читы"));
  }

  @Test
  void legacyConfigMigratesWithoutResettingSettingsOrOtherFiles() throws Exception {
    Files.writeString(
        directory.resolve("ms.cfg"), "hudX=34\nhudY=67\nclear=false\nclearEvery=9\nother=keep\n");
    var config = new MsConfig(directory);
    assertEquals(34, config.x());
    assertEquals(67, config.y());
    assertFalse(config.clear());
    assertEquals(9, config.every());
    String saved = Files.readString(config.file());
    assertTrue(saved.contains("# Положение GUI"));
    assertTrue(saved.contains("other=keep"));
    assertEquals(600000, config.fishMillis());
    assertEquals(new BigDecimal("1"), config.rules().score("Не истёк срок использования читов"));
  }

  @Test
  void badRulesDoNotRewriteTheFileAndEmptySectionCanDisableScoring() throws Exception {
    for (String text :
        List.of(
            "# Причины\nЧиты - nope\n",
            "# Причины\nЧиты;;Читер - 1\n",
            "# Причины\nЧиты - -1\n",
            "# Причины\nЧиты - 0.7\nчиты - 2\n",
            "autofishWindowMinutes=0\n")) {
      Files.writeString(directory.resolve("ms.cfg"), text);
      assertThrows(java.io.IOException.class, () -> new MsConfig(directory), text);
      assertEquals(text, Files.readString(directory.resolve("ms.cfg")));
    }
    Files.writeString(directory.resolve("ms.cfg"), "# Причины\n");
    assertEquals(BigDecimal.ZERO, new MsConfig(directory).rules().score("Читы"));
  }

  @Test
  void requestedDefaultsAndStrictCheatAliases() {
    for (String reason : ScoreRules.defaults().getFirst().split(" - ")[0].split(";"))
      assertEquals(new BigDecimal("1"), ScoreRules.standard().score(reason));
    for (String reason :
        List.of("Читы.", "CHEATS.", "ЧИТЫ", "Тиммейты с читерами", "TEAM WITH CHEATER"))
      assertEquals(new BigDecimal("0.7"), ScoreRules.standard().score(reason), reason);
    for (String reason :
        List.of(
            "Читы (автофиш)",
            "афтофиш",
            "auto-fish",
            "autofish",
            "читы (autofish)",
            "cheat [auto-fish]",
            "Auto Fishing",
            "Читы (Auto-Fish) // rec",
            "аutofish",
            "авто-фиш",
            "aut0fish",
            "Читы [autofishing, rec]")) {
      assertTrue(ScoreRules.autofish(reason), reason);
      assertEquals(new BigDecimal("0.7"), ScoreRules.standard().score(reason), reason);
    }
    for (String reason :
        List.of("Читы, совсем другая причина", "Читы (другое)", "Реклама auto-fish", "Без читов"))
      assertEquals(BigDecimal.ZERO, ScoreRules.standard().score(reason), reason);
  }

  @Test
  void configCommandsAndReservedNicknameRemainUnambiguous() {
    for (String value : List.of("dir", "reload", "load")) {
      assertEquals(MsCommand.Action.CONFIG, MsCommand.parse("config " + value).action());
      assertEquals(value, MsCommand.parse("config " + value).value());
    }
    assertEquals(MsCommand.Action.START, MsCommand.parse("config 100 1 октября 26").action());
    assertThrows(IllegalArgumentException.class, () -> MsCommand.parse("config unknown"));
  }
}
