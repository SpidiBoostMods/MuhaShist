package net.spidiboost.muhashist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ScoreRulesTest {
  @Test
  void everyRequestedOnePointReason() {
    for (String reason :
        List.of(
            "Хранение читов",
            "Читы при проверке",
            "Не истёк срок удаления читов",
            "Не истёк срок использования читов",
            "Помеха проверке через AnyDesk, было выдано предупреждение",
            "Помеха проведению проверки",
            "Лив с проверки",
            "Уклон от проверки",
            "Отказ от прохождения проверки",
            "Чистка перед проверкой",
            "Сложился при проверке",
            "Ограничение доступа администрации к файлам/папкам во время проверки",
            "Признание в использовании/хранении читов при проверке",
            "Неадекватное поведение во время проведения проверки",
            "Использование батников для удаления следов запрещённого ПО",
            "Хранение батников для удаления следов запрещённого ПО",
            "Использование запрещённого мода",
            "Хранение запрещённого мода")) {
      assertEquals(10, ScoreRules.tenths(reason), reason);
      assertEquals(
          10, ScoreRules.tenths("'" + reason.toUpperCase(Locale.ROOT) + "' [Истёк]"), reason);
    }
  }

  @Test
  void sevenTenthsCheatsAndTeamVariants() {
    for (String reason :
        List.of(
            "Читы",
            "Читер",
            "чит",
            "Cheats",
            "CHEAT",
            "Cheater",
            "Cheating",
            "Использование читов",
            "Тим чит",
            "Тимчит",
            "Тиммейт читера",
            "Тиммейты с читерами",
            "Тим с читером",
            "Тим с читами",
            "Команда с читером",
            "Игра с читером",
            "Союз с читером",
            "Буст с читером",
            "Team cheat",
            "TEAM WITH CHEATER",
            "Teammate cheater",
            "teamcheat",
            "teaming with cheater",
            "playing with a cheater",
            "Тиммейт читера by Admin")) assertEquals(7, ScoreRules.tenths(reason), reason);
  }

  @Test
  void unrelatedPunishmentsAndNegationsAreNotCheats() {
    for (String reason :
        List.of(
            "Флуд",
            "FunPay",
            "FP_RW",
            "Реклама",
            "ЧСП",
            "Пиар читов",
            "Читы // rec",
            "Читы на рыбалке вручную",
            "Не читы",
            "Не читер",
            "Некорректный ник",
            "Упоминание читов в чате",
            "Хранение ресурсов",
            "Использование обычного мода",
            "Отказ от телепорта",
            "Сложился в пвп",
            "Признание в любви")) assertEquals(0, ScoreRules.tenths(reason), reason);
  }

  @Test
  void oldExtraReasonsAreNotSilentlyAddedToNewDefaults() {
    for (String reason :
        List.of(
            "Помеха проверке через AnyDesk",
            "Сложился перед проверкой",
            "Признание при проверке",
            "Хранение батников для чистки следов"))
      assertEquals(0, ScoreRules.tenths(reason), reason);
    assertEquals(18, ScoreRules.defaults().getFirst().split(" - ")[0].split(";").length);
  }
}
