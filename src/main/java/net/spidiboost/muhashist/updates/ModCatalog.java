package net.spidiboost.muhashist.updates;

import java.net.URI;
import java.util.*;

public record ModCatalog(String id, String name, String repository, String manifest) {
  public static final List<ModCatalog> ALL =
      List.of(
          new ModCatalog("spidicard", "SpidiCard", "SpidiCard", "spidicard-update.properties"),
          new ModCatalog("spidiban", "SpidiBan", "SpidiBan", "spidiban-update.properties"),
          new ModCatalog("sixubaafp", "6ubaaFP", "6ubaaFP", "sixubaafp-update.properties"),
          new ModCatalog(
              "spidiboostlittlepet", "LittlePet", "LittlePet", "littlepet-update.properties"),
          new ModCatalog("muhashist", "MuhaShist", "MuhaShist", "muhashist-update.properties"));

  public String base() {
    return "https://github.com/SpidiBoostMods/" + repository;
  }

  public URI latest() {
    return URI.create(base() + "/releases/latest/download/" + manifest);
  }

  public String artifact(String version) {
    return id.equals("muhashist")
        ? "muhashist-1.21.4-v" + version + ".jar"
        : name + "-1.21.4-" + version + ".jar";
  }

  public static List<ModCatalog> installed(Set<String> ids) {
    return ALL.stream().filter(m -> ids.contains(m.id)).toList();
  }
}
