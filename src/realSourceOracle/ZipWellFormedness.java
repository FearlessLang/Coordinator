package realSourceOracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import coordinatorMessages.UserExit;
import tools.Fs;
import tools.SourceOracle;
import utils.IoErr;
import utils.Push;

//A segment is the 'folder like' single unit
//zips is the full path to the next nested zip.
//example: Zips: a/b/foo.zip;  c/d.zip;  k.txt //all the ones in the middle must have .zip at the end
//     segments: a;b;foo;c;d;k.txt  //for the same example
//     The last keeps the extension, even it is .zip

//Here Both PathEntry and ZipEntry can be used as Ref for a RealSourceOracle extends SourceOracle
record PathEntry(Path root, Path local) implements SourceOracle.Ref{
  @Override public String fearPath(){ return "fear:/"+localSegments(local).stream().collect(Collectors.joining("/")); }
  @Override public byte[] loadBytes(){ return IoErr.of(()->Files.readAllBytes(root.resolve(local))); }
  @Override public String loadString(){ return Fs.readUtf8(root.resolve(local)); }
  @Override public long lastModified(){ return Fs.lastModified(root.resolve(local)); }
  @Override public String toString(){ return fearPath(); }
  static List<String> localSegments(Path local){
    return StreamSupport.stream(local.spliterator(),false).map(Path::toString).toList();
  }
}
record ZipEntry(Path root, Path local, List<String> segments, List<String> zips, String lastZips) implements SourceOracle.Ref{
  @Override public String fearPath(){
    return "fear:/"+Stream.concat(localSegments(local).stream(), segments.stream())
      .collect(Collectors.joining("/"));
  }
  @Override public byte[] loadBytes(){ return ZipLocator.entryBytes(root.resolve(local), zips,lastZips); }
  @Override public long lastModified(){ return IoErr.of(()->Files.getLastModifiedTime(root.resolve(local)).toMillis()); }
  static List<String> localSegments(Path local){
    List<String> res= StreamSupport.stream(local.spliterator(),false).map(Path::toString).toList();
    var last= res.getLast();
    assert last.endsWith(".zip");
    return Push.of(res.subList(0, res.size()-1), last.substring(0, last.length()-4));
  }
  @Override public String toString(){ return fearPath(); }
}
public final class ZipWellFormedness{
  private static final int maxZipNesting= 64;
  public static List<ZipEntry> allEntryPaths(Path root, Path local){
    assert root.isAbsolute() && !local.isAbsolute();
    var out= new ArrayList<ZipEntry>();
    reqCollect(root.resolve(local),root,local, List.of(), 0, out);
    return Collections.unmodifiableList(out);
  }
  private static void reqCollect(Path diskZip, Path root, Path local, List<String> steps, int depth, ArrayList<ZipEntry> out){
    if (depth > maxZipNesting){ throw UserExit.zipNestingTooDeep(diskZip, steps, depth, maxZipNesting); }
    var names= ZipLocator.entryNames(diskZip, steps);
    for (var name: names){ singleName(diskZip, root, local, steps, depth, out, name); }
  }
  private static void singleName(Path diskZip, Path root, Path local, List<String> steps, int depth, ArrayList<ZipEntry> out, String name){
    out.add(new ZipEntry(root, local, zipsToSegments(steps, name),steps,name));
    if (name.endsWith(".zip")){ reqCollect(diskZip,root,local, Push.of(steps, name), depth+1, out); }
  }
  private static List<String> zipsToSegments(List<String> steps, String name){
    assert steps.stream().allMatch(e->e.endsWith(".zip"));
    return Stream.concat(
      steps.stream().map(e->segmentsOf(e)).flatMap(List::stream),
      Stream.of(name.split("/"))
    ).toList();
  }
  private static List<String> segmentsOf(String step){
    var res= List.of(step.split("/"));
    return Push.of(res.subList(0, res.size()-1),lastSegmentOf(res.getLast()));
  }
  private static String lastSegmentOf(String e){
    assert e.endsWith(".zip");
    return e.substring(0, e.length()-4);
  }
}