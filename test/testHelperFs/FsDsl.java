package testHelperFs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import coordinatorMessages.UserExit;
import realSourceOracle.RealSourceOracleWithZip;
import tools.SourceOracle;

public final class FsDsl{
  record Item(String name,String content){}
  private record Leaf(String name,String content,boolean isDir){}
  private static final class ZipNode{
    final LinkedHashMap<String,ZipNode> nested= new LinkedHashMap<>();
    final ArrayList<Leaf> leafs= new ArrayList<>();
  }

  static List<Item> parse(String spec){
    String s= spec.replace("\r\n","\n");
    var items= new ArrayList<Item>();
    var cur= new ArrayList<String>();
    for (var line: s.split("\n",-1)){
      if (line.equals("jjj")){ addItem(items, cur); cur.clear(); continue; }
      cur.add(line);
    }
    addItem(items, cur);
    return items;
  }

  private static void addItem(ArrayList<Item> out, ArrayList<String> lines){
    if (lines.isEmpty()){ return; }
    int i= 0;
    for (; i < lines.size(); i++){ if (lines.get(i).equals("iii")){ break; } }
    assert i < lines.size() : "Missing iii separator";
    String name= String.join("\n", lines.subList(0, i));
    String content= String.join("\n", lines.subList(i+1, lines.size()));
    out.add(new Item(name, content));
  }

  public static void materialize(Path root, String spec){
    var zips= new LinkedHashMap<Path,ZipNode>();
    for (var it: parse(spec)){ emit(root, zips, it); }
    zips.forEach((diskZip, node)-> writeDiskZip(diskZip, node));
  }

  private static void emit(Path root, LinkedHashMap<Path,ZipNode> zips, Item it){
    String name= it.name();
    boolean isDir= name.endsWith("/");
    if (isDir){ name= name.substring(0, name.length()-1); }
    var segs= name.split("/", -1);

    int zi= firstZipSeg(segs);
    if (zi < 0){ emitDisk(root, segs, isDir, it.content()); return; }

    Path diskZip= root;
    for (int i= 0; i <= zi; i++){ diskZip= diskZip.resolve(segs[i]); }

    var node= zips.computeIfAbsent(diskZip, _->new ZipNode());
    var after= List.of(segs).subList(zi+1, segs.length);
    emitZip(node, after, isDir, it.content());
  }

  private static int firstZipSeg(String[] segs){
    for (int i= 0; i < segs.length; i++){
      if (segs[i].endsWith(".zip")){ return i; }
    }
    return -1;
  }

  private static void emitDisk(Path root, String[] segs, boolean isDir, String content){
    Path p= root;
    for (var s: segs){ p= p.resolve(s); }
    if (isDir){ mkdirs(p); return; }
    mkdirs(p.getParent());
    writeString(p, content);
  }

  private static void emitZip(ZipNode root, List<String> after, boolean isDir, String content){
    if (after.isEmpty()){
      assert !isDir : "Use path ending with .zip (no trailing /) to declare empty zip";
      return; // declaration only: empty zip is fine
    }
    int from= 0;
    while (true){
      int next= nextZipIndex(after, from);
      if (next < 0){ break; }
      String step= String.join("/", after.subList(from, next+1));
      root= root.nested.computeIfAbsent(step, _->new ZipNode());
      from= next+1;
    }
    String entry= String.join("/", after.subList(from, after.size()));
    if (entry.isEmpty()){ return; } // declaration only at this zip level
    root.leafs.add(new Leaf(entry, content, isDir));
  }

  private static int nextZipIndex(List<String> segs, int from){
    for (int i= from; i < segs.size(); i++){
      if (segs.get(i).endsWith(".zip")){ return i; }
    }
    return -1;
  }

  private static void writeDiskZip(Path diskZip, ZipNode node){
    mkdirs(diskZip.getParent());
    writeBytes(diskZip, buildZip(node));
  }

  private static byte[] buildZip(ZipNode node){
    var bout= new ByteArrayOutputStream();
    try(var zos= new ZipOutputStream(bout, UTF_8)){
      node.nested.forEach((name, child)->{
        var bytes= buildZip(child);
        putFile(zos, name, bytes);
      });
      for (var e: node.leafs){
        if (e.isDir){ putDir(zos, e.name); }
        else{ putFile(zos, e.name, e.content.getBytes(UTF_8)); }
      }
    }
    catch (Exception ex){ throw new AssertionError(ex); }
    return bout.toByteArray();
  }

  private static void putDir(ZipOutputStream zos, String name){
    try{
      zos.putNextEntry(new java.util.zip.ZipEntry(name.endsWith("/") ? name : name + "/"));
      zos.closeEntry();
    }
    catch (Exception ex){ throw new AssertionError(ex); }
  }

  private static void putFile(ZipOutputStream zos, String name, byte[] bytes){
    try{
      zos.putNextEntry(new ZipEntry(name));
      zos.write(bytes);
      zos.closeEntry();
    }
    catch (Exception ex){ throw new AssertionError(ex); }
  }

  public static String dump(SourceOracle so){
    var rs= new ArrayList<SourceOracle.Ref>(so.allFiles());
    rs.sort(Comparator.comparing(SourceOracle.Ref::fearPath));
    var sb= new StringBuilder();
    for (var r: rs){
      sb.append("--- ").append(r.fearPath()).append("\n");
      sb.append(r.loadString()).append("\n");
    }
    return sb.toString();
  }

  public static String dumpErr(Path root, RuntimeException ex){
    String msg= String.valueOf(ex.getMessage());
    var abs= root.toAbsolutePath().normalize().toString();
    var absSl= abs.replace('\\','/');
    var uri= root.toUri().normalize().toString();
    return msg.replace(abs, "<root>").replace(absSl, "<root>").replace(uri, "<root-uri>");
  }

  private static void mkdirs(Path p){
    if (p == null){ return; }
    try{ Files.createDirectories(p); }
    catch (Exception ex){ throw new AssertionError(ex); }
  }

  private static void writeString(Path p, String s){
    try{ Files.writeString(p, s, UTF_8); }
    catch (Exception ex){ throw new AssertionError(ex); }
  }

  private static void writeBytes(Path p, byte[] bs){
    try{ Files.write(p, bs); }
    catch (Exception ex){ throw new AssertionError(ex); }
  }
  public static String runOk(Path tmp, String spec){
    Path root= tmp.resolve("root");
    mkdirs(root);
    materialize(root, spec);
    return dump(new RealSourceOracleWithZip(root));
  }

  public static String runErr(Path tmp, String spec){
    Path root= tmp.resolve("root");
    mkdirs(root);
    materialize(root, spec);
    try{
      dump(new RealSourceOracleWithZip(root));
      throw new AssertionError("Expected UserExit");
    }
    catch (UserExit ex){
      return dumpErr(root, ex);
    }
  }
  private FsDsl(){}
}
