package realSourceOracle;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import tools.Fs;
import userMessages.Report;
import utils.Join;
import utils.OneOr;

public final class Fix{
  private interface Dir{
    List<String> names();
    boolean isDir(String name);
    boolean isFile(String name);
    Dir dir(String name);
    byte[] read(String name);
    void write(String name, byte[] bytes);
    long time(String name);
    void time(String name, long time);
    void delete(String name);
    void rename(String name, String to);
  }
  private record Disk(Path p) implements Dir{
    public List<String> names(){ return Fs.of(() -> { try (var s= Fs.list(p)){ return s.map(e -> e.getFileName().toString()).sorted().toList(); } }); }
    public boolean isDir(String name){ return Fs.isDirectory(p.resolve(name)); }
    public boolean isFile(String name){ return Fs.isRegularFile(p.resolve(name)); }
    public Dir dir(String name){ return new Disk(p.resolve(name)); }
    public byte[] read(String name){ return Fs.of(() -> Fs.readAllBytes(p.resolve(name))); }
    public void write(String name, byte[] bytes){ Fs.ofV(() -> Fs.write(p.resolve(name), bytes)); }
    public long time(String name){ return Fs.lastModified(p.resolve(name)); }
    public void time(String name, long time){ Fs.ofV(() -> Fs.setLastModifiedTime(p.resolve(name), FileTime.fromMillis(time))); }
    public void delete(String name){ Fs.rmTree(p.resolve(name)); }
    public void rename(String name, String to){ Fs.ofV(() -> move(p.resolve(name), p.resolve(to))); }
    private static void move(Path from, Path to) throws IOException{
      if (Fs.exists(to) && !Files.isSameFile(from, to)){ throw new FileAlreadyExistsException(to.toString()); }
      Fs.move(from, to, StandardCopyOption.ATOMIC_MOVE);
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
        .map(n -> n.substring(prefix.length()).split("/")[0]).distinct().sorted().toList();
    }
    public boolean isDir(String name){ return all.stream().anyMatch(e -> e.name.startsWith(prefix+name+"/")); }
    public boolean isFile(String name){ return !isDir(name); }
    public Dir dir(String name){ return new Zip(all, prefix+name+"/"); }
    public byte[] read(String name){ return file(name).bytes; }
    public void write(String name, byte[] bytes){ each(name, e -> new Stored(e.name, e.time, bytes)); }
    public long time(String name){ return file(name).time.toMillis(); }
    public void time(String name, long time){ each(name, e -> new Stored(e.name, FileTime.fromMillis(time), e.bytes)); }
    public void delete(String name){
      var gone= all.stream().filter(e -> in(e, name)).toList();
      all.removeAll(gone);
      var empty= !prefix.isEmpty() && all.stream().noneMatch(e -> e.name.startsWith(prefix));
      if (empty){ all.add(new Stored(prefix, gone.getFirst().time, new byte[0])); }
    }
    public void rename(String name, String to){ each(name, e -> new Stored(prefix+to+e.name.substring(prefix.length()+name.length()), e.time, e.bytes)); }
  }
  private final Consumer<String> out;
  private final int depth;
  private final long now;
  private Fix(Consumer<String> out, int depth, long now){ this.out= out; this.depth= depth; this.now= now; }
  public static void of(Path folder, Consumer<String> out){ new Fix(out, 0, System.currentTimeMillis()).dir(new Disk(folder), "", false); }
  private void dir(Dir d, String path, boolean inside){
    for (var name : d.names()){ entry(d, name, path, inside); }
  }
  private void entry(Dir d, String old, String path, boolean inside){
    var isDir= d.isDir(old);
    if (!isDir && !d.isFile(old)){ return; }
    if (desk(old, isDir)){ d.delete(old); out.accept("Deleted "+path+old); return; }
    var invisible= inside || old.startsWith(".");
    var name= invisible ? old : renamed(d, old, path, isDir);
    if (isDir){ folder(d, name, path+name, invisible, inside); return; }
    var time= d.time(name);
    var stale= time<=0 || time>now+BuildWithZip.futureTolerance;
    if (!invisible && name.endsWith(".fear")){ fear(d, name, path+name); }
    if (stale){ d.time(name, now); out.accept("Reset the time of "+path+name); }
    if (!invisible && name.endsWith(".zip")){ zip(d, name, path+name); }
  }
  private void folder(Dir d, String name, String full, boolean invisible, boolean inside){
    dir(d.dir(name), full+"/", invisible);
    if (inside || !d.dir(name).names().isEmpty()){ return; }
    d.delete(name);
    out.accept("Deleted the empty folder "+full);
  }
  private String renamed(Dir d, String old, String path, boolean isDir){
    var ext= isDir ? "" : "."+ext(d, old);
    var seg= segment(isDir || !old.toLowerCase(Locale.ROOT).endsWith(ext) ? old : old.substring(0, old.length()-ext.length()));
    if ((seg+ext).equals(old)){ return old; }
    var taken= d.names().stream().filter(n -> !n.equals(old)).map(Fix::clashKey).collect(Collectors.toSet());
    var res= Stream.iterate(1, i -> i+1).map(i -> seg+(i==1 ? "" : i)+ext).filter(n -> !taken.contains(clashKey(n)) && !desk(n, isDir)).findFirst().get();
    d.rename(old, res);
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
  private static String segment(String s){
    var res= s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    return res.matches("[a-z_][a-z0-9_]*") && !Fs.winReserved.contains(res) ? res : "p"+res;
  }
  private static String clashKey(String name){
    var low= name.toLowerCase(Locale.ROOT);
    return low.endsWith(".zip") ? low.substring(0, low.length()-4) : low;
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
    return utf8(bytes).orElseGet(() -> new String(bytes, Charset.forName("windows-1252")));
  }
  private static Optional<String> utf8(byte[] bytes){
    try{ return Optional.of(UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()); }
    catch (CharacterCodingException _){ return Optional.empty(); }
  }
  private void zip(Dir d, String name, String full){
    var bytes= d.read(name);
    List<Stored> read;
    try{ read = read(bytes); }
    catch (IOException _){ return; }
    var all= new ArrayList<>(read.stream().map(e -> new Stored(storedName(e.name), e.time, e.bytes)).filter(e -> !e.name.equals("/")).toList());
    var names= all.stream().map(Stored::name).toList();
    var noZip= read.isEmpty() && !new String(bytes, ISO_8859_1).startsWith("PK\5\6");
    var leave= depth>ZipEntry.maxZipNesting || !namesOk(names) || noZip;
    if (leave){ return; }
    var lines= new ArrayList<String>();
    if (!names.equals(read.stream().map(Stored::name).toList())){ lines.add("Fixed the stored names of "+full); }
    new Fix(lines::add, depth+1, now).dir(new Zip(all, ""), full.substring(0, full.length()-4)+"/", false);
    if (all.isEmpty()){ d.delete(name); lines.add("Deleted the empty zip "+full); }
    if (!all.isEmpty() && !lines.isEmpty()){ d.write(name, write(all)); }
    lines.forEach(out);
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
  private static List<Stored> read(byte[] bytes) throws IOException{
    var res= new ArrayList<Stored>();
    try (var zin= new ZipInputStream(new ByteArrayInputStream(bytes), UTF_8)){
      for (var e= zin.getNextEntry(); e != null; e = zin.getNextEntry()){
        res.add(new Stored(e.getName(), e.getLastModifiedTime(), zin.readAllBytes()));
      }
    }
    return res;
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
  private static boolean desk(String name, boolean isDir){
    if (isDir){ return name.equals("__MACOSX") || name.equals(".AppleDouble") || name.startsWith(".Trash-"); }
    return name.equals(".DS_Store") || name.startsWith("._") || name.equals("Icon\r") || name.equals(".directory")
      || List.of("thumbs.db", "ehthumbs.db", "ehthumbs_vista.db", "desktop.ini").contains(name.toLowerCase(Locale.ROOT))
      || name.endsWith(":Zone.Identifier") || name.endsWith("~") || name.startsWith("~$")
      || name.startsWith(".~lock.") && name.endsWith("#") || name.startsWith("#") && name.endsWith("#")
      || name.startsWith(".") && name.endsWith(".swp");
  }
}
