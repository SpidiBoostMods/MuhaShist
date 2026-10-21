package net.spidiboost.muhashist.updates;

final class UpdateReminders {
  private int shown;
  private long next;

  boolean due(long now, boolean available) {
    return available && shown < 3 && now >= next;
  }

  void shown(long now) {
    shown++;
    next = now + 10 * 60 * 1000L;
  }
}
