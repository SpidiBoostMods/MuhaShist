package net.spidiboost.muhashist.updates;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class ReserveUpdates implements PreLaunchEntrypoint {
  public static final String PREFERRED = "spidiboost:update-preferred-muhashist-v1";

  @Override
  public void onPreLaunch() {
    reserve(FabricLoader.getInstance().getObjectShare());
  }

  public static void reserve(net.fabricmc.loader.api.ObjectShare share) {
    var preferred = new AtomicReference<Runnable>();
    Runnable relay =
        () -> {
          Runnable owner = preferred.get();
          if (owner != null) owner.run();
        };
    share.put(SharedUpdater.OWNER, relay);
    share.put(SharedUpdater.RESERVATION, List.of(relay, new AtomicReference<Runnable>(relay)));
    share.put("spidiboost:update-preferred-sixubaafp-v3", new AtomicReference<Runnable>(relay));
    share.put(PREFERRED, preferred);
  }
}
