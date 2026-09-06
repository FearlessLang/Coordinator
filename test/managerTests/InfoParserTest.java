package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import managerData.Info;
import managerData.InfoParser;
import managerData.InfoPrinter;
import userMessages.UserError;

final class InfoParserTest{
  private static final URI uri= URI.create("test:projects.info");
  private static Info parse(String text){ return InfoParser.parse(text,uri); }
  @Test void anEmptyObjectParsesToNoFields(){
    assertEquals(List.of(), ((Info.Obj)parse("{}")).fields());
  }
  @Test void aStringParsesToItsValue(){
    assertEquals("hello", ((Info.Str)parse("\"hello\"")).value());
  }
  @Test void escapesRoundTrip(){
    var s= (Info.Str)parse("\"a\\\"b\\\\c\\nd\"");
    assertEquals("a\"b\\c\nd", s.value());
    assertEquals("\"a\\\"b\\\\c\\nd\"", InfoPrinter.print(s).strip());
  }
  @Test void nestedListsAndObjectsParse(){
    var obj= (Info.Obj)parse("{\"a\":[\"x\",\"y\"],\"b\":{}}");
    assertEquals(2, obj.fields().size());
    assertEquals(List.of("x","y"), ((Info.Lst)obj.get("a").orElseThrow()).items().stream().map(i->((Info.Str)i).value()).toList());
    assertTrue(((Info.Obj)obj.get("b").orElseThrow()).fields().isEmpty());
  }
  @Test void lineCommentsAreSkipped(){
    var obj= (Info.Obj)parse("{\n  \"a\": \"x\" // a comment\n}");
    assertEquals("x", ((Info.Str)obj.get("a").orElseThrow()).value());
  }
  @Test void wholeWhitespaceAndCommentsRoundTripToTheSameStructure(){
    var a= parse("{\"a\":\"1\",\"b\":[\"2\",\"3\"]}");
    var b= parse("{\n  \"a\" : \"1\" ,\n  \"b\" : [ \"2\" , \"3\" ]\n}\n// trailing comment is not even reached\n");
    assertEquals(InfoPrinter.print(a), InfoPrinter.print(b));
  }
  @Test void anUnclosedStringPointsAtWhereItStarted(){
    var e= assertThrows(UserError.class, ()->parse("{\"a\": \"never closed"));
    assertTrue(e.getMessage().contains("never closed"), e.getMessage());
    assertTrue(e.getMessage().contains("^"), e.getMessage());
  }
  @Test void aMissingColonIsReported(){
    var e= assertThrows(UserError.class, ()->parse("{\"a\" \"x\"}"));
    assertTrue(e.getMessage().contains("Expected ':' after the key."), e.getMessage());
  }
  @Test void aDuplicateKeyIsReported(){
    var e= assertThrows(UserError.class, ()->parse("{\"a\":\"1\",\"a\":\"2\"}"));
    assertTrue(e.getMessage().contains("Duplicate key \"a\""), e.getMessage());
  }
  @Test void aRawNewlineInAStringIsRejected(){
    var e= assertThrows(UserError.class, ()->parse("\"a\nb\""));
    assertTrue(e.getMessage().contains("raw newline"), e.getMessage());
  }
  @Test void anUnsafeCharacterIsRejected(){
    var e= assertThrows(UserError.class, ()->parse("\"a\tb\""));
    assertTrue(e.getMessage().contains("safe character set"), e.getMessage());
  }
  @Test void trailingJunkIsRejected(){
    var e= assertThrows(UserError.class, ()->parse("\"a\" \"b\""));
    assertTrue(e.getMessage().contains("Unexpected extra text"), e.getMessage());
  }
}
