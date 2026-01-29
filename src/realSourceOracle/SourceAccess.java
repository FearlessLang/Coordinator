package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import tools.Fs;
import utils.IoErr;

record ZipDemoDfs(Path root){
  public ZipDemoDfs{ root= root.toAbsolutePath().normalize(); Fs.reqDir(root,"root"); }
  private boolean isZip(Path e){ return e.getFileName().toString().endsWith(".zip"); }
  private boolean isZip(ZipEntry e){ return e.getName().endsWith(".zip"); }
  private String toName(Path p){ return root.relativize(p).toString().replace('\\','/'); }//this method is wrong as is, we will need to go from Path to URI eventually
  private static String toName(String out, ZipEntry e){ return out+"/"+e.getName(); }//this method is wrong as is, we will need to go from Uri+ZipEntry to URI eventually
  public List<String> nameAndContent(){ return IoErr.walk(root, s-> s
    .filter(p->!p.equals(root))
    .filter(Files::isRegularFile)
    .mapMulti(this::nameAndContentConsumer)
    .toList());
  }
  private void nameAndContentConsumer(Path p, Consumer<String> out){
    if (!isZip(p)) { out.accept(toName(p)+"\n"+Fs.readUtf8(p)); return; }
    IoErr.ofV(()->scanZip(toName(p),out,()->new ZipInputStream(IoErr.of(()->Files.newInputStream(p)),UTF_8)));
  }
  private void scanZip(String outName, Consumer<String> out, Supplier<ZipInputStream> zis) throws IOException{
    try(var z= zis.get()){ for(ZipEntry e;(e = z.getNextEntry())!=null;){ scanZipEntry(z, outName, out, e); } } 
  }
  private void scanZipEntry(ZipInputStream zis, String mount, Consumer<String> out, ZipEntry e) throws IOException {
    if(e.isDirectory()){ zis.closeEntry(); return; }
    if(!isZip(e)){ out.accept(toName(mount,e)+"\n"+new String(zis.readAllBytes(),UTF_8)); }
    else {scanZip(toName(mount,e), out,()->new ZipInputStream(new NoCloseIn(zis),UTF_8)); }
    zis.closeEntry();
  }
  private static final class NoCloseIn extends FilterInputStream{
    NoCloseIn(InputStream in){ super(in); }
    @Override public void close(){} // lets nested ZipInputStream close without closing the parent zis
  }
}