package net.spidiboost.muhashist.updates;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.loader.api.ObjectShare;
import org.junit.jupiter.api.Test;

class CoordinatorTest {
  private ObjectShare share(Map<String, Object> map) {
    return (ObjectShare)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[] {ObjectShare.class},
            (o, m, a) ->
                switch (m.getName()) {
                  case "get" -> map.get(a[0]);
                  case "put" -> map.put((String) a[0], a[1]);
                  case "putIfAbsent" -> map.putIfAbsent((String) a[0], a[1]);
                  default -> throw new UnsupportedOperationException(m.getName());
                });
  }

  @Test
  void bothPrelaunchOrdersKeepExactlyOnePreferredCoordinator() {
    for (boolean oldFirst : List.of(true, false)) {
      var values = new ConcurrentHashMap<String, Object>();
      var share = share(values);
      Runnable old = () -> fail("older updater won");
      if (oldFirst) {
        share.put(SharedUpdater.OWNER, old);
        share.put(SharedUpdater.RESERVATION, List.of(old, new AtomicReference<Runnable>(old)));
      }
      ReserveUpdates.reserve(share);
      assertNotNull(share.putIfAbsent(SharedUpdater.OWNER, old));
      @SuppressWarnings("unchecked")
      var blocked =
          (AtomicReference<Runnable>) share.get("spidiboost:update-preferred-sixubaafp-v3");
      assertFalse(blocked.compareAndSet(null, old));
      var calls = new AtomicInteger();
      assertTrue(SharedUpdater.claim(share, calls::incrementAndGet));
      assertFalse(SharedUpdater.claim(share, old));
      ((Runnable) share.get(SharedUpdater.OWNER)).run();
      assertEquals(1, calls.get());
    }
  }

  @Test
  void canonicalMuhaArtifactMetadataAndEndpoint() throws Exception {
    var mod =
        ModCatalog.ALL.stream().filter(m -> m.id().equals("muhashist")).findFirst().orElseThrow();
    assertEquals("muhashist-1.21.4-v1.0.0.jar", mod.artifact("1.0.0"));
    assertEquals(
        "https://github.com/SpidiBoostMods/MuhaShist/releases/latest/download/muhashist-update.properties",
        mod.latest().toString());
    var release =
        Release.parse(
            mod,
            "version=1.0.1\nminecraft=1.21.4\nartifact=muhashist-1.21.4-v1.0.1.jar\nsha256="
                + "a".repeat(64));
    assertTrue(release.newerThan("1.0.0"));
    assertFalse(release.newerThan("1.0.1"));
    assertTrue(release.download().toString().endsWith("/v1.0.1/muhashist-1.21.4-v1.0.1.jar"));
  }
}
