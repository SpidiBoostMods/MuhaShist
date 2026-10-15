package net.spidiboost.muhashist;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.spidiboost.muhashist.updates.*;

public final class MuhaClient implements ClientModInitializer {
  public static MuhaClient INSTANCE;
  private MinecraftClient client;
  private MsConfig config;
  private MsHud hud;
  private MsFiles files;
  private HistoryCounter scan;
  private String configError = "";
  private boolean clearRequested, manualUpdate;
  private long updatePoll, updateDeadline, displayUntil;
  private Object connection;
  private final IncomingLines incoming = new IncomingLines();
  private long receivedBans;
  private final ExecutorService io =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "MuhaShist-files");
            t.setDaemon(true);
            return t;
          });

  @Override
  public void onInitializeClient() {
    INSTANCE = this;
    client = MinecraftClient.getInstance();
    Path directory = FabricLoader.getInstance().getConfigDir();
    files = new MsFiles(directory);
    try {
      config = new MsConfig(directory);
    } catch (Exception e) {
      configError = e.getMessage();
    }
    if (config != null) hud = new MsHud(config);
    scan =
        new HistoryCounter(
            new HistoryCounter.Listener() {
              public void clearChat() {
                if (receivedBans < scan.processed()) clearRequested = true;
              }

              public void finished(HistoryCounter.Result result, String error) {
                complete(result, error);
              }
            },
            () -> System.nanoTime() / 1_000_000);
    if (config != null) scan.clearing(config.clear(), config.every());
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> register(dispatcher));
    ClientTickEvents.END_CLIENT_TICK.register(c -> tick());
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, c) -> {
          incoming.clear();
          scan.stop("Соединение потеряно. Частичный результат сохранён.");
        });
    ClientLifecycleEvents.CLIENT_STOPPING.register(
        c -> {
          scan.stop("");
          io.shutdown();
          try {
            io.awaitTermination(5, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    HudRenderCallback.EVENT.register(
        (context, clock) -> {
          if (hud != null && hudVisible() && !(client.currentScreen instanceof ChatScreen))
            hud.draw(context, scan);
        });
    SharedUpdater.preserve("muhashist", () -> scan.stop(""));
    SharedUpdater.initialize();
  }

  public static void register(
      com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher) {
    for (String alias : List.of("ms", "muhashist"))
      dispatcher.register(
          ClientCommandManager.literal(alias)
              .executes(
                  c -> {
                    INSTANCE.execute("");
                    return 1;
                  })
              .then(
                  ClientCommandManager.argument("параметры", StringArgumentType.greedyString())
                      .suggests(
                          (context, builder) -> {
                            String text = builder.getRemaining();
                            int last = text.lastIndexOf(' ');
                            var offset = builder.createOffset(builder.getStart() + last + 1);
                            String part = text.substring(last + 1).toLowerCase(Locale.ROOT);
                            if (last < 0) {
                              for (String value : List.of("open", "stop", "clear", "update"))
                                if (value.startsWith(part)) offset.suggest(value);
                              var network = MinecraftClient.getInstance().getNetworkHandler();
                              if (network != null)
                                network
                                    .getPlayerList()
                                    .forEach(
                                        p -> {
                                          String name = p.getProfile().getName();
                                          if (name.toLowerCase(Locale.ROOT).startsWith(part))
                                            offset.suggest(name);
                                        });
                            } else if (text.matches("(?i)(?:update|clear)\\s+\\S*")) {
                              for (String value : List.of("on", "off"))
                                if (value.startsWith(part)) offset.suggest(value);
                            } else if (text.matches("\\S+\\s+\\d*")) {
                              for (String value : List.of("100", "1000", "100000"))
                                if (value.startsWith(part)) offset.suggest(value);
                            }
                            return offset.buildFuture();
                          })
                      .executes(
                          c -> {
                            INSTANCE.execute(StringArgumentType.getString(c, "параметры"));
                            return 1;
                          })));
  }

  public static boolean handle(String raw) {
    String text = raw == null ? "" : raw.strip();
    if (text.startsWith("/")) text = text.substring(1);
    else if (raw != null && raw.startsWith(" ")) text = text.strip();
    String[] parts = text.split("\\s+", 2);
    String root = parts[0];
    if (!root.equalsIgnoreCase("ms") && !root.equalsIgnoreCase("muhashist")) return false;
    if (INSTANCE != null) INSTANCE.execute(parts.length < 2 ? "" : parts[1]);
    return true;
  }

  public void execute(String arguments) {
    try {
      MsCommand command = MsCommand.parse(arguments);
      switch (command.action()) {
        case HELP -> help();
        case START -> start(command);
        case STOP -> {
          incoming.clear();
          if (scan.active()) scan.stop("Остановлено.");
          else message("Подсчёт не запущен.");
        }
        case OPEN ->
            io.execute(
                () -> {
                  try {
                    files.open();
                  } catch (Exception e) {
                    errorAsync("Не удалось открыть ms.txt: " + e.getMessage());
                  }
                });
        case CLEAR -> clear(command.value());
        case UPDATE -> update(command.value());
      }
    } catch (Exception e) {
      error(e.getMessage() == null ? "Не удалось выполнить команду." : e.getMessage());
    }
  }

  private void start(MsCommand command) {
    if (!configError.isEmpty())
      throw new IllegalStateException(
          configError + " Исправьте config/ms.cfg и перезапустите игру.");
    if (client.player == null || client.getNetworkHandler() == null)
      throw new IllegalStateException("Сначала зайдите на сервер.");
    String outgoing = "shist " + command.nickname() + " ban " + command.limit();
    if (outgoing.length() > 256)
      throw new IllegalArgumentException(
          "Число слишком длинное для сетевой команды Minecraft (256 символов).");
    scan.start(command.nickname(), command.limit(), command.since());
    incoming.clear();
    receivedBans = 0;
    connection = client.getNetworkHandler();
    client.getNetworkHandler().sendChatCommand(outgoing);
  }

  private void clear(String value) throws Exception {
    if (config == null) throw new IllegalStateException(configError);
    if (value.equals("on") || value.equals("off")) config.clearing(value.equals("on"));
    else if (!value.isEmpty()) config.interval(value);
    scan.clearing(config.clear(), config.every());
    message(
        "Очистка " + (config.clear() ? "вкл" : "выкл") + " · каждые " + config.every() + " банов.");
  }

  private void update(String value) throws Exception {
    if (!value.isEmpty()) {
      RestartPolicy.set(client.runDirectory.toPath(), value.equals("on"));
      SharedUpdater.preferenceChanged();
      message("Автоперезапуск " + (value.equals("on") ? "вкл" : "выкл") + ".");
      return;
    }
    SharedUpdater.requestCheck();
    manualUpdate = true;
    updatePoll = now() + 300;
    updateDeadline = now() + 60000;
  }

  private void help() {
    message("/ms ник количество дата — подсчёт");
    message("/ms open · /ms stop");
    message("/ms clear on|off|число");
    message("/ms update [on|off]");
  }

  private void complete(HistoryCounter.Result result, String problem) {
    incoming.clear();
    clearChat();
    displayUntil = now() + 3500;
    io.execute(
        () -> {
          try {
            files.write(result);
          } catch (Exception e) {
            errorAsync("Не удалось сохранить config/ms.txt: " + e.getMessage());
          }
        });
    if (!problem.isEmpty()) error(problem + " Баллы: " + result.points());
    else message("Баллы: " + result.points());
  }

  private void tick() {
    if (scan.active()) {
      if (connection != null
          && (client.getNetworkHandler() != connection || client.player == null)) {
        incoming.clear();
        scan.stop("Соединение изменилось. Частичный результат сохранён.");
      } else {
        incoming.drain(scan::accept, 3000, 5_000_000);
        if (incoming.empty()) scan.tick();
      }
    }
    clearChat();
    if (manualUpdate && now() >= updatePoll) {
      var state = SharedUpdater.status("muhashist");
      if (!state.getOrDefault("state", "checking").equals("checking") || now() > updateDeadline) {
        manualUpdate = false;
        String version =
            FabricLoader.getInstance()
                .getModContainer("muhashist")
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();
        message(
            "Версия "
                + version
                + " · GitHub "
                + (state.getOrDefault("latest", "").isBlank() ? "нет данных" : state.get("latest"))
                + " · перезапуск "
                + (RestartPolicy.enabled(client.runDirectory.toPath()) ? "вкл" : "выкл"));
        if (!Set.of("current", "development").contains(state.getOrDefault("state", "")))
          message(state.getOrDefault("status", "Проверка пока недоступна."));
      } else updatePoll = now() + 300;
    }
  }

  public void receive(String text) {
    if (scan.active()) {
      incoming.add(text);
      if (config != null && config.clear() && !text.contains("»")) {
        int at = 0;
        while ((at = text.indexOf(" был забанен ", at)) >= 0) {
          if (++receivedBans % config.every() == 0) clearRequested = true;
          at += 14;
        }
      }
    }
  }

  public void clearChat() {
    if (clearRequested) {
      clearRequested = false;
      client.inGameHud.getChatHud().clear(false);
    }
  }

  public boolean hudVisible() {
    return scan.active() || now() < displayUntil;
  }

  public MsHud hud() {
    return hud;
  }

  public HistoryCounter counter() {
    return scan;
  }

  public MsConfig config() {
    return config;
  }

  public boolean releaseHud() {
    if (hud != null)
      try {
        return hud.release();
      } catch (Exception e) {
        error("Не удалось сохранить положение панели: " + e.getMessage());
        return true;
      }
    return false;
  }

  private static long now() {
    return System.nanoTime() / 1_000_000;
  }

  private void message(String value) {
    if (client.inGameHud != null) client.inGameHud.getChatHud().addMessage(MsTheme.message(value));
  }

  private void error(String value) {
    if (client.inGameHud != null) client.inGameHud.getChatHud().addMessage(MsTheme.error(value));
  }

  private void errorAsync(String value) {
    client.execute(() -> error(value));
  }
}
