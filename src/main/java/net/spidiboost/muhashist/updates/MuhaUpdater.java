package net.spidiboost.muhashist.updates;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

public final class MuhaUpdater implements AutoCloseable {
  public enum Mode {
    NOW,
    ENTRY,
    STATUS,
    RETRY
  }

  public record Status(String installed, String latest, String state) {
    public boolean outdated() {
      return !latest.isEmpty() && new Release(MOD, latest, "", "").newerThan(installed);
    }

    public String link() {
      return MOD.base() + "/releases/latest";
    }
  }

  static final ModCatalog MOD =
      ModCatalog.ALL.stream().filter(mod -> mod.id().equals("muhashist")).findFirst().orElseThrow();

  @FunctionalInterface
  interface Installer {
    void install(Release release, byte[] bytes) throws Exception;

    default boolean ready(String version) {
      return true;
    }
  }

  private final Path game;
  private final SharedUpdater.Fetcher http;
  private final Installer installer;
  private final boolean development;
  private final ScheduledExecutorService worker =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var thread = new Thread(r, "MuhaShist-updates");
            thread.setDaemon(true);
            return thread;
          });
  private final EnumMap<Mode, CompletableFuture<Status>> requests = new EnumMap<>(Mode.class);
  private final UpdateChecks failures = new UpdateChecks();
  private final UpdateReminders reminders = new UpdateReminders();
  private volatile Status status;
  private volatile boolean automatic;
  private String downloaded = "";
  private ScheduledFuture<?> retry;
  private boolean running, closed;

  public MuhaUpdater(Path game, String installed, Path jar, boolean development) {
    this(game, installed, development, new GitHubDownload()::get, new ExitUpdate(game, jar));
  }

  MuhaUpdater(
      Path game,
      String installed,
      boolean development,
      SharedUpdater.Fetcher http,
      Installer installer) {
    this.game = game;
    this.http = http;
    this.installer = installer;
    this.development = development;
    automatic = AutoDownload.enabled(game);
    status = new Status(installed, "", "idle");
  }

  public Status status() {
    return status;
  }

  public boolean automatic() {
    return automatic;
  }

  public void automatic(boolean enabled) throws java.io.IOException {
    AutoDownload.set(game, enabled);
    automatic = enabled;
  }

  public boolean remind(long now) {
    var current = status;
    if (automatic() || !current.outdated() || current.state().equals("downloaded")) return false;
    if (!reminders.due(now, true)) return false;
    reminders.shown(now);
    return true;
  }

  public synchronized CompletableFuture<Status> request(Mode mode) {
    if (closed) return CompletableFuture.completedFuture(status);
    var previous = requests.get(mode);
    if (previous != null) return previous;
    var result = new CompletableFuture<Status>();
    requests.put(mode, result);
    if (!running) {
      running = true;
      worker.execute(this::drain);
    }
    return result;
  }

  private void drain() {
    while (true) {
      Mode mode;
      CompletableFuture<Status> result;
      synchronized (this) {
        if (closed || requests.isEmpty()) {
          running = false;
          return;
        }
        mode = requests.keySet().iterator().next();
        result = requests.get(mode);
      }
      try {
        inspect(mode);
      } catch (Exception | LinkageError e) {
        status = new Status(status.installed(), status.latest(), "unavailable");
        LoggerFactory.getLogger("MuhaShist").debug("Update unavailable", e);
        synchronized (this) {
          if (!closed)
            retry =
                worker.schedule(
                    () -> request(Mode.RETRY), failures.failedDelaySeconds(), TimeUnit.SECONDS);
        }
      }
      synchronized (this) {
        requests.remove(mode);
      }
      result.complete(status);
    }
  }

  private void inspect(Mode mode) throws Exception {
    if (retry != null) retry.cancel(false);
    if (development) {
      status = new Status(status.installed(), "", "development");
      return;
    }
    status = new Status(status.installed(), status.latest(), "checking");
    var release =
        Release.parse(MOD, new String(http.get(MOD.latest(), 16384), StandardCharsets.UTF_8));
    status =
        new Status(
            status.installed(),
            release.version(),
            release.newerThan(status.installed()) ? "available" : "current");
    if (status.outdated()) {
      if (downloaded.equals(release.version()) && installer.ready(release.version())) {
        status = new Status(status.installed(), release.version(), "downloaded");
      } else if (mode == Mode.NOW || (mode == Mode.ENTRY && automatic())) {
        status = new Status(status.installed(), release.version(), "downloading");
        byte[] data = http.get(release.download(), 32 * 1024 * 1024);
        if (mode == Mode.NOW || automatic()) {
          installer.install(release, data);
          downloaded = release.version();
          status = new Status(status.installed(), release.version(), "downloaded");
        } else status = new Status(status.installed(), release.version(), "available");
      }
    }
    failures.success();
  }

  @Override
  public synchronized void close() {
    closed = true;
    if (retry != null) retry.cancel(false);
    worker.shutdownNow();
    requests.values().forEach(result -> result.complete(status));
    requests.clear();
  }
}
