package realSourceOracle;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import userMessages.Report;
import tools.Fs;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import tools.SourceOracle.RefParent;
record Tree(
  Path root,
  ArrayList<Ref> visibleFiles,
  LinkedHashMap<RefParent,Set<RefParent>> visKidsByDir,
  LinkedHashMap<RefParent,Set<RefParent>> dotKidsByDir
  ){
  Tree{ assert root.equals(root.toAbsolutePath().normalize()); }
  public void collect(){    
    reqNoEmptyDirs();
    Fs.walkV(root, s->s
      .filter(p->!p.equals(root))
      .filter(p->!isDirectory(p))
      .forEach(this::collectFile)
    );
  }
  private void collectFile(Path abs){
    var rel= root.relativize(abs);
    var pe= new PathEntry(root, rel);
    var invisible= BuildWithZip.isInvisible(pe);
    if (Files.isSymbolicLink(abs)){
      if (invisible){ return; }
      throw Report.symlinkForbidden(abs);
    }
    if (!isRegularFile(abs)){ throw invisible
      ? Report.invisibleOnlyRegularFilesAndDirs(abs)
      : Report.onlyRegularFilesAndDirs(abs);
    }
    if (!invisible && rel.getFileName().toString().endsWith(".zip")){ reqNoSiblingForZipName(rel); collectBodyDiskZip(rel); return; }
    addKids(pe);
    if (!invisible){ visibleFiles.add(pe); }
  }
  private void reqNoSiblingForZipName(Path rel){
    var name= rel.getFileName().toString();
    var siblingRel= rel.resolveSibling(name.substring(0, name.length()-4));
    var sibling= root.resolve(siblingRel);
    if (!Files.exists(sibling, LinkOption.NOFOLLOW_LINKS)){ return; }
    throw Report.zipNameClashes(new PathEntry(root, rel), new PathEntry(root, siblingRel), isDirectory(sibling) ? "folder" : "plain file");
  }
  private void reqNoEmptyDirs(){
    var dirs= new LinkedHashSet<Path>();
    var nonEmpty= new LinkedHashSet<Path>();
    dirs.add(Path.of(""));
    Fs.walkV(root, s->s.filter(p->!p.equals(root)).forEach(abs->{
      var rel= root.relativize(abs);
      nonEmpty.add(rel.resolveSibling(""));
      if (BuildWithZip.isInvisible(new PathEntry(root, rel))){ return; }
      if (isDirectory(abs)){ dirs.add(rel); }
    }));
    for (var d: dirs){ if (!nonEmpty.contains(d)){ throw Report.emptyDirectory(d); } }
  }
  private static boolean isDirectory(Path abs){ return Files.isDirectory(abs, LinkOption.NOFOLLOW_LINKS); }
  private static boolean isRegularFile(Path abs){ return Files.isRegularFile(abs, LinkOption.NOFOLLOW_LINKS); }
  private void collectBodyDiskZip(Path rel){
    for (var e: ZipWellFormedness.allEntryPaths(root, rel)){
      if (e.segments().getLast().endsWith(".zip")){ continue; }//expanded
      addKids(e);
      if (!BuildWithZip.isInvisible(e)){ visibleFiles.add(e); }
    }
  }
  private void addKids(RefParent leaf){
    for (RefParent p= leaf; p.parent()!=p; p= p.parent()){
      var m= BuildWithZip.isInvisible(p.parent()) ? dotKidsByDir : visKidsByDir;
      m.computeIfAbsent(p.parent(), _->new LinkedHashSet<>()).add(p);
    }
  }
}
public final class BuildWithZip{
  public static boolean isInvisible(RefParent r){
    return AutoloadHandler.components(r.fearPath()).stream().anyMatch(s->s.startsWith("."));
  }
  private final Tree t;
  BuildWithZip(Path root){ t= new Tree(root.toAbsolutePath().normalize(), new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>()); }
  List<Ref> build(){
    t.collect();
    t.visKidsByDir().forEach((_,kids)->{
      kids.forEach(BuildWithZip::checkIndividualVisibleSegment);
      checkCollectiveVisible(kids);
    });
    t.dotKidsByDir().forEach((_,kids)->{
      kids.forEach(BuildWithZip::checkIndividualInvisibleSegment);
      checkCollectiveInvisible(kids);
    });
    return t.visibleFiles().stream().sorted(Comparator.comparing(Ref::fearPath)).toList();
  }
  static void checkTooLong(RefParent kid){     if (kid.fearPath().length() > 200 + SourceOracle.root.length()){ throw Report.pathTooLong(kid); } }
  public static void checkIndividualVisibleSegment(RefParent kid){
    checkTooLong(kid);
    var name= Fs.fileNameWithExtension(kid.fearPath());
    int d0= name.indexOf('.');
    if (d0 == 0){ checkIndividualInvisibleSegment(kid); return; }
    if (d0 < 0){
      checkVisibleAtom(kid, name);
      if (kid instanceof Ref && !Report.allowedNoExtFiles.contains(name)){ throw Report.needsExtension(kid); }
      return;
    }
    checkVisibleAtom(kid, name.substring(0, d0));
    checkExt(kid, name.substring(d0 + 1));
  }
  private static void checkVisibleAtom(RefParent kid, String atom){
    char c0= atom.charAt(0);
    var letterOr_= c0 == '_' || ('a' <= c0 && c0 <= 'z');
    if (!letterOr_){ throw Report.visibleMustStartWithLetterOrUnderscore(kid); }
    atom.chars().skip(1).filter(c->c != '_' && !Fs.isExtSegChar((char)c)).findFirst().ifPresent(c->{ throw Report.visibleInvalidChar(kid, (char)c); });
    if (winReserved.contains(atom)){ throw Report.windowsReservedName(kid); }
  }
  private static void checkExt(RefParent kid, String tail){
    if (tail.isEmpty()){ throw Report.missingExtension(kid); }
    if (tail.indexOf('.') >= 0){
      if (!Report.allowedMultiDotExts.contains(tail)){ throw Report.multiDotExtNotAllowed(kid); }
      return;
    }
    if (tail.length() > Fs.maxExtSeg){ throw Report.extLenMustBe1To16(kid); }
    tail.chars().filter(c->!Fs.isExtSegChar((char)c)).findFirst().ifPresent(c->{ throw Report.extInvalidChar(kid, (char)c); });
  }
  private static final Set<String> winReserved= Set.of(
    "con","prn","aux","nul",
    "com1","com2","com3","com4","com5","com6","com7","com8","com9",
    "lpt1","lpt2","lpt3","lpt4","lpt5","lpt6","lpt7","lpt8","lpt9"
  );
  private static final String winBadChars="<>:\"/\\|?*";

