package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import tools.Fs;
import tools.ReadZip;
import userMessages.Report;
import userMessages.UserError;
import utils.Bug;
import utils.Range;

public final class ZipLocator{
  private static Map<String,byte[]> readHere(Path diskZip, List<String> steps, byte[] bytes, Predicate<String> keep){
    try{ return readZip(diskZip, steps, bytes, keep); }
    catch (UncheckedIOException e){
      var notUtf8= e.getCause() instanceof ZipException z && z.getCause() instanceof IllegalArgumentException;
      if (notUtf8){ throw Report.zipEntryNameNotUtf8(diskZip, steps); }
      throw e;
    }
  }
  private static Map<String,byte[]> readZip(Path diskZip, List<String> steps, byte[] bytes, Predicate<String> keep){
    return new ReadZip(
      n -> Report.zipBadEntryName(diskZip, steps, n),
      a -> Report.zipDuplicateEntryName(diskZip, steps, a),
      n -> Report.zipEntryTooBig(diskZip, steps, n),
      n -> Report.zipEmptyDirectoryEntry(diskZip, steps, n),
      (n,t) -> reqTime(n, t, () -> Report.zipEntryDateUnset(diskZip, steps, n, t), now -> Report.zipEntryDateInTheFuture(diskZip, steps, n, t, now))
    ).read(() -> zipStream(diskZip, bytes), keep);
  }
  private record Listed(long size, FileTime time, String tail, List<String> names){}
  private static final ConcurrentHashMap<List<Object>,Listed> listed= new ConcurrentHashMap<>();
  public static List<String> entryNames(Path diskZip, List<String> steps){
    var stamp= Fs.of(() -> Files.readAttributes(diskZip, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
    var tail= tail(diskZip, stamp.size());
    var key= List.<Object>of(diskZip.toAbsolutePath(), List.copyOf(steps));
    var known= listed.get(key);
    if (known != null && known.size() == stamp.size() && known.time().equals(stamp.lastModifiedTime()) && known.tail().equals(tail)){ return known.names(); }
    var res= fetch(diskZip, steps, bytes -> reqEntries(diskZip, steps, bytes, List.copyOf(readHere(diskZip, steps, bytes, _ -> false).keySet())));
    listed.put(key, new Listed(stamp.size(), stamp.lastModifiedTime(), tail, res));
    return res;
  }
  //The central directory of a zip, at the end of the file, holds the name, the size, the CRC and the time of every entry;
  //the modification time alone misses a replaced zip: a copy keeps the time of its source on Windows.
  private static String tail(Path zip, long size){
    var bytes= Fs.of(() -> readTail(zip, size));
    try{ return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException e){ throw Bug.of(e); }
  }
  private static byte[] readTail(Path zip, long size) throws IOException{
    try (var ch= Files.newByteChannel(zip)){
      var res= ByteBuffer.allocate((int)Math.min(size, tailSize));
      ch.position(size - res.capacity());
      while (res.hasRemaining() && ch.read(res) >= 0){}
      return res.array();
    }
  }
  private static final int tailSize= 65_536;
  private static List<String> reqEntries(Path diskZip, List<String> steps, byte[] bytes, List<String> names){
    if (!names.isEmpty()){ return names; }
    var head= bytes!=null ? bytes : Fs.of(() -> { try (var in= Fs.newInputStream(diskZip)){ return in.readNBytes(4); } });
    var empty= head.length>=4 && Arrays.equals(head, 0, 4, emptyZipSignature, 0, 4);
    throw empty ? Report.zipNoEntries(diskZip, steps) : Report.zipNotAZip(diskZip, steps);
  }
  private static final byte[] emptyZipSignature= {'P', 'K', 5, 6};
  public static byte[] entryBytes(Path diskZip, List<String> steps, String entryName){
    var res= fetch(diskZip, steps, bytes -> readHere(diskZip, steps, bytes, entryName::equals).get(entryName));
    if (res == null){ throw Bug.of("The entry "+entryName+" of "+diskZip+" "+steps+" is gone"); }
    return res;
  }
  private static void reqTime(String name, long t, Supplier<UserError> unset, LongFunction<UserError> future){
    if (Stream.of(name.split("/")).anyMatch(s -> s.startsWith("."))){ return; }
    BuildWithZip.reqTime(t, unset, future);
  }
  private static ZipInputStream zipStream(Path diskZip, byte[] bytes) throws IOException{
    return bytes==null
      ? new ZipInputStream(Fs.newInputStream(diskZip), UTF_8)
      : new ZipInputStream(new ByteArrayInputStream(bytes), UTF_8);
  }
  private static <T> T fetch(Path diskZip, List<String> steps, Function<byte[],T> onFinal){
    byte[] bytes= null;
    for (int i : Range.of(steps)){
      var upTo= steps.subList(0, i+1);
      bytes = readHere(diskZip, upTo, bytes, steps.get(i)::equals).get(steps.get(i));
      if (bytes == null){ throw Bug.of("The entry "+steps.get(i)+" of "+diskZip+" "+upTo+" is gone"); }
    }
    return onFinal.apply(bytes);
  }
}
