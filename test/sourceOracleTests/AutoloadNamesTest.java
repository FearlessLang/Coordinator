package sourceOracleTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import core.TName;
import realSourceOracle.AutoloadHandler;

final class AutoloadNamesTest{
  @Test void anInnerUnderscoreCapitalisesTheNextLetter(){
    assertEquals("ExampleData",AutoloadHandler.capFirst("example_data"));
    assertEquals("Mydata",AutoloadHandler.capFirst("mydata"));
    assertEquals("MyData_2",AutoloadHandler.capFirst("my_data_2"));
    assertEquals("A_1",AutoloadHandler.capFirst("a_1"));
    assertEquals("A_",AutoloadHandler.capFirst("a_"));
  }
  @Test void extraInnerUnderscoresStay(){
    assertEquals("Example_Data",AutoloadHandler.capFirst("example__data"));
    assertEquals("Example__Data",AutoloadHandler.capFirst("example___data"));
  }
  @Test void leadingUnderscoresStayAndTheFirstLetterIsCapitalised(){
    assertEquals("_Foo",AutoloadHandler.capFirst("_foo"));
    assertEquals("__Foo",AutoloadHandler.capFirst("__foo"));
    assertEquals("___A",AutoloadHandler.capFirst("___a"));
    assertEquals("_FooBar",AutoloadHandler.capFirst("_foo_bar"));
    assertEquals("_",AutoloadHandler.capFirst("_"));
  }
  @Test void resultsAreTypeNames(){
    assertTrue(TName.isTypeName(AutoloadHandler.capFirst("example_data")));
    assertTrue(TName.isTypeName(AutoloadHandler.capFirst("_foo")));
    assertTrue(TName.isTypeName(AutoloadHandler.capFirst("___a")));
  }
}
