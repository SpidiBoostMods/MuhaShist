package net.spidiboost.muhashist.updates;

import java.net.URI;
import java.util.*;

public record ModCatalog(String id, String name, String repository, String manifest) {
  public static final List<ModCatalog> ALL =
      List.of(new ModCatalog("muhashist", "MuhaShist", "MuhaShist", "muhashist-update.properties"));

  public String base() {
    return "https://github.com/SpidiBoostMods/" + repository;
  }

  public ModCatalog {
    if (!id.equals("muhashist")
        || !name.equals("MuhaShist")
        || !repository.equals("MuhaShist")
        || !manifest.equals("muhashist-update.properties"))
      throw new IllegalArgumentException("MuhaShist updates only itself");
  }

  public URI latest() {
    return URI.create(base() + "/releases/latest/download/" + manifest);
  }

  public String artifact(String version) {
    return "muhashist-1.21.4-v" + version + ".jar";
  }

  public static List<ModCatalog> installed(Set<String> ids) {
    return ALL.stream().filter(m -> ids.contains(m.id)).toList();
  }
}
