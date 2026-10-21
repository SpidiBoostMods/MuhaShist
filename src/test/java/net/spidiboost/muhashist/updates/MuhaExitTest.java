package net.spidiboost.muhashist.updates;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuhaExitTest {
  @TempDir Path root;

  private Path jar(Path file, String version) throws Exception {
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(new ZipEntry("fabric.mod.json"));
      zip.write(
          ("{\"id\":\"muhashist\",\"version\":\""
                  + version
                  + "\",\"name\":\"spidiboost.MuhaShist\",\"authors\":[\"SpidiBoost\"],"
                  + "\"environment\":\"client\",\"depends\":{\"minecraft\":\"1.21.4\"}}")
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("muhashist-shared-update-agent.jar"));
      zip.write(new byte[] {1, 2, 3});
      zip.closeEntry();
    }
    return file;
  }

  @Test
  void realInstallerKeepsLiveJarThenRemovesOnlyOldMuhaAfterNormalExit() throws Exception {
    Path game = root.resolve("Призма с пробелами/инстанс/minecraft");
    Files.createDirectories(game.resolve("mods"));
    Path old = jar(game.resolve("mods/muhashist-1.21.4-v1.1.3.jar"), "1.1.3");
    Path newFile = jar(root.resolve("new.jar"), "1.2.0");
    Path next = game.resolve("mods/muhashist-1.21.4-v1.2.0.jar");
    Files.writeString(game.resolve("mods/OtherMod.jar"), "untouched");
    Files.createDirectories(game.resolve("config"));
    Files.writeString(game.resolve("config/ms.cfg"), "personal configuration");
    RestartPolicy.set(game, true);
    String oldHash = BatchInstall.hash(old), newHash = BatchInstall.hash(newFile);
    String java =
        Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
            .toString();
    var paths = new ArrayList<String>();
    for (Class<?> type :
        List.of(MuhaExitTest.class, ExitUpdate.class, BatchPlan.class, JsonParser.class))
      paths.add(
          Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
    paths.add(Path.of("build/resources/main").toAbsolutePath().toString());
    Process client =
        new ProcessBuilder(
                java, "-cp", String.join(File.pathSeparator, paths), ClientFixture.class.getName())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    try {
      var input = new DataOutputStream(client.getOutputStream());
      for (String value : List.of(game.toString(), old.toString(), newFile.toString(), newHash))
        input.writeUTF(value);
      input.flush();
      var ready = new java.util.concurrent.CompletableFuture<String>();
      Thread.ofPlatform()
          .daemon()
          .start(
              () -> {
                try {
                  ready.complete(
                      new BufferedReader(new InputStreamReader(client.getInputStream()))
                          .readLine());
                } catch (IOException e) {
                  ready.completeExceptionally(e);
                }
              });
      String response = ready.get(20, TimeUnit.SECONDS);
      assertNotNull(response);
      assertTrue(response.startsWith("READY "));
      var helper = ProcessHandle.of(Long.parseLong(response.substring(6))).orElseThrow();
      assertEquals(oldHash, BatchInstall.hash(old));
      assertEquals(newHash, BatchInstall.hash(next));
      assertTrue(client.isAlive());
      client.getOutputStream().write(1);
      client.getOutputStream().flush();
      assertTrue(client.waitFor(10, TimeUnit.SECONDS));
      assertEquals(0, client.exitValue());
      helper.onExit().get(15, TimeUnit.SECONDS);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      while (Files.exists(old) && System.nanoTime() < deadline) Thread.sleep(25);
      assertFalse(Files.exists(old));
      assertEquals(newHash, BatchInstall.hash(next));
      assertEquals(
          oldHash,
          BatchInstall.hash(game.resolve(".spidiboost-updates/backups/" + oldHash + ".jar.bak")));
      assertEquals("untouched", Files.readString(game.resolve("mods/OtherMod.jar")));
      assertEquals("personal configuration", Files.readString(game.resolve("config/ms.cfg")));
      long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      Path log = game.resolve(".spidiboost-updates/update.log");
      while (!Files.exists(log) && System.nanoTime() < until) Thread.sleep(25);
      assertFalse(Files.readString(log).contains("launcher restart"));
    } finally {
      client.destroyForcibly();
      assertTrue(client.waitFor(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void muhaOnlyPlansCannotRequestALauncherAndUseAnIndependentLock() {
    var change =
        new BatchPlan.Change(
            root.resolve("old.jar"),
            root.resolve("muhashist-1.21.4-v1.2.0.jar"),
            root.resolve("stage.jar"),
            "a".repeat(64),
            "b".repeat(64));
    var plan = new BatchPlan(1, root, List.of(change), List.of(), root, List.of("launcher"), false);
    assertTrue(BatchAgent.muhaOnly(plan));
    var family =
        new BatchPlan.Change(
            root.resolve("old.jar"),
            root.resolve("SpidiCard-1.21.4-1.2.0.jar"),
            root.resolve("stage.jar"),
            "a".repeat(64),
            "b".repeat(64));
    assertFalse(
        BatchAgent.muhaOnly(
            new BatchPlan(1, root, List.of(change, family), List.of(), root, List.of(), false)));
  }

  public static final class ClientFixture {
    public static void main(String[] args) throws Exception {
      var input = new DataInputStream(System.in);
      Path game = Path.of(input.readUTF()), old = Path.of(input.readUTF());
      Path download = Path.of(input.readUTF());
      String hash = input.readUTF();
      var mod =
          ModCatalog.ALL.stream()
              .filter(value -> value.id().equals("muhashist"))
              .findFirst()
              .orElseThrow();
      var release = new Release(mod, "1.2.0", mod.artifact("1.2.0"), hash);
      try (var loaded = new ZipFile(old.toFile())) {
        var update = new ExitUpdate(game, old);
        update.install(release, Files.readAllBytes(download));
        if (!update.ready("1.2.0")) throw new IOException("Helper not alive");
        var helper = ExitUpdate.class.getDeclaredField("helper");
        helper.setAccessible(true);
        System.out.println("READY " + ((Process) helper.get(update)).pid());
        System.out.flush();
        System.in.read();
      }
    }
  }
}
