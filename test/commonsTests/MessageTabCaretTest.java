package commonsTests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import metaParser.Frame;
import metaParser.Message;
import metaParser.Span;

public class MessageTabCaretTest{
  static final URI file= URI.create("mem:/t.fear");
  static String render(String src, Span s){ return Message.of(_->src, List.of(new Frame("", s)), "msg"); }
  @Test void caretAfterTabStartsUnderTheMarkedChar(){
    var res= render("a\tx", new Span(file,1,3,1,3));
    assertEquals("""
      In file: mem:/t.fear

      001| a   x
         |     ^

      While inspecting the file
      msg""", res);
  }
  @Test void caretOverTabCoversOnlyTheMarkedChars(){
    var res= render("ab\txyz", new Span(file,1,2,1,4));
    assertEquals("""
      In file: mem:/t.fear

      001| ab  xyz
         |  ^^^^

      While inspecting the file
      msg""", res);
  }
}
