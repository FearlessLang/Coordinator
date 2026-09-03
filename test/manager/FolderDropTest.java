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

final class FolderDropTest{
  @Test void aFileListDropIsReadStraightFromTheFlavor(){
    var t= fixed(DataFlavor.javaFileListFlavor, List.of(new File("C:\\Users\\me\\myproject")));
    assertTrue(FolderDrop.hasFileFlavor(t));
    assertEquals(List.of(Path.of("C:\\Users\\me\\myproject")), FolderDrop.pathsOf(t));
  }
  @Test void aUriListStringIsSplitOnLines(){
    var t= fixed(uriListFlavor("java.lang.String"),
      "file:///C:/Users/me/myproject\r\nfile:///C:/Users/me/other.fearless\r\n");
    assertEquals(List.of(Path.of("C:\\Users\\me\\myproject"),Path.of("C:\\Users\\me\\other.fearless")), FolderDrop.pathsOf(t));
  }
  @Test void aUriListReaderIsReadInFull(){
    var t= fixed(uriListFlavor("java.io.Reader"), new StringReader("file:///C:/Users/me/myproject\n"));
    assertEquals(List.of(Path.of("C:\\Users\\me\\myproject")), FolderDrop.pathsOf(t));
  }
  @Test void aUriListInputStreamIsReadAsUtf8(){
    var bytes= "file:///C:/Users/me/myproject\n".getBytes(StandardCharsets.UTF_8);
    var t= fixed(uriListFlavor("java.io.InputStream"), new ByteArrayInputStream(bytes));
    assertEquals(List.of(Path.of("C:\\Users\\me\\myproject")), FolderDrop.pathsOf(t));
  }
  @Test void commentAndBlankLinesAreSkipped(){
    var data= "# a comment\r\n\r\nfile:///C:/Users/me/myproject\r\n";
    assertEquals(List.of(Path.of("C:\\Users\\me\\myproject")), FolderDrop.fromUriList(data));
  }
  @Test void aSpaceInTheNameIsPercentDecoded(){
    var data= "file:///C:/Users/me/My%20Project\r\n";
    assertEquals(List.of(Path.of("C:\\Users\\me\\My Project")), FolderDrop.fromUriList(data));
  }
  @Test void aBareLineFeedIsAcceptedToo(){
    var data= "file:///C:/Users/me/myproject\n";
    assertEquals(List.of(Path.of("C:\\Users\\me\\myproject")), FolderDrop.fromUriList(data));
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
