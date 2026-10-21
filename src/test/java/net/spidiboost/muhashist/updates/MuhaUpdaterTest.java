package net.spidiboost.muhashist.updates;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuhaUpdaterTest {
  @TempDir Path game;

  byte[] manifest(String version) {
    return ("version="
            + version
            + "\nminecraft=1.21.4\nartifact="
            + MuhaUpdater.MOD.artifact(version)
            + "\nsha256="
            + "a".repeat(64))
        .getBytes(StandardCharsets.UTF_8);
  }

  MuhaUpdater.Status check(MuhaUpdater updater, MuhaUpdater.Mode mode) throws Exception {
    return updater.request(mode).get(10, TimeUnit.SECONDS);
  }

  @Test
  void offOnlyChecksMuhaAndNeverChangesFamilyPreferences() throws Exception {
    RestartPolicy.set(game, true);
    String family = Files.readString(RestartPolicy.file(game));
    AutoDownload.set(game, false);
    var calls = new AtomicInteger();
    try (var updater =
        new MuhaUpdater(
            game,
            "1.1.3",
            false,
            (uri, limit) -> {
              assertEquals(MuhaUpdater.MOD.latest(), uri);
              calls.incrementAndGet();
              return manifest("1.2.0");
            },
            (release, bytes) -> fail("off downloaded an update"))) {
      var state = check(updater, MuhaUpdater.Mode.ENTRY);
      assertEquals("available", state.state());
      assertTrue(state.outdated());
      assertEquals("https://github.com/SpidiBoostMods/MuhaShist/releases/latest", state.link());
      assertEquals(1, calls.get());
      assertEquals(family, Files.readString(RestartPolicy.file(game)));
      assertFalse(Files.exists(game.resolve("mods")));
    }
  }

  @Test
  void automaticDownloadsOnlyAtEntryAndOnlyOnceForTheSameRelease() throws Exception {
    AtomicInteger manifests = new AtomicInteger(),
        downloads = new AtomicInteger(),
        installed = new AtomicInteger();
    try (var updater =
        new MuhaUpdater(
            game,
            "1.1.3",
            false,
            (uri, limit) -> {
              if (uri.equals(MuhaUpdater.MOD.latest())) {
                manifests.incrementAndGet();
                return manifest("1.2.0");
              }
              assertEquals(
                  "https://github.com/SpidiBoostMods/MuhaShist/releases/download/v1.2.0/muhashist-1.21.4-v1.2.0.jar",
                  uri.toString());
              downloads.incrementAndGet();
              return new byte[] {1, 2, 3};
            },
            (release, bytes) -> {
              installed.incrementAndGet();
              assertArrayEquals(new byte[] {1, 2, 3}, bytes);
            })) {
      assertEquals("available", check(updater, MuhaUpdater.Mode.STATUS).state());
      assertEquals("available", check(updater, MuhaUpdater.Mode.RETRY).state());
      assertEquals(0, downloads.get());
      assertEquals("downloaded", check(updater, MuhaUpdater.Mode.ENTRY).state());
      assertEquals("downloaded", check(updater, MuhaUpdater.Mode.ENTRY).state());
      assertEquals("downloaded", check(updater, MuhaUpdater.Mode.NOW).state());
      assertEquals(1, installed.get());
      assertEquals(1, downloads.get());
      assertEquals(5, manifests.get());
    }
  }

  @Test
  void nowIsOneTimePermissionAndDoesNotEnableTheNextRelease() throws Exception {
    AutoDownload.set(game, false);
    var latest = new AtomicReference<>("1.2.0");
    var downloads = new AtomicInteger();
    var installs = new AtomicInteger();
    try (var updater =
        new MuhaUpdater(
            game,
            "1.1.3",
            false,
            (uri, limit) -> {
              if (uri.equals(MuhaUpdater.MOD.latest())) return manifest(latest.get());
              downloads.incrementAndGet();
              return new byte[] {1};
            },
            (release, bytes) -> installs.incrementAndGet())) {
      assertEquals("downloaded", check(updater, MuhaUpdater.Mode.NOW).state());
      assertFalse(updater.automatic());
      assertFalse(AutoDownload.enabled(game));
      latest.set("1.2.1");
      assertEquals("available", check(updater, MuhaUpdater.Mode.ENTRY).state());
      assertEquals("available", check(updater, MuhaUpdater.Mode.RETRY).state());
      assertEquals("available", check(updater, MuhaUpdater.Mode.STATUS).state());
      assertEquals(1, downloads.get());
      assertEquals(1, installs.get());
    }
  }

  @Test
  void failedOneTimeDownloadDoesNotAuthorizeATimerOrAFutureRelease() throws Exception {
    AutoDownload.set(game, false);
    AtomicInteger downloads = new AtomicInteger();
    try (var updater =
        new MuhaUpdater(
            game,
            "1.1.3",
            false,
            (uri, limit) -> {
              if (uri.equals(MuhaUpdater.MOD.latest())) return manifest("1.2.0");
              downloads.incrementAndGet();
              throw new IOException("connection reset");
            },
            (release, bytes) -> fail("Failed download installed"))) {
      assertEquals("unavailable", check(updater, MuhaUpdater.Mode.NOW).state());
      assertEquals("available", check(updater, MuhaUpdater.Mode.RETRY).state());
      assertEquals("available", check(updater, MuhaUpdater.Mode.ENTRY).state());
      assertEquals(1, downloads.get());
      assertFalse(updater.automatic());
    }
  }

  @Test
  void turningOffDuringDownloadPreventsAutomaticInstallation() throws Exception {
    var started = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    try (var updater =
        new MuhaUpdater(
            game,
            "1.1.3",
            false,
            (uri, limit) -> {
              if (uri.equals(MuhaUpdater.MOD.latest())) return manifest("1.2.0");
              started.countDown();
              assertTrue(resume.await(5, TimeUnit.SECONDS));
              return new byte[] {1};
            },
            (release, bytes) -> fail("Automatic update installed after off"))) {
      var pending = updater.request(MuhaUpdater.Mode.ENTRY);
      assertTrue(started.await(5, TimeUnit.SECONDS));
      updater.automatic(false);
      resume.countDown();
      assertEquals("available", pending.get(5, TimeUnit.SECONDS).state());
    } finally {
      resume.countDown();
    }
  }

  @Test
  void notificationsAreCappedPerJvmSessionNotPerJoinOrRelease() throws Exception {
    AutoDownload.set(game, false);
    var latest = new AtomicReference<>("1.2.0");
    try (var updater =
        new MuhaUpdater(
            game,
            "1.1.3",
            false,
            (uri, limit) -> manifest(latest.get()),
            (release, bytes) -> fail("off"))) {
      check(updater, MuhaUpdater.Mode.ENTRY);
      for (int i = 0; i < 3; i++) {
        assertTrue(updater.remind(i * 600000L));
        assertFalse(updater.remind(i * 600000L + 1));
        check(updater, MuhaUpdater.Mode.ENTRY);
      }
      latest.set("1.2.1");
      check(updater, MuhaUpdater.Mode.STATUS);
      assertFalse(updater.remind(Long.MAX_VALUE / 2));
    }
    try (var updater =
        new MuhaUpdater(
            game,
            "1.1.3",
            false,
            (uri, limit) -> manifest("1.2.1"),
            (release, bytes) -> fail("off"))) {
      check(updater, MuhaUpdater.Mode.ENTRY);
      assertTrue(updater.remind(0));
    }
  }

  @Test
  void currentNewerAndDevelopmentVersionsNeverDownloadOrRemind() throws Exception {
    AutoDownload.set(game, false);
    for (String version : List.of("1.2.0", "1.2.1"))
      try (var updater =
          new MuhaUpdater(
              game,
              version,
              false,
              (uri, limit) -> {
                assertEquals(MuhaUpdater.MOD.latest(), uri);
                return manifest("1.2.0");
              },
              (release, bytes) -> fail("No new version"))) {
        assertEquals("current", check(updater, MuhaUpdater.Mode.NOW).state());
        assertFalse(updater.remind(0));
      }
    try (var updater =
        new MuhaUpdater(
            game,
            "1.2.0",
            true,
            (uri, limit) -> {
              fail("Development contacted GitHub");
              return null;
            },
            (release, bytes) -> fail("Development installed"))) {
      assertEquals("development", check(updater, MuhaUpdater.Mode.NOW).state());
      assertFalse(updater.remind(0));
    }
  }

  @Test
  void concurrentTriggersCoalesceAndNeverFetchInParallel() throws Exception {
    var started = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var calls = new AtomicInteger();
    try (var updater =
        new MuhaUpdater(
            game,
            "1.2.0",
            false,
            (uri, limit) -> {
              calls.incrementAndGet();
              started.countDown();
              assertTrue(resume.await(5, TimeUnit.SECONDS));
              return manifest("1.2.0");
            },
            (release, bytes) -> fail("No new version"))) {
      var first = updater.request(MuhaUpdater.Mode.ENTRY);
      assertTrue(started.await(5, TimeUnit.SECONDS));
      for (int i = 0; i < 10000; i++) assertSame(first, updater.request(MuhaUpdater.Mode.ENTRY));
      var once = updater.request(MuhaUpdater.Mode.NOW);
      resume.countDown();
      first.get(5, TimeUnit.SECONDS);
      once.get(5, TimeUnit.SECONDS);
      assertEquals(2, calls.get());
    } finally {
      resume.countDown();
    }
  }

  @Test
  void settingsAreProductSpecificAndSurviveReload() throws Exception {
    RestartPolicy.set(game, false);
    try (var updater =
        new MuhaUpdater(game, "1.1.3", true, (uri, limit) -> null, (release, bytes) -> {})) {
      assertTrue(updater.automatic());
      updater.automatic(false);
      assertFalse(AutoDownload.enabled(game));
      updater.automatic(true);
      assertTrue(AutoDownload.enabled(game));
      assertFalse(RestartPolicy.enabled(game));
    }
    try (var updater =
        new MuhaUpdater(game, "1.1.3", true, (uri, limit) -> null, (release, bytes) -> {})) {
      assertTrue(updater.automatic());
    }
  }
}
