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
import java.util.ArrayList;
import java.util.List;

//Cross-platform extraction of the folders/files dropped onto the manager
//window, kept apart from the Swing wiring so the uri-list fallback below can
//be unit tested without a display.
//
//Windows and macOS file managers hand over DataFlavor.javaFileListFlavor on
//a drop. GTK file managers on Linux (Nautilus, Dolphin, ...) commonly don't:
//they offer only a "text/uri-list" payload (RFC 2483, one file:// URI per
//line), carried as String, Reader or InputStream depending on JDK/toolkit -
//all three are handled below since which one shows up is not testable here.
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
    catch(UnsupportedFlavorException|IOException e){ return List.of(); }//unreadable drop: nothing offered, rather than failing it
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
    var res= new ArrayList<Path>();
    for(var line: data.split("\r\n|\n|\r")){
      if (line.isBlank() || line.startsWith("#")){ continue; }//comments are part of the text/uri-list format
      var path= toPath(line.strip());
      if (path != null){ res.add(path); }
    }
    return res;
  }
  private static Path toPath(String line){
    try { return Path.of(new URI(line)); }
    catch(URISyntaxException|IllegalArgumentException e){ return null; }
  }
}
