package net.spidiboost.muhashist.updates;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;

public final class NativeUpdateProbe {
  public static void prepare(Path game) throws Exception {
    Path old = game.resolve("mods/muhashist-1.21.4-v0.0.0.jar");
    Path next = game.resolve("mods/muhashist-1.21.4-v0.0.1.jar");
    Files.deleteIfExists(next);
    try (var output = new ZipOutputStream(Files.newOutputStream(old))) {
      output.putNextEntry(new ZipEntry("old.txt"));
      output.write("original".getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
    }
    byte[] data;
    try (var bytes = new ByteArrayOutputStream();
        var output = new ZipOutputStream(bytes)) {
      output.putNextEntry(new ZipEntry("fabric.mod.json"));
      output.write(
          ("{\"id\":\"muhashist\",\"version\":\"0.0.1\",\"name\":\"spidiboost.MuhaShist\","
               + "\"authors\":[\"SpidiBoost\"],\"environment\":\"client\",\"depends\":{\"minecraft\":\"1.21.4\"}}")
              .getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
      output.putNextEntry(new ZipEntry("muhashist-shared-update-agent.jar"));
      output.write(new byte[] {1});
      output.closeEntry();
      output.finish();
      data = bytes.toByteArray();
    }
    String sha =
        java.util.HexFormat.of()
            .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
    byte[] manifest =
        ("version=0.0.1\nminecraft=1.21.4\nartifact=muhashist-1.21.4-v0.0.1.jar\nsha256=" + sha)
            .getBytes(StandardCharsets.UTF_8);
    try (var updater =
        new MuhaUpdater(
            game,
            "0.0.0",
            false,
            (uri, limit) -> uri.equals(MuhaUpdater.MOD.latest()) ? manifest : data,
            new ExitUpdate(game, old))) {
      updater.automatic(false);
      if (!updater
              .request(MuhaUpdater.Mode.ENTRY)
              .get(20, TimeUnit.SECONDS)
              .state()
              .equals("available")
          || Files.exists(next)) throw new AssertionError("Off downloaded");
      if (!updater
              .request(MuhaUpdater.Mode.NOW)
              .get(20, TimeUnit.SECONDS)
              .state()
              .equals("downloaded")
          || updater.automatic()
          || !Files.exists(old)
          || !Files.exists(next))
        throw new AssertionError("One-time update did not preserve the running game");
    }
  }
}
