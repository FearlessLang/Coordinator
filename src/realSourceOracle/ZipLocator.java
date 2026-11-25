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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import tools.Fs;
import tools.ReadZip;
import userMessages.Report;
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
  private static List<String> reqHeaders(Path diskZip, List<String> steps, byte[] bytes, List<String> names){
    var times= times(bytes!=null ? bytes : Fs.of(() -> Fs.readAllBytes(diskZip))).orElseThrow(() -> Report.zipCentralDirectory(diskZip, steps));
    var now= System.currentTimeMillis();
    times.forEach((n, t) -> { if (t > now+futureTolerance){ throw Report.zipEntryDateInTheFuture(diskZip, steps, n, t, now); } });
    return names;
  }
  private record Head(String name, int method, int time, int date, long crc, long csize, long usize, int at){
    boolean agrees(ByteBuffer b, java.util.zip.ZipEntry e){
      var sameHeader= b.getInt(at)==0x04034b50 && name.equals(new String(b.array(), at+30, b.getShort(at+26) & 0xFFFF, UTF_8));
      var sameFields= (b.getShort(at+8) & 0xFFFF)==method && (b.getShort(at+10) & 0xFFFF)==time && (b.getShort(at+12) & 0xFFFF)==date;
      return sameHeader && sameFields && e.getMethod()==method && e.getCrc()==crc && e.getCompressedSize()==csize && e.getSize()==usize;
    }
    long utc(){
      var day= LocalDate.of(1980+(date>>9), 1, 1).plusMonths((date>>5 & 0xF)-1).plusDays((date & 0x1F)-1);
      return day.atStartOfDay().plusHours(time>>11).plusMinutes(time>>5 & 0x3F).plusSeconds((time & 0x1F)*2).toInstant(ZoneOffset.UTC).toEpochMilli();
    }
  }
  static Optional<Map<String,Long>> times(byte[] all){
    var local= new ArrayList<java.util.zip.ZipEntry>();
    try{
      try (var zin= new ZipInputStream(new ByteArrayInputStream(all), UTF_8)){ for (var e= zin.getNextEntry(); e != null; e = zin.getNextEntry()){ local.add(e); } }
      var b= ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
      var central= new LinkedHashMap<String,Head>();
      var heads= central(b);
      heads.forEach(c -> central.put(c.name, c));
      var res= new LinkedHashMap<String,Long>();
      heads.forEach(c -> res.put(c.name, c.utc()));
      var ok= heads.size()==local.size() && central.size()==local.size() && local.stream().allMatch(e -> central.containsKey(e.getName()) && central.get(e.getName()).agrees(b, e));
      return Optional.<Map<String,Long>>of(res).filter(_ -> ok);
    }
    catch (IOException|IllegalArgumentException|IndexOutOfBoundsException|ArithmeticException _){ return Optional.empty(); }
  }
  private static List<Head> central(ByteBuffer b){
    var end= b.limit()-22;
    while (b.getInt(end)!=0x06054b50 || (b.getShort(end+20) & 0xFFFF)!=b.limit()-end-22){ end -= 1; }
    var total= (long)(b.getShort(end+10) & 0xFFFF);
    var at= b.getInt(end+16) & 0xFFFFFFFFL;
    if (total==0xFFFF || at==0xFFFFFFFFL){
      var end64= Math.toIntExact(b.getLong(end-20+8));
      if (b.getInt(end-20)!=0x07064b50 || b.getInt(end64)!=0x06064b50){ throw new IndexOutOfBoundsException(); }
      total = b.getLong(end64+32);
      at = b.getLong(end64+48);
    }
    var res= new ArrayList<Head>();
    var p= Math.toIntExact(at);
    for (var i= 0L; i<total; i += 1){
      if (b.getInt(p) != 0x02014b50){ throw new IndexOutOfBoundsException(); }
      var name= b.getShort(p+28) & 0xFFFF;
      var u= new long[]{b.getInt(p+16), b.getInt(p+20), b.getInt(p+24), b.getInt(p+42)};
      res.add(new Head(new String(b.array(), p+46, name, UTF_8), b.getShort(p+10) & 0xFFFF, b.getShort(p+12) & 0xFFFF, b.getShort(p+14) & 0xFFFF, u[0] & 0xFFFFFFFFL, u[1] & 0xFFFFFFFFL, u[2] & 0xFFFFFFFFL, Math.toIntExact(u[3] & 0xFFFFFFFFL)));
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
      (_,_) -> {}
    ).readAll(() -> zipStream(diskZip, bytes));
  }
  public static List<String> entryNames(Path diskZip, List<String> steps){
    if (Fs.of(() -> Fs.size(diskZip)) > maxBytes){ throw Report.zipTooBig(diskZip); }
    return fetch(diskZip, steps, bytes -> reqHeaders(diskZip, steps, bytes, reqEntries(diskZip, steps, bytes, List.copyOf(readHere(diskZip, steps, bytes).keySet()))));
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
  static final long maxBytes= 2147483639L;
  // A time of a zip entry has no time zone: reading it as UTC gives the same result on every machine.
  static long asUtc(long t){ return LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault()).toInstant(ZoneOffset.UTC).toEpochMilli(); }
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
