package sourceOracleTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import core.TName;
import realSourceOracle.AutoloadHandler;

final class AutoloadNamesTest{
  @Test void aSingleUnderscoreCapitalisesTheNextLetter(){
    assertEquals("ExampleData",AutoloadHandler.capFirst("example_data"));
    assertEquals("Mydata",AutoloadHandler.capFirst("mydata"));
    assertEquals("MyData_2",AutoloadHandler.capFirst("my_data_2"));
    assertEquals("Foo",AutoloadHandler.capFirst("_foo"));
  }
  @Test void extraUnderscoresStay(){
    assertEquals("Example_Data",AutoloadHandler.capFirst("example__data"));
    assertEquals("_Foo",AutoloadHandler.capFirst("__foo"));
    assertEquals("__A",AutoloadHandler.capFirst("___a"));
    assertEquals("A_1",AutoloadHandler.capFirst("a_1"));
    assertEquals("A_",AutoloadHandler.capFirst("a_"));
  }
  @Test void resultsAreTypeNames(){
    assertTrue(TName.isTypeName(AutoloadHandler.capFirst("example_data")));
    assertTrue(TName.isTypeName(AutoloadHandler.capFirst("__foo")));
    assertTrue(TName.isTypeName(AutoloadHandler.capFirst("___a")));
  }
}
