package coordinator;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import realSourceOracle.RealSourceOracle;
import tools.JavaTool;
import tools.SourceOracle;

public interface Coordinator {
  default void runAllMains(String pkgName,OutputOracle out){
    var classes= out.pathOf(List.of("gen_java","_classes"));
    var runOut= JavaTool.runMain(classes, pkgName+".Main");
    assertEquals("", runOut);
  }
  static SourceOracle sourceOracle(Path path){
    try{ return new RealSourceOracle(path); }
    catch(UncheckedIOException ioe){
      System.err.println(ioe.getCause().getMessage());
      throw ioe;
      }
  }
  
  default void main(Path path){}
  /*  OutputOracle out= ps->  ps.stream().map(Path::of).reduce(path.resolve(".fearless_out"), (acc,e)->acc.resolve(e));
    //if base is not compiled== no jar present in ".fearless_out/base.jar", compile it
    SourceOracle o= sourceOracle(path);
    List<URI> files= o.allFiles();
    //list->map will need a new class. To big to fit here
    //classify files into packages: forall f:files, if path contains _foo (underscore starting name)
    //that is a package name.
    //if more then one segment of path has _, all but one must be in a whitelisted list and are ignored
    //For each file *.fear, either there is exactly one valid package segment (add to pkg) 
    //or produce a good err message.
    Map<String,List<URI>> pkgs= new HashMap<>();
    Map<String,List<URI>> ranks= new HashMap<>();
    //Rank: each pkg has a file caled _rank_xxxxNNN.fear
    //base 1 | core 2 | driver 3 | worker 4 | framework 5 | accumulator 6 | tool 7 | app 8 
    //example core043 = rank 2043; worker999 = rank 4999
    //Then, open all the rank files of all the packages, parse them and extract the map component.
    //We can merge the various map components by rank (higher rank win)
    //We can save the tot map as a file under out called _map.json 
    //We can use the last modified time of the map and the rank files to decide to recompute or not.
    //now, for each package, ranked by 'rank'
    //- is there a file pkgName.json whose last modified time is > then the max(last modified time) of the source files? (we may have to consider the _map.json too)
    //- Yes: skip 
    //- NO: compile using the map (frontend+backend) AND set so that any package with higher rank has to be recompiled
    //- Note that packages with the same rank can still use their cache (this will be very common, many pkg with same rank is common) 
    //- Then, all the pkg(s) with the highest rank get their mains executed.
  }*/
 }