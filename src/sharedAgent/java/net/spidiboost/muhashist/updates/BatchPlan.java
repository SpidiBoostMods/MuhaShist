package net.spidiboost.muhashist.updates;

import java.io.*;
import java.nio.file.Path;
import java.util.List;

public record BatchPlan(long pid, Path game, List<Change> changes) {
  public record Change(
      Path target, Path destination, Path staged, String oldHash, String newHash) {}

  public BatchPlan {
    changes = List.copyOf(changes);
    if (pid <= 0 || changes.size() != 1)
      throw new IllegalArgumentException("One MuhaShist update required");
  }

  public void write(OutputStream stream) throws IOException {
    var out = new DataOutputStream(stream);
    out.writeInt(0x4d535531);
    out.writeLong(pid);
    out.writeUTF(game.toString());
    var change = changes.getFirst();
    for (String value :
        List.of(
            change.target().toString(),
            change.destination().toString(),
            change.staged().toString(),
            change.oldHash(),
            change.newHash())) out.writeUTF(value);
    out.flush();
  }

  public static BatchPlan read(InputStream stream) throws IOException {
    var in = new DataInputStream(stream);
    if (in.readInt() != 0x4d535531) throw new IOException("Unknown MuhaShist update protocol");
    long pid = in.readLong();
    Path game = Path.of(in.readUTF());
    var change =
        new Change(
            Path.of(in.readUTF()),
            Path.of(in.readUTF()),
            Path.of(in.readUTF()),
            in.readUTF(),
            in.readUTF());
    if (pid <= 0) throw new IOException("Invalid game process");
    return new BatchPlan(pid, game, List.of(change));
  }
}
