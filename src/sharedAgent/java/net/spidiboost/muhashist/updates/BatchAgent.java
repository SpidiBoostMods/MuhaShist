package net.spidiboost.muhashist.updates;

import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public final class BatchAgent {
  public static void main(String[] args) {
    BatchPlan plan = null;
    try {
      plan = BatchPlan.read(System.in);
      BatchInstall.validate(plan);
      Path directory = plan.game().resolve(".muhashist-updates");
      try (var channel =
              FileChannel.open(
                  directory.resolve("update.lock"),
                  StandardOpenOption.CREATE,
                  StandardOpenOption.WRITE);
          var lock = channel.tryLock()) {
        if (lock == null) throw new IllegalStateException("MuhaShist update already pending");
        ProcessHandle parent = ProcessHandle.of(plan.pid()).orElseThrow();
        System.out.println("READY");
        System.out.flush();
        parent.onExit().get(7, TimeUnit.DAYS);
        BatchInstall.install(plan);
        Files.writeString(
            directory.resolve("update.log"),
            "MuhaShist update installed.\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      }
    } catch (Exception e) {
      if (plan != null)
        try {
          Files.writeString(
              plan.game().resolve(".muhashist-updates/update.log"),
              "MuhaShist update failed: " + e.getClass().getSimpleName() + ". Backups preserved.\n",
              StandardOpenOption.CREATE,
              StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
      if (plan != null)
        for (var change : plan.changes())
          try {
            ModsDownload.discard(change);
          } catch (Exception ignored) {
          }
      System.out.println("ERROR");
      System.out.flush();
      System.exit(1);
    }
  }
}
