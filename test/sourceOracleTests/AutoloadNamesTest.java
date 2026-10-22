package sourceOracleTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import core.TName;
import realSourceOracle.AutoloadHandler;
import tools.Fs;

final class AutoloadNamesTest{
  @Test void dropExtKeepsTheDotsOfTheBaseName(){
    assertEquals("readme.v2", AutoloadHandler.dropExt("readme.v2.txt"));
  }
  @Test void resultsAreTypeNames(){
    assertTrue(TName.isTypeName(Fs.capFirst("example_data")));
    assertTrue(TName.isTypeName(Fs.capFirst("_foo")));
    assertTrue(TName.isTypeName(Fs.capFirst("___a")));
  }
}
