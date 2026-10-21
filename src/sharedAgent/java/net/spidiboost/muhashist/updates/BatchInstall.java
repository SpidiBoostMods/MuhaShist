package net.spidiboost.muhashist.updates;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public final class BatchInstall {
  private BatchInstall() {}

  public static String hash(Path file) throws IOException {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(file)) {
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static void validate(BatchPlan plan) throws IOException {
    Path game = plan.game().toRealPath();
    Path mods = game.resolve("mods").toRealPath();
    Path directory = game.resolve(".muhashist-updates").toRealPath();
    if (!mods.startsWith(game)
        || !directory.getParent().equals(game)
        || Files.isSymbolicLink(game.resolve(".muhashist-updates")))
      throw new IOException("Foreign MuhaShist update directory");
    var change = plan.changes().getFirst();
    if (Files.isSymbolicLink(change.target())
        || Files.isSymbolicLink(change.destination())
        || Files.isSymbolicLink(change.staged())
        || !change.target().toRealPath().getParent().equals(mods)
        || !change.destination().getParent().toRealPath().equals(mods)
        || !change.staged().toRealPath().getParent().equals(directory)
        || !change.target().getFileName().toString().endsWith(".jar")
        || !change
            .destination()
            .getFileName()
            .toString()
            .matches("muhashist-1\\.21\\.4-v[0-9]+\\.[0-9]+\\.[0-9]+\\.jar")
        || change.target().toRealPath().equals(change.destination().toAbsolutePath().normalize())
        || !change.oldHash().matches("[a-f0-9]{64}")
        || !change.newHash().matches("[a-f0-9]{64}")
        || !hash(change.target()).equals(change.oldHash())
        || !hash(change.staged()).equals(change.newHash())
        || (Files.exists(change.destination()) && !ModsDownload.owns(change)))
      throw new IOException("Invalid or changed MuhaShist update");
    identity(change.target());
    identity(change.staged());
  }

  private static void identity(Path file) throws IOException {
    try (var zip = new ZipFile(file.toFile())) {
      var metadata = zip.getEntry("fabric.mod.json");
      if (metadata == null) throw new IOException("Missing MuhaShist metadata");
      byte[] data;
      try (var input = zip.getInputStream(metadata)) {
        data = input.readNBytes(65537);
      }
      if (data.length > 65536
          || !Pattern.compile("\"id\"\\s*:\\s*\"muhashist\"")
              .matcher(new String(data, StandardCharsets.UTF_8))
              .find()) throw new IOException("Only MuhaShist may be replaced");
    }
  }

  public static void install(BatchPlan plan) throws IOException {
    validate(plan);
    var change = plan.changes().getFirst();
    Path directory = plan.game().resolve(".muhashist-updates/backups");
    Files.createDirectories(directory);
    if (Files.isSymbolicLink(directory)
        || !directory.toRealPath().startsWith(plan.game().toRealPath()))
      throw new IOException("Foreign backup directory");
    Path backup = directory.resolve(change.oldHash() + ".jar.bak");
    if (Files.isSymbolicLink(backup)) throw new IOException("Unsafe MuhaShist backup");
    if (!Files.exists(backup)) Files.copy(change.target(), backup);
    if (!hash(backup).equals(change.oldHash())) throw new IOException("Invalid MuhaShist backup");
    try {
      validate(plan);
      ModsDownload.publish(plan);
      if (!hash(change.target()).equals(change.oldHash()) || !ModsDownload.owns(change))
        throw new IOException("MuhaShist changed before commit");
      Files.delete(change.target());
    } catch (IOException | RuntimeException e) {
      ModsDownload.discard(change);
      throw e;
    }
    try {
      ModsDownload.forget(change);
      Files.deleteIfExists(change.staged());
    } catch (IOException ignored) {
    }
  }
}
