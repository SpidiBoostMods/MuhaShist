package net.spidiboost.muhashist.updates;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

final class ExitUpdate implements MuhaUpdater.Installer {
  private final Path game, jar;
  private Process helper;
  private BatchPlan plan;
  private String version = "";

  ExitUpdate(Path game, Path jar) {
    this.game = game;
    this.jar = jar;
  }

  public boolean ready(String release) {
    if (!release.equals(version) || helper == null || !helper.isAlive() || plan == null)
      return false;
    try {
      for (var change : plan.changes()) if (!ModsDownload.owns(change)) return false;
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public void install(Release release, byte[] bytes) throws Exception {
    if (jar == null) throw new IOException("Mod JAR is outside this instance");
    Path dir = game.resolve(".spidiboost-updates");
    Files.createDirectories(dir);
    if (Files.isSymbolicLink(dir) || !dir.toRealPath().getParent().equals(game.toRealPath()))
      throw new IOException("Foreign update directory");
    Path staged = Files.createTempFile(dir, "muhashist-release-", ".jar");
    Process waiting = null;
    BatchPlan next = null;
    try {
      Files.write(staged, bytes);
      Artifact.validate(staged, release);
      var change =
          new BatchPlan.Change(
              jar,
              game.resolve("mods").resolve(release.artifact()),
              staged,
              BatchInstall.hash(jar),
              release.sha256());
      next =
          new BatchPlan(
              ProcessHandle.current().pid(),
              game,
              List.of(change),
              List.of(),
              game,
              List.of(),
              false);
      BatchInstall.validate(next);
      if (helper != null) {
        helper.destroy();
        if (!helper.waitFor(3, TimeUnit.SECONDS)) {
          helper.destroyForcibly();
          if (!helper.waitFor(3, TimeUnit.SECONDS)) throw new IOException("Old helper still alive");
        }
        helper = null;
      }
      if (plan != null) {
        for (var old : plan.changes()) {
          ModsDownload.discard(old);
          Files.deleteIfExists(old.staged());
        }
        plan = null;
      }
      Path agent = Files.createTempFile(dir, "muhashist-agent-", ".jar");
      try (var input = ExitUpdate.class.getResourceAsStream("/muhashist-shared-update-agent.jar")) {
        if (input == null) throw new IOException("Missing update helper");
        byte[] contents = input.readNBytes(1048577);
        if (contents.length > 1048576) throw new IOException("Update helper too large");
        Files.write(agent, contents);
      }
      String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
      Path java = Path.of(System.getProperty("java.home"), "bin", executable);
      waiting =
          new ProcessBuilder(java.toString(), "-jar", agent.getFileName().toString())
              .directory(dir.toFile())
              .redirectError(
                  ProcessBuilder.Redirect.appendTo(dir.resolve("muhashist-agent.log").toFile()))
              .start();
      try (var output = waiting.getOutputStream()) {
        next.write(output);
      }
      Process child = waiting;
      var ready = new CompletableFuture<String>();
      Thread.ofPlatform()
          .daemon()
          .name("MuhaShist-helper-ready")
          .start(
              () -> {
                try {
                  ready.complete(
                      new BufferedReader(
                              new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))
                          .readLine());
                } catch (IOException e) {
                  ready.completeExceptionally(e);
                }
              });
      if (!"READY".equals(ready.get(15, TimeUnit.SECONDS)))
        throw new IOException("Update helper unavailable");
      ModsDownload.publish(next);
      plan = next;
      helper = waiting;
      version = release.version();
      waiting = null;
      staged = null;
    } finally {
      if (waiting != null) {
        waiting.destroyForcibly();
        waiting.waitFor(5, TimeUnit.SECONDS);
      }
      if (staged != null) {
        if (next != null) for (var change : next.changes()) ModsDownload.discard(change);
        Files.deleteIfExists(staged);
      }
    }
  }
}
