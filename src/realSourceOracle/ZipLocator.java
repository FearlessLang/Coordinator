package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import tools.Fs;
import tools.ReadZip;
import userMessages.Report;
import userMessages.UserError;
import utils.Bug;
import utils.Range;

public final class ZipLocator{
  private static Map<String,byte[]> readHere(Path diskZip, List<String> steps, byte[] bytes){
    try{ return readZip(diskZip, steps, bytes); }
    catch (UncheckedIOException e){
      var notUtf8= e.getCause() instanceof ZipException z && z.getCause() instanceof IllegalArgumentException;
      if (notUtf8){ throw Report.zipEntryNameNotUtf8(diskZip, steps); }
      if (e.getCause() instanceof ZipException || e.getCause() instanceof EOFException){ throw Report.zipCanNotBeRead(diskZip, steps, e.getCause().getMessage()); }
      throw e;
    }
  }
  private static List<String> reqCentralDirectory(Path diskZip, List<String> steps, byte[] bytes, List<String> names){
    var all= bytes!=null ? bytes : Fs.of(() -> Fs.readAllBytes(diskZip));
    var local= new ArrayList<String>();
    Fs.ofV(() -> { try (var zin= new ZipInputStream(new ByteArrayInputStream(all), UTF_8)){ for (var e= zin.getNextEntry(); e != null; e = zin.getNextEntry()){ local.add(e.getName()); } } });
    List<String> central;
    try{ central = centralNames(ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN)); }
    catch (IndexOutOfBoundsException|ArithmeticException _){ throw Report.zipCentralDirectory(diskZip, steps); }
    if (!local.stream().sorted().toList().equals(central.stream().sorted().toList())){ throw Report.zipCentralDirectory(diskZip, steps); }
    return names;
  }
  private static List<String> centralNames(ByteBuffer b){
    var end= b.limit()-22;
    while (b.getInt(end) != 0x06054b50){ end -= 1; }
    var total= (long)(b.getShort(end+10) & 0xFFFF);
    var at= b.getInt(end+16) & 0xFFFFFFFFL;
    if (total==0xFFFF || at==0xFFFFFFFFL){
      var end64= Math.toIntExact(b.getLong(end-20+8));
      if (b.getInt(end-20)!=0x07064b50 || b.getInt(end64)!=0x06064b50){ throw new IndexOutOfBoundsException(); }
      total = b.getLong(end64+32);
      at = b.getLong(end64+48);
    }
    var res= new ArrayList<String>();
    var p= Math.toIntExact(at);
    for (var i= 0L; i<total; i += 1){
      if (b.getInt(p) != 0x02014b50){ throw new IndexOutOfBoundsException(); }
      var name= b.getShort(p+28) & 0xFFFF;
      res.add(new String(b.array(), p+46, name, UTF_8));
      p += 46+name+(b.getShort(p+30) & 0xFFFF)+(b.getShort(p+32) & 0xFFFF);
    }
    return res;
  }
  private static Map<String,byte[]> readZip(Path diskZip, List<String> steps, byte[] bytes){
    return new ReadZip(
      n -> Report.zipBadEntryName(diskZip, steps, n),
      a -> Report.zipDuplicateEntryName(diskZip, steps, a),
      n -> Report.zipEntryTooBig(diskZip, steps, n),
      n -> Report.zipEmptyDirectoryEntry(diskZip, steps, n),
      (n,t) -> reqTime(asUtc(t), now -> Report.zipEntryDateInTheFuture(diskZip, steps, n, asUtc(t), now))
    ).readAll(() -> zipStream(diskZip, bytes));
  }
  public static List<String> entryNames(Path diskZip, List<String> steps){
    return fetch(diskZip, steps, bytes -> reqCentralDirectory(diskZip, steps, bytes, reqEntries(diskZip, steps, bytes, List.copyOf(readHere(diskZip, steps, bytes).keySet()))));
  }
  private static List<String> reqEntries(Path diskZip, List<String> steps, byte[] bytes, List<String> names){
    if (!names.isEmpty()){ return names; }
    var head= bytes!=null ? bytes : Fs.of(() -> { try (var in= Fs.newInputStream(diskZip)){ return in.readNBytes(4); } });
    var empty= head.length>=4 && Arrays.equals(head, 0, 4, emptyZipSignature, 0, 4);
    throw empty ? Report.zipNoEntries(diskZip, steps) : Report.zipNotAZip(diskZip, steps);
  }
  private static final byte[] emptyZipSignature= {'P', 'K', 5, 6};
  public static byte[] entryBytes(Path diskZip, List<String> steps, String entryName){
    var res= fetch(diskZip, steps, bytes -> readHere(diskZip, steps, bytes).get(entryName));
    if (res == null){ throw Bug.of("The entry "+entryName+" of "+diskZip+" "+steps+" is gone"); }
    return res;
  }
  static final long futureTolerance= 86_400_000;
  // A time of a zip entry has no time zone: reading it as UTC gives the same result on every machine.
  static long asUtc(long t){ return LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault()).toInstant(ZoneOffset.UTC).toEpochMilli(); }
  private static void reqTime(long t, LongFunction<UserError> future){
    var now= System.currentTimeMillis();
    if (t > now+futureTolerance){ throw future.apply(now); }
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
      bytes = readHere(diskZip, upTo, bytes).get(steps.get(i));
      if (bytes == null){ throw Bug.of("The entry "+steps.get(i)+" of "+diskZip+" "+upTo+" is gone"); }
    }
    return onFinal.apply(bytes);
  }
}
