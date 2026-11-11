package realSourceOracle;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
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
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import tools.Fs;
import tools.PathBytes;
import userMessages.Report;
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
    Path away(String name, Path into);
  }
  private record Disk(Path p) implements Dir{
    public List<String> names(){
      var order= Fs.isWindows() ? Comparator.comparing(Path::toString) : Comparator.<Path>naturalOrder();
      return Fs.of(() -> { try (var s= Files.list(p)){ return s.sorted(order).map(e -> e.getFileName().toString()).toList(); } });
    }
    public Kind kind(String name){
      var at= at(name);
      var a= Fs.of(() -> Files.readAttributes(at, BasicFileAttributes.class, NOFOLLOW_LINKS));
      if (Fs.isLink(at, a)){ return Kind.other; }
      if (a.isDirectory()){ return Kind.folder; }
      return a.isRegularFile() || Fs.isWindows() && a.isOther() ? Kind.file : Kind.other;
    }
    public Dir dir(String name){ return new Disk(at(name)); }
    public byte[] read(String name){ return Fs.of(() -> Files.readAllBytes(at(name))); }
    public void write(String name, byte[] bytes){ Fs.ofV(() -> Files.write(oneName(at(name)), bytes)); }
    public long time(String name){ return Fs.of(() -> Files.getLastModifiedTime(at(name), NOFOLLOW_LINKS)).toMillis(); }
    public void time(String name, long time){ Fs.ofV(() -> Files.setLastModifiedTime(oneName(at(name)), FileTime.fromMillis(time))); }
    public void delete(String name){ Fs.ofV(() -> remove(noMountBelow(at(name)))); }
    public void rename(String name, String to){ Fs.ofV(() -> rename(at(name), p.resolve(to))); }
    public Path away(String name, Path into){
      var at= at(name);
      var to= into.resolve(at.getFileName());
      Fs.ofV(() -> Files.move(noMountBelow(at), free(to)));
      return to;
    }
    private Path at(String name){
      reachable(p, name);
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
      if (mountPoints().test(f)){ throw new FileSystemException(f.toString(), null, "a file system is mounted here"); }
      if (Fs.isWindows()){ Files.setAttribute(f, "dos:readonly", false, NOFOLLOW_LINKS); return f; }
      if ((int)Files.getAttribute(f, "unix:nlink", NOFOLLOW_LINKS) == 1){ return f; }
      throw new FileSystemException(f.toString(), null, "this file has more than one name, so writing it changes those other names too");
    }
    private static Path noMountBelow(Path f) throws IOException{
      mounts(f, mountPoints());
      return f;
    }
    private static void mounts(Path f, Predicate<Path> mounted) throws IOException{
      if (!realFolder(f)){ return; }
      if (mounted.test(f)){ throw new FileSystemException(f.toString(), null, "a file system is mounted here"); }
      try (var s= Files.list(f)){ for (var e : s.toList()){ mounts(e, mounted); } }
    }
    private static void remove(Path f) throws IOException{
      if (realFolder(f)){ try (var s= Files.list(f)){ for (var e : s.toList()){ remove(e); } } }
      if (Fs.isWindows()){ Files.setAttribute(f, "dos:readonly", false, NOFOLLOW_LINKS); }
      Files.delete(f);
    }
  }
  private record Stored(String name, FileTime time, byte[] bytes){}
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
    public void write(String name, byte[] bytes){ each(name, e -> new Stored(e.name, FileTime.fromMillis(System.currentTimeMillis()), bytes)); }
    public long time(String name){ return file(name).time.toMillis(); }
    public void time(String name, long time){ each(name, e -> new Stored(e.name, FileTime.fromMillis(time), e.bytes)); }
    public void delete(String name){
      var gone= all.stream().filter(e -> in(e, name)).toList();
      all.removeAll(gone);
      var empty= !prefix.isEmpty() && all.stream().noneMatch(e -> e.name.startsWith(prefix));
      if (empty){ all.add(new Stored(prefix, gone.getFirst().time, new byte[0])); }
    }
    public void rename(String name, String to){ each(name, e -> new Stored(prefix+to+e.name.substring(prefix.length()+name.length()), e.time, e.bytes)); }
    public Path away(String name, Path into){
      var to= resolve(into, name);
      Fs.ofV(() -> Disk.free(to));
      try{ for (var e : all.stream().filter(e -> in(e, name)).toList()){ extract(e, resolve(into, e.name.substring(prefix.length()))); } }
      catch (RuntimeException ex){
        Fs.ofV(() -> { if (Files.exists(to, NOFOLLOW_LINKS)){ Disk.remove(to); } });
        throw ex;
      }
      delete(name);
      return to;
    }
    private static void extract(Stored e, Path to){
      if (e.name.endsWith("/")){ Fs.ofV(() -> Files.createDirectories(to)); return; }
      Fs.ofV(() -> Files.createDirectories(to.getParent()));
      Fs.ofV(() -> Files.write(to, e.bytes, StandardOpenOption.CREATE_NEW));
      Fs.ofV(() -> Files.setLastModifiedTime(to, e.time));
    }
  }
  private final Consumer<String> out;
  private final int depth;
  private final Path fixFolder;
  private Fix(Consumer<String> out, int depth, Path fixFolder){ this.out= out; this.depth= depth; this.fixFolder= fixFolder; }
  public static void of(Path folder, Consumer<String> out){ new Fix(out, 0, fixFolder(folder)).dir(new Disk(folder), "", false); }
  public static Predicate<Path> mountPoints(){
    if (!Fs.isLinux()){ return p -> !Fs.of(() -> Files.getFileStore(p)).equals(Fs.of(() -> Files.getFileStore(p.getParent()))); }
    var all= new String(Fs.of(() -> Files.readAllBytes(Path.of("/proc/self/mountinfo"))), ISO_8859_1).lines().map(l -> mountPath(l.split(" ")[4])).collect(Collectors.toSet());
    return all::contains;
  }
  private static Path mountPath(String field){
    var res= new ByteArrayOutputStream();
    for (var i= 0; i<field.length(); i += 1){
      var octal= field.charAt(i)=='\\' && i+3<field.length();
      res.write(octal ? Integer.parseInt(field.substring(i+1, i+4), 8) : field.charAt(i));
      if (octal){ i += 3; }
    }
    return PathBytes.path(res.toByteArray());
  }
  public static boolean realFolder(Path p){ return Files.isDirectory(p, NOFOLLOW_LINKS) && !Fs.isLink(p); }
  private static Path fixFolder(Path folder){
    var taken= new Disk(folder.getParent()).names().stream().map(Fix::clashKey).collect(Collectors.toSet());
    var f= folder.getFileName().toString();
    return Stream.iterate(1, i -> i+1).map(i -> f+"_fix"+(i==1 ? "" : i)).filter(n -> !taken.contains(clashKey(n))).map(folder::resolveSibling).findFirst().get();
  }
  private void dir(Dir d, String path, boolean inside){
    var kept= new HashSet<String>();
    var listing= new ArrayList<>(d.names());
    for (var name : List.copyOf(listing)){ entry(d, name, path, inside, kept, listing); }
  }
  private void entry(Dir d, String old, String path, boolean inside, Set<String> kept, ArrayList<String> listing){
    var kind= d.kind(old);
    if (junk(old, kind)){ d.delete(old); listing.remove(old); out.accept("Deleted "+path+old); return; }
    if (kind == Kind.other){ away(d, old, path); listing.remove(old); return; }
    var hidden= inside || old.startsWith(".");
    var name= hidden ? old : renamed(d, old, path, kind == Kind.folder, listing);
    var tooLong= BuildWithZip.len(path+name) > (hidden ? 250 : 200);
    var badHidden= hidden && BuildWithZip.hiddenProblem(name).isPresent();
    var key= BuildWithZip.foldKey(name);
    if (tooLong || badHidden || kept.contains(key)){ away(d, name, path); listing.remove(name); return; }
    var deleted= kind == Kind.folder ? folder(d, name, path+name, hidden, inside) : file(d, name, path, hidden);
    if (deleted){ listing.remove(name); return; }
    kept.add(key);
  }
  private boolean file(Dir d, String name, String path, boolean hidden){
    if (!hidden && name.endsWith(".fear")){ fear(d, name, path+name); }
    var time= d.time(name);
    var now= System.currentTimeMillis();
    if (time<=0 || time>now+BuildWithZip.futureTolerance){ d.time(name, now); out.accept("Reset the time of "+path+name); }
    return !hidden && name.endsWith(".zip") && zip(d, name, path);
  }
  private void away(Dir d, String name, String path){
    var into= folderOf(fixFolder);
    for (var seg : Stream.of(path.split("/")).filter(s -> !s.isEmpty()).toList()){ into = folderOf(resolve(into, seg)); }
    var at= d.away(name, into);
    out.accept("Moved "+path+name+" to "+at);
  }
  private static void reachable(Path into, String name){
    var no= Fs.isWindows() && (name.endsWith(".") || name.endsWith(" ") || BuildWithZip.reserved(name));
    if (no){ throw new UncheckedIOException(new FileSystemException(into+File.separator+name, null, "Windows can not reach a name ending with a dot or a space, or a reserved device name")); }
  }
  private static Path resolve(Path into, String name){
    for (var s : name.split("/")){ reachable(into, s); }
    try{ return into.resolve(name); }
    catch (InvalidPathException e){ throw new UncheckedIOException(new FileSystemException(into+File.separator+name, null, e.getReason())); }
  }
  private static Path folderOf(Path p){
    Fs.ofV(() -> {
      if (!Files.exists(p, NOFOLLOW_LINKS)){ Files.createDirectory(p); return; }
      if (!realFolder(p)){ throw new FileAlreadyExistsException(p.toString()); }
    });
    return p;
  }
  private boolean folder(Dir d, String name, String full, boolean hidden, boolean inside){
    dir(d.dir(name), full+"/", hidden);
    if (inside || !d.dir(name).names().isEmpty()){ return false; }
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
    return res.matches("[a-z_][a-z0-9_]*") && !Fs.winReserved.contains(res) ? res : "p"+res;
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
    var bytes= d.read(name);
    var read= read(bytes);
    var all= new ArrayList<>(read.orElse(List.of()).stream().map(e -> new Stored(storedName(e.name), e.time, e.bytes)).filter(e -> !e.name.equals("/")).toList());
    var names= all.stream().map(Stored::name).toList();
    var noZip= read.isEmpty() || read.get().isEmpty() && !new String(bytes, ISO_8859_1).startsWith("PK\5\6");
    if (noZip || depth>ZipEntry.maxZipNesting || !namesOk(names)){ away(d, name, path); return true; }
    var lines= new ArrayList<String>();
    if (!names.equals(read.get().stream().map(Stored::name).toList())){ lines.add("Fixed the stored names of "+full); }
    try{ new Fix(lines::add, depth+1, fixFolder).dir(new Zip(all, ""), full.substring(0, full.length()-4)+"/", false); }
    catch (RuntimeException e){
      if (!lines.isEmpty()){ d.write(name, write(all)); }
      lines.forEach(out);
      throw e;
    }
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
        res.add(new Stored(e.getName(), e.getLastModifiedTime(), zin.readAllBytes()));
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
          entry.setLastModifiedTime(e.time);
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
