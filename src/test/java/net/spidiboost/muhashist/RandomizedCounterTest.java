package net.spidiboost.muhashist;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;

class RandomizedCounterTest {
  @Test
  void hundredThousandRandomizedRecordsHaveIndependentExactTotals() {
    String[] reasons = {
      "Читы",
      "Читер",
      "Cheats",
      "Тим чит",
      "Тиммейт читера",
      "Хранение читов",
      "Уклон от проверки",
      "Помеха проведению проверки",
      "FunPay",
      "Флуд"
    };
    int[] values = {7, 7, 7, 7, 7, 10, 10, 10, 0, 0};
    for (int seed = 0; seed < 50; seed++) {
      var random = new Random(seed);
      var run = new CounterTest.Run("2000");
      var names = new ArrayList<String>();
      long score = 0;
      for (int i = 0; i < 2000; i++) {
        int which = random.nextInt(reasons.length);
        String name = "Same" + random.nextInt(13);
        boolean own = random.nextInt(5) > 0, after = random.nextInt(4) > 0;
        String staff = own ? (random.nextBoolean() ? "SPIDIBOOST" : "SpidiBoost") : "SomeoneElse";
        int reversal = random.nextInt(5);
        run.ban(name, staff, reasons[which], after ? "2026-10-02 12:00" : "2026-09-30 23:59");
        if (reversal == 0) run.unban(name, "ManualAdmin");
        if (reversal == 1) run.unban(name, "ReallyWorld");
        if (own && after && reversal != 0 && values[which] > 0) {
          score += values[which];
          names.add(name);
        }
      }
      run.end();
      assertEquals(BigInteger.valueOf(score), run.result.tenths(), "seed=" + seed);
      assertEquals(names, run.result.names(), "seed=" + seed);
      assertEquals(names.size(), run.result.counted());
    }
  }
}
