package manager;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

final class FolderDrop{
  private FolderDrop(){}
  static boolean hasFileFlavor(Transferable t){
    return t.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || uriListFlavor(t) != null;
  }
  static List<Path> pathsOf(Transferable t){
    try {
      if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)){ return fromFileList(t); }
      var flavor= uriListFlavor(t);
      return flavor == null ? List.of() : fromUriList(text(t,flavor));
    }
    catch(UnsupportedFlavorException|IOException e){ return List.of(); }
  }
  @SuppressWarnings("unchecked")
  private static List<Path> fromFileList(Transferable t) throws UnsupportedFlavorException, IOException {
    var files= (List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
    return files.stream().map(File::toPath).toList();
  }
  private static DataFlavor uriListFlavor(Transferable t){
    for(var flavor: t.getTransferDataFlavors()){
      if (flavor.getPrimaryType().equalsIgnoreCase("text") && flavor.getSubType().equalsIgnoreCase("uri-list")){ return flavor; }
    }
    return null;
  }
  private static String text(Transferable t, DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    var data= t.getTransferData(flavor);
    if (data instanceof String s){ return s; }
    if (data instanceof Reader r){ return readAll(r); }
    if (data instanceof InputStream in){ return readAll(new InputStreamReader(in, StandardCharsets.UTF_8)); }
    return "";
  }
  private static String readAll(Reader r) throws IOException {
    var out= new StringWriter();
    try(r){ r.transferTo(out); }
    return out.toString();
  }
  static List<Path> fromUriList(String data){
    return data.lines()
      .filter(line -> !line.isBlank() && !line.startsWith("#"))
      .map(FolderDrop::toPath)
      .filter(Objects::nonNull)
      .toList();
  }
  private static Path toPath(String line){
    try { return Path.of(new URI(line)); }
    catch(URISyntaxException|IllegalArgumentException e){ return null; }
  }
}
