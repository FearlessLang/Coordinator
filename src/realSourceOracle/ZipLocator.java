package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

import tools.Fs;
import tools.ReadZip;
import userMessages.Report;
import userMessages.UserError;
import userMessages.Violation;
import utils.Range;

public final class ZipLocator{
  private static Map<String,byte[]> readHere(Path diskZip, List<String> steps, byte[] bytes){
    return new ReadZip(
      n -> Report.zipBadEntryName(diskZip, steps, n),
      a -> Report.zipDuplicateEntryName(diskZip, steps, a),
      n -> Report.zipEntryTooBig(diskZip, steps, n),
      n -> Report.zipEmptyDirectoryEntry(diskZip, steps, n),
      (n,t) -> reqTime(n, t, () -> Report.zipEntryDateUnset(diskZip, steps, n, t), now -> Report.zipEntryDateInTheFuture(diskZip, steps, n, t, now))
    ).readAll(() -> zipStream(diskZip, bytes));
  }
  public static List<String> entryNames(Path diskZip, List<String> steps){
    return fetch(diskZip, steps, bytes -> reqEntries(diskZip, steps, bytes, List.copyOf(readHere(diskZip, steps, bytes).keySet())));
  }
  private static List<String> reqEntries(Path diskZip, List<String> steps, byte[] bytes, List<String> names){
    if (!names.isEmpty()){ return names; }
    var head= bytes != null ? bytes : Fs.of(() -> { try (var in= Files.newInputStream(diskZip)){ return in.readNBytes(4); } });
    var empty= head.length >= 4 && Arrays.equals(head, 0, 4, emptyZipSignature, 0, 4);
    throw empty ? Report.zipNoEntries(diskZip, steps) : Report.zipNotAZip(diskZip, steps);
  }
  private static final byte[] emptyZipSignature= {'P', 'K', 5, 6};
  public static byte[] entryBytes(Path diskZip, List<String> steps, String entryName){
    var res= fetch(diskZip, steps, bytes -> readHere(diskZip, steps, bytes).get(entryName));
    if (res == null){ throw Violation.cacheCouldNotFindZipEntry(diskZip, steps, entryName); }
    return res;
  }
  private static void reqTime(String name, long t, Supplier<UserError> unset, LongFunction<UserError> future){
    if (Stream.of(name.split("/")).anyMatch(s -> s.startsWith("."))){ return; }
    BuildWithZip.reqTime(t, unset, future);
  }
  private static ZipInputStream zipStream(Path diskZip, byte[] bytes) throws IOException{
    return bytes == null
      ? new ZipInputStream(Files.newInputStream(diskZip), UTF_8)
      : new ZipInputStream(new ByteArrayInputStream(bytes), UTF_8);
  }
  private static <T> T fetch(Path diskZip, List<String> steps, Function<byte[],T> onFinal){
    byte[] bytes= null;
    for (int i : Range.of(steps)){
      var upTo= steps.subList(0, i+1);
      bytes = readHere(diskZip, upTo, bytes).get(steps.get(i));
      if (bytes == null){ throw Violation.cacheCouldNotFindZipEntry(diskZip, upTo, steps.get(i)); }
    }
    return onFinal.apply(bytes);
  }
}
