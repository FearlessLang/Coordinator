package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipInputStream;

import userMessages.Violation;
import userMessages.Report;
import tools.Fs;
import tools.ReadZip;

final class ZipLocator{
  private static Map<String, byte[]> readHere(Path diskZip, List<String> steps, byte[] bytes) {
    return readZip(diskZip, steps) .readAll(()->zipStream(diskZip, bytes));
  }
  public static List<String> entryNames(Path diskZip, List<String> steps){
    return fetch(diskZip, steps, bytes -> List.copyOf(readHere(diskZip, steps, bytes).keySet()));
  }
  public static List<String> dirEntryNames(Path diskZip, List<String> steps){
    return fetch(diskZip, steps, bytes -> List.copyOf(readZip(diskZip, steps).dirNames(()->zipStream(diskZip, bytes))));
  }
  public static byte[] entryBytes(Path diskZip, List<String> steps, String entryName){
    var res= fetch(diskZip, steps, bytes -> readHere(diskZip, steps, bytes).get(entryName));
    if (res == null){ throw Violation.cacheCanNotFindZipEntry(diskZip, steps, entryName); }
    return res;
  }
  private static <T> T fetch(Path diskZip, List<String> steps, Function<byte[],T> onFinal){
    return Fs.of(() -> fetchSteps(diskZip, steps, onFinal));
  }
  private static ReadZip readZip(Path diskZip, List<String> steps){
    return new ReadZip(
      n -> Report.zipBadEntryName(diskZip, steps, n),
      a -> Report.zipDuplicateEntryName(diskZip, steps, a),
      n -> Report.zipEntryTooBig(diskZip, steps, n)
    );
  }
  private static ZipInputStream zipStream(Path diskZip, byte[] bytes) throws IOException{
    return bytes==null
      ? new ZipInputStream(Files.newInputStream(diskZip), UTF_8)
      : new ZipInputStream(new ByteArrayInputStream(bytes), UTF_8);
  }
  private static <T> T fetchSteps(Path diskZip, List<String> steps, Function<byte[],T> onFinal) throws IOException{
    byte[] bytes= null; int rounds= 0;
    for (var step: steps){ rounds++;
      bytes= readHere(diskZip, steps.subList(0, rounds), bytes).get(step);
      if (bytes == null){ throw Violation.cacheCanNotFindZipEntry(diskZip, steps.subList(0, rounds), step); }
    }
    return onFinal.apply(bytes);
  }
}