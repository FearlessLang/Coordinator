package realSourceOracle;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import coordinatorMessages.UserExit;
import tools.Fs;

class Build{
  private final Path root;
  Build(Path root){ this.root= root.toAbsolutePath().normalize(); }
  protected void walkRoot(Consumer<Path> f){ Fs.walkV(root, s-> s.filter(p->!p.equals(root)).forEach(f)); }
  protected Path relativize(Path abs){ return root.relativize(abs); }
  protected Path resolve(Path rel){ return root.resolve(rel); }
  protected URI uriOf(Path abs){ return abs.toUri().normalize(); }
  protected boolean isSymbolicLink(Path abs){ return Files.isSymbolicLink(abs); }
  protected boolean isDirectory(Path abs){ return Files.isDirectory(abs, LinkOption.NOFOLLOW_LINKS); }
  protected boolean isRegularFile(Path abs){ return Files.isRegularFile(abs, LinkOption.NOFOLLOW_LINKS); }

  private final Map<URI,Path> files= new TreeMap<>(Comparator.comparing(URI::toString));
  private final Map<Path,List<Path>> visKidsByDir= new TreeMap<>(Comparator.comparing(Path::toString));
  private final Map<Path,List<Path>> dotKidsByDir= new TreeMap<>(Comparator.comparing(Path::toString));  
  Map<URI,Path>  build(){
    walkRoot(this::collect);
    visKidsByDir.forEach((_,kids)->{
      kids.forEach(kid->checkIndividualVisibleSegment(kid));
      checkCollectiveVisible(kids);
    });
    dotKidsByDir.forEach((_,kids)->{
      kids.forEach(kid->checkIndividualInvisibleSegment(kid));
      checkCollectiveInvisible(kids);
    });
    return Collections.unmodifiableMap(files);
  }
  private void collect(Path abs){
    var rel= relativize(abs);
    var dir= rel.getParent(); if (dir == null){ dir= relativize(root); }
    var m= isInvisible(dir) ? dotKidsByDir : visKidsByDir;
    m.computeIfAbsent(dir, _->new ArrayList<>()).add(rel);
    if (isInvisible(rel)){ return; }
    if (!isRegularFile(abs)){ return; }
    var u= uriOf(abs);
    var res= files.putIfAbsent(u, abs);
    assert res == null;
  }
  private boolean isInvisible(Path relDir){ return firstDotStartIndex(relDir) < relDir.getNameCount(); }
  private int firstDotStartIndex(Path rel){
    return IntStream.range(0, rel.getNameCount())
      .filter(i->rel.getName(i).toString().startsWith("."))
      .findFirst().orElse(rel.getNameCount());
  }
  private void checkIndividualVisibleSegment(Path kid){
    if(kid.toString().length()>200){ throw UserExit.pathTooLong(kid); }
    var name= kid.getFileName().toString();
    if (name.startsWith(".")){ checkIndividualInvisibleSegment(kid); return; }
    var abs= resolve(kid);
    if (isSymbolicLink(abs)){ throw UserExit.symlinkForbidden(kid); }
    boolean isDir= isDirectory(abs);
    boolean isFile= isRegularFile(abs);
    if (!isDir && !isFile){ throw UserExit.onlyRegularFilesAndDirs(kid); }
    var validNoExt= isDir || UserExit.allowedNoExtFilesS.contains(name);
    var badNoExt= isFile && !name.contains(".") && !validNoExt;
    if (badNoExt){ throw UserExit.needsExtension(kid); }
    if(validNoExt){ checkVisibleAtom(kid, name); return; }
    int d0= name.indexOf('.');
    assert d0 > 0;
    checkVisibleAtom(kid, name.substring(0,d0));
    checkExt(kid, name.substring(d0+1,name.length()));
  }
  private void checkVisibleAtom(Path kid, String atom){
    assert !atom.isEmpty();
    char c0= atom.charAt(0);
    if (!(c0=='_' || ('a'<=c0 && c0<='z'))){ throw UserExit.visibleMustStartWithLetterOrUnderscore(kid); }
    for (int i= 1; i < atom.length(); i++){
      char c= atom.charAt(i);
      boolean ok= ('a' <= c && c <= 'z')||('0' <= c && c <= '9')||c == '_';
      if (!ok){ throw UserExit.visibleInvalidChar(kid, c); }
      if (c == '_' && atom.charAt(i-1) == '_'){ throw UserExit.visibleNoDoubleUnderscore(kid); }
    }
    if (winReserved.contains(atom)){ throw UserExit.windowsReservedName(kid); }
  }
  private void checkExt(Path kid, String tail){
    if (tail.isEmpty()){ throw UserExit.missingExtension(kid); }
    int d= tail.indexOf('.');
    if (d < 0){ checkExtSeg(kid, tail); return; }
    if (!UserExit.allowedMultiDotExtsS.contains(tail)){ throw UserExit.multiDotExtNotAllowed(kid); }
  }
  private void checkExtSeg(Path kid, String seg){
    int n= seg.length();
    if (n < 1 || n > 16){ throw UserExit.extLenMustBe1To16(kid); }
    for (int i= 0; i < n; i++){
      char c= seg.charAt(i);
      boolean ok= ('a' <= c && c <= 'z')||('0' <= c && c <= '9');
      if (!ok){ throw UserExit.extInvalidChar(kid, c); }
    }
  }
  private static final Set<String> winReserved=Set.of(
    "con","prn","aux","nul",
    "com1","com2","com3","com4","com5","com6","com7","com8","com9",
    "lpt1","lpt2","lpt3","lpt4","lpt5","lpt6","lpt7","lpt8","lpt9"
    );
  private static final String winBadChars="<>:\"/\\|?*";
  private void checkIndividualInvisibleSegment(Path kid){
    assert isInvisible(kid);
    if(kid.toString().length()>200){ throw UserExit.pathTooLong(kid); }
    var abs= resolve(kid);
    if (isSymbolicLink(abs)){ throw UserExit.invisibleSymlinkForbidden(kid); }
    boolean isDir= isDirectory(abs);
    boolean isFile= isRegularFile(abs);
    if (!isDir && !isFile){ throw UserExit.invisibleOnlyRegularFilesAndDirs(kid); }
    var name= kid.getFileName().toString();
    assert !name.isEmpty();
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
  private void checkCollectiveInvisible(List<Path> kids){
    var seenKeyToName= new HashMap<String,String>();
    for (var kid: kids){ checkCollectiveInvisibleFocusOn(seenKeyToName, kid); }
  }
  private void checkCollectiveInvisibleFocusOn(HashMap<String, String> seenKeyToName, Path kid) {
    var name= kid.getFileName().toString();
    var lowName= name.toLowerCase(Locale.ROOT);
    var nfc= Normalizer.normalize(name, Form.NFC);
    var key= nfc.toLowerCase(Locale.ROOT);
    var prev= seenKeyToName.putIfAbsent(key, name);
    if (prev == null){ return; }
    var lowPrev= prev.toLowerCase(Locale.ROOT);
    var prevNfc= Normalizer.normalize(prev, Form.NFC);
    boolean caseOnly= lowPrev.equals(lowName) && !prevNfc.equals(nfc);
    boolean nfcOnly= prevNfc.equals(nfc) && !lowPrev.equals(lowName);
    throw UserExit.hiddenSiblingNamesCollide(kid, prev, name, caseOnly,nfcOnly);
  }
  private void checkCollectiveVisible(List<Path> kids){
    var dotKids= new ArrayList<Path>();
    var visKids= new ArrayList<Path>();
    for (var kid: kids){ (kid.getFileName().toString().startsWith(".") ? dotKids : visKids).add(kid);  }
    if (!dotKids.isEmpty()){ checkCollectiveInvisible(dotKids); }
    for (var kid: visKids){
      var name= kid.getFileName().toString();
      if (name.indexOf('.') >= 0){ continue; }
      if (!UserExit.allowedNoExtFilesS.contains(name)){ continue; }
      if (!isRegularFile(resolve(kid))){ continue; }//is directory
      checkNoExtBaseClash(visKids, kid, name);
    }
  }
  private void checkNoExtBaseClash(List<Path> visKids, Path noExtKid, String base){
    for (var kid: visKids){
      if (kid.equals(noExtKid)){ continue; }
      var name= kid.getFileName().toString();
      int d0= name.indexOf('.');
      if (d0 < 0){ continue; }
      if (!name.substring(0, d0).equals(base)){ continue; }
      throw UserExit.extensionlessMaskExtension(kid, noExtKid);
    }
  }
}