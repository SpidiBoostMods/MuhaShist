package net.spidiboost.muhashist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheckReplyTest {
  @Test
  void screenshotResponseRequiresOwnedTargetCuratorAndSilentNo() {
    for (String silent : List.of("no", "нет", "false", "выкл")) {
      var reply = new CheckReply("Waiterho", "SpidiBoost");
      reply.accept(
          "[21:52:13] Цель [Waiterho]: забанена.\n"
              + "Забанил: SpidiBoost\n"
              + "Причина: Лив с проверки\n"
              + "Забанен: 2026-10-15 16:51 число\n"
              + "Окончание бана: 2026-12-04 16:51\n"
              + "IP бан: no, Тихий: "
              + silent
              + ", Навсегда: no");
      assertEquals(CheckReply.State.ACCEPT, reply.state(), silent);
    }
    for (String silent : List.of("yes", "да", "true", "вкл")) {
      var reply = new CheckReply("Nick", "SpidiBoost");
      reply.accept("Цель [Nick]: забанена.\nЗабанил: SpidiBoost\nТихий: " + silent);
      assertEquals(CheckReply.State.REJECT, reply.state());
    }
  }

  @Test
  void foreignAndPartialRepliesNeverBecomeSilentNoByDefault() {
    var reply = new CheckReply("Nick", "SpidiBoost");
    reply.accept("Цель [Other]: забанена.\nЗабанил: SpidiBoost\nТихий: no");
    assertEquals(CheckReply.State.WAITING, reply.state());
    reply.accept("Цель [Nick]: забанена.\nЗабанил: SpidiBoost");
    assertEquals(CheckReply.State.WAITING, reply.state());
    reply.accept("Тихий: unknown");
    assertEquals(CheckReply.State.WAITING, reply.state());
    reply.accept("Тихий: no");
    assertEquals(CheckReply.State.ACCEPT, reply.state());
    var foreign = new CheckReply("Nick", "SpidiBoost");
    foreign.accept("Цель [Nick]: забанена.\nЗабанил: Other\nТихий: no");
    assertEquals(CheckReply.State.REJECT, foreign.state());
  }

  @Test
  void fieldsMayArriveInEitherOrderButNotFromPlayerChat() {
    var reply = new CheckReply("Nick", "SpidiBoost");
    reply.accept("User » Цель [Nick]: забанена.\nUser » Забанил: SpidiBoost\nUser » Тихий: no");
    assertEquals(CheckReply.State.WAITING, reply.state());
    reply.accept("Цель [Nick]: забанена.\nIP бан: no, Тихий: no, Навсегда: no");
    assertEquals(CheckReply.State.WAITING, reply.state());
    reply.accept("§6Забанил: §cSPIDIBOOST");
    assertEquals(CheckReply.State.ACCEPT, reply.state());
  }

  @Test
  void noBanNoPlayerPermissionsAndThrottleAreDistinct() {
    var none = new CheckReply("Nick", "SpidiBoost");
    none.accept("Цель [Nick] не забанена.");
    assertEquals(CheckReply.State.REJECT, none.state());
    var absent = new CheckReply("Nick", "SpidiBoost");
    absent.accept("Ошибка: Игрок не найден.");
    assertEquals(CheckReply.State.REJECT, absent.state());
    var denied = new CheckReply("Nick", "SpidiBoost");
    denied.accept("Недостаточно прав.");
    assertEquals(CheckReply.State.ERROR, denied.state());
    var throttle = new CheckReply("Nick", "SpidiBoost");
    throttle.accept("Слишком быстро! Подождите.");
    assertEquals(CheckReply.State.RETRY, throttle.state());
  }
}
