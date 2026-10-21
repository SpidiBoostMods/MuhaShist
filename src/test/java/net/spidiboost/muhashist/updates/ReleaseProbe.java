package net.spidiboost.muhashist.updates;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipFile;

public final class ReleaseProbe {
  public static void main(String[] args) throws Exception {
    var http = new GitHubDownload();
    var release =
        Release.parse(
            MuhaUpdater.MOD,
            new String(http.get(MuhaUpdater.MOD.latest(), 16384), StandardCharsets.UTF_8));
    if (!release.version().equals(args[0]))
      throw new IllegalStateException("Unexpected latest release");
    Path dir = Path.of("build/release-verification");
    Files.createDirectories(dir);
    Path jar = dir.resolve(release.artifact());
    Files.write(jar, http.get(release.download(), 32 * 1024 * 1024));
    Artifact.validate(jar, release);
    try (var zip = new ZipFile(jar.toFile())) {
      if (zip.stream()
          .anyMatch(
              entry ->
                  entry.getName().contains("/qa/")
                      || entry.getName().contains("NativeUpdateProbe")
                      || entry.getName().contains("runtimeProbe")))
        throw new IllegalStateException("Fixture found in release");
      try (var input =
          zip.getInputStream(
              zip.getEntry("net/spidiboost/muhashist/updates/GitHubDownload.class"))) {
        if (new String(input.readAllBytes(), StandardCharsets.ISO_8859_1).contains("java/net/http"))
          throw new IllegalStateException("Old HTTP client found in release");
      }
    }
    System.out.println(
        "PASS GitHub manifest, canonical download, SHA-256, Fabric identity, clean artifact and"
            + " synchronous HTTPS: "
            + release.version()
            + " "
            + release.sha256());
  }
}
