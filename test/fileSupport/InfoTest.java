package fileSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import metaParser.Span;
import userMessages.UserError;

final class InfoTest{
  private static final URI uri= URI.create("test:projects.info");
  static{ utils.Err.setUp(AssertionFailedError.class,Assertions::assertEquals,Assertions::assertTrue); }
  private static void err(String expected, Runnable body){ utils.Err.strCmp(expected,assertThrows(UserError.class,body::run).getMessage()); }
  private static Info parse(String text){ return Info.parse(text,uri); }
  @Test void anEmptyObjectParsesToNoFields(){
    assertEquals(List.of(),((Info.Obj)parse("{}")).fields());
  }
  @Test void aStringParsesToItsValue(){
    assertEquals("hello",((Info.Str)parse("\"hello\"")).value());
  }
  @Test void escapesRoundTrip(){
    var s= (Info.Str)parse("\"a\\\"b\\\\c\\nd\"");
    assertEquals("a\"b\\c\nd",s.value());
    assertEquals("\"a\\\"b\\\\c\\nd\"\n",Info.print(s));
  }
  @Test void aPrintedCharacterOutsideTheSetIsPointedAtWhenParsedBack(){
    err("[###]\n001| \"caf?\"\n   |     ^\n[###]The character [U+00E9] is outside the safe character set of Fearless[###]",()->parse(Info.print(new Info.Str("caf\u00e9",Info.noSpan))));
  }
  @Test void nestedListsAndObjectsParse(){
    var obj= (Info.Obj)parse("{\"a\":[\"x\",\"y\"],\"b\":{}}");
    assertEquals(2,obj.fields().size());
    assertEquals(List.of("x","y"),((Info.Lst)obj.field("a").orElseThrow().value()).items().stream().map(i->((Info.Str)i).value()).toList());
    assertTrue(((Info.Obj)obj.field("b").orElseThrow().value()).fields().isEmpty());
  }
  @Test void lineCommentsAreSkipped(){
    var obj= (Info.Obj)parse("{\n  \"a\": \"x\" // a comment\n}");
    assertEquals("x",((Info.Str)obj.field("a").orElseThrow().value()).value());
  }
  @Test void wholeWhitespaceAndCommentsRoundTripToTheSameStructure(){
    var a= parse("{\"a\":\"1\",\"b\":[\"2\",\"3\"]}");
    var b= parse("{\n  \"a\" : \"1\" ,\n  \"b\" : [ \"2\" , \"3\" ]\n}\n// trailing comment is not even reached\n");
    assertEquals(Info.print(a),Info.print(b));
  }
  @Test void anUnclosedStringPointsAtWhereItStarted(){
    err("[###]never closed with a matching \".[###]",()->parse("{\"a\": \"never closed"));
  }
  @Test void aMissingColonIsReported(){
    err("[###]Expected ':' after the key \"a\".[###]",()->parse("{\"a\" \"x\"}"));
  }
  @Test void aDuplicateKeyIsReported(){
    err("[###]Duplicate key \"a\": this object already has this key.[###]",()->parse("{\"a\":\"1\",\"a\":\"2\"}"));
  }
  @Test void aRawNewlineInAStringIsRejected(){
    err("[###]cannot contain a raw newline; write \\n instead.[###]",()->parse("\"a\nb\""));
  }
  @Test void anUnknownEscapeIsRejected(){
    err("[###]Unknown escape \\u: only \\\", \\\\ and \\n exist.[###]",()->parse("\"\\u(E9)\""));
  }
  @Test void anUnsafeCharacterIsRejected(){
    err("[###]outside the safe character set of Fearless[###]",()->parse("\"a\tb\""));
  }
  @Test void trailingJunkIsRejected(){
    err("[###]Unexpected extra text after the end of the value[###]",()->parse("\"a\" \"b\""));
  }
  @Test void anUnclosedObjectIsReported(){
    err("[###]never closed with a matching }.[###]",()->parse("{\"a\":\"1\""));
  }
  @Test void valuesNestAtMost100Deep(){
    assertEquals("["+"[".repeat(99)+"]".repeat(99)+"]\n",Info.print(parse("[".repeat(100)+"]".repeat(100))));
    err("[###]Lists and objects nest at most 100 deep, and this value is inside 100 of them.[###]",()->parse("[".repeat(100_000)));
  }
  @Test void anObjectOverSeveralLinesSpansToItsClosingBrace(){
    var obj= (Info.Obj)parse("{\"somekey\": {\"b\": \"1\",\n\"c\": \"2\"}}");
    assertEquals(new Span(uri,1,13,2,9),obj.field("somekey").orElseThrow().value().span());
  }
  @Test void anUnclosedListIsReported(){
    err("[###]never closed with a matching ].[###]",()->parse("[\"a\""));
  }
}
