package realSourceOracle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import coordinatorMessages.CacheCorruptionError;
import coordinatorMessages.UserTreeError;
import tools.Fs;
import tools.SourceOracle;
import utils.IoErr;

import static offensiveUtils.Require.*;

public record RealSourceOracleWithZip(Path root) implements SourceOracle{
  public RealSourceOracleWithZip{
    assert nonNull(root);
    root = root.toAbsolutePath().normalize();
    assert root.isAbsolute();
  }
  @Override public List<Ref> allFiles(){
    var out=new ArrayList<Ref>();
    walkFs(root, root, false, out);
    assert out.stream().map(Ref::fearPath).distinct().count()==out.size();
    return Collections.unmodifiableList(out);
  }
  private static void walkFs(Path root, Path dir, boolean dotSeen, ArrayList<Ref> out){
    try(var s= IoErr.of(()->Files.list(dir))){
      var dirs=new ArrayList<Path>();
      var files=new ArrayList<Path>();
      var dirNames=new TreeSet<String>();
      s.sorted().forEach(p->{
        if (Files.isDirectory(p)){
          dirs.add(p);
          dirNames.add(p.getFileName().toString());
          return;
        }
        files.add(p);
      });
      for(var f:files){
        var name=f.getFileName().toString();
        if (isZip(name) && !dotSeen && !name.startsWith(".")){
          var base=stripZip(name);
          if (dirNames.contains(base)){ throw UserTreeError.zipAndDirConflict(new FSRef(root, root.relativize(f))); }
          walkZip(root, root.relativize(f), List.of(), false, out);
          continue;
        }
        out.add(new FSRef(root, root.relativize(f)));
      }
      for(var d:dirs){
        var name=d.getFileName().toString();
        walkFs(root, d, dotSeen||name.startsWith("."), out);
      }
    }
  }

  private static void walkZip(Path root, Path diskZipRel, List<String> steps, boolean dotSeen, ArrayList<Ref> out){
    var diskZipAbs=root.resolve(diskZipRel);
    var names=ZipLocator.entryNames(diskZipAbs, steps);

    var files=new ArrayList<List<String>>();
    var dirs=new ArrayList<List<String>>();
    for(var raw:names){
      if (raw.isEmpty()){ continue; }
      var segs=split(raw);
      if (raw.endsWith("/")){ dirs.add(segs); }
      else{ files.add(segs); }
    }
    walkZipDir(root, diskZipRel, steps, files, dirs, List.of(), dotSeen, out);
  }

  private static void walkZipDir(
    Path root, Path diskZipRel, List<String> steps,
    List<List<String>> files, List<List<String>> dirs,
    List<String> prefix, boolean dotSeen, ArrayList<Ref> out
  ){
    int n=prefix.size();
    var dirNames=new TreeSet<String>();
    var fileNames=new TreeSet<String>();

    for(var d:dirs){
      if (d.size()==n+1 && startsWith(d,prefix)){ dirNames.add(d.get(n)); }
    }
    for(var f:files){
      if (!startsWith(f,prefix)){ continue; }
      if (f.size()==n+1){ fileNames.add(f.get(n)); }
      else{ dirNames.add(f.get(n)); }
    }

    for(var f:fileNames){
      if (!isZip(f) || dotSeen || f.startsWith(".")){ continue; }
      var base=stripZip(f);
      if (dirNames.contains(base)){
        throw UserTreeError.zipAndDirConflict(new ZipRef(root, diskZipRel, internalSegs(steps, prefix, f)));
      }
    }

    for(var f:fileNames){
      if (isZip(f) && !dotSeen && !f.startsWith(".")){
        walkZip(root, diskZipRel, plus(steps, join(prefix, f)), dotSeen||stripZip(f).startsWith("."), out);
        continue;
      }
      out.add(new ZipRef(root, diskZipRel, internalSegs(steps, prefix, f)));
    }

    for(var d:dirNames){
      walkZipDir(root, diskZipRel, steps, files, dirs, plus(prefix,d), dotSeen||d.startsWith("."), out);
    }
  }

