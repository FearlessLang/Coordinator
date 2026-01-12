package testBuildBase;


import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import coordinator.Coordinator;
import coordinator.OutputOracle;
import core.OtherPackages;
import tools.SourceOracle;
import utils.Bug;
import utils.ResolveResource;

class TestBuildBase {
  Coordinator c= new Coordinator(){
    @Override public SourceOracle oracle(){ throw Bug.of(); }
    @Override public OtherPackages other(){ throw Bug.of(); }
    @Override  public OutputOracle out(){ throw Bug.of(); }
    @Override  public void main(){
      OutputOracle out= ps->  ps.stream().map(Path::of).reduce(ResolveResource.stLibDebugOut, (acc,e)->acc.resolve(e)); 
      compileBase(out);
      runAllMains("base",out);
    }
  };
  @Test void test(){  c.main(); }
}