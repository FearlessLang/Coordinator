package integrationTests;

import org.junit.jupiter.api.Test;

import coordinator.Coordinator;
import utils.ResolveResource;

public class RunIntegration {
  void testOk(String name){
    var c= new Coordinator(){};
    c.main(ResolveResource.integrationTests.resolve(name));
  }
  @Test void helloWorld(){ testOk("helloWorld");}
  @Test void map_a_to_pkc(){ testOk("map_a_to_pkc");}

}
