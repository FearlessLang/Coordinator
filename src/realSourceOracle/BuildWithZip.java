package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;

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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import tools.Fs;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import tools.SourceOracle.RefParent;
import userMessages.Report;
import userMessages.UserError;

public final class BuildWithZip{
  public static boolean isInvisible(RefParent r){
    return AutoloadHandler.components(r.fearPath()).stream().anyMatch(s -> s.startsWith("."));
  }
  private final Path root;
  private final boolean dated;
  private final Since since;
  private final ArrayList<Ref> visibleFiles= new ArrayList<>();
  private final LinkedHashMap<RefParent,LinkedHashSet<RefParent>> visKidsByDir= new LinkedHashMap<>();
  private final LinkedHashMap<RefParent,LinkedHashSet<RefParent>> dotKidsByDir= new LinkedHashMap<>();
  BuildWithZip(Path root, boolean dated, Since since){
    this.root= root.toAbsolutePath().normalize();
    this.dated= dated;
    this.since= since;
  }
  List<Ref> build(){
    reqNoEmptyDirs();
    Fs.walkV(root, s -> s
      .filter(p -> !p.equals(root))
      .filter(p -> !Fs.isDirectory(p))
      .forEach(this::collectFile)
    );
    visKidsByDir.forEach((_,kids) -> {
      kids.forEach(BuildWithZip::checkIndividualVisibleSegment);
      checkCollectiveVisible(kids);
    });
    dotKidsByDir.forEach((_,kids) -> {
      kids.forEach(BuildWithZip::checkIndividualInvisibleSegment);
      checkCollectiveInvisible(kids);
    });
    return visibleFiles.stream().sorted(Comparator.comparing(Ref::fearPath)).toList();
  }
  private void collectFile(Path abs){
    var rel= root.relativize(abs);
    var pe= new PathEntry(root, rel, since);
    var invisible= isInvisible(pe);
    if (!Fs.isRegularFile(abs)){ throw invisible
      ? Report.invisibleOnlyRegularFilesAndDirs(abs)
      : Report.onlyRegularFilesAndDirs(abs);
    }
    if (!invisible && dated){ reqTime(abs, rel); }
    if (!invisible && rel.getFileName().toString().endsWith(".zip")){ reqNoSiblingForZipName(rel); collectBodyDiskZip(rel); return; }
    addKids(pe);
    if (!invisible){ visibleFiles.add(pe); }
  }
  private void reqNoSiblingForZipName(Path rel){
    var siblingRel= Path.of(AutoloadHandler.dropExt(rel.toString()));
    var sibling= root.resolve(siblingRel);
    if (!Fs.exists(sibling)){ return; }
    throw Report.zipNameClashes(new PathEntry(root, rel), new PathEntry(root, siblingRel), Fs.isDirectory(sibling) ? "folder" : "plain file");
  }
  private void reqNoEmptyDirs(){
    var dirs= new LinkedHashSet<Path>();
    var nonEmpty= new LinkedHashSet<Path>();
    dirs.add(Path.of(""));
    Fs.walkV(root, s -> s.filter(p -> !p.equals(root)).forEach(abs -> {
      var rel= root.relativize(abs);
      nonEmpty.add(rel.resolveSibling(""));
      if (Fs.isDirectory(abs)){ dirs.add(rel); }
    }));
    dirs.removeAll(nonEmpty);
    for (var d : dirs){
      if (!isInvisible(new PathEntry(root, d.resolveSibling("")))){ throw Report.emptyDirectory(d); }
      addKids(new PathEntry(root, d, since));
    }
  }
  private static void reqTime(Path abs, Path rel){
    var t= Fs.lastModified(abs);
    BuildWithZip.reqTime(t, () -> Report.dateUnset(rel, t), now -> Report.dateInTheFuture(rel, t, now));
  }
  private void collectBodyDiskZip(Path rel){
    for (var e : since.read(root.resolve(rel), () -> ZipEntry.allEntryPaths(root, rel, since))){
      if (e.expanded()){ continue; }//expanded
      addKids(e);
      if (!isInvisible(e)){ visibleFiles.add(e); }
    }
  }
  private void addKids(RefParent leaf){
    for (var p= leaf; p.parent() != p; p = p.parent()){
      var m= isInvisible(p.parent()) ? dotKidsByDir : visKidsByDir;
      m.computeIfAbsent(p.parent(), _ -> new LinkedHashSet<>()).add(p);
    }
  }
  static final long futureTolerance= 5_000;
  static void reqTime(long t, Supplier<UserError> unset, LongFunction<UserError> future){
    if (t <= 0){ throw unset.get(); }
    var now= System.currentTimeMillis();
    if (t > now+futureTolerance){ throw future.apply(now); }
  }
  static void checkTooLong(RefParent kid){
    var limit= isInvisible(kid) ? 250 : 200;
    if (len(kid.fearPath().substring(SourceOracle.root.length())) > limit){ throw Report.pathTooLong(kid, limit); }
  }
  static int len(String path){ return Fs.isWindows() ? path.length() : path.getBytes(UTF_8).length; }
  public static String foldKey(String name){
    return Normalizer.normalize(name, Form.NFC).codePoints().map(c -> c==0x130 || c==0x131 ? c : Character.toLowerCase(Character.toUpperCase(c)))
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }
  public static void checkIndividualVisibleSegment(RefParent kid){
    checkTooLong(kid);
    var name= Fs.fileNameWithExtension(kid.fearPath());
    var d0= name.indexOf('.');
    if (d0 == 0){ checkIndividualInvisibleSegment(kid); return; }
    var split= d0>0 && kid instanceof Ref;
    checkVisibleAtom(kid, split ? name.substring(0, d0) : name);
    if (split){ checkExt(kid, name.substring(d0 + 1)); return; }
    if (kid instanceof Ref){ throw Report.needsExtension(kid); }
  }
  private static void checkVisibleAtom(RefParent kid, String atom){
    var c0= atom.charAt(0);
    if (c0!='_' && !('a'<=c0 && c0<='z')){ throw Report.visibleMustStartWithLetterOrUnderscore(kid); }
    atom.chars().skip(1).filter(c -> c!='_' && !Fs.isExtSegChar((char)c)).findFirst().ifPresent(c -> { throw Report.visibleInvalidChar(kid, (char)c); });
    if (Fs.winReserved.contains(atom)){ throw Report.windowsReservedName(kid); }
  }
  private static void checkExt(RefParent kid, String tail){
    if (tail.isEmpty()){ throw Report.missingExtension(kid); }
    if (Report.allowedMultiDotExts.contains(tail)){ return; }
    if (tail.indexOf('.') >= 0){ throw Report.multiDotExtNotAllowed(kid); }
    if (tail.length() > Fs.maxExtSeg){ throw Report.extLenMustBe1To16(kid); }
    tail.chars().filter(c -> !Fs.isExtSegChar((char)c)).findFirst().ifPresent(c -> { throw Report.extInvalidChar(kid, (char)c); });
  }
  private static final String winBadChars= "<>:\"/\\|?*";
  private static void checkIndividualInvisibleSegment(RefParent kid){
    assert isInvisible(kid);
    checkTooLong(kid);
    hiddenProblem(Fs.fileNameWithExtension(kid.fearPath())).ifPresent(f -> { throw f.apply(kid); });
  }
  static Optional<Function<RefParent,UserError>> hiddenProblem(String name){
    if (name.endsWith(".") || name.endsWith(" ")){ return Optional.of(k -> Report.invisibleNoTrailingDotOrSpace(k, name)); }
    for (int cp : name.codePoints().toArray()){
      if (0xD800<=cp && cp<=0xDFFF || cp==0xFFFD){ return Optional.of(k -> Report.invisibleInvalidSurrogate(k, name)); }
      if (Character.isISOControl(cp)){ return Optional.of(k -> Report.invisibleNoControlChars(k, cp, name)); }
      if (winBadChars.indexOf(cp) >= 0){ return Optional.of(k -> Report.invisibleNoWindowsBadChars(k, (char)cp, name)); }
    }
    var d= name.indexOf('.');
    var base= (d<0 ? name : name.substring(0, d)).stripTrailing().toLowerCase(Locale.ROOT);
    if (Fs.winReserved.contains(base) || moreReserved.matcher(base).matches()){ return Optional.of(k -> Report.invisibleWindowsReservedDeviceName(k, base, name)); }
    if (name.getBytes(UTF_8).length > 255){ return Optional.of(k -> Report.invisibleNameTooLong(k, name)); }
    return Optional.empty();
  }
  private static final Pattern moreReserved= Pattern.compile("conin\\$|conout\\$|(com|lpt)[\\u00B9\\u00B2\\u00B3]");
  private static void checkCollectiveInvisible(Collection<RefParent> kids){
    var seenKeyToName= new HashMap<String,String>();
    for (var kid : kids){ checkCollectiveInvisibleFocusOn(seenKeyToName, kid); }
  }
  private static void checkCollectiveInvisibleFocusOn(HashMap<String,String> seenKeyToName, RefParent kid){
    var name= Fs.fileNameWithExtension(kid.fearPath());
    var lowName= name.toLowerCase(Locale.ROOT);
    var nfc= Normalizer.normalize(name, Form.NFC);
    var prev= seenKeyToName.putIfAbsent(foldKey(name), name);
    if (prev == null){ return; }
    var lowPrev= prev.toLowerCase(Locale.ROOT);
    var prevNfc= Normalizer.normalize(prev, Form.NFC);
    var caseOnly= lowPrev.equals(lowName) && !prevNfc.equals(nfc);
    var nfcOnly= prevNfc.equals(nfc) && !lowPrev.equals(lowName);
    throw Report.invisibleSiblingNamesCollide(kid, prev, name, caseOnly, nfcOnly);
  }
  private static void checkCollectiveVisible(Set<RefParent> kids){
    checkCollectiveInvisible(kids.stream().filter(kid -> Fs.fileNameWithExtension(kid.fearPath()).startsWith(".")).toList());
  }
}
