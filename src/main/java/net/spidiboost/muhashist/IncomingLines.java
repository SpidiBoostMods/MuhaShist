package net.spidiboost.muhashist;

import java.util.ArrayDeque;
import java.util.function.Consumer;

public final class IncomingLines {
  private static final class Packet {
    final String text;
    int position;

    Packet(String text) {
      this.text = text;
    }
  }

  private final ArrayDeque<Packet> queue = new ArrayDeque<>();

  public void add(String text) {
    if (!text.isEmpty()) queue.addLast(new Packet(text));
  }

  public boolean empty() {
    return queue.isEmpty();
  }

  public void clear() {
    queue.clear();
  }

  public int drain(Consumer<String> consumer, int maximum, long nanos) {
    long stop = System.nanoTime() + nanos;
    int lines = 0;
    while (!queue.isEmpty() && lines < maximum) {
      Packet packet = queue.getFirst();
      int start = packet.position, end = start;
      while (end < packet.text.length()
          && packet.text.charAt(end) != '\n'
          && packet.text.charAt(end) != '\r') end++;
      packet.position = end;
      while (packet.position < packet.text.length()
          && (packet.text.charAt(packet.position) == '\n'
              || packet.text.charAt(packet.position) == '\r')) packet.position++;
      if (packet.position >= packet.text.length()) queue.removeFirst();
      if (end > start) {
        consumer.accept(packet.text.substring(start, end));
        lines++;
      }
      if ((lines & 15) == 0 && System.nanoTime() >= stop) break;
    }
    return lines;
  }
}
