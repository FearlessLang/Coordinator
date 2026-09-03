package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

//file:// URIs here always carry a drive letter: this JVM runs on Windows, and
//Path.of(URI) rejects a driveless Unix-style path here (there is nothing to
//resolve it against). The line-splitting, comment/blank skipping and flavor
//lookup below are the same code Linux runs; only that last URI-to-Path step
//is platform-specific JDK behaviour, not something this suite re-proves.
final class FolderDropTest{
  @Test void aFileListDropIsReadStraightFromTheFlavor(){
    var t= fixed(DataFlavor.javaFileListFlavor, List.of(new File("C:\\Users\\marco\\myproject")));
    assertTrue(FolderDrop.hasFileFlavor(t));
    assertEquals(List.of(Path.of("C:\\Users\\marco\\myproject")), FolderDrop.pathsOf(t));
  }
  @Test void aUriListStringIsSplitOnLines(){
    var t= fixed(uriListFlavor("java.lang.String"),
      "file:///C:/Users/marco/myproject\r\nfile:///C:/Users/marco/other.fearless\r\n");
    assertEquals(List.of(Path.of("C:\\Users\\marco\\myproject"),Path.of("C:\\Users\\marco\\other.fearless")), FolderDrop.pathsOf(t));
  }
  @Test void aUriListReaderIsReadInFull(){
    var t= fixed(uriListFlavor("java.io.Reader"), new StringReader("file:///C:/Users/marco/myproject\n"));
    assertEquals(List.of(Path.of("C:\\Users\\marco\\myproject")), FolderDrop.pathsOf(t));
  }
  @Test void aUriListInputStreamIsReadAsUtf8(){
    var bytes= "file:///C:/Users/marco/myproject\n".getBytes(StandardCharsets.UTF_8);
    var t= fixed(uriListFlavor("java.io.InputStream"), new ByteArrayInputStream(bytes));
    assertEquals(List.of(Path.of("C:\\Users\\marco\\myproject")), FolderDrop.pathsOf(t));
  }
  @Test void commentAndBlankLinesAreSkipped(){
    var data= "# a comment\r\n\r\nfile:///C:/Users/marco/myproject\r\n";
    assertEquals(List.of(Path.of("C:\\Users\\marco\\myproject")), FolderDrop.fromUriList(data));
  }
  @Test void aSpaceInTheNameIsPercentDecoded(){
    var data= "file:///C:/Users/marco/My%20Project\r\n";
    assertEquals(List.of(Path.of("C:\\Users\\marco\\My Project")), FolderDrop.fromUriList(data));
  }
  @Test void aBareLineFeedIsAcceptedToo(){
    var data= "file:///C:/Users/marco/myproject\n";//RFC 2483 mandates CRLF; some toolkits hand over LF instead
    assertEquals(List.of(Path.of("C:\\Users\\marco\\myproject")), FolderDrop.fromUriList(data));
  }
  @Test void aDropWithNeitherFlavorHasNothingToOffer(){
    var t= fixed(DataFlavor.stringFlavor, "just text");
    assertFalse(FolderDrop.hasFileFlavor(t));
    assertEquals(List.of(), FolderDrop.pathsOf(t));
  }
  private static DataFlavor uriListFlavor(String repClass){
    try { return new DataFlavor("text/uri-list;class="+repClass); }
    catch(ClassNotFoundException e){ throw new RuntimeException(e); }
  }
  private static Transferable fixed(DataFlavor flavor, Object data){
    return new Transferable(){
      @Override public DataFlavor[] getTransferDataFlavors(){ return new DataFlavor[]{flavor}; }
      @Override public boolean isDataFlavorSupported(DataFlavor f){ return f.equals(flavor); }
      @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(f)){ throw new UnsupportedFlavorException(f); }
        return data;
      }
    };
  }
}
