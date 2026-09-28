package commonsTests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

import metaParser.PrettyFileName;

public class PrettyFileNameEllipsisTest{
  @Test void longNameWithoutSlashesFitsIn80(){
    var res= PrettyFileName.displayFileName(URI.create("mem:"+"a".repeat(100)));
    assertEquals("..."+"a".repeat(77), res);
    assertEquals(80, res.length());
  }
  @Test void longBasenameFitsIn80(){
    var res= PrettyFileName.displayFileName(URI.create("mem:/x/y/"+"c".repeat(100)));
    assertEquals("..."+"c".repeat(77), res);
    assertEquals(80, res.length());
  }
}
