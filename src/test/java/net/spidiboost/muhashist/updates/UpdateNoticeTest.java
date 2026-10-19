package net.spidiboost.muhashist.updates;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UpdateNoticeTest {
 @Test void reportsActualUpdatedMod() {
  String message=UpdateNotice.downloaded(List.of(Path.of("6ubaaFP-1.21.4-1.8.1.jar")));
  assertTrue(message.contains("6ubaaFP"));assertFalse(message.contains("MuhaShist"));assertFalse(message.contains("SpidiCard"));
 }
 @Test void listsEachUpdatedModOnce() {
  String message=UpdateNotice.downloaded(List.of(Path.of("SpidiCard-1.21.4-1.8.1.jar"),Path.of("6ubaaFP-1.21.4-1.8.1.jar")));
  assertTrue(message.contains("SpidiCard, 6ubaaFP"));
 }
 @Test void emptyPlanDoesNotClaimAnUpdateAndUnknownArtifactIsNamed() {
  assertEquals("Новых обновлений для скачивания нет.",UpdateNotice.downloaded(List.of()));
  assertTrue(UpdateNotice.downloaded(List.of(Path.of("QA-1.21.4-1.0.1.jar"))).contains("QA-1.21.4-1.0.1.jar"));
 }
}
