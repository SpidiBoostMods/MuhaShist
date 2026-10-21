package net.spidiboost.muhashist.updates;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
import net.minecraft.client.gui.screen.Screen;

public final class UpdateBoard {
  public static final String VIEW = "spidiboost:update-view-v1:muhashist";
  private static final Set<Screen> attached = Collections.newSetFromMap(new WeakHashMap<>());

  private UpdateBoard() {}

  public static void initialize(MuhaUpdater updater) {
    var share = FabricLoader.getInstance().getObjectShare();
    ClientTickEvents.END_CLIENT_TICK.register(client -> publish(share, updater.status()));
    ScreenEvents.BEFORE_INIT.register(
        (client, screen, width, height) -> attach(screen, updater::status));
  }

  public static void publish(ObjectShare share, MuhaUpdater.Status state) {
    var view =
        Map.of(
            "id",
            "muhashist",
            "name",
            "MuhaShist",
            "installed",
            state.installed(),
            "latest",
            state.latest(),
            "state",
            state.state(),
            "status",
            text(state));
    if (!view.equals(share.get(VIEW))) share.put(VIEW, view);
  }

  public static String text(MuhaUpdater.Status state) {
    return switch (state.state()) {
      case "downloaded" -> "Скачано · применится после выхода";
      case "downloading" -> "Скачиваем обновление…";
      case "available" -> "Доступна версия " + state.latest();
      case "checking" -> "Проверяем обновление…";
      case "current" -> "Актуальная версия";
      case "unavailable" -> "Проверим позже";
      default -> "Установлена " + state.installed();
    };
  }

  public static void attach(Screen screen, Supplier<MuhaUpdater.Status> state) {
    if (!screen.getTitle().getString().equals("SpidiBoost — обновления")
        || attached.contains(screen)) return;
    try {
      Field field = rowsField(screen);
      if (field == null) return;
      if (!(field.get(screen) instanceof Supplier<?> supplier)) return;
      if (!(supplier.get() instanceof List<?> rows) || rows.isEmpty()) return;
      Class<?> type = rows.getFirst().getClass();
      Constructor<?> constructor =
          type.getDeclaredConstructor(String.class, String.class, String.class, String.class);
      Method id = type.getRecordComponents()[0].getAccessor();
      constructor.setAccessible(true);
      id.setAccessible(true);
      Supplier<List<?>> merged =
          () -> {
            Object original = supplier.get();
            if (!(original instanceof List<?> list)) return List.of();
            try {
              for (Object row : list) if ("muhashist".equals(id.invoke(row))) return list;
              var current = state.get();
              var result = new ArrayList<Object>(list);
              String version =
                  current.state().equals("downloaded") ? current.latest() : current.installed();
              result.add(constructor.newInstance("muhashist", "MuhaShist", version, text(current)));
              return List.copyOf(result);
            } catch (ReflectiveOperationException | RuntimeException e) {
              return list;
            }
          };
      field.set(screen, merged);
      attached.add(screen);
    } catch (ReflectiveOperationException | RuntimeException ignored) {
    }
  }

  private static Field rowsField(Screen screen) throws IllegalAccessException {
    for (Field field : screen.getClass().getDeclaredFields()) {
      if (!Supplier.class.isAssignableFrom(field.getType())) continue;
      field.setAccessible(true);
      if (!(field.get(screen) instanceof Supplier<?> source)
          || !(source.get() instanceof List<?> rows)
          || rows.isEmpty()) continue;
      Class<?> type = rows.getFirst().getClass();
      if (!type.isRecord()) continue;
      var fields = type.getRecordComponents();
      if (fields.length == 4
          && Arrays.stream(fields).allMatch(value -> value.getType() == String.class)) return field;
    }
    return null;
  }
}
