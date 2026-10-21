package net.spidiboost.muhashist.updates;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;
import net.fabricmc.loader.api.ObjectShare;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IsolationTest {
  @TempDir Path game;

  Path jar(Path file, String id, String payload) throws Exception {
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(new ZipEntry("fabric.mod.json"));
      zip.write(("{\"id\":\"" + id + "\"}").getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("data.txt"));
      zip.write(payload.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return file;
  }

  BatchPlan plan() throws Exception {
    Files.createDirectories(game.resolve("mods"));
    Files.createDirectories(game.resolve(".muhashist-updates"));
    Path old = jar(game.resolve("mods/renamed-old.jar"), "muhashist", "old");
    Path stage = jar(game.resolve(".muhashist-updates/new.jar"), "muhashist", "new");
    var change =
        new BatchPlan.Change(
            old,
            game.resolve("mods/muhashist-1.21.4-v1.2.2.jar"),
            stage,
            BatchInstall.hash(old),
            BatchInstall.hash(stage));
    return new BatchPlan(ProcessHandle.current().pid(), game, List.of(change));
  }

  BatchPlan with(BatchPlan p, Path old, Path next, Path stage) throws Exception {
    return new BatchPlan(
        p.pid(),
        p.game(),
        List.of(
            new BatchPlan.Change(
                old, next, stage, BatchInstall.hash(old), BatchInstall.hash(stage))));
  }

  @Test
  void onlyOwnRepositoryCanEnterTheCatalog() {
    assertEquals(1, ModCatalog.ALL.size());
    assertEquals("muhashist", ModCatalog.ALL.getFirst().id());
    assertTrue(ModCatalog.installed(Set.of("other", "sixubaafp", "spidicard")).isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ModCatalog("other", "Other", "Other", "other.properties"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ModCatalog("muhashist", "MuhaShist", "Other", "muhashist-update.properties"));
  }

  @Test
  void ownPlanRoundTripsWithoutLauncherCommandsOrOtherTargets() throws Exception {
    var p = plan();
    var bytes = new ByteArrayOutputStream();
    p.write(bytes);
    assertEquals(p, BatchPlan.read(new ByteArrayInputStream(bytes.toByteArray())));
    assertEquals(3, BatchPlan.class.getRecordComponents().length);
    assertThrows(IllegalArgumentException.class, () -> new BatchPlan(1, game, List.of()));
  }

  @Test
  void formerSharedProtocolIsRejected() throws Exception {
    var bytes = new ByteArrayOutputStream();
    new DataOutputStream(bytes).writeInt(0x53425531);
    assertThrows(
        IOException.class, () -> BatchPlan.read(new ByteArrayInputStream(bytes.toByteArray())));
    try (var input = BatchAgent.class.getResourceAsStream("BatchAgent.class")) {
      String code = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
      assertFalse(code.contains("java/lang/ProcessBuilder"));
      assertFalse(code.contains("java/net/http"));
    }
  }

  @Test
  void ownInstallPreservesOtherModsConfigsAndOtherUpdaterFiles() throws Exception {
    var p = plan();
    var c = p.changes().getFirst();
    Path other = jar(game.resolve("mods/Other.jar"), "other", "foreign");
    String hash = BatchInstall.hash(other);
    Files.createDirectories(game.resolve(".spidiboost-updates"));
    Files.writeString(game.resolve(".spidiboost-updates/settings.properties"), "other setting");
    ModsDownload.publish(p);
    assertTrue(Files.exists(c.target()));
    assertTrue(ModsDownload.owns(c));
    BatchInstall.install(p);
    assertFalse(Files.exists(c.target()));
    assertEquals(c.newHash(), BatchInstall.hash(c.destination()));
    assertEquals(hash, BatchInstall.hash(other));
    assertEquals(
        "other setting", Files.readString(game.resolve(".spidiboost-updates/settings.properties")));
    assertEquals(
        c.oldHash(),
        BatchInstall.hash(game.resolve(".muhashist-updates/backups/" + c.oldHash() + ".jar.bak")));
  }

  @Test
  void foreignOldJarCannotBeReplacedEvenWhenRenamed() throws Exception {
    var p = plan();
    var c = p.changes().getFirst();
    jar(c.target(), "spidicard", "old");
    var foreign = with(p, c.target(), c.destination(), c.staged());
    assertThrows(IOException.class, () -> BatchInstall.install(foreign));
    assertTrue(Files.exists(c.target()));
    assertFalse(Files.exists(c.destination()));
  }

  @Test
  void foreignArtifactAndForeignDestinationAreRejected() throws Exception {
    var p = plan();
    var c = p.changes().getFirst();
    assertThrows(
        IOException.class,
        () ->
            BatchInstall.validate(
                with(p, c.target(), game.resolve("mods/SpidiCard-1.21.4-1.2.2.jar"), c.staged())));
    jar(c.staged(), "sixubaafp", "new");
    assertThrows(
        IOException.class,
        () -> BatchInstall.validate(with(p, c.target(), c.destination(), c.staged())));
  }

  @Test
  void changedOldJarOrStageCannotBeInstalled() throws Exception {
    var p = plan();
    var c = p.changes().getFirst();
    byte[] original = Files.readAllBytes(c.target());
    jar(c.target(), "muhashist", "changed");
    assertThrows(IOException.class, () -> BatchInstall.install(p));
    Files.write(c.target(), original);
    jar(c.staged(), "muhashist", "changed");
    assertThrows(IOException.class, () -> BatchInstall.install(p));
    assertFalse(Files.exists(c.destination()));
  }

  @Test
  void userDestinationIsNeverOverwrittenOrDiscarded() throws Exception {
    var p = plan();
    var c = p.changes().getFirst();
    Files.writeString(c.destination(), "user file");
    assertThrows(IOException.class, () -> ModsDownload.publish(p));
    ModsDownload.discard(c);
    assertEquals("user file", Files.readString(c.destination()));
    assertTrue(Files.exists(c.target()));
  }

  @Test
  void stagesOutsideOwnFolderAreRejected() throws Exception {
    var p = plan();
    var c = p.changes().getFirst();
    Path stage = Files.copy(c.staged(), game.resolve("foreign-stage.jar"));
    assertThrows(
        IOException.class,
        () -> BatchInstall.validate(with(p, c.target(), c.destination(), stage)));
  }

  @Test
  void invalidBackupPreservesBothLoadedAndDownloadedVersions() throws Exception {
    var p = plan();
    var c = p.changes().getFirst();
    ModsDownload.publish(p);
    Files.createDirectories(game.resolve(".muhashist-updates/backups"));
    Files.writeString(
        game.resolve(".muhashist-updates/backups/" + c.oldHash() + ".jar.bak"), "foreign backup");
    assertThrows(IOException.class, () -> BatchInstall.install(p));
    assertEquals(c.oldHash(), BatchInstall.hash(c.target()));
    assertEquals(c.newHash(), BatchInstall.hash(c.destination()));
  }

  @Test
  void viewPublishingCannotClaimCoordinatorOrChangePeerRows() {
    var data = new ConcurrentHashMap<String, Object>();
    Object owner = new Object();
    data.put("spidiboost:update-coordinator-v1", owner);
    data.put("spidiboost:update-view-v1:other", Map.of("state", "current"));
    var share =
        (ObjectShare)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {ObjectShare.class},
                (instance, method, args) ->
                    switch (method.getName()) {
                      case "get" -> data.get(args[0]);
                      case "put" -> {
                        assertEquals(UpdateBoard.VIEW, args[0]);
                        yield data.put((String) args[0], args[1]);
                      }
                      default ->
                          throw new AssertionError("Updater coordination is not a view operation");
                    });
    UpdateBoard.publish(share, new MuhaUpdater.Status("1.2.1", "1.2.2", "downloaded"));
    assertSame(owner, data.get("spidiboost:update-coordinator-v1"));
    assertEquals(Map.of("state", "current"), data.get("spidiboost:update-view-v1:other"));
    assertInstanceOf(Map.class, data.get(UpdateBoard.VIEW));
  }
}
