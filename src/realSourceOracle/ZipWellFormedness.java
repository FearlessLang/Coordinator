package realSourceOracle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import userMessages.Report;
import utils.Push;

//A segment is the 'folder like' single unit
//zips is the full path to the next nested zip.
//example: Zips: a/b/foo.zip;  c/d.zip;  k.txt //all the ones in the middle must have .zip at the end
//     segments: a;b;foo;c;d;k.txt  //for the same example
//     The last keeps the extension, even it is .zip

//Here Both PathEntry and ZipEntry can be used as Ref for a RealSourceOracle extends SourceOracle
public final class ZipWellFormedness{
  private static final int maxZipNesting= 64;
  public static List<ZipEntry> allEntryPaths(Path root, Path local){
    assert root.isAbsolute() && !local.isAbsolute();
    var out= new ArrayList<ZipEntry>();
    reqCollect(root.resolve(local),root,local, List.of(), 0, out);
    reqNoFileUsedAsDirectory(out);
    return Collections.unmodifiableList(out);
  }
  private static void reqCollect(Path diskZip, Path root, Path local, List<String> steps, int depth, ArrayList<ZipEntry> out){
    if (depth > maxZipNesting){ throw Report.zipNestingTooDeep(diskZip, steps, depth, maxZipNesting); }
    var names= ZipLocator.entryNames(diskZip, steps);
    for (var name: names){ singleName(diskZip, root, local, steps, depth, out, name); }
  }
  private static void reqNoFileUsedAsDirectory(List<ZipEntry> out){
    for (var e: out){
      var nested= out.stream().filter(o->isProperPrefix(e.segments(), o.segments())).findFirst();
      if (nested.isEmpty()){ continue; }
      var extra= nested.get().segments().subList(e.segments().size(), nested.get().segments().size());
      var nestedDisplay= e.lastZips()+"/"+String.join("/", extra);
      throw Report.zipFileUsedAsDirectory(e.root().resolve(e.local()), e.zips(), e.lastZips(), nestedDisplay);
    }
  }
  private static boolean isProperPrefix(List<String> prefix, List<String> full){
    return full.size() > prefix.size() && full.subList(0, prefix.size()).equals(prefix);
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