package realSourceOracle;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import tools.Fs;
import userMessages.Report;
import utils.Bug;
import utils.Join;
import utils.OneOr;

public final class Fix{
  private enum Kind{ folder, file, other }
  private interface Dir{
    List<String> names();
    Kind kind(String name);
    Dir dir(String name);
    byte[] read(String name);
    void write(String name, byte[] bytes);
    long time(String name);
    void time(String name, long time);
    void delete(String name);
    void rename(String name, String to);
    String away(String name, Path into);
  }
  private record Disk(Path p) implements Dir{
    public List<String> names(){ return names(p); }
    static List<String> names(Path p){
      var order= Fs.isWindows() ? Comparator.comparing(Path::toString) : Comparator.<Path>naturalOrder();
      return Fs.of(() -> { try (var s= Files.list(p)){ return s.sorted(order).map(e -> e.getFileName().toString()).toList(); } });
    }
    public Kind kind(String name){
      if (unreachable(name)){ return kindOf(Fs.of(() -> LongPath.attributes(LongPath.of(p, name))).orElseThrow()); }
      var at= at(name);
      var a= Fs.of(() -> Files.readAttributes(at, BasicFileAttributes.class, NOFOLLOW_LINKS));
      if (Fs.isLink(at, a)){ return Kind.other; }
      if (a.isDirectory()){ return Kind.folder; }
      return a.isRegularFile() || Fs.isWindows() && a.isOther() ? Kind.file : Kind.other;
    }
    private static Kind kindOf(int attributes){
      if ((attributes & 0x10) == 0){ return Kind.file; }
      return (attributes & 0x400) == 0 ? Kind.folder : Kind.other;
    }
    public Dir dir(String name){ return new Disk(at(name)); }
    public byte[] read(String name){
      if (!unreachable(name)){ return Fs.of(() -> Files.readAllBytes(at(name))); }
      return Fs.of(() -> {
        var mid= moveOut(p, name);
        try{ return Files.readAllBytes(mid); }
        finally{ back(mid, name); }
      });
    }
    public void write(String name, byte[] bytes){ Fs.ofV(() -> Files.write(oneName(at(name)), bytes)); }
    public long time(String name){ return Fs.of(() -> Files.getLastModifiedTime(at(name), NOFOLLOW_LINKS)).toMillis(); }
    public void time(String name, long time){ Fs.ofV(() -> Files.setLastModifiedTime(realFolder(at(name)) ? at(name) : oneName(at(name)), FileTime.fromMillis(time))); }
    public void delete(String name){
      Fs.ofV(() -> {
        var at= checked(name);
        writable(p);
        remove(at);
      });
    }
    public void rename(String name, String to){
      Fs.ofV(() -> {
        writable(p);
        if (!unreachable(name)){ rename(at(name), p.resolve(to)); return; }
        LongPath.move(LongPath.of(p, name), LongPath.of(free(p.resolve(to))));
      });
    }
    public String away(String name, Path into){
      Fs.ofV(() -> {
        var at= checked(name);
        writable(p);
        if (realFolder(at)){ writable(at); }
        if (!unreachable(name)){ Files.move(at, free(into.resolve(at.getFileName()))); return; }
        try{ LongPath.move(LongPath.of(at), LongPath.of(into, name)); }
        catch (IOException e){ back(at, name); throw e; }
      });
      return into+File.separator+name;
    }
    private Path checked(String name) throws IOException{
      var at= unreachable(name) ? moveOut(p, name) : at(name);
      try{ return noMountBelow(at); }
      catch (IOException e){ back(at, name); throw e; }
    }
    private void back(Path at, String name) throws IOException{
      if (unreachable(name)){ LongPath.move(LongPath.of(at), LongPath.of(p, name)); }
    }
    private static Path moveOut(Path folder, String name) throws IOException{
      var mid= free(folder.resolve("fix_"+UUID.randomUUID()));
      LongPath.move(LongPath.of(folder, name), LongPath.of(mid));
      return mid;
    }
    private Path at(String name){
      assert !unreachable(name);
      return resolved(name).orElseGet(() -> listed(name));
    }
    private Optional<Path> resolved(String name){
      if (stray.matcher(name).find()){ return Optional.empty(); }
      try{ return Optional.of(p.resolve(name)); }
      catch (InvalidPathException _){ return Optional.empty(); }
    }
    private Path listed(String name){
      return Fs.of(() -> { try (var s= Files.list(p)){ return s.filter(e -> e.getFileName().toString().equals(name)).sorted().findFirst().orElseThrow(); } });
    }
    static Path free(Path to) throws IOException{
      if (Files.exists(to, NOFOLLOW_LINKS)){ throw new FileAlreadyExistsException(to.toString()); }
      return to;
    }
    private static void rename(Path from, Path to) throws IOException{
      if (!Files.exists(to, NOFOLLOW_LINKS)){ Files.move(from, to); return; }
      if (!Files.isSameFile(from, to)){ throw new FileAlreadyExistsException(to.toString()); }
      var mid= free(from.resolveSibling("fix_"+UUID.randomUUID()));
      Files.move(from, mid);
      Files.move(mid, to);
    }
    private static Path oneName(Path f) throws IOException{
      noMountBelow(f);
      var names= Fs.isWindows() ? LongPath.names(LongPath.of(f)) : (int)Files.getAttribute(f, "unix:nlink", NOFOLLOW_LINKS);
      if (names != 1){ throw new FileSystemException(f.toString(), null, "this file has more than one name, so writing it changes those other names too"); }
      writable(f);
      return f;
    }
    private static void writable(Path f) throws IOException{
      if (Fs.isWindows()){
        if (!Files.isDirectory(f, NOFOLLOW_LINKS)){ Files.setAttribute(f, "dos:readonly", false, NOFOLLOW_LINKS); }
        return;
      }
      var all= Files.getPosixFilePermissions(f, NOFOLLOW_LINKS);
      if (all.add(OWNER_WRITE)){ Files.setPosixFilePermissions(f, all); }
    }
    private static Path noMountBelow(Path f) throws IOException{
      if (mountAtOrBelow(f) || holdsASource(f)){ throw new FileSystemException(f.toString(), null, "this entry, or an entry inside it, is a mount point or is also mounted at another place"); }
      return f;
    }
    private static void remove(Path f) throws IOException{
      var folder= realFolder(f);
      if (Fs.isWindows()){ Files.setAttribute(f, "dos:readonly", false, NOFOLLOW_LINKS); }
      if (folder){ writable(f); }
      if (!folder){ Files.delete(f); return; }
      try (var s= Files.list(f)){ for (var e : s.toList()){ remove(unreachable(e.getFileName().toString()) ? moveOut(f, e.getFileName().toString()) : e); } }
      Files.delete(f);
    }
  }
  private record Stored(String name, long time, byte[] bytes){}
  private record Zip(ArrayList<Stored> all, String prefix) implements Dir{
    private boolean in(Stored e, String name){ return e.name.equals(prefix+name) || e.name.startsWith(prefix+name+"/"); }
    private Stored file(String name){ return OneOr.of("", all.stream().filter(e -> e.name.equals(prefix+name))); }
    private void each(String name, UnaryOperator<Stored> f){ all.replaceAll(e -> in(e, name) ? f.apply(e) : e); }
    public List<String> names(){
      return all.stream().map(Stored::name)
        .filter(n -> n.startsWith(prefix) && n.length()>prefix.length())
        .map(n -> n.substring(prefix.length()).split("/")[0]).distinct().sorted(Zip::order).toList();
    }
    private static int order(String a, String b){ return Fs.isWindows() ? a.compareTo(b) : Arrays.compareUnsigned(a.getBytes(UTF_8), b.getBytes(UTF_8)); }
    public Kind kind(String name){ return all.stream().anyMatch(e -> e.name.startsWith(prefix+name+"/")) ? Kind.folder : Kind.file; }
    public Dir dir(String name){ return new Zip(all, prefix+name+"/"); }
    public byte[] read(String name){ return file(name).bytes; }
    private boolean is(Stored e, String name){ return e.name.equals(prefix+name) || e.name.equals(prefix+name+"/"); }
    public void write(String name, byte[] bytes){ each(name, e -> new Stored(e.name, ZipLocator.asUtc(System.currentTimeMillis()), bytes)); }
    public long time(String name){ return all.stream().filter(e -> is(e, name)).mapToLong(Stored::time).max().orElse(0); }
    public void time(String name, long time){ all.replaceAll(e -> is(e, name) ? new Stored(e.name, ZipLocator.asUtc(time), e.bytes) : e); }
    public void delete(String name){
      var gone= all.stream().filter(e -> in(e, name)).toList();
      all.removeAll(gone);
      var empty= !prefix.isEmpty() && all.stream().noneMatch(e -> e.name.startsWith(prefix));
      if (empty){ all.add(new Stored(prefix, gone.getFirst().time, new byte[0])); }
    }
    public void rename(String name, String to){ each(name, e -> new Stored(prefix+to+e.name.substring(prefix.length()+name.length()), e.time, e.bytes)); }
    public String away(String name, Path into){ throw Bug.unreachable(); }
  }
  // Fix moves nothing out of a zip file: a zip file that the changes inside it can not make ok is moved away whole.
  private static final class NotFixable extends RuntimeException{ private static final long serialVersionUID= 1L; }
  private final Consumer<String> out;
  private final int depth;
  private final Path fixFolder;
  private Fix(Consumer<String> out, int depth, Path fixFolder){ this.out= out; this.depth= depth; this.fixFolder= fixFolder; }
  public static void of(Path folder, Consumer<String> out){
    new Fix(out, 0, fixFolder(folder)).dir(new Disk(folder), "");
    if (Disk.names(folder).isEmpty()){ throw Report.fixLeftNoEntry(folder); }
    var now= System.currentTimeMillis();
    if (Fs.lastModified(folder) <= now+BuildWithZip.futureTolerance){ return; }
    Fs.ofV(() -> Files.setLastModifiedTime(folder, FileTime.fromMillis(now)));
    out.accept("Reset the time of the project folder");
  }
  private record Mount(String dev, Path root, Path point){}
  private static List<Mount> mounts(){
    return Fs.mountInfo().map(f -> new Mount(f[2], Fs.mountPath(f[3]), Fs.mountPath(f[4]))).toList();
  }
  private static Set<Path> mountSet(){
    if (Fs.isWindows()){ return Fs.of(LongPath::mountPoints); }
    return mounts().stream().map(Mount::point).collect(Collectors.toSet());
  }
  private static boolean mountAtOrBelow(Path f) throws IOException{
    if (!Fs.isMac()){ return mountSet().stream().anyMatch(m -> m.startsWith(f)); }
    if (!Files.isDirectory(f, NOFOLLOW_LINKS)){ return false; }
    if (Fs.isLink(f)){ return true; }
    try (var s= Files.list(f)){ for (var e : s.toList()){ if (mountAtOrBelow(e)){ return true; } } }
    return false;
  }
  private static boolean holdsASource(Path f){
    if (Fs.isMac()){ return Fs.of(() -> exportedInside(f)); }
    if (Fs.isWindows()){
      var real= Fs.of(() -> f.getParent().toRealPath()).resolve(f.getFileName());
      return Fs.of(LongPath::shownFolders).stream().filter(Files::exists).anyMatch(s -> Fs.of(s::toRealPath).startsWith(real));
    }
    if (!Fs.isLinux()){ return false; }
    var all= mounts();
    var on= all.stream().filter(m -> f.startsWith(m.point)).reduce((a, b) -> b.point.getNameCount() >= a.point.getNameCount() ? b : a).orElseThrow();
    var inside= on.root.resolve(on.point.relativize(f));
    return all.stream().anyMatch(m -> m != on && m.dev.equals(on.dev) && m.root.startsWith(inside));
  }
  private static boolean exportedInside(Path f) throws IOException{
    if (!realFolder(f)){ return false; }
    var own= Files.getFileStore(f);
    var server= own.name().indexOf(":/")+1;
    if (server == 0){ return false; }
    var point= f;
    while (Files.getFileStore(point.getParent()).equals(own)){ point= point.getParent(); }
    var inside= Path.of(own.name().substring(server)).resolve(point.relativize(f));
    for (var s : f.getFileSystem().getFileStores()){
      var same= !s.equals(own) && s.name().startsWith(own.name().substring(0, server));
      if (same && Path.of(s.name().substring(server)).startsWith(inside)){ return true; }
    }
    return false;
  }
  public static boolean realFolder(Path p){ return Files.isDirectory(p, NOFOLLOW_LINKS) && !Fs.isLink(p); }
  private static Path fixFolder(Path folder){
    var taken= Disk.names(folder.getParent()).stream().map(Fix::clashKey).collect(Collectors.toSet());
    var f= folder.getFileName().toString();
    return Stream.iterate(1, i -> i+1).map(i -> f+"_fix"+(i==1 ? "" : i)).filter(n -> !taken.contains(clashKey(n))).map(folder::resolveSibling).findFirst().get();
  }
  private void dir(Dir d, String path){
    var kept= new HashSet<String>();
    var listing= new ArrayList<>(d.names());
    for (var name : List.copyOf(listing)){ entry(d, name, path, kept, listing); }
  }
  private void entry(Dir d, String old, String path, Set<String> kept, ArrayList<String> listing){
    var kind= d.kind(old);
    if (junk(old, kind)){ d.delete(old); listing.remove(old); out.accept("Deleted "+path+old); return; }
    if (kind == Kind.other){ away(d, old, path); listing.remove(old); return; }
    var hidden= old.startsWith(".");
    var name= hidden ? old : renamed(d, old, path, kind == Kind.folder, listing);
    var tooLong= BuildWithZip.len(path+name) > (hidden ? 250 : 200) || !hidden && name.replaceAll("\\..*", "").length() > 191;
    var inZip= d instanceof Zip;
    var badHidden= hidden && (BuildWithZip.hiddenProblem(name).isPresent() || (kind != Kind.folder ? inZip : inZip ? !d.dir(name).names().isEmpty() : !hiddenOk(d.dir(name), path+name+"/")));
    var key= BuildWithZip.foldKey(name);
    if (tooLong || badHidden || kept.contains(key)){ away(d, name, path); listing.remove(name); return; }
    var deleted= kind == Kind.folder ? folder(d, name, path+name, hidden) : !hidden && file(d, name, path);
    if (deleted){ listing.remove(name); return; }
    kept.add(key);
  }
  // Fix changes nothing inside a hidden folder: a hidden folder holding an entry that is not ok is moved away whole.
  private static boolean hiddenOk(Dir d, String path){
    var kept= new HashSet<String>();
    for (var name : d.names()){
      var kind= d.kind(name);
      var ok= kind != Kind.other && BuildWithZip.len(path+name) <= 250 && BuildWithZip.hiddenProblem(name).isEmpty() && kept.add(BuildWithZip.foldKey(name));
      if (!ok || kind == Kind.folder && !hiddenOk(d.dir(name), path+name+"/")){ return false; }
    }
    return true;
  }
  private boolean file(Dir d, String name, String path){
    if (name.endsWith(".fear")){ fear(d, name, path+name); }
    time(d, name, path+name);
    return name.endsWith(".zip") && zip(d, name, path);
  }
  private void time(Dir d, String name, String full){
    var now= System.currentTimeMillis();
    var bad= d.time(name) > now+(d instanceof Zip ? ZipLocator.futureTolerance : BuildWithZip.futureTolerance);
    if (bad){ d.time(name, now); out.accept("Reset the time of "+full); }
  }
  private void away(Dir d, String name, String path){
    if (d instanceof Zip){ throw new NotFixable(); }
    var into= folderOf(fixFolder);
    for (var seg : Stream.of(path.split("/")).filter(s -> !s.isEmpty()).toList()){ into = folderOf(into.resolve(seg)); }
    var at= d.away(name, into);
    out.accept("Moved "+path+name+" to "+at);
  }
  private static boolean unreachable(String name){ return Fs.isWindows() && (name.endsWith(".") || name.endsWith(" ") || BuildWithZip.reserved(name)); }
  private static Path folderOf(Path p){
    Fs.ofV(() -> {
      if (!Files.exists(p, NOFOLLOW_LINKS)){ Files.createDirectory(p); return; }
      if (!realFolder(p)){ throw new FileAlreadyExistsException(p.toString()); }
    });
    return p;
  }
  private boolean folder(Dir d, String name, String full, boolean hidden){
    if (!hidden){ dir(d.dir(name), full+"/"); }
    if (!d.dir(name).names().isEmpty()){
      if (!hidden){ time(d, name, full); }
      return false;
    }
    d.delete(name);
    out.accept("Deleted the empty folder "+full);
    return true;
  }
  private String renamed(Dir d, String old, String path, boolean isDir, ArrayList<String> listing){
    var ext= isDir ? "" : "."+ext(d, old);
    var seg= segment(isDir || !old.toLowerCase(Locale.ROOT).endsWith(ext) ? old : old.substring(0, old.length()-ext.length()));
    var zipOf= !isDir && old.endsWith(".zip") && listing.contains(old.substring(0, old.length()-4));
    if ((seg+ext).equals(old) && !zipOf){ return old; }
    var taken= listing.stream().filter(n -> !n.equals(old)).map(Fix::clashKey).collect(Collectors.toSet());
    var res= Stream.iterate(1, i -> i+1).map(i -> seg+(i==1 ? "" : i)).filter(s -> !Fs.winReserved.contains(s)).map(s -> s+ext)
      .filter(n -> !taken.contains(clashKey(n)) && !junk(n, isDir ? Kind.folder : Kind.file)).findFirst().get();
    d.rename(old, res);
    listing.set(listing.indexOf(old), res);
    out.accept("Renamed "+path+old+" to "+res);
    return res;
  }
  private static String ext(Dir d, String name){
    var low= name.toLowerCase(Locale.ROOT);
    var last= name.substring(name.lastIndexOf('.')+1);
    return OneOr.opt("", Report.allowedMultiDotExts.stream().filter(e -> low.endsWith("."+e)))
      .or(() -> Optional.of(last.toLowerCase(Locale.ROOT)).filter(_ -> last.length()<name.length() && last.matches("[a-zA-Z0-9]{1,16}")))
      .orElseGet(() -> guess(d.read(name)));
  }
  private static String guess(byte[] bytes){
    var head= new String(bytes, 0, Math.min(8, bytes.length), ISO_8859_1);
    if (head.equals("\u0089PNG\r\n\u001A\n")){ return "png"; }
    if (head.startsWith("\u00FF\u00D8\u00FF")){ return "jpg"; }
    if (head.startsWith("GIF87a") || head.startsWith("GIF89a")){ return "gif"; }
    var text= utf8(bytes).filter(s -> s.chars().noneMatch(c -> Character.isISOControl(c) && "\t\n\r".indexOf(c)<0));
    return text.isPresent() ? "txt" : "bin";
  }
  public static String segment(String s){
    var res= s.replaceAll("[^A-Za-z0-9_]+", "_").toLowerCase(Locale.ROOT);
    var ok= res.matches("[a-z_][a-z0-9_]*") && !Fs.winReserved.contains(res) && (!res.startsWith("_") || s.startsWith("_"));
    return ok ? res : "p"+res;
  }
  private static final Pattern stray= Pattern.compile("[\\uFFFD\\p{Cs}]");
  private static String clashKey(String name){
    var key= BuildWithZip.foldKey(name);
    return key.endsWith(".zip") ? key.substring(0, key.length()-4) : key;
  }
  private void fear(Dir d, String name, String full){
    var old= d.read(name);
    var text= text(old);
    var noMark= text.startsWith("\uFEFF") ? text.substring(1) : text;
    var lf= noMark.replaceAll("\r(\\uFEFF*\n)?|[\\x0B\\f\\x85\\u2028\\u2029]", "\n");
    var cleaned= lf.codePoints().filter(c -> !str(c)).count();
    var ascii= lf.replaceAll("[\\u2018\\u2019]", "'").replaceAll("[\\u201C\\u201D]", "\"").replaceAll("[\\u2013\\u2014\\u2212]", "-").replace('\t', ' ');
    var res= Normalizer.normalize(ascii, Normalizer.Form.NFKD).codePoints().filter(Fix::str)
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().getBytes(UTF_8);
    if (!Arrays.equals(res, old)){ d.write(name, res); }
    if (noMark.length() != text.length()){ out.accept("Removed the byte order mark of "+full); }
    if (!lf.equals(noMark)){ out.accept("Fixed the line endings of "+full); }
    if (cleaned > 0){ out.accept("Cleaned "+cleaned+(cleaned==1 ? " character of " : " characters of ")+full); }
  }
  private static boolean str(int c){ return c=='\n' || c>=' ' && c<='~'; }
  private static String text(byte[] bytes){
    var head= new String(bytes, 0, Math.min(2, bytes.length), ISO_8859_1);
    if (head.equals("\u00FF\u00FE")){ return new String(bytes, UTF_16LE); }
    if (head.equals("\u00FE\u00FF")){ return new String(bytes, UTF_16BE); }
    return utf8(bytes).orElseGet(() -> new String(bytes, Charset.forName("windows-1252")).replaceFirst("^\u00EF\u00BB\u00BF", "\uFEFF"));
  }
  private static Optional<String> utf8(byte[] bytes){
    try{ return Optional.of(UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()); }
    catch (CharacterCodingException _){ return Optional.empty(); }
  }
  private boolean zip(Dir d, String name, String path){
    var full= path+name;
    var tooBig= d instanceof Disk disk && Fs.of(() -> Files.size(disk.at(name))) > ZipLocator.maxBytes;
    if (tooBig){ away(d, name, path); return true; }
    var bytes= d.read(name);
    var times= ZipLocator.times(bytes);
    var read= times.flatMap(_ -> read(bytes));
    var all= new ArrayList<>(read.orElse(List.of()).stream().map(e -> new Stored(storedName(e.name), times.map(m -> m.get(e.name)).orElse(0L), e.bytes)).filter(e -> !e.name.equals("/")).toList());
    var names= all.stream().map(Stored::name).toList();
    var noZip= read.isEmpty() || read.get().isEmpty() && !new String(bytes, ISO_8859_1).startsWith("PK\5\6");
    if (noZip || depth>ZipEntry.maxZipNesting || !namesOk(names)){ away(d, name, path); return true; }
    var lines= new ArrayList<String>();
    if (!names.equals(read.get().stream().map(Stored::name).toList())){ lines.add("Fixed the stored names of "+full); }
    try{ new Fix(lines::add, depth+1, fixFolder).dir(new Zip(all, ""), full.substring(0, full.length()-4)+"/"); }
    catch (NotFixable _){ away(d, name, path); return true; }
    if (all.isEmpty()){ d.delete(name); lines.add("Deleted the empty zip "+full); }
    if (!all.isEmpty() && !lines.isEmpty()){ d.write(name, write(all)); }
    lines.forEach(out);
    return all.isEmpty();
  }
  private static String storedName(String name){
    var s= name.replace('\\', '/');
    return Join.of(Stream.of(s.split("/")).filter(e -> !e.isEmpty() && !e.equals(".")), "", "/", "", "")+(s.endsWith("/") ? "/" : "");
  }
  private static boolean namesOk(List<String> names){
    var keys= names.stream().map(n -> n.endsWith("/") ? n.substring(0, n.length()-1) : n).toList();
    var files= names.stream().filter(n -> !n.endsWith("/")).collect(Collectors.toSet());
    return new HashSet<>(keys).size()==keys.size()
      && keys.stream().allMatch(n -> !n.isEmpty() && n.indexOf('\0')<0 && Stream.of(n.split("/")).noneMatch(s -> s.equals("..")))
      && keys.stream().flatMap(Fix::above).noneMatch(files::contains);
  }
  private static Stream<String> above(String name){
    return IntStream.range(0, name.length()).filter(i -> name.charAt(i) == '/').mapToObj(i -> name.substring(0, i));
  }
  private static Optional<List<Stored>> read(byte[] bytes){
    var res= new ArrayList<Stored>();
    try (var zin= new ZipInputStream(new ByteArrayInputStream(bytes), UTF_8)){
      for (var e= zin.getNextEntry(); e != null; e = zin.getNextEntry()){
        res.add(new Stored(e.getName(), 0, zin.readAllBytes()));
      }
    }
    catch (IOException | IllegalArgumentException _){ return Optional.empty(); }
    return Optional.of(res);
  }
  private static byte[] write(List<Stored> all){
    var res= new ByteArrayOutputStream();
    Fs.ofV(() -> {
      try (var zout= new ZipOutputStream(res, UTF_8)){
        for (var e : all){
          var entry= new java.util.zip.ZipEntry(e.name);
          entry.setTimeLocal(LocalDateTime.ofEpochSecond(e.time/1000, 0, ZoneOffset.UTC));
          zout.putNextEntry(entry);
          zout.write(e.bytes);
        }
      }
    });
    return res.toByteArray();
  }
  private static boolean junk(String name, Kind kind){
    if (kind == Kind.folder){ return name.equals("__MACOSX") || name.equals(".AppleDouble") || name.startsWith(".Trash-"); }
    if (kind != Kind.file){ return false; }
    return name.equals(".DS_Store") || name.startsWith("._") || name.equals("Icon\r") || name.equals(".directory")
      || name.matches("(?i)thumbs\\.db|ehthumbs(_vista)?\\.db|desktop\\.ini")
      || name.endsWith(":Zone.Identifier") || name.endsWith("~") || name.startsWith("~$")
      || name.startsWith(".~lock.") && name.endsWith("#") || name.startsWith("#") && name.endsWith("#")
      || name.startsWith(".") && name.endsWith(".swp");
  }
}
