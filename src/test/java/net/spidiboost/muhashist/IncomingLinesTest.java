package net.spidiboost.muhashist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class IncomingLinesTest {
  @Test
  void chunksPreserveOrderWithoutBlockingAnEntireReply() {
    var input = new IncomingLines();
    input.add("A\r\nB\nC\rD");
    var output = new ArrayList<String>();
    assertEquals(2, input.drain(output::add, 2, 1_000_000_000));
    assertEquals(List.of("A", "B"), output);
    assertFalse(input.empty());
    input.drain(output::add, 99, 1_000_000_000);
    assertEquals(List.of("A", "B", "C", "D"), output);
    assertTrue(input.empty());
  }

  @Test
  void hundredThousandQueuedLinesAndCancellation() {
    var input = new IncomingLines();
    for (int i = 0; i < 100000; i++) input.add("line" + i);
    int[] next = {0};
    while (!input.empty()) {
      int count =
          input.drain(
              value -> {
                assertEquals("line" + next[0], value);
                next[0]++;
              },
              500,
              1_000_000_000);
      assertTrue(count <= 500);
    }
    assertEquals(100000, next[0]);
    input.add("stale");
    input.clear();
    assertTrue(input.empty());
  }

  @Test
  void zeroBudgetYieldsAfterABoundedSmallBatchAndBlankPacketsAreSafe() {
    var input = new IncomingLines();
    input.add("\n\r\n");
    input.drain(v -> fail(), 1, 0);
    assertTrue(input.empty());
    for (int i = 0; i < 10000; i++) input.add("A");
    assertTrue(input.drain(v -> {}, 3000, 0) <= 16);
    assertFalse(input.empty());
  }
}
