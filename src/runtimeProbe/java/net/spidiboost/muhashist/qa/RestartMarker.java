package net.spidiboost.muhashist.qa;

import java.nio.file.*;

public final class RestartMarker {
  public static void main(String[] args) throws Exception {
    Path result = Path.of(args[0]), old = Path.of(args[1]), next = Path.of(args[2]);
    if (Files.exists(old) || !Files.isRegularFile(next))
      throw new IllegalStateException("Exit transaction incomplete");
    if (!Files.readString(Path.of(args[3])).equals("saved"))
      throw new IllegalStateException("Save hook was not called");
    Files.writeString(
        result,
        "PASS detached updater waited for Minecraft, replaced the old QA JAR, then requested"
            + " restart\n");
  }
}
