package coordinator;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
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
import main.FrontendLogicMain.TestSource;
import naiveBackend.BackendTools;
import naiveBackend.NaiveBackendLogicMain;
import offensiveUtils.Require;
import realSourceOracle.RealSourceOracleWithZip;
import realSourceOracle.Since;
import realSourceOracle.SourceOracleWithAutoload;
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
      "--add-opens=java.desktop/"+(Fs.isWindows() ? "sun.awt.windows" : Fs.isMac() ? "sun.lwawt.macosx" : "sun.awt.X11")+"=ALL-UNNAMED",
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
    Stream.of(boot, Path.of(System.getProperty("java.home"))).filter(p -> !Fs.isWindows() && !namedInUtf8(p)).findFirst().ifPresent(p -> { throw Report.installationNotUtf8(p); });
    var args= Push.of(List.of(jvmArgs, PathClassLoader.codeArgs(codeDirs), List.of("-cp", boot.toString(), PathClassLoader.class.getName(), mainClass), List.of(mainArgs)));
    return ChildJvm.start(args, Fs.isLinux() ? Map.of("LC_ALL", "C.UTF-8") : Map.of(), out);
  }
  private static boolean namedInUtf8(Path p){
    var bytes= p.toString().getBytes(Charset.forName(System.getProperty("sun.jnu.encoding")));
    try{ StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)); return true; }
    catch (CharacterCodingException _){ return false; }
  }
  static boolean zipped(List<Ref> files){ return OneOr.of("One rank file per package", files.stream().filter(Helper::hasReservedRankPrefix)) instanceof ZipEntry; }
  default SourceOracle sourceOracle(Path path, Since since){ return new RealSourceOracleWithZip(path, since); }
  /// What Coordinator.check gives (see CoordinatorDesign).
  sealed interface State{
    record Invalid(String problem) implements State{}
    record OutOfDate() implements State{}
    record Compiled(MainsInfo manifest, List<String> packages) implements State{}
  }
  /// Answered by the manager: the first candidate no claim uses, if any.
  interface TakeFreeExtension{ Optional<String> take(List<String> candidates); }
  default State check(Path folder){ return guarded(folder, () -> Project.of(this, folder, Since.anyTime).check()); }
  default Optional<UserError> compile(Path folder, Path stdLibBase, String projectName, TakeFreeExtension take){
    return guarded(folder, () -> Project.compile(this, folder, stdLibBase, projectName, take));
  }
  static void finish(Path folder){
    var icons= folder.resolve(outDir).resolve(Project.iconsDir);
    var kept= cachedMains(folder).values().stream().flatMap(m -> m.icons().stream()).flatMap(i -> iconFiles(folder, i).stream()).toList();
    if (Fs.isDirectory(icons)){ Fs.walk(icons, s -> s.filter(f -> Fs.isRegularFile(f) && !kept.contains(f)).toList()).forEach(Fs::rmTree); }
    Fs.ofV(() -> Fs.deleteIfExists(folder.resolve(outDir).resolve(Project.unfinishedFile)));
  }
  static void dropUnfinished(Path folder){ if (Fs.exists(folder.resolve(outDir).resolve(Project.unfinishedFile))){ deleteCache(folder); } }
  static void writeError(Path folder, String error){ Project.writeError(folder, error); }
  static void deleteCache(Path folder){
    var out= folder.resolve(outDir);
    if (!Fs.exists(out)){ return; }
    var unfinished= out.resolve(Project.unfinishedFile);
    Fs.writeUtf8(unfinished, "");
    try (var s= Fs.of(() -> Fs.list(out))){ s.filter(f -> !f.equals(unfinished)).toList().forEach(Fs::rmTree); }
    Fs.ofV(() -> Fs.delete(unfinished));
    Fs.ofV(() -> Fs.delete(out));
  }
  static List<Path> iconFiles(Path folder, MainsInfo.Claim icon){
    var zips= Stream.concat(Stream.of(icon.diskPath()), Stream.of(icon.zipSteps().split(";")).filter(z -> !z.isEmpty()));
    var segments= icon.zipEntry().isEmpty() ? Stream.of(icon.diskPath()) : Stream.concat(zips.map(z -> z.substring(0, z.length()-".zip".length())), Stream.of(icon.zipEntry()));
    var png= folder.resolve(outDir).resolve(Project.iconsDir).resolve((icon.icon().startsWith(Project.base+".") ? "_base/" : "")+Join.of(segments, "", "/", ""));
    var name= png.getFileName().toString();
    return Fs.isWindows() ? List.of(png, png.resolveSibling(name.substring(0, name.lastIndexOf('.'))+".ico")) : List.of(png);
  }
  /// The mains of every <pkg>.json of .fearless_out, by name.
  static Map<String,MainsInfo.Main> cachedMains(Path folder){
    var out= folder.resolve(outDir);
    if (!Fs.isDirectory(out)){ return Map.of(); }
    try (var s= Fs.of(() -> Fs.list(out))){
      var jsons= s.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList();
      return guarded(folder, () -> MainsInfo.merge(jsons.stream().map(j -> new LimitedJsonParser(OutputHelper.read(j).orElseThrow(), j).api().manifest())).mains());
    }
  }
  default Path docs(Path folder, String packageName){
    return packageName.equals(Project.base) ? baseCachePath().orElseThrow().resolve("base.html") : folder.resolve(outDir).resolve(packageName+".html");
  }
  /// Compiles, then runs the mains of the packages of the highest rank: the portable Fearless.
  default String main(Path project, Path stdLibBase) throws InterruptedException{
    var used= new java.util.HashSet<String>();
    compile(project, stdLibBase, project.getFileName().toString(), cs -> cs.stream().filter(used::add).findFirst()).ifPresent(e -> { throw e; });
    finish(project);
    var c= Project.of(this, project, Since.anyTime);
    var top= c.ownPackages.stream().mapToInt(Project.Pkg::rank).max().orElse(0);
    var sb= new StringBuilder();
    for (var p : c.ownPackages.stream().filter(p -> p.rank() == top).toList()){ sb.append(runAllMains(project, stdLibBase, p.name())); }
    return sb.toString();
  }
  default String runAllMains(Path project, Path stdLibBase, String pkgName) throws InterruptedException{
    return runMains(Push.of(runData(project, stdLibBase), "-Dapple.awt.application.name="+project.getFileName()), Push.of(project.resolve(outDir), sharedClasspath()), "_"+pkgName+".Main");
  }
  /// Writes base.json, base.jar, base.html and base.txt into baseCache; the backend of this Coordinator writes base into out.
  default void buildBaseCache(Path stdLibBase, Path out, Path baseCache){
    Fs.ensureDir(baseCache);
    var o= sourceOracle(stdLibBase, Since.anyTime);
    var other= OtherPackages.empty();
    var rich= SourceOracleWithAutoload.ofBase(o);
    var capabilities= new CapabilityEnvironment(rich.autoloadedAssets());
    var core= frontend(Project.base, rich.sources(o.allFiles()), rich.oracle(), other, Map.of(), capabilities, Runnable::run);
    backend(Project.base, core, rich.oracle(), other, capabilities);
    Project.replace(baseCache.resolve("base.json"), OutputHelper.apiJson(core, "", new MainsInfo(Map.of(), MainsInfo.attenuators(core), List.of(), List.of())));
    for (var x : List.of(".jar", ".html", ".txt")){ Fs.ofV(() -> Fs.move(out.resolve("base"+x), baseCache.resolve("base"+x), StandardCopyOption.REPLACE_EXISTING)); }
  }
  static <R> R guarded(Path project, Supplier<R> f){
    var cached= Fs.exists(project.resolve(outDir));
    try{ return f.get(); }
    catch (UserError|UncheckedIOException e){ throw e; }
    catch (RuntimeException | AssertionError e){
      if (!cached){ throw e; }
      throw UserError.cacheCorruptedOrCrash(e);
    }
  }
  default String drawing() throws InterruptedException{ return runMains(List.of("--enable-native-access=ALL-UNNAMED"), sharedClasspath(), "_base.Vk").strip(); }
  static ChildJvm startMain(Path project, Path base, String main, Optional<Path> input, Map<String,Path> mounts, Map<String,Path> locks, List<String> attenuators, List<String> desk, List<Path> sharedClasspath, Consumer<String> out){
    var jvmArgs= new ArrayList<>(runData(project, base));
    input.ifPresent(i -> jvmArgs.add("-DfearlessInput.file="+PathClassLoader.argOf(i)));
    mounts.forEach((k,v) -> jvmArgs.add("-DfearlessData."+k+"="+PathClassLoader.argOf(v)));
    locks.forEach((k,v) -> jvmArgs.add("-DfearlessDataLock."+k+"="+PathClassLoader.argOf(v)));
    if (!attenuators.isEmpty()){ jvmArgs.add("-DfearlessAttenuators="+String.join(",", attenuators)); }
    jvmArgs.addAll(desk);
    jvmArgs.add("-Dapple.awt.application.name="+main.substring(main.indexOf('.') + 1));
    return startProgram(jvmArgs, Push.of(project.resolve(outDir), sharedClasspath), "_"+main.substring(0, main.indexOf('.'))+".Main", out, main);
  }
  String outDir= ".fearless_out";
  default List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres, CapabilityEnvironment capabilities, Consumer<Runnable> writes){
    try{ return new FrontendLogicMain().of(pkgName, vres, files, other, core -> docs(pkgName, !zipped(files), () -> files, other, core, capabilities, names(pkgName, files, oracle, other, vres), writes)); }
    catch (FearlessException e){ throw Report.sourceError(e.render(oracle)); }
  }
  default Function<TName,String> names(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres){
    try{ return new FrontendLogicMain().names(pkgName, vres, files, other); }
    catch (FearlessException e){ throw Report.sourceError(e.render(oracle)); }
  }
  private List<TestSource> docs(String pkgName, boolean testable, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities, Function<TName,String> names, Consumer<Runnable> writes){
    var docs= backendTools(pkgName, oracle, other, core, capabilities).docs();
    core.forEach(docs::visitAll);
    return docs.complete(names, testable, writes);
  }
  default void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, CapabilityEnvironment capabilities){
    new NaiveBackendLogicMain().of(backendTools(pkgName, oracle, other, core, capabilities), sharedClasspath());
  }
  default BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
    var unused= Path.of("unused");
    return BackendTools.of(pkgName, oracle, other, core, unused, baseCachePath(), false, unused, capabilities);
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
  static Map<String,Map<String,String>> rankMap(SourceOracle o, List<Ref> allRanks){
    var ranks= new LinkedHashMap<String,Ref>();
    allRanks.forEach(r -> ranks.put(pkgName(r), r));
    try{ return new FrontendLogicMain().parseRankFiles(ranks, Comparator.comparingInt(Helper::rankNumber)); }
    catch (FearlessException e){ throw Report.sourceError(e.render(o)); }
  }
  static LinkedHashMap<String,List<Ref>> pkgMap(SourceOracle o){
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
