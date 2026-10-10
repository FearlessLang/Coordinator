package coordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import offensiveUtils.Require;
import userMessages.Report;
import userMessages.Violation;
import core.FearlessException;
import core.OtherPackages;
import core.TName;
import core.E.Literal;
import main.FrontendLogicMain;
import naiveBackend.BackendTools;
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
  default String runAllMains(String pkgName,OutputOracle out) throws InterruptedException{
    return runMains(runData(out.rootDir().getParent(),stdLibBase()), Push.of(out.rootDir().resolve("gen_java"),sharedClasspath()), "_"+pkgName+".Main");
  }
  static String runMains(List<String> jvmArgs, List<Path> jarDirs, String mainClass) throws InterruptedException{
    var sb= new StringBuilder();
    var jvm= JavaTool.startMainFromJars(jvmArgs, jarDirs, mainClass, s->{ sb.append(s); System.out.print(s); });
    var ec= jvm.await();
    Require.check(ec == 0 || ec == 1, "Exit code "+ec+" from\n"+String.join("\n",jvm.cmd())+"\nOutput:\n"+sb);
    return sb.toString();
  }
  static List<String> runData(Path project, Path base){
    return List.of(
      "--enable-native-access=ALL-UNNAMED",
      "-DfearlessUser.dir="+project,
      "-DfearlessBase.dir="+base);
  }
  default SourceOracle sourceOracle(Path path){ return new RealSourceOracleWithZip(path); }
  static Map<String,Boolean> pkgsBuilt(Path path){
    var map= Helper.pkgMap(new RealSourceOracleWithZip(path),path);
    var ranks= map.values().stream().map(Helper::okPkgContent).toList();
    var out= Helper.out(path);
    var top= ranks.stream().mapToInt(Helper::rankNumber).max().getAsInt();
    var listed= Files.exists(out.mainsPath());
    var res= new LinkedHashMap<String,Boolean>();
    for (var r: ranks){
      var pkg= Helper.pkgName(r);
      var below= ranks.stream().filter(d->Helper.rankNumber(d) < Helper.rankNumber(r)).mapToLong(d->out.pkgApiStamp(Helper.pkgName(d)));
      var maxIn= LongStream.concat(LongStream.concat(map.get(pkg).stream().mapToLong(Ref::lastModified),below),LongStream.of(out.mapStamp())).max().getAsLong();
      res.put(pkg,out.stillBuilt(pkg,map.get(pkg),maxIn) && (listed || Helper.rankNumber(r) != top));
    }
    return Collections.unmodifiableMap(res);
  }
  default String main(Path project, SourceOracle stLib) throws InterruptedException{
    var sb= new StringBuilder();
    for (var p: compile(project,stLib)){ sb.append(runAllMains(p,Helper.out(project))); }
    return sb.toString();
  }
  default List<String> compile(Path project, SourceOracle stLib){
    SourceOracle o= sourceOracle(project);
    var out= Helper.out(project);
    Layer l= Helper.layerOf(this,o,project,out,stLib);
    l.compile(o, out);
    Fs.writeUtf8(out.mainsPath(), out.mains(l.pkgs().keySet()).located(o,stLib).print());
    return List.copyOf(l.pkgs().keySet());//by design: only the highest rank number's packages have their Main run
  }
  default Optional<Map<String,String>> mains(Path project, SourceOracle stLib){
    var c= new NoCompile(baseCachePath());
    SourceOracle o= sourceOracle(project);
    var out= new NoCommit(Helper.out(project).rootDir());
    Layer l;
    try{ l= Helper.layerOf(c,o,project,out,stLib); l.compile(o,out); }
    catch(WouldCompile _){ return Optional.empty(); }
    var res= new TreeMap<String,String>();
    out.mains(l.pkgs().keySet()).mains().forEach((k,v)->res.put(k,v.file()));
    return Optional.of(Collections.unmodifiableMap(res));
  }
  static ChildJvm startMain(Path project, Path base, String main, List<Path> sharedClasspath, java.util.function.Consumer<String> out){
    return JavaTool.startMainFromJars(runData(project,base),Push.of(genJava(project),sharedClasspath), "_"+main.substring(0, main.indexOf('.'))+".Main", out, main);
  }
  static Path genJava(Path project){ return project.resolve(outDir).resolve("gen_java"); }
  String outDir= ".fearless_out";
  
  default List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other,Map<String,String> vres){
    try{ return new FrontendLogicMain().of(pkgName,vres, files, other); }
    catch(FearlessException fe){ throw Report.sourceError(fe.render(oracle)); }
  }
  default void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, CapabilityEnvironment capabilities){
    new NaiveBackendLogicMain().of(backendTools(pkgName,oracle,other,core,capabilities),sharedClasspath());
  }
  default BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
    var unused= Path.of("unused");
    return BackendTools.of(pkgName, oracle, other, core, unused, baseCachePath(), unused, unused, capabilities);
  }
  default Path modsPath(){
    var path= JavacTool.reqAppDir(Violation::mustUseLauncher).resolve(JavacTool.deployedModsDirName);
    Fs.ensureDir(path);
    return path;
  }
  default Path stdLibBase(){ return JavacTool.reqAppDir(Violation::mustUseLauncher).resolve("stdLib").resolve("base"); }
  default Optional<Path> baseCachePath(){ return Optional.empty(); }
  default List<Path> sharedClasspath(){
    return Stream.concat(Stream.of(modsPath()), baseCachePath().stream()).toList();
  }
}
class Helper{
  static boolean isFear(Ref u){ return u.fearPath().endsWith(".fear"); }
  static OutputOracle out(Path path){
    return ()->path.resolve(Coordinator.outDir);
  }
  static Layer layerOf(Coordinator coordinator, SourceOracle o, Path project, OutputOracle out, SourceOracle stLib){
    var map= pkgMap(o,project);
    List<Ref> allRanks= map.values().stream().map(Helper::okPkgContent).toList();
    Map<String,Map<String,String>> res; try {res= new FrontendLogicMain()
      .parseRankFiles(allRanks, Comparator.comparingInt(Helper::rankNumber), Push.of(allRanks.stream().map(Helper::pkgName).toList(), "base"));}
    catch(FearlessException fe){ throw Report.sourceError(fe.render(o)); }
    Layer l= new BaseLayer(coordinator,res,out.commitMap(res, allRanks.stream().mapToLong(Ref::lastModified).max().getAsLong()),stLib);
    var byRank= new TreeMap<Integer,LinkedHashMap<String,List<Ref>>>();
    allRanks.stream().sorted(Comparator.comparing(Ref::fearPath)).forEach(r->byRank.computeIfAbsent(rankNumber(r),_->new LinkedHashMap<>()).put(pkgName(r),map.get(pkgName(r))));
    for (var pkgs: byRank.values()){ l= new MiddleLayer(coordinator,l,pkgs); }
    return l;
  }
  static LinkedHashMap<String,List<Ref>> pkgMap(SourceOracle o, Path path){
    if (o.allFiles().stream().noneMatch(Helper::isFear)){ throw Report.projectEmpty(path); }
    o.allFiles().stream().filter(Helper::isFear).forEach(Helper::pkgName);//err if not under a pkg
    return o.allFiles().stream().filter(u->pkgNameOpt(u).isPresent()).collect(Collectors.groupingBy(Helper::pkgName,LinkedHashMap::new,Collectors.toList()));
  }
  static int rankNumber(Ref u){
    var m= rankName.matcher(isFear(u) ? Fs.fileNameWithoutExtension(u.fearPath()) : "");
    if (!m.matches()){ throw Report.projectMalformedRankFileName(u); }
    return (ranks.indexOf(m.group(1))+1)*1000+(m.group(2) == null ? 999 : Integer.parseInt(m.group(2))); // shortcut: _rank_app.fear == _rank_app999.fear
  }
  private static final List<String> ranks= List.of(
    "_rank_base","_rank_core","_rank_driver","_rank_worker","_rank_framework","_rank_accumulator","_rank_tool","_rank_app");
  private static final Pattern rankName= Pattern.compile("("+String.join("|",ranks)+")(\\d{3})?");

