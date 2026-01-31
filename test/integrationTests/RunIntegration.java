package integrationTests;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import coordinator.Coordinator;
import mainCoordinator.ResolveResource;

public class RunIntegration {
  void testOk(String name){
    var c= new Coordinator(){
      public Path rtPath(){    return ResolveResource.stLibRTPath; }  
      public Path stLibPath(){ return ResolveResource.stLibPath; }
    };
    c.main(ResolveResource.integrationTests.resolve(name));
  }
  @Test void helloWorld(){ testOk("helloWorld");}
  @Test void map_a_to_pkc(){ testOk("map_a_to_pkc");}

}
