package commonsTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import metaParser.Span;
import metaParser.Token;
import metaParser.TokenKind;
import metaParser.TokenMatch;

public class SpanPositionOrderTest{
  static final URI f= URI.create("fear:/a/b.fear");
  enum K implements TokenKind{
    Word;
    public TokenMatch matcher(){ return TokenMatch.fromRegex("[a-z]+"); }
    public int priority(){ return 0; }
  }
  record T(K kind, String content, int line, int column, List<T> tokens) implements Token<T,K>{}
  @Test void containedMultiLineOuterStartColAfterInnerStartCol(){
    var outer= new Span(f,1,10,5,2);
    var inner= new Span(f,2,1,3,50);
    assertTrue(outer.contained(inner));
  }
  @Test void containedInnerLineStrictlyInsideLongerThanOuterEndCol(){
    var outer= new Span(f,1,1,3,5);
    var inner= new Span(f,2,1,2,20);
    assertTrue(outer.contained(inner));
  }
  @Test void containedColumnsStillMatterOnSharedBoundaryLines(){
    var outer= new Span(f,1,5,3,5);
    assertFalse(outer.contained(new Span(f,1,3,2,1)));
    assertFalse(outer.contained(new Span(f,2,1,3,6)));
  }
  @Test void makeSpanEndColOnLaterLineIsNotFirstColumn(){
    var first= new T(K.Word,"a",1,10,List.of());
    var last= new T(K.Word,"x",3,1,List.of());
    assertEquals(new Span(f,1,10,3,1), Token.makeSpan(f,first,last));
  }
}