  private static void checkIndividualInvisibleSegment(RefParent kid){
    assert isInvisible(kid);
    checkTooLong(kid);
    var name= Fs.fileNameWithExtension(kid.fearPath());
    if (name.endsWith(".") || name.endsWith(" ")){ throw Report.invisibleNoTrailingDotOrSpace(kid, name); }
    for (int i= 0; i < name.length(); ){
      int cp= name.codePointAt(i);
      if (0xD800 <= cp && cp <= 0xDFFF){ throw Report.invisibleInvalidSurrogate(kid, name); }
      if (Character.isISOControl(cp)){ throw Report.invisibleNoControlChars(kid, cp, name); }
      if (winBadChars.indexOf(cp) >= 0){ throw Report.invisibleNoWindowsBadChars(kid, (char)cp, name); }
      i += Character.charCount(cp);
    }
    int d= name.indexOf('.');
    var base= (d < 0 ? name : name.substring(0, d)).toLowerCase(Locale.ROOT);
    if (winReserved.contains(base)){ throw Report.invisibleWindowsReservedDeviceName(kid, base, name); }
  }
  private static void checkCollectiveInvisible(Collection<RefParent> kids){
    var seenKeyToName= new HashMap<String,String>();
    for (var kid: kids){ checkCollectiveInvisibleFocusOn(seenKeyToName, kid); }
  }
  private static void checkCollectiveInvisibleFocusOn(HashMap<String,String> seenKeyToName, RefParent kid){
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
    throw Report.invisibleSiblingNamesCollide(kid, prev, name, caseOnly, nfcOnly);
  }
  private static void checkCollectiveVisible(Set<RefParent> kids){
    var dotKids= new ArrayList<RefParent>();
    var visKids= new ArrayList<RefParent>();
    for (var kid: kids){ (Fs.fileNameWithExtension(kid.fearPath()).startsWith(".") ? dotKids : visKids).add(kid); }
    checkCollectiveInvisible(dotKids);
    for (var kid: visKids){
      var name= Fs.fileNameWithExtension(kid.fearPath());
      if (!(kid instanceof Ref)){ continue; } // directory
      if (Report.allowedNoExtFiles.contains(name)){ checkNoExtBaseClash(visKids, kid, name); }
    }
  }
  private static void checkNoExtBaseClash(List<RefParent> visKids, RefParent noExtKid, String base){
    visKids.stream()
      .filter(kid->!kid.equals(noExtKid) && Fs.fileNameWithExtension(kid.fearPath()).startsWith(base+"."))
      .findFirst().ifPresent(kid->{ throw Report.extensionlessMaskExtension(kid, noExtKid); });
  }
}