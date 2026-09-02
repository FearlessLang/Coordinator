package coordinator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import userMessages.Report;
import userMessages.Violation;
import core.FearlessException;
import core.LiteralDeclarations;
import core.OtherPackages;
import core.TName;
import core.E.Literal;
import main.FrontendLogicMain;
import naiveBackend.NaiveBackendLogicMain;
import realSourceOracle.RealSourceOracleWithZip;
import tools.Fs;
import tools.ChildJvm;
import tools.JavaTool;
import tools.JavacTool;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import utils.Push;

public interface Coordinator {
  Path rtPath();
  Path stLibPath();

  default String runAllMains(String pkgName,OutputOracle out) throws InterruptedException{
    return JavaTool.runMainFromJars(runData(out.rootDir().getParent()), Push.of(out.rootDir().resolve("gen_java"),sharedClasspath()), pkgName+".Main");
  }
  static List<String> runData(Path project){
    return List.of(
      "--enable-native-access=ALL-UNNAMED",
      "-DfearlessUser.dir="+project);
  }
  default SourceOracle sourceOracle(Path path){ return new RealSourceOracleWithZip(path); }
  static List<String> pkgNames(Path path){
    var o= new RealSourceOracleWithZip(path);
    var map= Helper.pkgMap(o,path);
    map.values().forEach(u->Helper.okPkgContent(u,path));
    return List.copyOf(map.keySet());
  }
  default String main(Path path) throws InterruptedException{ return Helper.main(this, path); }
  default List<String> compile(Path path){ return Helper.compile(this, path); }
  default Optional<List<String>> mains(Path path){ return Helper.mains(this, path); }
  static ChildJvm startMain(Path project, String main, List<Path> sharedClasspath, java.util.function.Consumer<String> out){
    var pkg= main.substring(0, main.indexOf('.'));
    return JavaTool.startMainFromJars(runData(project), Push.of(genJava(project),sharedClasspath), pkg+".Main", out, main);
  }
  static Path genJava(Path project){ return project.resolve(outDir).resolve("gen_java"); }
  String outDir= ".fearless_out";
  
