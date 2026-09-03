package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerInfo.LogFiles;
import tools.Fs;

final class LogFilesTest{
  private static Path logsDir(Path project){ return project.resolve(".out").resolve("logs"); }
  @Test void aRotatedLogNameDecodesBackToItsStampedInstant(@TempDir Path project){
    var stamp= Instant.parse("2026-09-04T12:34:56.789Z");
    var file= logsDir(project).resolve("_base").resolve("log$20260904_123456_789Z.log");
    Fs.writeUtf8(file,"hello\n");
    assertEquals(stamp,LogFiles.decode(file).orElseThrow());
  }
  @Test void aLiveLogWithNoStampInItsNameDecodesToNothing(@TempDir Path project){
    var file= logsDir(project).resolve("_base").resolve("log.log");
    Fs.writeUtf8(file,"hello\n");
    assertTrue(LogFiles.decode(file).isEmpty());
  }
  @Test void listingFindsEveryLogUnderTheProjectAndSortsNewestFirst(@TempDir Path project){
    Fs.writeUtf8(logsDir(project).resolve("_base").resolve("log$20260901_000000_000Z.log"),"old\n");
    Fs.writeUtf8(logsDir(project).resolve("_base").resolve("log$20260903_000000_000Z.log"),"new\n");
    Fs.writeUtf8(logsDir(project).resolve("err$20260902_000000_000Z.log"),"err\n");
    var found= LogFiles.list(project);
    assertEquals(3,found.size());
    assertEquals("log$20260903_000000_000Z.log",found.get(0).path().getFileName().toString());
    assertEquals("err$20260902_000000_000Z.log",found.get(1).path().getFileName().toString());
    assertEquals("log$20260901_000000_000Z.log",found.get(2).path().getFileName().toString());
  }
  @Test void aMissingLogsDirectoryListsAsEmpty(@TempDir Path project){
    assertTrue(LogFiles.list(project).isEmpty());
  }
  @Test void aLiveLogFallsBackToItsFileSystemModifiedTime(@TempDir Path project){
    var file= logsDir(project).resolve("_base").resolve("log.log");
    Fs.writeUtf8(file,"hello\n");
    var entry= LogFiles.list(project).get(0);
    assertTrue(ChronoUnit.SECONDS.between(entry.when(),Instant.now()) < 60);
  }
}
