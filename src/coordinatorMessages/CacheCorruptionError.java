package coordinatorMessages;

import java.nio.file.Path;
import java.util.List;

import metaParser.Message;
import metaParser.PrettyFileName;
import utils.Join;

  /**
  CacheCorruptionError: problems in the fearless project folder, 
  - either in generated outputs under ".fearless_out"
  - or in the other files of the project, but after the initial verification checks.
  These require multiple actors or abnormal termination:
  - another process touched those files
  - a previous run stopped mid-build after deleting (some of the) outputs
  - the filesystem returned partial writes / stale directory state
  */
@SuppressWarnings("serial")
public final class CacheCorruptionError extends RuntimeException{
  private CacheCorruptionError(String msg){ super(msg); }
  public static CacheCorruptionError missingPkgApiFile(Path apiJson){
    return new CacheCorruptionError("""
Build cache is missing a generated package API file.
That metadata should be stored in:
  %s
""".formatted(PrettyFileName.displayFileName(apiJson.toUri())));
  }
  public static CacheCorruptionError invalidVirtualizationMap(Path mapJson, String parseErr){
    return new CacheCorruptionError("""
Build cache contains an invalid virtualization map.

Fearless tried to read:
  %s
but the file content is not valid.

Parse error:
  %s
      """.formatted(PrettyFileName.displayFileName(mapJson.toUri()), parseErr));
  }
  public static CacheCorruptionError canNotFindExpected(Path diskZip, List<String> steps, String entryName){
    return new CacheCorruptionError("""
Can not find entry in zip (that was found before)
Zip:
  %s
Steps:
  %s
Entry name:
  %s
""".formatted(PrettyFileName.displayFileName(diskZip.toUri()),
      Join.of(steps.stream().map(Message::displayString),"[",", ","]", "<no steps>"),
      Message.displayString(entryName)));
  }
}