  public record FSRef(Path root, Path rel) implements Ref{
    public FSRef{
      assert nonNull(root,rel);
      assert !rel.isAbsolute();
    }
    @Override public String fearPath(){ return "fear:/"+rel.toString().replace('\\','/'); }
    @Override public byte[] loadBytes(){ return IoErr.of(()->Files.readAllBytes(root.resolve(rel))); }
    @Override public long lastModified(){ return Fs.lastModified(root.resolve(rel)); }
    @Override public String toString(){ return fearPath(); }
  }

  public record ZipRef(Path root, Path diskZipRel, List<String> segs) implements Ref{
    public ZipRef{
      assert nonNull(root,diskZipRel,segs);
      segs = List.copyOf(segs);
      assert !diskZipRel.isAbsolute();
    }

    @Override public String fearPath(){
      var parts=new ArrayList<String>();
      var diskSegs=new ArrayList<String>();
      for(var p:diskZipRel){ diskSegs.add(p.toString()); }
      for(int i=0;i<diskSegs.size();i++){
        var s=diskSegs.get(i);
        if (i==diskSegs.size()-1){ s=stripZip(s); }
        parts.add(s);
      }
      for(int i=0;i<segs.size();i++){
        var s=segs.get(i);
        if (isZip(s) && i<segs.size()-1){ s=stripZip(s); }
        parts.add(s);
      }
      return "fear:/"+String.join("/", parts);
    }

    @Override public byte[] loadBytes(){
      var p=parse(segs);
      return ZipLocator.fetch(root.resolve(diskZipRel), p.steps, zin->IoErr.of(()->readEntryBytes(zin, p.entry)));
    }

    @Override public long lastModified(){ return Fs.lastModified(root.resolve(diskZipRel)); }
    @Override public String toString(){ return fearPath(); }
  }

  private record ZipParts(List<String> steps, String entry){}

  private static ZipParts parse(List<String> segs){
    assert !segs.isEmpty();
    var steps=new ArrayList<String>();
    var acc=new ArrayList<String>();
    for(int i=0;i<segs.size()-1;i++){
      var s=segs.get(i);
      acc.add(s);
      if (isZip(s)){
        steps.add(String.join("/", acc));
        acc.clear();
      }
    }
    acc.add(segs.getLast());
    return new ZipParts(Collections.unmodifiableList(steps), String.join("/", acc));
  }

  private static byte[] readEntryBytes(ZipInputStream zin, String entry) throws IOException{
    for(ZipEntry e; (e=zin.getNextEntry())!=null; zin.closeEntry()){
      if (e.isDirectory()||!e.getName().equals(entry)){ continue; }
      var baos=new ByteArrayOutputStream();
      zin.transferTo(baos);
      return baos.toByteArray();
    }
    throw CacheCorruptionError.canNotFindExpected(entry);
  }

  private static boolean isZip(String name){ return name.endsWith(".zip"); }
  private static String stripZip(String name){ return name.substring(0, name.length()-4); }

  private static boolean startsWith(List<String> p, List<String> prefix){
    if (p.size()<prefix.size()){ return false; }
    for(int i=0;i<prefix.size();i++){
      if (!p.get(i).equals(prefix.get(i))){ return false; }
    }
    return true;
  }

  private static List<String> split(String raw){
    var parts=raw.split("/");
    var res=new ArrayList<String>(parts.length);
    for(var p:parts){
      if (!p.isEmpty()){ res.add(p); }
    }
    return res;
  }

  private static List<String> plus(List<String> xs, String x){
    var r=new ArrayList<String>(xs.size()+1);
    r.addAll(xs);
    r.add(x);
    return r;
  }

  private static String join(List<String> prefix, String file){
    return prefix.isEmpty() ? file : String.join("/", prefix)+"/"+file;
  }

  private static List<String> internalSegs(List<String> steps, List<String> prefix, String file){
    var res=new ArrayList<String>();
    for(var s:steps){ res.addAll(split(s)); }
    res.addAll(prefix);
    res.add(file);
    return List.copyOf(res);
  }
}