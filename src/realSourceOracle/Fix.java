package realSourceOracle;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import tools.Fs;

public final class Fix{
  private record Stored(String name, boolean dir, FileTime time, byte[] bytes){}
  private static final byte[] noFile= {};
  private final Consumer<String> out;
  private final long now= System.currentTimeMillis();
  private final ArrayList<String> lines= new ArrayList<>();
  private Fix(Consumer<String> out){ this.out= out; }
  public static void of(Path folder, Consumer<String> out){ new Fix(out).dir(folder, "", false); }
  private void dir(Path d, String path, boolean invisible){
    for (var p : Fs.of(() -> { try (var s= Fs.list(d)){ return s.sorted(Comparator.comparing(Path::toString)).toList(); } })){
      entry(p, path+p.getFileName(), invisible || p.getFileName().toString().startsWith("."));
      lines.forEach(out);
      lines.clear();
    }
  }
  private void entry(Path p, String path, boolean invisible){
    var isDir= Fs.isDirectory(p);
    if (!isDir && !Fs.isRegularFile(p)){ return; }
    if (desk(p.getFileName().toString(), isDir)){ Fs.rmTree(p); lines.add("Deleted "+path); return; }
    if (isDir){ folder(p, path, invisible); return; }
    if (!invisible && !content(p, path)){ return; }
    if (!stale(Fs.lastModified(p))){ return; }
    Fs.ofV(() -> Fs.setLastModifiedTime(p, FileTime.fromMillis(now)));
    lines.add("Reset the time of "+path);
  }
  private void folder(Path p, String path, boolean invisible){
    dir(p, path+"/", invisible);
    var empty= !invisible && Fs.of(() -> { try (var s= Fs.list(p)){ return s.findAny().isEmpty(); } });
    if (!empty){ return; }
    Fs.ofV(() -> Fs.delete(p));
    lines.add("Deleted the empty folder "+path);
  }
  private boolean content(Path p, String path){
    if (!path.endsWith(".fear") && !path.endsWith(".zip")){ return true; }
    var old= Fs.of(() -> Fs.readAllBytes(p));
    var res= content(path, old, 0);
    if (res == old){ return true; }
    if (res != noFile){ Fs.ofV(() -> Fs.write(p, res)); return true; }
    Fs.ofV(() -> Fs.delete(p));
    lines.add("Deleted the empty zip "+path);
    return false;
  }
  private byte[] content(String path, byte[] bytes, int depth){
    if (path.endsWith(".fear")){ return fear(path, bytes); }
    if (path.endsWith(".zip")){ return zip(path.substring(0, path.length()-4)+"/", bytes, depth); }
    return bytes;
  }
  private byte[] fear(String path, byte[] bytes){
    var s= new String(bytes, ISO_8859_1);
    var noMark= s.startsWith("\u00EF\u00BB\u00BF") ? s.substring(3) : s;
    if (noMark.length() != s.length()){ lines.add("Removed the byte order mark of "+path); }
    var res= noMark.replace("\r\n", "\n");
    if (res.length() != noMark.length()){ lines.add("Fixed the line endings of "+path); }
    return res.length()==s.length() ? bytes : res.getBytes(ISO_8859_1);
  }
  private byte[] zip(String path, byte[] bytes, int depth){
    if (depth > ZipEntry.maxZipNesting){ return bytes; }
    List<Stored> all;
    try{ all = read(bytes); }
    catch (IOException _){ return bytes; }
    var endOnly= bytes.length>=4 && Arrays.equals(bytes, 0, 4, new byte[]{'P', 'K', 5, 6}, 0, 4);
    if (all.isEmpty()){ return endOnly ? noFile : bytes; }
    if (!namesOk(all)){ return bytes; }
    var said= lines.size();
    var gone= new HashSet<String>();
    var folders= new TreeSet<String>(Comparator.reverseOrder());
    var kept= new ArrayList<Stored>();
    for (var e : all){
      var desk= deskPrefix(e);
      above(desk.orElse(e.name)).forEach(folders::add);
      if (desk.isPresent()){
        if (gone.add(desk.get())){ lines.add("Deleted "+path+desk.get()); }
        continue;
      }
      if (e.dir){ folders.add(e.name); kept.add(e); continue; }
      var res= visible(e.name) ? content(path+e.name, e.bytes, depth+1) : e.bytes;
      if (res == noFile){ lines.add("Deleted the empty zip "+path+e.name); continue; }
      var reset= stale(e.time.toMillis());
      if (reset){ lines.add("Reset the time of "+path+e.name); }
      kept.add(new Stored(e.name, false, reset ? FileTime.fromMillis(now) : e.time, res));
    }
    var full= kept.stream().filter(e -> !e.dir).flatMap(e -> above(e.name)).collect(Collectors.toSet());
    folders.stream().filter(f -> !full.contains(f)).forEach(f -> lines.add("Deleted the empty folder "+path+f));
    kept.removeIf(e -> e.dir && !full.contains(e.name));
    if (lines.size() == said){ return bytes; }
    return kept.isEmpty() ? noFile : write(kept);
  }
  private static List<Stored> read(byte[] bytes) throws IOException{
    var res= new ArrayList<Stored>();
    try (var zin= new ZipInputStream(new ByteArrayInputStream(bytes), UTF_8)){
      for (var e= zin.getNextEntry(); e != null; e = zin.getNextEntry()){
        var n= e.getName();
        var dir= n.endsWith("/");
        res.add(new Stored(dir ? n.substring(0, n.length()-1) : n, dir, e.getLastModifiedTime(), zin.readAllBytes()));
      }
    }
    return res;
  }
  private static byte[] write(List<Stored> kept){
    var res= new ByteArrayOutputStream();
    Fs.ofV(() -> {
      try (var zout= new ZipOutputStream(res, UTF_8)){
        for (var e : kept){
          var entry= new java.util.zip.ZipEntry(e.dir ? e.name+"/" : e.name);
          entry.setLastModifiedTime(e.time);
          zout.putNextEntry(entry);
          zout.write(e.bytes);
        }
      }
    });
    return res.toByteArray();
  }
  private static boolean namesOk(List<Stored> all){
    var names= all.stream().map(Stored::name).toList();
    var files= all.stream().filter(e -> !e.dir).map(Stored::name).collect(Collectors.toSet());
    return new HashSet<>(names).size()==names.size()
      && names.stream().allMatch(n -> n.indexOf('\0')<0 && Stream.of(n.split("/", -1)).noneMatch(s -> s.isEmpty() || s.equals(".") || s.equals("..")))
      && names.stream().flatMap(Fix::above).noneMatch(files::contains);
  }
  private static Optional<String> deskPrefix(Stored e){
    return Stream.concat(above(e.name), Stream.of(e.name))
      .filter(p -> desk(p.substring(p.lastIndexOf('/')+1), e.dir || p.length()<e.name.length()))
      .findFirst();
  }
  private static boolean visible(String name){ return Stream.of(name.split("/")).noneMatch(s -> s.startsWith(".")); }
  private static boolean desk(String name, boolean isDir){
    if (isDir){ return name.equals("__MACOSX") || name.startsWith(".Trash-"); }
    var low= name.toLowerCase(Locale.ROOT);
    return name.equals(".DS_Store") || name.startsWith("._") || name.equals("Icon\r") || name.equals(".directory")
      || low.equals("thumbs.db") || low.equals("desktop.ini") || name.endsWith("~");
  }
  private boolean stale(long t){ return t<=0 || t>now+BuildWithZip.futureTolerance; }
  private static Stream<String> above(String name){
    return IntStream.range(0, name.length()).filter(i -> name.charAt(i) == '/').mapToObj(i -> name.substring(0, i));
  }
}
