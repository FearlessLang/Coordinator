package integrationTests;

import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import coordinator.Coordinator;
import mainCoordinator.ResolveResource;

public class RunIntegration {
  void testOk(String name){
    var c= new Coordinator(){
      public Path rtPath(){    return ResolveResource.stLibRTPath; }  
      public Path stLibPath(){ return ResolveResource.stLibPath; }
    };
    try { c.main(ResolveResource.integrationTests.resolve(name));}
    catch (InterruptedException e){ Assertions.fail(e);}
  }
  @Test void helloWorld(){ testOk("helloWorld");}
  @Test void testUnitTests(){ testOk("testUnitTests");}
  @Test void map_a_to_pkc(){ testOk("map_a_to_pkc");}
  @Test void helloStackTraces(){ testOk("helloStackTraces");}
  @Test void testingStandardLibrary(){ testOk("testingStandardLibrary");}
  @Test void testDocs(){ testOk("testDocs");}

}
