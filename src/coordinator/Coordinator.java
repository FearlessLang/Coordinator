package coordinator;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import core.E.Literal;
import core.FearlessException;
import core.OtherPackages;
import core.TName;
import main.FrontendLogicMain;
import naiveBackend.BackendTools;
import naiveBackend.NaiveBackendLogicMain;
import offensiveUtils.Require;
import realSourceOracle.RealSourceOracleWithZip;
import realSourceOracle.ZipEntry;
import tools.ChildJvm;
import tools.Fs;
import tools.JavacTool;
import tools.PathClassLoader;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import userMessages.Report;
import userMessages.UserError;
import userMessages.Violation;
import utils.Join;
import utils.OneOr;
import utils.Push;

public interface Coordinator{
  default String runAllMains(String pkgName, OutputOracle out) throws InterruptedException{
    return runMains(runData(out.rootDir().getParent(), stdLibBase()), Push.of(genJava(out.rootDir()), sharedClasspath()), "_"+pkgName+".Main");
  }
  static String runMains(List<String> jvmArgs, List<Path> jarDirs, String mainClass) throws InterruptedException{
    var sb= new StringBuilder();
    var jvm= startProgram(jvmArgs, jarDirs, mainClass, s -> { sb.append(s); System.out.print(s); });
    var ec= jvm.await();
    Require.check(ec==0 || ec==1, "Exit code "+ec+" from\n"+Join.of(jvm.cmd(), "", "\n", "")+"\nOutput:\n"+sb);
    return sb.toString();
  }
  static List<String> runData(Path project, Path base){
    return List.of(
      "--enable-native-access=ALL-UNNAMED",
      "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
      "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
      Fs.isWindows() ? "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED" : "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
      "-Dsun.java2d.d3d=false",
      "-DfearlessUser.dir="+PathClassLoader.argOf(project),
      "-DfearlessBase.dir="+PathClassLoader.argOf(base));
  }
  ///A Fearless program decodes file names as UTF-8 on every system: on Linux LC_ALL=C.UTF-8 sets it.
  ///Its code is loaded through PathClassLoader, and the paths it reads arrive as PathClassLoader.argOf,
  ///so the names of the project and of the data are bytes the program keeps as they are. The JVM itself
  ///and the jar of PathClassLoader are found through the text of their paths: this JVM writes that text
  ///as the bytes naming the installation, and the program decodes them as UTF-8, so they must be valid UTF-8.
  static ChildJvm startProgram(List<String> jvmArgs, List<Path> codeDirs, String mainClass, Consumer<String> out, String... mainArgs){
    var boot= PathClassLoader.jar();
    Stream.of(boot, Path.of(System.getProperty("java.home"))).filter(p -> !namedInUtf8(p)).findFirst().ifPresent(p -> { throw Report.installationNotUtf8(p); });
    var args= Push.of(List.of(jvmArgs, PathClassLoader.codeArgs(codeDirs), List.of("-cp", boot.toString(), PathClassLoader.class.getName(), mainClass), List.of(mainArgs)));
    return ChildJvm.start(args, Fs.isLinux() ? Map.of("LC_ALL", "C.UTF-8") : Map.of(), out);
  }
  private static boolean namedInUtf8(Path p){
    var bytes= p.toString().getBytes(Charset.forName(System.getProperty("sun.jnu.encoding")));
    try{ StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)); return true; }
    catch (CharacterCodingException _){ return false; }
  }
  static Optional<URI> testsUri(String pkg, List<Ref> files){
    var rank= OneOr.of("One rank file per package", files.stream().filter(Helper::hasReservedRankPrefix));
    if (rank instanceof ZipEntry){ return Optional.empty(); }
    return Optional.of(rank.fearURI().resolve("auto_tests/"+pkg+"_test.fear"));
  }
  default SourceOracle sourceOracle(Path path){ return new RealSourceOracleWithZip(path); }
  static Map<String,Boolean> pkgsBuilt(Path path, Path baseCache){
    var o= new RealSourceOracleWithZip(path);
    var map= Helper.pkgMap(o, path);
    var baseApi= Fs.lastModified(baseCache.resolve("base.json"));
    var ranks= map.values().stream().map(Helper::okPkgContent).toList();
    var out= Helper.out(path);
    var mapCommitted= OutputHelper.mapFromJson(out.mapPath()).filter(Helper.rankMap(o, ranks)::equals).isPresent();
    var res= new LinkedHashMap<String,Boolean>();
    for (var r : ranks){
      var pkg= Helper.pkgName(r);
      var lower= ranks.stream().filter(d -> Helper.rankNumber(d) < Helper.rankNumber(r)).map(Helper::pkgName).toList();
      var below= lower.stream().mapToLong(out::pkgApiStamp);
      var maxIn= LongStream.concat(LongStream.concat(map.get(pkg).stream().mapToLong(Ref::lastModified), below), LongStream.of(out.mapStamp(), baseApi)).max().getAsLong();
      res.put(pkg, mapCommitted && out.stillBuilt(pkg, map.get(pkg), Push.of(lower, "base"), maxIn));
    }
    return Collections.unmodifiableMap(res);
  }
  default String main(Path project, SourceOracle stLib) throws InterruptedException{
    var sb= new StringBuilder();
    for (var p : compile(project, stLib)){ sb.append(runAllMains(p, Helper.out(project))); }
    return sb.toString();
  }
  default List<String> compile(Path project, SourceOracle stLib){ return guardingCache(project, () -> compileNow(project, stLib)); }
  default Optional<MainsInfo> mains(Path project, SourceOracle stLib){
    try{ return Optional.of(guardingCache(project, () -> mainsNow(project, stLib))); }
    catch (WouldCompile _){ return Optional.empty(); }
  }
  static <R> R guardingCache(Path project, Supplier<R> compile){
    var out= project.resolve(outDir);
    var cached= Fs.exists(out);
    try{ return compile.get(); }
    catch (UserError | WouldCompile e){ throw e; }
    catch (RuntimeException | AssertionError e){
      if (!cached){ throw e; }
      Fs.rmTree(out);
      throw UserError.cacheCorruptedOrCrash(e);
    }
  }
  private List<String> compileNow(Path project, SourceOracle stLib){
    var o= sourceOracle(project);
    var out= Helper.out(project);
    var l= Helper.layerOf(this, o, project, out, stLib);
    out.forgetAllBut(l.pkgNames());
    l.compile(o, out);
    l.pkgs().keySet().forEach(p -> out.relocate(p, o, stLib));
    return List.copyOf(l.pkgs().keySet());//by design: only the highest rank number's packages have their Main run
  }
  private MainsInfo mainsNow(Path project, SourceOracle stLib){
    var c= new NoCompile(baseCachePath());
    var o= sourceOracle(project);
    var out= new NoCommit(Helper.out(project).rootDir());
    Helper.layerOf(c, o, project, out, stLib).compile(o, out);
    var res= MainsInfo.read(project).orElseThrow();
    return baseCachePath().map(b -> MainsInfo.merge(Stream.of(res, MainsInfo.parse(b.resolve("base"+MainsInfo.suffix))))).orElse(res);
  }
  static ChildJvm startMain(Path project, Path base, String main, Optional<Path> input, Map<String,Path> reads, Map<String,Path> edits, List<String> attenuators, List<String> desk, List<Path> sharedClasspath, Consumer<String> out){
    var jvmArgs= new ArrayList<>(runData(project, base));
    input.ifPresent(i -> jvmArgs.add("-DfearlessInput.file="+PathClassLoader.argOf(i)));
    reads.forEach((k,v) -> jvmArgs.add("-DfearlessDataRead."+k+"="+PathClassLoader.argOf(v)));
    edits.forEach((k,v) -> jvmArgs.add("-DfearlessDataEdit."+k+"="+PathClassLoader.argOf(v)));
    if (!attenuators.isEmpty()){ jvmArgs.add("-DfearlessAttenuators="+String.join(",", attenuators)); }
    jvmArgs.addAll(desk);
    return startProgram(jvmArgs, Push.of(genJava(project.resolve(outDir)), sharedClasspath), "_"+main.substring(0, main.indexOf('.'))+".Main", out, main);
  }
  static Path genJava(Path outRoot){ return outRoot.resolve("gen_java"); }
  String outDir= ".fearless_out";
  default List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres, CapabilityEnvironment capabilities){
    var tests= new ArrayList<Ref>();
    try{ return new FrontendLogicMain().of(pkgName, vres, files, other, core -> docs(pkgName, testsUri(pkgName, files), oracle, other, core, capabilities, tests)); }
    catch (FearlessException e){ throw Report.sourceError(e.render(oracle.withFallback(() -> tests))); }
  }
  private Optional<Ref> docs(String pkgName, Optional<URI> testsUri, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities, ArrayList<Ref> tests){
    var docs= backendTools(pkgName, oracle, other, core, capabilities).docs();
    core.forEach(docs::visitAll);
    var res= docs.complete(testsUri);
    res.ifPresent(tests::add);
    return res;
  }
  default void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, CapabilityEnvironment capabilities){
    new NaiveBackendLogicMain().of(backendTools(pkgName, oracle, other, core, capabilities), sharedClasspath());
  }
  default BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
    var unused= Path.of("unused");
    return BackendTools.of(pkgName, oracle, other, core, unused, baseCachePath(), Optional.empty(), unused, capabilities);
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
final class Helper{
  static boolean isFear(Ref u){ return u.fearPath().endsWith(".fear"); }
  static OutputOracle out(Path path){
    return () -> path.resolve(Coordinator.outDir);
  }
  static Layer layerOf(Coordinator coordinator, SourceOracle o, Path project, OutputOracle out, SourceOracle stLib){
    var map= pkgMap(o, project);
    var allRanks= map.values().stream().map(Helper::okPkgContent).toList();
    var res= rankMap(o, allRanks);
    Layer l= new BaseLayer(coordinator, res, out.commitMap(res, allRanks.stream().mapToLong(Ref::lastModified).max().getAsLong()), stLib);
    var byRank= new TreeMap<Integer,LinkedHashMap<String,List<Ref>>>();
    for (var r : allRanks.stream().sorted(Comparator.comparing(Ref::fearPath)).toList()){
      byRank.computeIfAbsent(rankNumber(r), _ -> new LinkedHashMap<>()).put(pkgName(r), map.get(pkgName(r)));
    }
    for (var pkgs : byRank.values()){ l = new MiddleLayer(coordinator, l, pkgs); }
    return l;
  }
  static Map<String,Map<String,String>> rankMap(SourceOracle o, List<Ref> allRanks){
    var ranks= new LinkedHashMap<String,Ref>();
    allRanks.forEach(r -> ranks.put(pkgName(r), r));
    try{ return new FrontendLogicMain().parseRankFiles(ranks, Comparator.comparingInt(Helper::rankNumber)); }
    catch (FearlessException e){ throw Report.sourceError(e.render(o)); }
  }
  static LinkedHashMap<String,List<Ref>> pkgMap(SourceOracle o, Path path){
    if (o.allFiles().stream().noneMatch(Helper::isFear)){ throw Report.projectEmpty(path); }
    o.allFiles().stream().filter(Helper::isFear).forEach(Helper::pkgName);//err if not under a pkg
    return o.allFiles().stream().filter(u -> pkgNameOpt(u).isPresent()).collect(Collectors.groupingBy(Helper::pkgName, LinkedHashMap::new, Collectors.toList()));
  }
  static int rankNumber(Ref u){
    var m= rankName.matcher(isFear(u) ? Fs.fileNameWithoutExtension(u.fearPath()) : "");
    if (!m.matches()){ throw Report.projectMalformedRankFileName(u); }
    return (ranks.indexOf(m.group(1))+1)*1000+(m.group(2)==null ? 999 : Integer.parseInt(m.group(2))); // shortcut: _rank_app.fear == _rank_app999.fear
  }
  private static final List<String> ranks= List.of(
    "_rank_base", "_rank_core", "_rank_driver", "_rank_worker", "_rank_framework", "_rank_accumulator", "_rank_tool", "_rank_app");
  private static final Pattern rankName= Pattern.compile(Join.of(ranks, "(", "|", ")")+"(\\d{3})?");
  static boolean hasReservedRankPrefix(Ref u){ return Fs.fileNameWithExtension(u.fearPath()).startsWith("_rank_"); }
  static Ref okPkgContent(List<Ref> u){
    var pkg= pkgName(u.getFirst());
    var reserved= u.stream().filter(Helper::hasReservedRankPrefix).toList();
    reserved.forEach(Helper::rankNumber);//err malformed rank file name is malformed
    if (reserved.isEmpty()){ throw Report.projectMissingRankFile(pkg); }
    if (reserved.size() > 1){ throw Report.projectMultipleRankFiles(pkg, reserved); }
    return reserved.getFirst();
  }
  static String pkgName(Ref u){ return pkgNameOpt(u).orElseThrow(() -> Report.projectNoPackageSegment(u)); }
  static Optional<String> pkgNameOpt(Ref u){
    var candidates= Stream.of(u.fearPath().split("/"))
      .filter(s -> s.startsWith("_") && !s.contains("."))
      .toList();
    if (candidates.isEmpty()){ return Optional.empty(); }
    if (candidates.size() != 1){ throw Report.projectAmbiguousPackageSegment(u, candidates); }
    var pkg= candidates.getFirst().substring(1);
    if (!TName.isPkgName(pkg)){ throw Report.projectBadPackageName(u, candidates.getFirst()); }
    if (List.of("base", "rank").contains(pkg)){ throw Report.projectReservedPackageName(u, candidates.getFirst()); }
    return Optional.of(pkg);
  }
}
@SuppressWarnings("serial") final class WouldCompile extends RuntimeException{}
record NoCompile(Optional<Path> baseCachePath) implements Coordinator{
  @Override public List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres, CapabilityEnvironment capabilities){ throw new WouldCompile(); }
  @Override public void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, CapabilityEnvironment capabilities){ throw new WouldCompile(); }
}
record NoCommit(Path rootDir) implements OutputOracle{
  @Override public long commitMap(Map<String,Map<String,String>> map, long minExclusiveMillis){
    if (OutputHelper.mapFromJson(mapPath()).filter(map::equals).isEmpty()){ throw new WouldCompile(); }
    return mapStamp();
  }
}