  default List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other,Map<String,String> vres){
    try{ return new FrontendLogicMain().of(pkgName,vres, files, oracle, other); }
    catch(FearlessException fe){ throw Report.sourceError(fe.render(oracle)); }
  }
  default void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, OutputOracle out){
    new NaiveBackendLogicMain().of(pkgName,oracle,other,core,out.rootDir(),rtPath(),sharedClasspath());
  }
  default Path modsPath(){
    var appDir= System.getProperty(JavacTool.appDirKey);
    assert appDir != null;
    var path =  Path.of(appDir).resolve(JavacTool.deployedModsDirName);
    Fs.ensureDir(path);
    return path;
  }
  default Optional<Path> baseCachePath(){ return Optional.empty(); }
  default List<Path> sharedClasspath(){
    return Stream.concat(Stream.of(modsPath()), baseCachePath().stream()).toList();
  }
}
class Helper{
  static boolean isFear(Ref u){ return u.toString().endsWith(".fear"); }
  static OutputOracle out(Path path){
    var pOut= path.resolve(Coordinator.outDir);
    return ()->pOut;
  }
  static Layer layerOf(Coordinator coordinator, SourceOracle o, Path path, OutputOracle out){
    var map= pkgMap(o,path);
    List<Ref> allRanks= map.values().stream().map(u->Helper.okPkgContent(u,path)).toList();
    Layer l= mapFromRanks(coordinator,allRanks,o,out);
    return layers(coordinator,map,l,allRanks.stream()
      .sorted(Comparator.comparingInt(Helper::rankNumber).thenComparing(Object::toString)).toList());
  }
  static List<String> compile(Coordinator coordinator, Path path){
    SourceOracle o= coordinator.sourceOracle(path);
    var out= out(path);
    seedBaseCache(coordinator, out);
    Layer l= layerOf(coordinator,o,path,out);
    l.compile(o, out);
    return List.copyOf(l.pkgs().keySet());//by design: only the highest rank number's packages have their Main run
  }
  private static void seedBaseCache(Coordinator coordinator, OutputOracle out){
    var cacheDir= coordinator.baseCachePath();
    if (cacheDir.isEmpty()){ return; }
    Fs.copyFresh(cacheDir.get().resolve("base.json"), out.rootDir().resolve("base.json"));
    var baseSrc= coordinator.sourceOracle(coordinator.stLibPath());
    long maxIn= baseSrc.allFiles().stream().mapToLong(Ref::lastModified).max().getAsLong();
    out.commitBuilt("base", baseSrc.allFiles(), maxIn);
  }
  static String main(Coordinator coordinator, Path path) throws InterruptedException{
    var out= out(path);
    var sb= new StringBuilder();
    for(var p: compile(coordinator,path)){ sb.append(coordinator.runAllMains(p,out)); }
    return sb.toString();
  }
  static Optional<List<String>> mains(Coordinator coordinator, Path path){
    var c= new NoCompile(coordinator);
    SourceOracle o= c.sourceOracle(path);
    var out= new NoCommit(out(path).rootDir());
    Layer l;
    try{ l= layerOf(c,o,path,out); l.compile(o,out); }
    catch(WouldCompile _){ return Optional.empty(); }
    return Optional.of(l.pkgs().keySet().stream().flatMap(p->mainsOf(out,p)).toList());
  }
  private static final TName baseMain= new TName("base.Main",0,utils.Pos.unknown);
  static Stream<String> mainsOf(OutputOracle out, String pkg){
    var api= new OutputHelper().pgkApiFromJSon(out.rootDir().resolve(pkg+".json"));
    if (api.isEmpty()){ throw Violation.cacheMissingPkgApiFile(out.rootDir().resolve(pkg+".json")); }
    return api.get().values().stream().filter(Helper::isMain).map(l->l.name().s()).sorted();
  }
  static boolean isMain(Literal l){
    var hasInstance= l.thisName().equals("this") || LiteralDeclarations.has(l.cs(),LiteralDeclarations.captureFree);
    return hasInstance
      && LiteralDeclarations.has(l.cs(),baseMain)
      && l.ms().stream().noneMatch(m->m.sig().abs());
  }
  static LinkedHashMap<String,List<Ref>> pkgMap(SourceOracle o, Path path){
    if (o.allFiles().stream().noneMatch(Helper::isFear)){ throw Report.projectEmpty(path); }
    o.allFiles().stream().filter(Helper::isFear).forEach(Helper::pkgName);//err if not under a pkg
    var map= new LinkedHashMap<String,List<Ref>>();
    for(Ref u:o.allFiles()){ pkgNameOpt(u).ifPresent(pn->map.computeIfAbsent(pn,_->new ArrayList<>()).add(u)); }
    return map;
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
  static Layer mapFromRanks(Coordinator coordinator, List<Ref> allRanks, SourceOracle o, OutputOracle out){
    Map<String,Map<String,String>> res; try {res= new FrontendLogicMain()
      .parseRankFiles(allRanks,o, Comparator.comparingInt(Helper::rankNumber));}
    catch(FearlessException fe){ throw Report.sourceError(fe.render(o)); }
    long baseStamp= out.commitMap(res, allRanks.stream().mapToLong(Ref::lastModified).max().getAsLong());
    return new BaseLayer(coordinator,res,baseStamp);
  }
  static int rankNumber(Ref u){
    var name= u.toString();
    if(!u.toString().endsWith(".fear")){ throw Report.projectMalformedRankFileName(u); }
    var stem= Fs.fileNameWithoutExtension(name);
    for(int i=0;i<ranks.size();i++){
      var pref= ranks.get(i);
      int base= (i+1)*1000;
      if(stem.equals(pref)){ return base+999; } // shortcut: _rank_app.fear == _rank_app999.fear
      if(!stem.startsWith(pref)){ continue; }
      if(stem.length()!=pref.length()+3){ throw Report.projectMalformedRankFileName(u); }
      var digits= stem.substring(pref.length());
      if(!digits.chars().allMatch(Character::isDigit)){ throw Report.projectMalformedRankFileName(u); }
      return base+Integer.parseInt(digits);
    }
    throw Report.projectMalformedRankFileName(u);
  }
  private static final List<String> ranks= List.of(
    "_rank_base","_rank_core","_rank_driver","_rank_worker","_rank_framework","_rank_accumulator","_rank_tool","_rank_app");

  static boolean hasReservedRankPrefix(Ref u){ return Fs.fileNameWithExtension(u.toString()).startsWith("_rank_"); }
  static Ref okPkgContent(List<Ref> u, Path root){
    var pkg= pkgName(u.getFirst());
    var reserved= u.stream().filter(Helper::hasReservedRankPrefix).toList();
    reserved.forEach(Helper::rankNumber);//err malformed rank file name is malformed
    if (reserved.isEmpty()){ throw Report.projectMissingRankFile(pkg, root); }
    if (reserved.size() > 1){ throw Report.projectMultipleRankFiles(pkg, reserved); }
    return reserved.getFirst();
  }
  static String pkgName(Ref u){ return pkgNameOpt(u).orElseThrow(()->Report.projectNoPackageSegment(u)); }

  private static final Set<String> reservedPkgNames= Set.of("base","rank");
  static Optional<String> pkgNameOpt(Ref u){
    var candidates= Stream.of(u.toString().split("/"))
      .filter(s->s.startsWith("_") && !s.contains("."))
      .toList();
    if (candidates.isEmpty()){ return Optional.empty(); }
    if (candidates.size() != 1){ throw Report.projectAmbiguousPackageSegment(u, candidates); }
    var pkg= candidates.getFirst().substring(1);
    if (!TName.isPkgName(pkg)){ throw Report.projectBadPackageName(u, candidates.getFirst()); }
    if (reservedPkgNames.contains(pkg)){ throw Report.projectReservedPackageName(u, candidates.getFirst()); }
    return Optional.of(pkg);
  }
}class WouldCompile extends RuntimeException{
  private static final long serialVersionUID= 1L;
}
record NoCompile(Coordinator inner) implements Coordinator{
  @Override public Path rtPath(){ return inner.rtPath(); }
  @Override public Path stLibPath(){ return inner.stLibPath(); }
  @Override public SourceOracle sourceOracle(Path path){ return inner.sourceOracle(path); }
  @Override public List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres){ throw new WouldCompile(); }
  @Override public void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, OutputOracle out){ throw new WouldCompile(); }
}
record NoCommit(Path rootDir) implements OutputOracle{
  @Override public long commitMap(Map<String,Map<String,String>> map, long minExclusiveMillis){
    var res= new OutputHelper().mapFromJSon(rootDir.resolve("_map.json"));
    if (res.isPresent() && res.get().equals(map)){ return mapStamp(); }
    throw new WouldCompile();
  }
}
