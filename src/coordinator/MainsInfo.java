package coordinator;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.AllLs;
import core.E.Literal;
import core.LiteralDeclarations;
import core.T;
import core.TName;
import fileSupport.Info;
import realSourceOracle.SourceOracleWithAutoload;
import tools.Fs;
import tools.SourceOracle;
import userMessages.Report;
import userMessages.UserError;
import userMessages.Violation;
import utils.DistinctBy;
import utils.Pos;

public record MainsInfo(Map<String,Main> mains, List<String> attenuators){
  public MainsInfo{ mains = Collections.unmodifiableMap(new TreeMap<>(mains)); attenuators = attenuators.stream().sorted().toList(); }
  public MainsInfo(Map<String,Main> mains){ this(mains, List.of()); }
  public record Main(String file, List<Claim> shortcuts, List<Claim> openWiths, List<Claim> icons){}
  public record Claim(String icon, String diskPath, String zipSteps, String zipEntry, String extension){}
  public static final String suffix= "_manifest.info";
  public static List<Path> files(Path project){
    var out= Helper.out(project).rootDir();
    if (!Fs.isDirectory(out)){ return List.of(); }
    try (var s= Fs.of(() -> Fs.list(out))){ return s.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted().toList(); }
  }
  public static Optional<MainsInfo> read(Path project){
    var fs= files(project);
    if (fs.isEmpty()){ return Optional.empty(); }
    return Optional.of(merge(fs.stream().map(MainsInfo::parse)));
  }
  static MainsInfo merge(Stream<MainsInfo> infos){
    var all= infos.toList();
    var mains= all.stream().flatMap(i -> i.mains().entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return new MainsInfo(mains, all.stream().flatMap(i -> i.attenuators().stream()).sorted().toList());
  }
  public static void write(Path project, MainsInfo info){
    for (var f : files(project)){
      var old= parse(f);
      var now= new MainsInfo(old.mains().keySet().stream().collect(Collectors.toMap(k -> k, k -> info.mains().get(k))), old.attenuators());
      if (!now.equals(old)){ Fs.writeUtf8(f, now.print()); }
    }
  }
  public String print(){
    var ms= mains.entrySet().stream().map(e -> new Info.Obj.Field(e.getKey(), Info.noSpan, info(e.getValue()))).toList();
    var as= lst(attenuators.stream().map(MainsInfo::str).toList());
    var fields= List.of(new Info.Obj.Field("mains", Info.noSpan, new Info.Obj(ms, Info.noSpan)), new Info.Obj.Field("attenuators", Info.noSpan, as));
    return Info.print(new Info.Obj(fields, Info.noSpan));
  }
  MainsInfo located(SourceOracle src, SourceOracle stLib){
    var of= new Of(src, stLib);
    return new MainsInfo(mains.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> of.located(e.getValue()))), attenuators);
  }
  private static Info info(Main m){ return lst(List.of(str(m.file()), claims(m.shortcuts()), claims(m.openWiths()), claims(m.icons()))); }
  private static Info claims(List<Claim> cs){ return lst(cs.stream().map(MainsInfo::info).toList()); }
  private static Info info(Claim c){ return lst(Stream.of(c.icon(), c.diskPath(), c.zipSteps(), c.zipEntry(), c.extension()).map(MainsInfo::str).toList()); }
  private static Info str(String s){ return new Info.Str(s, Info.noSpan); }
  private static Info lst(List<Info> items){ return new Info.Lst(items, Info.noSpan); }
  static MainsInfo parse(Path file){
    var text= OutputHelper.readAllowed(file).orElseThrow();
    try{ return new Shape(text).all(Info.parse(text, file.toUri())); }
    catch (UserError e){ throw OutputHelper.dropCache(file, Violation.cacheInvalidFile(file, e.getMessage())); }
  }
  private record Shape(String text){
    MainsInfo all(Info i){
      var shape= i instanceof Info.Obj o && o.fields().size() == 2 && o.fields().get(0).key().equals("mains") && o.fields().get(1).key().equals("attenuators");
      if (!shape){ throw err(i, "an object {\"mains\": {...}, \"attenuators\": [...]}"); }
      var fs= ((Info.Obj)i).fields();
      return new MainsInfo(mains(fs.get(0).value()), lst(fs.get(1).value(), "a list [...] of attenuator names").stream().map(this::str).toList());
    }
    Map<String,Main> mains(Info i){
      if (!(i instanceof Info.Obj o)){ throw err(i, "an object {...} mapping each main to [file, [shortcut claims], [openWith claims], [icon claims]]"); }
      return o.fields().stream().collect(Collectors.toMap(Info.Obj.Field::key, f -> main(f.value())));
    }
    Main main(Info i){
      var l= lst(i, 4, "a main as [file, [shortcut claims], [openWith claims], [icon claims]]");
      return new Main(str(l.get(0)), claims(l.get(1)), claims(l.get(2)), claims(l.get(3)));
    }
    List<Claim> claims(Info i){ return lst(i, "a list [...] of claims").stream().map(this::claim).toList(); }
    Claim claim(Info i){
      var s= lst(i, 5, "a claim as [icon, diskPath, zipSteps, zipEntry, extension]").stream().map(this::str).toList();
      return new Claim(s.get(0), s.get(1), s.get(2), s.get(3), s.get(4));
    }
    List<Info> lst(Info i, int size, String what){
      var l= lst(i, what);
      if (l.size() != size){ throw err(i, what); }
      return l;
    }
    List<Info> lst(Info i, String what){
      if (i instanceof Info.Lst l){ return l.items(); }
      throw err(i, what);
    }
    String str(Info i){
      if (i instanceof Info.Str s){ return s.value(); }
      throw err(i, "a string \"...\"");
    }
    UserError err(Info i, String what){ return Info.err(text, i.span(), "Expected "+what+" here."); }
  }
  public static List<TName> icons(List<T.C> cs){
    var shortcuts= icons(cs, "Shortcut");
    return Stream.of(shortcuts, Stream.of(new TName("base.IconsFearless", 0, Pos.unknown)), icons(cs, "PreLoadIcon"), icons(cs, "OpenWith"))
      .flatMap(s -> s).gather(DistinctBy.of(TName::s)).toList();
  }
  private static Stream<TName> icons(List<T.C> cs, String kind){ return cs.stream().filter(c -> LiteralDeclarations.claims.contains(c.name()) && c.name().simpleName().equals(kind)).map(c -> Of.name(c.ts().getFirst())); }
  static MainsInfo of(List<Literal> core, SourceOracle src, SourceOracle stLib){
    var all= AllLs.of(core).values();
    var of= new Of(src, stLib);
    all.stream().sorted(Comparator.comparingInt((Literal l) -> l.cs().size()).thenComparing(l -> l.name().s())).forEach(of::check);
    var mains= all.stream().filter(MainsInfo::isMain).collect(Collectors.toUnmodifiableMap(l -> l.name().s(), of::main));
    return new MainsInfo(mains, attenuators(core));
  }
  public static List<String> attenuators(List<Literal> core){ return AllLs.of(core).values().stream().filter(l -> concrete(l, LiteralDeclarations.attenuator)).map(l -> l.name().s()).toList(); }
  public static boolean isMain(Literal l){ return concrete(l, LiteralDeclarations.main); }
  private static boolean concrete(Literal l, TName t){ return LiteralDeclarations.has(l.cs(), t) && l.ms().stream().noneMatch(m -> m.sig().abs()); }
  private record Of(SourceOracle src, SourceOracle stLib){
    void check(Literal l){
      for (var c : l.cs()){
        var notAsset= LiteralDeclarations.claims.contains(c.name()) && SourceOracleWithAutoload.imageAsset(src, stLib, name(c.ts().getFirst())).isEmpty();
        if (notAsset){ throw Report.claimIconNotAsset(src, l, c); }
      }
    }
    Main main(Literal l){
      var file= l.name().pos().fileName().toString().substring(SourceOracle.root.length());
      return new Main(file, claims(l, "Shortcut"), claims(l, "OpenWith"), icons(l.cs()).stream().map(n -> claim(n, "").orElseThrow()).toList());
    }
    List<Claim> claims(Literal l, String kind){
      return l.cs().stream()
        .filter(c -> LiteralDeclarations.claims.contains(c.name()) && c.name().simpleName().equals(kind))
        .map(this::claim).toList();
    }
    Claim claim(T.C c){
      var lit= c.ts().size()==1 ? "" : name(c.ts().get(1)).simpleName();
      return claim(name(c.ts().getFirst()), lit.isEmpty() ? "" : lit.substring(1, lit.length()-1)).orElseThrow();
    }
    Optional<Claim> claim(TName icon, String ext){
      return SourceOracleWithAutoload.imageAsset(src, stLib, icon).map(t -> new Claim(icon.s(), t.diskPath(), t.zipSteps(), t.zipEntry(), ext));
    }
    Main located(Main m){ return new Main(m.file(), located(m.shortcuts()), located(m.openWiths()), located(m.icons())); }
    List<Claim> located(List<Claim> cs){ return cs.stream().map(c -> claim(new TName(c.icon(), 0, Pos.unknown), c.extension()).orElseThrow()).toList(); }
    static TName name(T t){ return ((T.RCC)t).c().name(); }
  }
}
