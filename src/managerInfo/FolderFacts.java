package managerInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import coordinator.Coordinator;
import tools.Fs;
import userMessages.UserError;

public record FolderFacts(
    Path folder, int files, long bytes, long modified,
    long jsonStamp, long cacheStamp, List<String> pkgs, Optional<String> problem){
  public static final String outDir= ".fearless_out";
  public boolean valid(){ return problem.isEmpty(); }
  public boolean cacheUpToDate(){ return cacheStamp >= 0 && cacheStamp >= modified; }
  public boolean hasCache(){ return Files.isDirectory(folder.resolve(outDir)); }
  public static FolderFacts of(Path folder){
    var f= folder.toAbsolutePath().normalize();
    var src= sources(f);
    List<String> pkgs;
    Optional<String> problem;
    UserError.root= f;
    try { pkgs= Coordinator.pkgNames(f); problem= Optional.empty(); }
    catch(UserError e){ pkgs= List.of(); problem= Optional.of(e.getMessage()); }
    return new FolderFacts(f,src.size(),src.stream().mapToLong(FolderFacts::size).sum(),
      newest(src),stamp(f,".json",true),stamp(f,".built",false),pkgs,problem);
  }
  public static long modified(Path folder){ return newest(sources(folder.toAbsolutePath().normalize())); }
  private static List<Path> sources(Path folder){
    var out= folder.resolve(outDir);
    return Fs.walk(folder,s->s.filter(p->!p.startsWith(out)).filter(Files::isRegularFile).toList());
  }
  private static long newest(List<Path> files){ return files.stream().mapToLong(Fs::lastModified).max().orElse(-1); }
  private static long size(Path file){ return Fs.of(()->Files.size(file)); }
  private static long stamp(Path folder, String ext, boolean newest){
    var out= folder.resolve(outDir);
    if (!Files.isDirectory(out)){ return -1; }
    var all= LongStream.of(Fs.walk(out,s->s
      .filter(p->p.getFileName().toString().endsWith(ext))
      .mapToLong(Fs::lastModified).toArray()));
    return (newest ? all.max() : all.min()).orElse(-1);
  }
}
