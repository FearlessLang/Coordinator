package coordinator;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import coordinatorMessages.UserTreeError;
import realSourceOracle.RealSourceOracle;
import tools.JavaTool;
import tools.SourceOracle;

public interface Coordinator {
  default void runAllMains(String pkgName,OutputOracle out){
    var classes= out.rootDir().resolve("gen_java","_classes");
    var runOut= JavaTool.runMain(classes, pkgName+".Main");
    assertEquals("", runOut);
  }
  static SourceOracle sourceOracle(Path path){
    try{ return new RealSourceOracle(path); }
    catch(UncheckedIOException ioe){ System.err.println(ioe.getCause().getMessage()); throw ioe; }
  }  
  default void main(Path path){
    SourceOracle o= sourceOracle(path);
    long maxStamp= o.allFiles().stream()
      .filter(u->Path.of(u).endsWith(".fear"))
      .mapToLong(u->o.lastModified(u))
      .max().orElseThrow(()->UserTreeError.emptyProject(path));
    Map<String,List<URI>> map= o.allFiles().stream().collect(
      Collectors.groupingBy(Helper::pkgName, LinkedHashMap::new, Collectors.toList()));
    List<URI> allRanks= map.values().stream().map(u->Helper.okPkgContent(u,path)).toList();
    var pOut= path.resolve(".fearless_out");
    OutputOracle out= ()->pOut;
    Layer l= Helper.mapFromRanks(allRanks,out,maxStamp);
    l = Helper.layers(map,l,allRanks.stream()
      .sorted(Comparator.comparingInt(Helper::rankNumber).thenComparing(URI::toString)).toList());
    //l = Helper.layers(l,allRanks.stream().sorted(Helper::rankNumber).toList());//also sort on the toString of the full URI to get unique sorting
    l.compile(o, out);
  }
}
class Helper{
  static Layer layers(Map<String,List<URI>> map, Layer l, List<URI> ranks){
    int lastNum= 0;
    var pkgs= new LinkedHashMap<String, List<URI>>();
    for(URI u:ranks){
      var pkgName= pkgName(u);
      int currNum= rankNumber(u);
      if (currNum == lastNum){ pkgs.put(pkgName,map.get(pkgName)); continue; }
      lastNum = currNum;
      if (!pkgs.isEmpty()){continue; }
      l = new MiddleLayer(l, pkgs);
      pkgs = new LinkedHashMap<String, List<URI>>(); 
    }
    return l;    
  }
  static Layer mapFromRanks(List<URI> allRanks, OutputOracle out, long maxStamp){
    /*this for will do later*/
    Map<String,Map<String,String>> res= new LinkedHashMap<>();
    long baseStamp= out.commitMap(res, 0);
    return new BaseLayer(res,Math.max(baseStamp,maxStamp));
  }
  static int rankNumber(URI u){ 
    int res= _rankNumber(u);
    assert res != -1;
    return res;
  }
  static int _rankNumber(URI u){
    var name= Path.of(u).getFileName().toString();
    assert name.endsWith(".fear");
    var prefix= name.substring(name.length() - 8); //8 is length of NNN.fear
    var digits= name.substring(name.length() - 8,name.length() - 5); //5 is length of .fear
    int base= ranks.indexOf(prefix) + 1;
    assert base > 0; 
    return base*1000+Integer.parseInt(digits);
  }
  private static final List<String> ranks= List.of(
    "_rank_base","_rank_core","_rank_driver","_rank_worker","_rank_framework","_rank_accumulator","_rank_tool","_rank_app");
    
  static URI okPkgContent(List<URI> u,Path root){
    var pkg= pkgName(u.getFirst());
    var rankFiles= u.stream()
      .filter(ui->Path.of(ui).getFileName().toString().startsWith("_rank_")).toList();
    if (rankFiles.isEmpty()){ throw UserTreeError.missingRankFile(pkg, root); }
    if (rankFiles.size() > 1){ throw UserTreeError.multipleRankFiles(pkg, rankFiles); }
    boolean ok= _rankNumber(rankFiles.getFirst()) != -1;
    if (ok){ return rankFiles.getFirst(); }
    throw UserTreeError.malformedRankFileName(rankFiles.getFirst());
  }  
  static String pkgName(URI u){//TODO: finalize the real whitelist
    List<String> whiteListAfterPkg= List.of("_asset","_dbg");
    //List<String> whiteList= List.of("_ignore");//TODO: no, those files will have to be filtered way before we reach here
    var p= Path.of(u);
    var candidates= IntStream.range(0, p.getNameCount() -1)
      .mapToObj(i->p.getName(i).toString())
      .filter(s->s.startsWith("_"))
      .toList();
    if (candidates.isEmpty()){ throw UserTreeError.noPackageSegment(u); }
    if (whiteListAfterPkg.contains(candidates.getFirst())){ throw UserTreeError.reservedBeforePkg(u); }    
    var onlyGood= candidates.stream().skip(1).allMatch(s->whiteListAfterPkg.contains(s));
    if (onlyGood){ return candidates.getFirst(); } 
    throw UserTreeError.ambiguousPackageSegment(u, candidates);    
  }
}
    /*OutputOracle out= ps->  ps.stream().map(Path::of).reduce(path.resolve(".fearless_out"), (acc,e)->acc.resolve(e));
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