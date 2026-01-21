package realSourceOracle;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import coordinatorMessages.UserExit;
import tools.Fs;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import tools.SourceOracle.RefParent;
import utils.IoErr;
record Tree(
  Path root,
  ArrayList<Ref> visibleFiles,
  LinkedHashMap<RefParent,Set<RefParent>> visKidsByDir,
  LinkedHashMap<RefParent,Set<RefParent>> dotKidsByDir
  ){
  Tree{ assert root.equals(root.toAbsolutePath().normalize()); }
  public void collect(){    
    reqNoEmptyDirs();
    IoErr.walkV(root, s->s
      .filter(p->!p.equals(root))
      .filter(p->!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
      .forEach(abs->collectFile(abs))
    );
  }
  private void collectFile(Path abs){
    var rel= root.relativize(abs);
    var pe= new PathEntry(root, rel);
    if (Files.isSymbolicLink(abs)){ throw isInvisible(pe)
      ? UserExit.invisibleSymlinkForbidden(abs)
      : UserExit.symlinkForbidden(abs);
    }
    if (!isRegularFile(abs) && !isDiskZip(abs, rel)){ throw isInvisible(pe)
      ? UserExit.invisibleOnlyRegularFilesAndDirs(abs)
      : UserExit.onlyRegularFilesAndDirs(abs);
    }
    if (isDiskZip(abs, rel)){ collectBodyDiskZip(root, rel); return; }
    for (RefParent p= pe; p.parent()!=p; p= p.parent()){ addKid(p); }
    if (isRegularFile(abs) && !isInvisible(pe)){ visibleFiles.add(pe); }
  }
  private void reqNoEmptyDirs(){
    var dirs= new LinkedHashSet<Path>();
    var nonEmpty= new LinkedHashSet<Path>();
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
  private static boolean isRegularFile(Path abs){ return Files.isRegularFile(abs, LinkOption.NOFOLLOW_LINKS); }
  private void collectBodyDiskZip(Path root, Path rel){
    for (var e: ZipWellFormedness.allEntryPaths(root, rel)){
      if (e.segments().getLast().endsWith(".zip")){ continue; }//expanded
      for (RefParent p= e; p.parent()!=p; p= p.parent()){ addKid(p); }
      if (!isInvisible(e)){ visibleFiles.add(e); }
    }
  }
  private void addKid(RefParent kid){
    var dir= kid.parent();
    if (dir == kid){ return; } // root
    var m= isInvisible(dir) ? dotKidsByDir : visKidsByDir;
    m.computeIfAbsent(dir, _->new LinkedHashSet<>()).add(kid);
  }
  private static boolean isDiskZip(Path abs, Path rel){ return isRegularFile(abs) && rel.getFileName().toString().endsWith(".zip"); }
  private static Path parentOrEmpty(Path p){ return p.getParent()==null ? Path.of("") : p.getParent(); }
  public static boolean isInvisible(RefParent r){
    var fp= r.fearPath();
    assert fp.startsWith(SourceOracle.root);
    return Stream.of(fp.substring(SourceOracle.root.length()).split("/")).anyMatch(s->s.startsWith("."));
  }
}
final class BuildWithZip{
  private final Tree t;
  BuildWithZip(Path root){
    var r= root.toAbsolutePath().normalize();
    t= new Tree(r, new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
  }
  List<Ref> build(){
    t.collect();
    t.visKidsByDir().forEach((_,kids)->{
      kids.forEach(this::checkIndividualVisibleSegment);
      checkCollectiveVisible(kids);
    });
    t.dotKidsByDir().forEach((_,kids)->{
      kids.forEach(this::checkIndividualInvisibleSegment);
      checkCollectiveInvisible(kids);
    });
    return List.copyOf(t.visibleFiles());
  }
  void checkTooLong(RefParent kid){     if (kid.fearPath().length() > 200 + SourceOracle.root.length()){ throw UserExit.pathTooLong(kid); } }
  private void checkIndividualVisibleSegment(RefParent kid){
    checkTooLong(kid);
    var name= Fs.fileNameWithExtension(kid.fearPath());
    int d0= name.indexOf('.');
    if (d0 == 0){ checkIndividualInvisibleSegment(kid); return; }
    boolean isFile= kid instanceof Ref;
    var validNoExt= !isFile || UserExit.allowedNoExtFilesS.contains(name);
    var badNoExt= isFile && d0 < 0 && !validNoExt;
    if (badNoExt){ throw UserExit.needsExtension(kid); }
    if (validNoExt){ checkVisibleAtom(kid, name); return; }
    assert d0 > 0;
    checkVisibleAtom(kid, name.substring(0, d0));
    checkExt(kid, name.substring(d0 + 1, name.length()));
  }
  private void checkVisibleAtom(RefParent kid, String atom){
    char c0= atom.charAt(0);
    var letterOr_= c0 == '_' || ('a' <= c0 && c0 <= 'z');
    if (!letterOr_){ throw UserExit.visibleMustStartWithLetterOrUnderscore(kid); }
    for (int i= 1; i < atom.length(); i++){
      char c= atom.charAt(i);
      boolean ok= ('a' <= c && c <= 'z') || ('0' <= c && c <= '9') || c == '_';
      if (!ok){ throw UserExit.visibleInvalidChar(kid, c); }
      var double_ = c == '_' && atom.charAt(i - 1) == '_';
      if (double_){ throw UserExit.visibleNoDoubleUnderscore(kid); }
    }
    if (winReserved.contains(atom)){ throw UserExit.windowsReservedName(kid); }
  }
  private void checkExt(RefParent kid, String tail){
    if (tail.isEmpty()){ throw UserExit.missingExtension(kid); }
    int d= tail.indexOf('.');
    if (d < 0){ checkExtSeg(kid, tail); return; }
    if (!UserExit.allowedMultiDotExtsS.contains(tail)){ throw UserExit.multiDotExtNotAllowed(kid); }
  }
  private void checkExtSeg(RefParent kid, String seg){
    int n= seg.length();
    if (n < 1 || n > 16){ throw UserExit.extLenMustBe1To16(kid); }
    for (int i= 0; i < n; i++){
      char c= seg.charAt(i);
      boolean ok= ('a' <= c && c <= 'z') || ( '0' <= c && c <= '9' );
      if (!ok){ throw UserExit.extInvalidChar(kid, c); }
    }
  }
  private static final Set<String> winReserved= Set.of(
    "con","prn","aux","nul",
    "com1","com2","com3","com4","com5","com6","com7","com8","com9",
    "lpt1","lpt2","lpt3","lpt4","lpt5","lpt6","lpt7","lpt8","lpt9"
  );
  private static final String winBadChars="<>:\"/\\|?*";

  private void checkIndividualInvisibleSegment(RefParent kid){
    assert Tree.isInvisible(kid);
    checkTooLong(kid);
    var name= Fs.fileNameWithExtension(kid.fearPath());
    if (name.endsWith(".") || name.endsWith(" ")){ throw UserExit.invisibleNoTrailingDotOrSpace(kid, name); }
    for (int i= 0; i < name.length(); ){
      int cp= name.codePointAt(i);
      if (0xD800 <= cp && cp <= 0xDFFF){ throw UserExit.invisibleInvalidSurrogate(kid, name); }
      if (Character.isISOControl(cp)){ throw UserExit.invisibleNoControlChars(kid, cp, name); }
      if (winBadChars.indexOf(cp) >= 0){ throw UserExit.invisibleNoWindowsBadChars(kid, (char)cp, name); }
      i += Character.charCount(cp);
    }
    int d= name.indexOf('.');
    var base= (d < 0 ? name : name.substring(0, d)).toLowerCase(Locale.ROOT);
    if (!base.isEmpty() && winReserved.contains(base)){ throw UserExit.invisibleWindowsReservedDeviceName(kid, base, name); }
  }
  private void checkCollectiveInvisible(Set<RefParent> kids){
    var seenKeyToName= new HashMap<String,String>();
    for (var kid: kids){ checkCollectiveInvisibleFocusOn(seenKeyToName, kid); }
  }
  private void checkCollectiveInvisibleFocusOn(HashMap<String,String> seenKeyToName, RefParent kid){
    var name= Fs.fileNameWithExtension(kid.fearPath());
    var lowName= name.toLowerCase(Locale.ROOT);
    var nfc= Normalizer.normalize(name, Form.NFC);
    var key= nfc.toLowerCase(Locale.ROOT);
    var prev= seenKeyToName.putIfAbsent(key, name);
    if (prev == null){ return; }
    var lowPrev= prev.toLowerCase(Locale.ROOT);
    var prevNfc= Normalizer.normalize(prev, Form.NFC);
    boolean caseOnly= lowPrev.equals(lowName) && !prevNfc.equals(nfc);
    boolean nfcOnly= prevNfc.equals(nfc) && !lowPrev.equals(lowName);
    throw UserExit.hiddenSiblingNamesCollide(kid, prev, name, caseOnly, nfcOnly);
  }
  private void checkCollectiveVisible(Set<RefParent> kids){
    var dotKids= new ArrayList<RefParent>();
    var visKids= new ArrayList<RefParent>();
    for (var kid: kids){ (Fs.fileNameWithExtension(kid.fearPath()).startsWith(".") ? dotKids : visKids).add(kid); }
    if (!dotKids.isEmpty()){ checkCollectiveInvisible(new LinkedHashSet<>(dotKids)); }
    for (var kid: visKids){
      var name= Fs.fileNameWithExtension(kid.fearPath());
      if (name.indexOf('.') >= 0){ continue; }
      if (!UserExit.allowedNoExtFilesS.contains(name)){ continue; }
      if (!(kid instanceof Ref)){ continue; } // directory
      checkNoExtBaseClash(visKids, kid, name);
    }
  }
  private void checkNoExtBaseClash(List<RefParent> visKids, RefParent noExtKid, String base){
    for (var kid: visKids){
      if (kid.equals(noExtKid)){ continue; }
      var name= Fs.fileNameWithExtension(kid.fearPath());
      int d0= name.indexOf('.');
      if (d0 < 0){ continue; }
      if (!name.substring(0, d0).equals(base)){ continue; }
      throw UserExit.extensionlessMaskExtension(kid, noExtKid);
    }
  }
}