package coordinator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import coordinator.Coordinator.State;
import coordinator.Coordinator.TakeFreeExtension;
import core.E.Literal;
import core.OtherPackages;
import core.TName;
import fileAssociations.Ico;
import realSourceOracle.RealSourceOracleWithZip;
import realSourceOracle.SourceOracleWithAutoload;
import realSourceOracle.ZipLocator;
import tools.Fs;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import userMessages.Report;
import userMessages.UserError;
import utils.Bug;
import utils.Join;
import utils.OneOr;

/// The Project of CoordinatorDesign: the files of a project folder at one moment, and what
/// the Coordinator keeps of them in `.fearless_out`.
final class Cache{
  static final String errorFile= "_error.txt";
  static final String unfinishedFile= "_unfinished.txt";
  static final String iconsDir= "icons";
  static final String base= "base";
  record Pkg(String name, int rank, List<Ref> files){}
  record File(String path, long time, String content){}
  record Api(Map<TName,Literal> types, String inputsSnapshot, MainsInfo manifest){}
  final Coordinator c;
  final Path folder;
  final Path out;
  final SourceOracle o;
  final List<File> files;
  final Optional<UserError> problem;
  final List<Pkg> ownPackages;
  final Map<String,Map<String,String>> rankMap;
  final Map<String,Ref> rankFiles;
  private Cache(Coordinator c, Path folder, SourceOracle o, List<File> files, Optional<UserError> problem, List<Pkg> ownPackages, Map<String,Map<String,String>> rankMap, Map<String,Ref> rankFiles){
    this.c= c; this.folder= folder; out= folder.resolve(Coordinator.outDir); this.o= o; this.files= files; this.problem= problem;
    this.ownPackages= ownPackages; this.rankMap= rankMap; this.rankFiles= rankFiles;
  }
  static Cache of(Coordinator c, Path folder){
    try{
      var o= c.sourceOracle(folder);
      var map= Helper.pkgMap(o);
      var ranks= map.values().stream().map(Helper::okPkgContent).toList();
      var rankMap= Helper.rankMap(o, ranks);
      var own= ranks.stream().map(r -> new Pkg(Helper.pkgName(r), Helper.rankNumber(r), map.get(Helper.pkgName(r))))
        .sorted(Comparator.comparingInt(Pkg::rank).thenComparing(Pkg::name)).toList();
      var rankFiles= ranks.stream().collect(Collectors.toUnmodifiableMap(Helper::pkgName, r -> r));
      return new Cache(c, folder, o, files(o), Optional.empty(), own, rankMap, rankFiles);
    }
    catch (UserError e){ return new Cache(c, folder, null, List.of(), Optional.of(e), List.of(), Map.of(), Map.of()); }
  }
  private static List<File> files(SourceOracle o){
    return o.allFiles().stream().map(r -> new File(r.fearPath(), r.lastModified(), sha(r.loadBytes()))).sorted(Comparator.comparing(File::path)).toList();
  }
  private static String sha(byte[] bytes){
    try{ return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException e){ throw Bug.of(e); }
  }
  List<Pkg> packages(){ return Stream.concat(Stream.of(new Pkg(base, 0, List.of())), ownPackages.stream()).toList(); }
  Path outFolder(String pkg){ return pkg.equals(base) ? c.baseCachePath().orElseThrow() : out; }
  Optional<Api> optApi(String pkg){
    var json= outFolder(pkg).resolve(pkg+".json");
    return OutputHelper.read(json).map(s -> new LimitedJsonParser(s, json).api());
  }
  Optional<Long> optJarTime(String pkg){
    var t= Fs.lastModified(outFolder(pkg).resolve(pkg+".jar"));
    return t < 0 ? Optional.empty() : Optional.of(t);
  }
  List<Pkg> lowerPackages(Pkg p){
    return packages().stream().filter(q -> q.rank() < p.rank()).toList();
  }
  String inputsSnapshot(Pkg p){
    var paths= p.files().stream().map(Ref::fearPath).sorted();
    var lower= lowerPackages(p).stream().map(q -> q.name()+" "+optJarTime(q.name()).orElseThrow()).sorted();
    var directives= rankMap.getOrDefault(p.name(), Map.of()).entrySet().stream().map(e -> e.getKey()+" "+e.getValue().split(" ")[0]).sorted();
    return Join.of(paths, "", "\n", "")+"\n---\n"+Join.of(lower, "", "\n", "")+"\n---\n"+Join.of(directives, "", "\n", "", "");
  }
  static long newestFileTime(Pkg p){ return p.files().stream().mapToLong(Ref::lastModified).max().getAsLong(); }
  boolean upToDate(Pkg p){
    var api= optApi(p.name());
    var jar= optJarTime(p.name());
    if (api.isEmpty() || jar.isEmpty()){ return false; }
    return api.get().inputsSnapshot().equals(inputsSnapshot(p)) && jar.get() >= newestFileTime(p);
  }
  boolean unfinished(){ return Fs.exists(out.resolve(unfinishedFile)); }
  private List<Path> jars(){
    try (var s= Fs.of(() -> Fs.list(out))){ return s.filter(f -> f.getFileName().toString().endsWith(".jar")).toList(); }
  }
  List<Path> otherFiles(){
    if (!Fs.isDirectory(out)){ return List.of(); }
    var kept= Stream.concat(Stream.of(errorFile, unfinishedFile, iconsDir),
      ownPackages.stream().flatMap(p -> Stream.of(".json", ".jar", ".html", ".txt").map(x -> p.name()+x))).toList();
    try (var s= Fs.of(() -> Fs.list(out))){ return s.filter(f -> !kept.contains(f.getFileName().toString())).sorted().toList(); }
  }
  MainsInfo mergedManifest(){ return MainsInfo.merge(packages().stream().map(p -> optApi(p.name()).orElseThrow().manifest())); }
  State check(){
    if (problem.isPresent()){ return new State.Invalid(problem.get().getMessage()); }
    if (unfinished()){ return new State.OutOfDate(); }
    if (!otherFiles().isEmpty()){ return new State.OutOfDate(); }
    if (ownPackages.stream().anyMatch(p -> !upToDate(p))){ return new State.OutOfDate(); }
    return new State.Compiled(mergedManifest(), packages().stream().map(Pkg::name).toList());
  }
  Optional<UserError> compile(Path stdLibBase, String projectName, TakeFreeExtension take){
    if (problem.isPresent()){ return fail(problem.get()); }
    var cached= Fs.exists(out);
    if (unfinished()){ jars().forEach(Fs::rmTree); }
    Fs.writeUtf8(out.resolve(unfinishedFile), "");
    otherFiles().forEach(Fs::rmTree);
    var stLib= RealSourceOracleWithZip.stdLib(stdLibBase);
    Optional<UserError> error= Optional.empty();
    try{ for (var p : ownPackages){ if (!upToDate(p)){ compilePackage(p, stLib, stdLibBase, projectName, take); } } }
    catch (UserError e){ error = Optional.of(e); }
    catch (RuntimeException|AssertionError e){
      if (!cached){ throw e; }
      error = Optional.of(UserError.cacheCorruptedOrCrash(e));
    }
    if (!of(c, folder).files.equals(files)){ return fail(Report.filesUpdated(projectName)); }
    if (error.isPresent()){ return fail(error.get()); }
    Fs.ofV(() -> Fs.deleteIfExists(out.resolve(errorFile)));
    return Optional.empty();
  }
  private Optional<UserError> fail(UserError e){
    writeError(folder, e.getMessage());
    return Optional.of(e);
  }
  private OtherPackages other(Pkg p){
    var lower= lowerPackages(p);
    var res= OtherPackages.start(rankMap, rankFiles, optApi(base).orElseThrow().types(), 0);
    for (var q : lower){ if (!q.name().equals(base)){ res = res.mergeWith(optApi(q.name()).orElseThrow().types(), 0); } }
    return res;
  }
  private void compilePackage(Pkg p, SourceOracle stLib, Path stdLibBase, String projectName, TakeFreeExtension take){
    var other= other(p);
    var vres= other.virtualizationMap().getOrDefault(p.name(), Map.of());
    var rich= SourceOracleWithAutoload.of(o, "_"+p.name(), c.names(p.name(), p.files(), o, other, vres));
    var capabilities= new CapabilityEnvironment(rich.autoloadedAssets());
    var core= c.frontend(p.name(), rich.sources(p.files()), rich.oracle(), other, vres, capabilities);
    var manifest= MainsInfo.of(core, o, stLib);
    writeIcons(manifest, stdLibBase);
    var chosen= manifest.withExtensions((main, kind, claim) -> autoselect(p, manifest, projectName, main, kind, claim, take));
    replace(out.resolve(p.name()+".json"), OutputHelper.apiJson(core, inputsSnapshot(p), chosen));
    writeCompiled(p, () -> c.backend(p.name(), core, rich.oracle(), other, capabilities));
  }
  private void writeCompiled(Pkg p, Runnable backend){
    Fs.writeUtf8(out.resolve(unfinishedFile), "", newestFileTime(p));
    backend.run();
    Fs.writeUtf8(out.resolve(unfinishedFile), "", Fs.lastModified(out.resolve(p.name()+".jar")));
  }
  private String autoselect(Pkg p, MainsInfo manifest, String projectName, String main, boolean shortcut, MainsInfo.Claim claim, TakeFreeExtension take){
    var named= Stream.concat(Stream.of(manifest), lowerPackages(p).stream().map(q -> optApi(q.name()).orElseThrow().manifest()))
      .flatMap(m -> m.mains().values().stream()).flatMap(m -> Stream.concat(m.shortcuts().stream(), m.openWiths().stream()))
      .map(MainsInfo.Claim::named).filter(n -> !n.isEmpty()).toList();
    var previous= OneOr.opt("previous extension of "+main, optApi(p.name()).stream()
      .flatMap(a -> Stream.ofNullable(a.manifest().mains().get(main)))
      .flatMap(m -> (shortcut ? m.shortcuts() : m.openWiths()).stream())
      .filter(c -> c.named().isEmpty() && c.icon().equals(claim.icon()))
      .map(MainsInfo.Claim::extension)
      .filter(e -> !named.contains(e)));
    if (previous.isPresent()){ return previous.get(); }
    var prefix= shortcut ? "fapp" : "ffile";
    var firstNumber= Math.floorMod((projectName+"::"+main+"::"+claim.icon()).hashCode(), 1000);
    var candidates= IntStream.range(0, 1000).mapToObj(i -> prefix+String.format(Locale.ROOT, "%03d", (firstNumber+i)%1000)).filter(e -> !named.contains(e)).toList();
    return take.take(candidates).orElseThrow(() -> Report.noFreeExtension(main, shortcut, claim.icon()));
  }
  private static final byte[] pngHead= {(byte)0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
  private void writeIcons(MainsInfo manifest, Path stdLibBase){
    manifest.mains().forEach((main, m) -> m.icons().forEach(icon -> writeIcon(main, written(m, icon), icon, stdLibBase)));
  }
  /// the kind and the claim of the icon as the source writes the claim, for the error of a refused icon
  private static Map.Entry<String,MainsInfo.Claim> written(MainsInfo.Main m, MainsInfo.Claim icon){
    var shortcut= m.shortcuts().stream().filter(c -> c.icon().equals(icon.icon())).findFirst().map(c -> Map.entry("Shortcut", c));
    var openWith= m.openWiths().stream().filter(c -> c.icon().equals(icon.icon())).findFirst().map(c -> Map.entry("OpenWith", c));
    return shortcut.or(() -> openWith).orElse(Map.entry("PreLoadIcon", icon));
  }
  private void writeIcon(String main, Map.Entry<String,MainsInfo.Claim> claim, MainsInfo.Claim icon, Path stdLibBase){
    var file= (icon.icon().startsWith(base+".") ? stdLibBase : folder).resolve(icon.diskPath());
    var steps= icon.zipSteps().isEmpty() ? List.<String>of() : List.of(icon.zipSteps().split(";"));
    var bytes= icon.zipEntry().isEmpty() ? Fs.of(() -> Fs.readAllBytes(file)) : ZipLocator.entryBytes(file, steps, icon.zipEntry());
    if (bytes.length < 24 || !Arrays.equals(bytes, 0, 16, pngHead, 0, 16)){ throw Report.iconRefused(main, claim.getKey(), claim.getValue(), "is not a PNG image"); }
    var w= ByteBuffer.wrap(bytes).getInt(16);
    var h= ByteBuffer.wrap(bytes).getInt(20);
    if (w != h || w < 64 || w > 1024){ throw Report.iconRefused(main, claim.getKey(), claim.getValue(), "is "+w+"x"+h+" pixels"); }
    if (!decodes(bytes)){ throw Report.iconRefused(main, claim.getKey(), claim.getValue(), "is not a PNG image"); }
    var files= Coordinator.iconFiles(folder, icon.icon());
    replace(files.getFirst(), bytes);
    if (files.size() > 1){
      var tmp= files.get(1).resolveSibling(UUID.randomUUID()+".tmp");
      Ico.fromPng(files.getFirst(), tmp);
      Fs.ofV(() -> Fs.move(tmp, files.get(1), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }
  }
  private static boolean decodes(byte[] bytes){
    try{ return ImageIO.read(new ByteArrayInputStream(bytes)) != null; }
    catch (IOException e){ return false; }
  }
  static void replace(Path file, String text){ replace(file, text.getBytes(StandardCharsets.UTF_8)); }
  static void replace(Path file, byte[] bytes){
    Fs.ensureDir(file.getParent());
    var tmp= file.resolveSibling(UUID.randomUUID()+".tmp");
    Fs.ofV(() -> Fs.write(tmp, bytes));
    Fs.ofV(() -> Fs.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
  }
  static void writeError(Path folder, String error){ replace(folder.resolve(Coordinator.outDir).resolve(errorFile), error); }
}
