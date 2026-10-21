package net.spidiboost.muhashist.qa;

import com.mojang.brigadier.CommandDispatcher;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import net.spidiboost.muhashist.*;
import net.spidiboost.muhashist.updates.*;

public final class NativeProbe implements ClientModInitializer {
  public static final List<String> sent = new ArrayList<>();
  public static final ArrayDeque<String> pendingChecks = new ArrayDeque<>();
  public static final List<Long> checkTimes = new ArrayList<>();
  public static boolean routeMessages;
  public static long primaryMessages, staffMessages;
  private static volatile Throwable packetFailure;
  private boolean checkShot, delayChanged;
  private int phase, frames, produced;
  private long started = System.nanoTime(), stopped;
  private Path report;
  private Process helper;

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private void pass(String value) throws Exception {
    Files.writeString(
        report, "PASS " + value + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  private static void field(Object owner, String name, Object value) throws Exception {
    var f = owner.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(owner, value);
  }

  private static void receive(MinecraftClient client, String value) {
    try {
      net.spidiboost.muhashist.qa.mixin.PacketAccess.muhaqa$dispatch(
          new GameMessageS2CPacket(Text.literal(value), false), client.getNetworkHandler());
    } catch (net.minecraft.network.OffThreadException ignored) {
    }
  }

  private static String ban(int i) {
    return "-- [2026-10-02 12:00] --\nRepeat"
        + (i % 17)
        + " был забанен куратором SpidiBoost\nПо причине: '"
        + (i % 2 == 0 ? "Читы" : "Хранение читов")
        + "' [Истёк]\nОкончание в 1 день\n";
  }

  private static void jar(Path target, String text) throws Exception {
    try (var out = new ZipOutputStream(Files.newOutputStream(target))) {
      out.putNextEntry(new ZipEntry("payload.txt"));
      out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  @Override
  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(this::tick);
  }

  private void tick(MinecraftClient client) {
    if (phase == 99) return;
    try {
      if (System.nanoTime() - started > 480_000_000_000L)
        throw new AssertionError("Native fixture exceeded eight minutes");
      if (client.player == null || client.player.age < 40 || client.getOverlay() != null) return;
      var mod = MuhaClient.INSTANCE;
      if (packetFailure != null)
        throw new AssertionError("Network-thread fixture failed", packetFailure);
      if (!pendingChecks.isEmpty()) {
        String name = pendingChecks.removeFirst();
        receive(
            client,
            "Цель ["
                + name
                + "]: забанена.\nЗабанил: "
                + (name.equals("Wrong") ? "Other" : "SpidiBoost")
                + "\nIP бан: no, Тихий: "
                + (name.equals("Silent") ? "yes" : "no")
                + ", Навсегда: no");
      }
      if (phase == 0) {
        if (client.currentScreen != null) return;
        report = client.runDirectory.toPath().resolve("results.txt");
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        MuhaClient.register(dispatcher);
        for (String alias : List.of("ms", "muhashist")) {
          require(
              !dispatcher
                  .parse(alias + " SpidiBoost 100000 1 октября 26", null)
                  .getReader()
                  .canRead(),
              "greedy date parse");
          for (String nick : List.of("open", "update", "clear", "stop", "config", "delay"))
            require(
                !dispatcher.parse(alias + " " + nick + " 100 1.10.26", null).getReader().canRead(),
                "reserved nickname parse");
          var suggestions =
              dispatcher
                  .getCompletionSuggestions(dispatcher.parse(alias + " ", null))
                  .get()
                  .getList()
                  .stream()
                  .map(s -> s.getText())
                  .toList();
          require(
              suggestions.containsAll(List.of("open", "stop", "clear", "update", "delay")),
              "root TAB");
          var delaySuggestions =
              dispatcher
                  .getCompletionSuggestions(dispatcher.parse(alias + " delay ", null))
                  .get()
                  .getList()
                  .stream()
                  .map(s -> s.getText())
                  .toList();
          require(delaySuggestions.containsAll(List.of("0", "100", "250", "1000")), "delay TAB");
          var updates =
              dispatcher
                  .getCompletionSuggestions(dispatcher.parse(alias + " update ", null))
                  .get()
                  .getList()
                  .stream()
                  .map(s -> s.getText())
                  .toList();
          require(updates.containsAll(List.of("on", "off", "now")), "update TAB");
        }
        checkUpdateCommands(client, mod);
        require(
            mod.config().delay() == 100 && mod.counter().delay() == 100, "default delay changed");
        client.getNetworkHandler().sendChatCommand("MS delay 250");
        require(mod.counter().delay() == 250, "delay command did not reach the queue");
        require(
            new MsConfig(FabricLoader.getInstance().getConfigDir()).delay() == 250,
            "delay did not persist");
        mod.execute("delay -1");
        require(
            mod.config().delay() == 250 && mod.counter().delay() == 250,
            "bad delay changed settings");
        mod.execute("delay");
        mod.execute("delay 0");
        require(mod.counter().delay() == 0, "zero delay rejected");
        mod.execute("delay 100");
        pass(
            "actual delay command, aliases, TAB, 100 ms default, zero, persistence and invalid"
                + " input");
        mod.execute("clear off");
        mod.execute("clear 7");
        var cfg = new MsConfig(FabricLoader.getInstance().getConfigDir());
        require(!cfg.clear() && cfg.every() == 7, "persistent clear settings");
        mod.execute("clear on");
        mod.execute("clear 20");
        Path configFile = cfg.file();
        String initialConfig = Files.readString(configFile);
        Files.writeString(
            configFile,
            initialConfig.replace("checkbanDelay=100", "checkbanDelay=350")
                + "\nПроверка, запятая - 0,125\n");
        mod.execute("config reload");
        require(mod.counter().delay() == 350, "config reload did not apply delay");
        require(
            mod.config().rules().score("Проверка, запятая").toPlainString().equals("0.125"),
            "config reload did not load decimal rule");
        var goodConfig = mod.config();
        Files.writeString(configFile, "# Причины\nbad\n");
        mod.execute("config load");
        require(mod.config() == goodConfig, "bad reload replaced working config");
        Files.writeString(configFile, initialConfig);
        mod.execute("config load");
        require(mod.counter().delay() == 100, "config load did not restore delay");
        pass(
            "config reload/load, decimal comma and reason comma; invalid reload keeps previous"
                + " settings");
        routeMessages = true;
        client.getNetworkHandler().sendChatCommand("MS SpidiBoost 100000 1 октября 26");
        require(
            sent.equals(List.of("shist SpidiBoost ban 100000")),
            "local alias leaked or count clamped");
        var wire =
            new Thread(
                () -> {
                  receive(client, "История персонала SpidiBoost (Лимит: 100000):");
                  receive(client, ban(0) + ban(1) + ban(2) + ban(3) + ban(4) + ban(5) + ban(6));
                },
                "MuhaQA-wire");
        wire.setDaemon(true);
        wire.setUncaughtExceptionHandler((thread, error) -> packetFailure = error);
        wire.start();
        produced = 7;
        phase = 1;
        pass(
            "real command fallback, both aliases, greedy dates/reserved nicknames, TAB and"
                + " persistent clear settings; exactly one shist and no checkban/checkmute");
      } else if (phase == 1) {
        require(
            mod.counter().active() || mod.counter().processed() >= 6,
            "raw packet capture failed before chat-panel cancellation");
        if (mod.counter().processed() < 6) return;
        receive(client, "Окончание в 1 день");
        client.setScreen(new ChatScreen(""));
        var b =
            mod.hud()
                .bounds(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        require(
            b.x() == (client.getWindow().getScaledWidth() - b.width()) / 2,
            "default panel not centered");
        require(
            b.y() + b.height() < client.getWindow().getScaledHeight() - 35,
            "default panel covers hotbar");
        require(
            client.currentScreen.mouseClicked(b.x() + 22, b.y() + 15, 0), "HUD click not captured");
        require(
            client.currentScreen.mouseDragged(b.x() + 60, b.y() - 15, 0, 38, -30),
            "HUD drag not captured");
        client.currentScreen.mouseReleased(b.x() + 60, b.y() - 15, 0);
        var saved = new MsConfig(FabricLoader.getInstance().getConfigDir());
        var restored =
            new MsHud(saved)
                .bounds(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        require(
            restored.x() == b.x() + 38 && restored.y() == Math.max(0, b.y() - 30),
            "position did not survive config reload");
        for (int width : List.of(180, 320, 854, 1920))
          for (int height : List.of(120, 240, 480, 1080)) {
            var fit = mod.hud().bounds(width, height);
            require(
                fit.x() >= 0
                    && fit.y() >= 0
                    && fit.x() + fit.width() <= width
                    && fit.y() + fit.height() <= height,
                "HUD out of bounds");
          }
        pass(
            "actual ChatScreen mouse press/drag/release saves ms.cfg; reload and 16 viewport"
                + " combinations; panel above hotbar and no actionbar duplicate");
        phase = 2;
      } else if (phase == 2) {
        receive(client, "Окончание в 1 день");
        if (++frames < 35) return;
        ScreenshotRecorder.saveScreenshot(
            client.runDirectory, "muhashist-hud.png", client.getFramebuffer(), text -> {});
        client.setScreen(null);
        client.inGameHud.getChatHud().addToMessageHistory("/qa-preserved");
        phase = 3;
      } else if (phase == 3) {
        if (produced < 100000) {
          if (produced == 2007)
            ScreenshotRecorder.saveScreenshot(
                client.runDirectory, "muhashist-running.png", client.getFramebuffer(), text -> {});
          var packet = new StringBuilder();
          int end = Math.min(100000, produced + 250);
          while (produced < end) packet.append(ban(produced++));
          receive(client, packet.toString());
          return;
        }
        if (mod.counter().active()) return;
        require(mod.counter().processed() == 100000, "native pipeline lost records");
        require(
            mod.counter().points().equals("85000"),
            "native score drift or repeated nick deduplication");
        require(sent.size() == 1, "extra network commands");
        require(
            client.inGameHud.getChatHud().getMessageHistory().contains("/qa-preserved"),
            "chat clear removed input history");
        Path result = FabricLoader.getInstance().getConfigDir().resolve("ms.txt");
        if (!Files.exists(result) || !Files.readString(result).contains("Баллы: 85000")) return;
        String text = Files.readString(result);
        require(
            text.lines().count() == 4
                && text.contains("Засчитано банов: 100000")
                && !text.contains("Хранение"),
            "result fields wrong");
        pass(
            "100000 records through real game-message mixin and sliced UI queue: 85000 exact"
                + " points, repeated names retained, actual chat clears, 4-field ms.txt saved");
        require(primaryMessages > 0 && staffMessages > 0, "both routed panels not exercised");
        pass(
            "raw packet capture precedes two chat-panel routes, including cancelled vanilla game"
                + " messages and network-thread rescheduling without duplicates");
        client.getNetworkHandler().sendChatCommand("muhashist SpidiBoost 100 01.10.2026");
        receive(client, "История персонала SpidiBoost (Лимит: 100):");
        receive(client, ban(0));
        stopped = System.nanoTime();
        phase = 4;
      } else if (phase == 4) {
        if (System.nanoTime() - stopped < 200_000_000L) return;
        mod.execute("stop");
        require(!mod.counter().active(), "stop left scan active");
        long count = mod.counter().processed();
        receive(client, ban(1));
        require(mod.counter().processed() == count, "late packet changed stopped result");
        pass("stop checkpoints partial result and rejects late packets");
        client.getNetworkHandler().sendChatCommand("ms SpidiBoost 20 1.10.2026");
        receive(client, "История персонала SpidiBoost (Лимит: 20):");
        StringBuilder advanced = new StringBuilder();
        for (String name : List.of("Keep", "Silent", "Wrong"))
          advanced
              .append("-- [2026-10-02 12:00] --\n")
              .append(name)
              .append(" был забанен куратором SpidiBoost\nПо причине: ")
              .append(name.equals("Keep") ? "Уклон от проверки" : "Читы")
              .append(" [Активный]\n");
        advanced.append(
            "-- [2026-10-02 12:01] --\n"
                + "Expired был забанен куратором SpidiBoost\n"
                + "По причине: Уклон от проверки [Истёк]\n");
        for (String time : List.of("12:00", "12:05", "12:11"))
          advanced
              .append("-- [2026-10-02 ")
              .append(time)
              .append(
                  "] --\n"
                      + "Fish был забанен куратором SpidiBoost\n"
                      + "По причине: cheat [auto-fish] [Истёк]\n");
        receive(client, advanced.toString());
        phase = 5;
      } else if (phase == 5) {
        if (!delayChanged && mod.counter().checking() && mod.counter().checked() == 0) {
          delayChanged = true;
          mod.execute("delay 250");
          require(mod.counter().delay() == 250, "live delay command ignored");
        }
        if (!checkShot && mod.counter().checking() && mod.counter().checked() == 1) {
          checkShot = true;
          ScreenshotRecorder.saveScreenshot(
              client.runDirectory, "muhashist-checks.png", client.getFramebuffer(), text -> {});
        }
        if (mod.counter().active()) return;
        require(mod.counter().points().equals("3,4"), "active/silent/owner/autofish native total");
        require(
            sent.stream()
                .filter(s -> s.startsWith("checkban "))
                .toList()
                .equals(List.of("checkban Keep", "checkban Silent", "checkban Wrong")),
            "active-only sequential commands or unexpected check for expired/autofish");
        Path reportFile = FabricLoader.getInstance().getConfigDir().resolve("ms.txt");
        if (!Files.readString(reportFile).contains("Баллы: 3,4")) return;
        require(checkTimes.size() == 3, "missing checkban send timestamps");
        long fastest = Long.MAX_VALUE;
        for (int i = 1; i < checkTimes.size(); i++)
          fastest = Math.min(fastest, checkTimes.get(i) - checkTimes.get(i - 1));
        require(delayChanged && fastest >= 249, "live checkban delay was not respected");
        pass(
            "native live /ms delay 250, sequential checkban spacing: minimum "
                + fastest
                + " ms, no overlapping requests");
        pass(
            "actual active-only checkban commands, foreign curator/silent filtering, inactive score"
                + " and ten-minute autofish grouping; evasion counts one point for active and"
                + " expired bans");
        routeMessages = false;
        prepareUpdate(client);
        phase = 99;
      }
    } catch (Throwable e) {
      e.printStackTrace();
      try {
        if (report != null)
          Files.writeString(
              report, "FAIL " + e + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (Exception ignored) {
      }
      if (helper != null) helper.destroyForcibly();
      phase = 99;
      client.scheduleStop();
    }
  }

  private void prepareUpdate(MinecraftClient client) throws Exception {
    Path game =
        client
            .runDirectory
            .toPath()
            .toAbsolutePath()
            .resolve("updater-QA/Призма с пробелами/instances/Muha-QA/minecraft");
    Files.createDirectories(game.resolve("mods"));
    net.spidiboost.muhashist.updates.NativeUpdateProbe.prepare(game);
    pass("independent MuhaShist off/now transaction, own helper, no launcher and no foreign mods");
    var state =
        new net.spidiboost.muhashist.updates.MuhaUpdater.Status("1.2.1", "1.2.2", "downloaded");
    var board = new OpaqueWindow();
    UpdateBoard.attach(board, () -> state);
    require(
        board.values().size() == 2 && board.values().get(1).a().equals("muhashist"),
        "combined UI row missing");
    UpdateBoard.attach(board, () -> state);
    require(board.values().size() == 2, "combined UI duplicated MuhaShist");
    client.setScreen(board);
    pass("shared update screen keeps existing row and adds independent MuhaShist status once");
    client.scheduleStop();
  }

  public record OpaqueRow(String a, String b, String c, String d) {}

  public static final class OpaqueWindow extends net.minecraft.client.gui.screen.Screen {
    private final java.util.function.Supplier<List<OpaqueRow>> entries =
        () -> List.of(new OpaqueRow("other", "Other", "1.0.0", "Актуальная версия"));

    public OpaqueWindow() {
      super(Text.literal("SpidiBoost — обновления"));
    }

    public List<OpaqueRow> values() {
      return entries.get();
    }
  }

  private void checkUpdateCommands(MinecraftClient client, MuhaClient mod) throws Exception {
    Path game = client.runDirectory.toPath();
    Path other = game.resolve("config/other-updater.properties");
    Files.writeString(other, "untouched");
    mod.execute("update off");
    require(!AutoDownload.enabled(game), "off did not persist");
    require(Files.readString(other).equals("untouched"), "Muha changed another setting");
    mod.execute("update now");
    var updaterField = MuhaClient.class.getDeclaredField("updater");
    updaterField.setAccessible(true);
    MuhaUpdater updater = (MuhaUpdater) updaterField.get(mod);
    updater.request(MuhaUpdater.Mode.STATUS).get(5, java.util.concurrent.TimeUnit.SECONDS);
    require(!updater.automatic(), "now enabled future downloads");
    var statusField = MuhaUpdater.class.getDeclaredField("status");
    statusField.setAccessible(true);
    var remindersField = MuhaUpdater.class.getDeclaredField("reminders");
    remindersField.setAccessible(true);
    Object reminders = remindersField.get(updater);
    var nextField = reminders.getClass().getDeclaredField("next");
    nextField.setAccessible(true);
    var tick = MuhaClient.class.getDeclaredMethod("tick");
    tick.setAccessible(true);
    var chatField = client.inGameHud.getChatHud().getClass().getDeclaredField("messages");
    chatField.setAccessible(true);
    client.inGameHud.getChatHud().clear(false);
    String current =
        FabricLoader.getInstance()
            .getModContainer("muhashist")
            .orElseThrow()
            .getMetadata()
            .getVersion()
            .getFriendlyString();
    statusField.set(updater, new MuhaUpdater.Status(current, "99.0.0", "available"));
    for (int i = 0; i < 4; i++) {
      nextField.setLong(reminders, 0);
      tick.invoke(mod);
      require(
          ((List<?>) chatField.get(client.inGameHud.getChatHud())).size() == Math.min(i + 1, 3) * 2,
          "update notification budget");
    }
    var title = client.inGameHud.getClass().getDeclaredField("title");
    title.setAccessible(true);
    require(
        ((Text) title.get(client.inGameHud)).getString().equals("Взгляни в чат"),
        "update title missing");
    var messages = (List<?>) chatField.get(client.inGameHud.getChatHud());
    Text latest =
        (Text) messages.getFirst().getClass().getMethod("content").invoke(messages.getFirst());
    var links = new ArrayList<String>();
    latest.visit(
        (style, text) -> {
          if (style.getClickEvent() != null) links.add(style.getClickEvent().getValue());
          return Optional.empty();
        },
        net.minecraft.text.Style.EMPTY);
    require(
        links.contains("https://github.com/SpidiBoostMods/MuhaShist/releases/latest"),
        "GitHub link not clickable");
    statusField.set(updater, new MuhaUpdater.Status(current, "", "development"));
    mod.execute("update on");
    require(AutoDownload.enabled(game), "on did not persist");
    require(Files.readString(other).equals("untouched"), "on changed another setting");
    pass(
        "native update on/off/now, TAB aliases, persistent isolated setting, clickable GitHub link,"
            + " exact title and three-notification limit");
  }
}
