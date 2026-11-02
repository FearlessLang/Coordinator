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
import utils.Bug;
import utils.DistinctBy;
import utils.Pos;

public record MainsInfo(Map<String,Main> mains, List<String> attenuators, List<String> dataRead, List<String> dataEdit){
  public MainsInfo{ mains = Collections.unmodifiableMap(new TreeMap<>(mains)); attenuators = attenuators.stream().sorted().toList(); dataRead = dataRead.stream().sorted().toList(); dataEdit = dataEdit.stream().sorted().toList(); }
  public MainsInfo(Map<String,Main> mains){ this(mains, List.of(), List.of(), List.of()); }
  public record Main(List<Claim> shortcuts, List<Claim> openWiths, List<Claim> icons){}
  /// An icon, as the source writes the claim, with the chosen extension: named is the extension the source names, empty for an autoselected one.
  public record Claim(String icon, String diskPath, String zipSteps, String zipEntry, String named, String extension){}
  public interface Choose{ String extension(String main, boolean shortcut, Claim claim); }
  MainsInfo withExtensions(Choose f){
    return new MainsInfo(mains.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> chosen(e.getKey(), e.getValue(), f))), attenuators, dataRead, dataEdit);
  }
  private static Main chosen(String main, Main m, Choose f){ return new Main(chosen(main, m.shortcuts(), true, f), chosen(main, m.openWiths(), false, f), m.icons()); }
  private static List<Claim> chosen(String main, List<Claim> cs, boolean shortcut, Choose f){
    return cs.stream().map(c -> new Claim(c.icon(), c.diskPath(), c.zipSteps(), c.zipEntry(), c.named(), c.named().isEmpty() ? f.extension(main, shortcut, c) : c.named())).toList();
  }
  static MainsInfo merge(Stream<MainsInfo> infos){
    var all= infos.toList();
    var mains= all.stream().flatMap(i -> i.mains().entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return new MainsInfo(mains, all.stream().flatMap(i -> i.attenuators().stream()).toList(), all.stream().flatMap(i -> i.dataRead().stream()).toList(), all.stream().flatMap(i -> i.dataEdit().stream()).toList());
  }
  public String print(){
    var ms= mains.entrySet().stream().map(e -> new Info.Obj.Field(e.getKey(), Info.noSpan, info(e.getValue()))).toList();
    var fields= List.of(new Info.Obj.Field("mains", Info.noSpan, new Info.Obj(ms, Info.noSpan)), names("attenuators", attenuators), names("dataRead", dataRead), names("dataEdit", dataEdit));
    return Info.print(new Info.Obj(fields, Info.noSpan));
  }
  private static Info info(Main m){ return lst(List.of(claims(m.shortcuts()), claims(m.openWiths()), claims(m.icons()))); }
  private static Info claims(List<Claim> cs){ return lst(cs.stream().map(MainsInfo::info).toList()); }
  private static Info info(Claim c){ return lst(Stream.of(c.icon(), c.diskPath(), c.zipSteps(), c.zipEntry(), c.named(), c.extension()).map(MainsInfo::str).toList()); }
  private static Info.Obj.Field names(String key, List<String> names){ return new Info.Obj.Field(key, Info.noSpan, lst(names.stream().map(MainsInfo::str).toList())); }
  private static Info str(String s){ return new Info.Str(s, Info.noSpan); }
  private static Info lst(List<Info> items){ return new Info.Lst(items, Info.noSpan); }
  static MainsInfo parse(String text, Path file){
    try{ return new Shape(text).all(Info.parse(text, file.toUri())); }
    catch (UserError e){ throw Bug.of(e); }
  }
  private record Shape(String text){
    MainsInfo all(Info i){
      var shape= i instanceof Info.Obj o && o.fields().stream().map(Info.Obj.Field::key).toList().equals(List.of("mains", "attenuators", "dataRead", "dataEdit"));
      if (!shape){ throw err(i, "an object {\"mains\": {...}, \"attenuators\": [...], \"dataRead\": [...], \"dataEdit\": [...]}"); }
      var fs= ((Info.Obj)i).fields();
      return new MainsInfo(mains(fs.get(0).value()), names(fs.get(1).value(), "attenuator"), names(fs.get(2).value(), "DataRead type"), names(fs.get(3).value(), "DataEdit type"));
    }
    List<String> names(Info i, String what){ return lst(i, "a list [...] of "+what+" names").stream().map(this::str).toList(); }
    Map<String,Main> mains(Info i){
      if (!(i instanceof Info.Obj o)){ throw err(i, "an object {...} mapping each main to [[shortcut claims], [openWith claims], [icon claims]]"); }
      return o.fields().stream().collect(Collectors.toMap(Info.Obj.Field::key, f -> main(f.value())));
    }
    Main main(Info i){
      var l= lst(i, 3, "a main as [[shortcut claims], [openWith claims], [icon claims]]");
      return new Main(claims(l.get(0)), claims(l.get(1)), claims(l.get(2)));
    }
    List<Claim> claims(Info i){ return lst(i, "a list [...] of claims").stream().map(this::claim).toList(); }
    Claim claim(Info i){
      var s= lst(i, 6, "a claim as [icon, diskPath, zipSteps, zipEntry, named, extension]").stream().map(this::str).toList();
      return new Claim(s.get(0), s.get(1), s.get(2), s.get(3), s.get(4), s.get(5));
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
    var dataEdit= data(core, new TName("base.DataEdit", 0, Pos.unknown));
    var dataRead= data(core, LiteralDeclarations.dataRead).stream().filter(n -> !dataEdit.contains(n)).toList();
    return new MainsInfo(mains, attenuators(core), dataRead, dataEdit);
  }
  public static List<String> attenuators(List<Literal> core){ return concrete(core, LiteralDeclarations.attenuator); }
  /// a concrete DataRead or DataEdit type has no generic parameter (DataReadDataEditDesign)
  private static List<String> data(List<Literal> core, TName t){ return AllLs.of(core).values().stream().filter(l -> l.name().arity() == 0 && concrete(l, t)).map(l -> l.name().s()).toList(); }
  private static List<String> concrete(List<Literal> core, TName t){ return AllLs.of(core).values().stream().filter(l -> concrete(l, t)).map(l -> l.name().s()).toList(); }
  public static boolean isMain(Literal l){ return concrete(l, LiteralDeclarations.main); }
  private static boolean concrete(Literal l, TName t){ return LiteralDeclarations.has(l.cs(), t) && l.ms().stream().noneMatch(m -> m.sig().abs()); }
  private record Of(SourceOracle src, SourceOracle stLib){
    void check(Literal l){
      for (var c : l.cs()){
        var notAsset= LiteralDeclarations.claims.contains(c.name()) && SourceOracleWithAutoload.imageAsset(src, stLib, name(c.ts().getFirst())).isEmpty();
        if (notAsset){ throw Report.claimIconNotAsset(src, l, c); }
      }
    }
    Main main(Literal l){ return new Main(claims(l, "Shortcut"), claims(l, "OpenWith"), icons(l.cs()).stream().map(n -> claim(n, "").orElseThrow()).toList()); }
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
      return SourceOracleWithAutoload.imageAsset(src, stLib, icon).map(t -> new Claim(icon.s(), t.diskPath(), t.zipSteps(), t.zipEntry(), ext, ext));
    }
    static TName name(T t){ return ((T.RCC)t).c().name(); }
  }
}
