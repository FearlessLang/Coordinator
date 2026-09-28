package commonsTests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import metaParser.Frame;
import metaParser.Message;
import metaParser.Span;

public class MessageCoordinatesTest{
  static final URI file= URI.create("fear:/a.fear");
  static String render(String src, Span s){
    return Message.of(_->src, List.of(new Frame("", s)), "M");
  }
  @Test void spanAfterSupplementaryCharIsNotTrimmed(){
    var src= "\uD83D\uDE00a b";
    assertEquals("""
      In file: fear:/a.fear

      001| ?a b
         |  ^^^

      While inspecting the file
      M""", render(src, new Span(file,1,2,1,4)));
  }
  @Test void tabAfterSupplementaryCharUsesCodePointColumns(){
    var src= "\uD83D\uDE00\tx";
    assertEquals("""
      In file: fear:/a.fear

      001| ?   x
         |     ^

      While inspecting the file
      M""", render(src, new Span(file,1,3,1,3)));
  }
  @Test void byteOrderMarkIsNotAColumn(){
    var src= "\uFEFFab c";
    assertEquals("""
      In file: fear:/a.fear

      001| ab c
         | ^^

      While inspecting the file
      M""", render(src, new Span(file,1,1,1,2)));
  }
  @Test void formFeedIsNotALineBreak(){
    var src= "ab\fcd";
    assertEquals("""
      In file: fear:/a.fear

      001| ab?cd
         |   ^

      While inspecting the file
      M""", render(src, new Span(file,1,3,1,3)));
  }
}
