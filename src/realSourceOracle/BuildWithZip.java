package realSourceOracle;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import coordinatorMessages.UserExit;
import tools.SourceOracle.Ref;
import tools.SourceOracle.RefParent;
import utils.IoErr;
//TODO: Open bug: some Refs are not records and we use them in hash maps here.
record Tree(
  Path root,
  ArrayList<Ref> visibleFiles,
  HashMap<RefParent,Set<RefParent>> visKidsByDir,
  HashMap<RefParent,Set<RefParent>> dotKidsByDir
  ){
  Tree{ assert root.equals(root.toAbsolutePath().normalize()); }
  public void collect(){    
    reqNoEmptyDirs();
    IoErr.walkV(root, s->s.filter(p->!p.equals(root)).forEach(abs->collectBody(abs)));
  }
  private void reqNoEmptyDirs(){
    var dirs= new HashSet<Path>();
    var nonEmpty= new HashSet<Path>();
    dirs.add(Path.of(""));
    IoErr.walkV(root, s->s.filter(p->!p.equals(root)).forEach(abs->{
      var rel= root.relativize(abs);
      nonEmpty.add(parentOrEmpty(rel));
      if (isDirectory(abs)){ dirs.add(rel); }
      if (!isDiskZip(abs, rel)){ return; }
      var es= ZipWellFormedness.allEntryPaths(root, rel);
      if (es.isEmpty()){ throw UserExit.emptyExpandedZip(rel); }
    }));
    for (var d: dirs){ if (!nonEmpty.contains(d)){ throw UserExit.emptyDirectory(d); } }
  }
  private boolean isDirectory(Path abs){ return Files.isDirectory(abs, LinkOption.NOFOLLOW_LINKS); }
  private void collectBody(Path abs){
    var rel= root.relativize(abs);
    if (isDiskZip(abs, rel)){ collectBodyDiskZip(root, rel); return; }
    var pe= new PathEntry(root, rel);
    addKid(pe);
    if (isRegularFile(abs) && !isInvisible(pe)){ visibleFiles.add(pe); }
  }
  private static boolean isRegularFile(Path abs){ return Files.isRegularFile(abs, LinkOption.NOFOLLOW_LINKS); }
  private void collectBodyDiskZip(Path root, Path rel){
    for (var e: ZipWellFormedness.allEntryPaths(root, rel)){
      if (e.segments().getLast().endsWith(".zip")){ continue; }//expanded
      for (RefParent p= e.parent(); p.parent()!=p; p= p.parent()){ addKid(p); }
      addKid(e);
      if (!isInvisible(e)){ visibleFiles.add(e); }
    }
  }
  private void addKid(RefParent kid){
    var dir= kid.parent();
    if (dir == kid){ return; } // root
    var m= isInvisible(dir) ? dotKidsByDir : visKidsByDir;
    m.computeIfAbsent(dir, _->new HashSet<>()).add(kid);
  }
  private static boolean isDiskZip(Path abs, Path rel){ return isRegularFile(abs) && rel.getFileName().toString().endsWith(".zip"); }
  private static Path parentOrEmpty(Path p){ return p.getParent()==null ? Path.of("") : p.getParent(); }
  private static boolean isInvisible(RefParent r){
    var fp= r.fearPath();
    assert fp.startsWith("fear:/");
    return Stream.of(fp.substring("fear:/".length()).split("/")).anyMatch(s->s.startsWith("."));
  }
}