  static Ref okPkgContent(List<Ref> u){
    var pkg= pkgName(u.getFirst());
    var reserved= u.stream().filter(r->Fs.fileNameWithExtension(r.fearPath()).startsWith("_rank_")).toList();
    reserved.forEach(Helper::rankNumber);//err malformed rank file name is malformed
    if (reserved.isEmpty()){ throw Report.projectMissingRankFile(pkg); }
    if (reserved.size() > 1){ throw Report.projectMultipleRankFiles(pkg, reserved); }
    return reserved.getFirst();
  }
  static String pkgName(Ref u){ return pkgNameOpt(u).orElseThrow(()->Report.projectNoPackageSegment(u)); }

  static Optional<String> pkgNameOpt(Ref u){
    var candidates= Stream.of(u.fearPath().split("/"))
      .filter(s->s.startsWith("_") && !s.contains("."))
      .toList();
    if (candidates.isEmpty()){ return Optional.empty(); }
    if (candidates.size() != 1){ throw Report.projectAmbiguousPackageSegment(u, candidates); }
    var pkg= candidates.getFirst().substring(1);
    if (!TName.isPkgName(pkg)){ throw Report.projectBadPackageName(u, candidates.getFirst()); }
    if (List.of("base","rank").contains(pkg)){ throw Report.projectReservedPackageName(u, candidates.getFirst()); }
    return Optional.of(pkg);
  }
}
@SuppressWarnings("serial") class WouldCompile extends RuntimeException{}
record NoCompile(Optional<Path> baseCachePath) implements Coordinator{
  @Override public List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres){ throw new WouldCompile(); }
  @Override public void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, CapabilityEnvironment capabilities){ throw new WouldCompile(); }
}
record NoCommit(Path rootDir) implements OutputOracle{
  @Override public long commitMap(Map<String,Map<String,String>> map, long minExclusiveMillis){
    if (OutputHelper.mapFromJSon(mapPath()).filter(map::equals).isEmpty()){ throw new WouldCompile(); }
    return mapStamp();
  }
}
