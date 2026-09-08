package managerInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import coordinator.Coordinator;
import managerData.Kind;
import realSourceOracle.RealSourceOracleWithZip;
import tools.Fs;
import userMessages.UserError;

public record FolderFacts(
    Path folder, int files, long bytes, long modified,
    long jsonStamp, long cacheStamp, List<String> pkgs, Optional<String> problem){
  public static final String outDir= Coordinator.outDir;
  ///Logs and reports Fearless writes about a project: never content, so writing one must not make the cache look stale.
  public static final String runDir= ".out";
  public boolean valid(){ return problem.isEmpty(); }
  public boolean cacheUpToDate(){ return cacheStamp >= 0 && cacheStamp >= modified; }
  public boolean hasCache(){ return hasCache(folder); }
  public static boolean hasCache(Path folder){ return Files.isDirectory(folder.toAbsolutePath().normalize().resolve(outDir)); }
  public static FolderFacts of(Path folder, Kind kind){
    var f= folder.toAbsolutePath().normalize();
    var src= sources(f);
    List<String> pkgs= List.of();
    Optional<String> problem;
    UserError.root= f;
    try {
      if (kind == Kind.code){ pkgs= Coordinator.pkgNames(f); } else { new RealSourceOracleWithZip(f); }
      problem= Optional.empty();
    }
    catch(UserError e){ problem= Optional.of(e.getMessage()); }
    return new FolderFacts(f,src.size(),src.stream().mapToLong(FolderFacts::size).sum(),
      modified(f),stamp(f,".json",true),stamp(f,".built",false),pkgs,problem);
  }
  public static long modified(Path folder){
    var f= folder.toAbsolutePath().normalize();
    return Math.max(newest(sources(f)),newest(dirs(f)));
  }
  public static boolean cacheUpToDate(Path folder, long modified){
    var built= stamp(folder.toAbsolutePath().normalize(),".built",false);
    return built >= 0 && built >= modified;
  }
  private static List<Path> sources(Path folder){
    return Fs.walk(folder,s->authored(folder,s).filter(Files::isRegularFile).toList());
  }
  private static List<Path> dirs(Path folder){
    return Fs.walk(folder,s->authored(folder,s).filter(Files::isDirectory).toList());
  }
  private static Stream<Path> authored(Path folder, Stream<Path> all){
    var cache= folder.resolve(outDir);
    var written= folder.resolve(runDir);
    return all.filter(p->!p.startsWith(cache) && !p.startsWith(written));
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
