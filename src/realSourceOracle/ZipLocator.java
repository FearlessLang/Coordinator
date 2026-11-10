package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private record Stamp(long size, FileTime time, String hash){}
  private record Seen(Stamp stamp, List<String> files){}
  private record Listed(Stamp stamp, ConcurrentHashMap<List<String>,List<String>> names){}
  private static final ConcurrentHashMap<Path,ConcurrentHashMap<Path,Listed>> roots= new ConcurrentHashMap<>();
  public static List<String> entryNames(Path root, Path diskZip, List<String> steps){
    var seen= seen(diskZip);
    if (seen.isEmpty()){
      Optional.ofNullable(roots.get(root)).ifPresent(z -> z.remove(diskZip));
      return listed(diskZip, steps);
    }
    var stamp= seen.get().stamp();
    var zips= roots.computeIfAbsent(root, _ -> new ConcurrentHashMap<>());
    var known= zips.compute(diskZip, (_, l) -> l != null && l.stamp().equals(stamp) ? l : new Listed(stamp, new ConcurrentHashMap<>()));
    var names= known.names().get(steps);
    if (names != null){ return names; }
    names = listed(diskZip, steps);
    var described= steps.isEmpty() ? files(seen.get().files()).equals(files(names)) : known.names().containsKey(List.of());
    if (described && seen(diskZip).map(Seen::stamp).equals(Optional.of(stamp))){ known.names().put(List.copyOf(steps), names); }
    return names;
  }
  private static List<String> files(List<String> names){ return names.stream().filter(n -> !n.endsWith("/")).sorted().toList(); }
  public static void keep(Path root, Set<Path> zips){
    roots.computeIfPresent(root, (_, known) -> kept(known, zips));
  }
  private static ConcurrentHashMap<Path,Listed> kept(ConcurrentHashMap<Path,Listed> known, Set<Path> zips){
    known.keySet().retainAll(zips);
    return known.isEmpty() ? null : known;
  }
  public static void forget(Path root){ roots.remove(root.toAbsolutePath().normalize()); }
  private static Optional<Seen> seen(Path zip){
    var a= Fs.of(() -> Files.readAttributes(zip, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
    if (a.isDirectory() || a.isSymbolicLink()){ return Optional.empty(); }
    return Fs.of(() -> ZipDirectory.of(zip, a.size())).map(d -> new Seen(new Stamp(a.size(), a.lastModifiedTime(), d.hash()), d.files()));
  }
  private static List<String> listed(Path diskZip, List<String> steps){
    return fetch(diskZip, steps, bytes -> reqEntries(diskZip, steps, bytes, List.copyOf(readHere(diskZip, steps, bytes, _ -> false).keySet())));
  }
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
