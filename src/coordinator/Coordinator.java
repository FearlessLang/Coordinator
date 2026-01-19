package coordinator;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import coordinatorMessages.UserExit;
import coordinatorMessages.UserTreeError;
import core.FearlessException;
import core.OtherPackages;
import core.E.Literal;
import main.FrontendLogicMain;
import naiveBackend.NaiveBackendLogicMain;
import realSourceOracle.RealSourceOracle;
import tools.JavaTool;
import tools.SourceOracle;
import tools.SourceOracle.Ref;

public interface Coordinator {
  Path rtPath();  
  Path stLibPath();

  default void runAllMains(String pkgName,OutputOracle out){
    var jars= out.rootDir().resolve("gen_java");
    JavaTool.runMainFromJars(jars,pkgName+".Main");
  }
  default SourceOracle sourceOracle(Path path){ return new RealSourceOracle(path); }
  default void main(Path path){ Helper.main(this, path); }
  
  default List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other,Map<String,String> vres){
    try{ return new FrontendLogicMain().of(pkgName,vres, files, oracle, other); }
    catch(FearlessException fe){ throw new UserExit(fe.render(oracle)); }
  }
  default void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, OutputOracle out){
    new NaiveBackendLogicMain().of(pkgName,core,out.rootDir(),rtPath());
  }
}
class Helper{
  static boolean isFear(Ref u){ return u.toString().endsWith(".fear"); }
  static void main(Coordinator coordinator, Path path){
    SourceOracle o= coordinator.sourceOracle(path);
    long maxStamp= o.allFiles().stream()
      .filter(Helper::isFear).mapToLong(Ref::lastModified)
      .max().orElseThrow(()->UserTreeError.emptyProject(path));
    o.allFiles().stream().filter(Helper::isFear).forEach(Helper::pkgName);//err if not under a pkg
    var map= new LinkedHashMap<String,List<Ref>>();
    for(Ref u:o.allFiles()){ pkgNameOpt(u).ifPresent(pn->map.computeIfAbsent(pn,_->new ArrayList<>()).add(u)); }
    List<Ref> allRanks= map.values().stream().map(u->Helper.okPkgContent(u,path)).toList();
    var pOut= path.resolve(".fearless_out");
    OutputOracle out= ()->pOut;
    Layer l= mapFromRanks(coordinator,allRanks,o,out,maxStamp);
    l = layers(coordinator,map,l,allRanks.stream()
      .sorted(Comparator.comparingInt(Helper::rankNumber).thenComparing(Object::toString)).toList());
    l.compile(o, out);
    l.pkgs().keySet().forEach(p->coordinator.runAllMains(p,out));
  }
  static Layer layers(Coordinator coordinator, Map<String,List<Ref>> map, Layer l, List<Ref> ranks){
    int lastNum= rankNumber(ranks.getFirst());
    var pkgs= new LinkedHashMap<String, List<Ref>>();
    for(Ref u:ranks){
      var pkgName= pkgName(u);
      int currNum= rankNumber(u);
      if (currNum == lastNum){ pkgs.put(pkgName,map.get(pkgName)); continue; }
      lastNum = currNum;
      l = new MiddleLayer(coordinator,l, pkgs);
      pkgs = new LinkedHashMap<String, List<Ref>>();
      pkgs.put(pkgName,map.get(pkgName));
    }
    return pkgs.isEmpty() ? l : new MiddleLayer(coordinator,l, pkgs);    
  }
  static Layer mapFromRanks(Coordinator coordinator, List<Ref> allRanks, SourceOracle o, OutputOracle out, long maxStamp){
    Map<String,Map<String,String>> res; try {res= new FrontendLogicMain()
      .parseRankFiles(allRanks,o, Comparator.comparingInt(Helper::rankNumber));}
    catch(FearlessException fe){ System.err.println(fe.render(o)); throw fe; }
    long baseStamp= out.commitMap(res, maxStamp);
    return new BaseLayer(coordinator,res,baseStamp);
  }
  static int rankNumber(Ref u){
    var name= u.toString();
    if(!u.toString().endsWith(".fear")){ throw UserTreeError.malformedRankFileName(u); }
    var stem= name.substring(name.lastIndexOf("/"), name.length()-5); // no ".fear"
    for(int i=0;i<ranks.size();i++){
      var pref= ranks.get(i);
      int base= (i+1)*1000;
      if(stem.equals(pref)){ return base+999; } // shortcut: _rank_app.fear == _rank_app999.fear
      if(!stem.startsWith(pref)){ continue; }
      if(stem.length()!=pref.length()+3){ throw UserTreeError.malformedRankFileName(u); }
      var digits= stem.substring(pref.length());
      if(!digits.chars().allMatch(Character::isDigit)){ throw UserTreeError.malformedRankFileName(u); }
      return base+Integer.parseInt(digits);
    }
    throw UserTreeError.malformedRankFileName(u);
  }
  private static final List<String> ranks= List.of(
    "_rank_base","_rank_core","_rank_driver","_rank_worker","_rank_framework","_rank_accumulator","_rank_tool","_rank_app");
    
  static Ref okPkgContent(List<Ref> u, Path root){
    var pkg= pkgName(u.getFirst());
    var rankFiles= u.stream()
      .filter(ui->ui.toString().contains("/_rank_")).toList();
    if (rankFiles.isEmpty()){ throw UserTreeError.missingRankFile(pkg, root); }
    if (rankFiles.size() > 1){ throw UserTreeError.multipleRankFiles(pkg, rankFiles); }
    rankNumber(rankFiles.getFirst());//err malformed rank file name is malformed
    return rankFiles.getFirst();
  }
  static String pkgName(Ref u){ return pkgNameOpt(u).orElseThrow(()->UserTreeError.noPackageSegment(u)); }

  static Optional<String> pkgNameOpt(Ref u){//TODO: finalize the real whitelist
    List<String> whiteListAfterPkg= List.of("_asset","_dbg");
    //List<String> whiteList= List.of("_ignore");//TODO: no, those files will have to be filtered way before we reach here
    var candidates= Stream.of(u.toString().split("/"))
      .filter(s->s.startsWith("_"))
      .toList();
    if (candidates.isEmpty()){ return Optional.empty(); }
    if (whiteListAfterPkg.contains(candidates.getFirst())){ throw UserTreeError.reservedBeforePkg(u); }    
    var onlyGood= candidates.stream().skip(1).allMatch(s->whiteListAfterPkg.contains(s));
    if (onlyGood){ return Optional.of(candidates.getFirst().substring(1)); } 
    throw UserTreeError.ambiguousPackageSegment(u, candidates);    
  }
}