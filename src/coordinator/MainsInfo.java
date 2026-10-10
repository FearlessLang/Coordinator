package coordinator;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.AllLs;
import core.LiteralDeclarations;
import core.T;
import core.TName;
import core.E.Literal;
import fileSupport.Info;
import realSourceOracle.SourceOracleWithAutoload;
import tools.Fs;
import tools.SourceOracle;
import userMessages.Report;
import userMessages.UserError;
import utils.Pos;

public record MainsInfo(Map<String,Main> mains){
  public MainsInfo{ mains= Collections.unmodifiableMap(new TreeMap<>(mains)); }
  public record Main(String file, List<Claim> shortcuts, List<Claim> openWiths){}
  public record Claim(String icon, String diskPath, String zipSteps, String zipEntry, String extension){}
  public static Optional<MainsInfo> read(Path project){ return Helper.out(project).mainsInfo(); }
  public String print(){
    var fields= mains.entrySet().stream().map(e->new Info.Obj.Field(e.getKey(),Info.noSpan,info(e.getValue()))).toList();
    return Info.print(new Info.Obj(fields,Info.noSpan));
  }
  MainsInfo located(SourceOracle src, SourceOracle stLib){
    var of= new Of(src,stLib);
    return new MainsInfo(mains.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e->of.located(e.getValue()))));
  }
  private static Info info(Main m){ return lst(List.of(str(m.file()),claims(m.shortcuts()),claims(m.openWiths()))); }
  private static Info claims(List<Claim> cs){ return lst(cs.stream().map(MainsInfo::info).toList()); }
  private static Info info(Claim c){ return lst(Stream.of(c.icon(),c.diskPath(),c.zipSteps(),c.zipEntry(),c.extension()).map(MainsInfo::str).toList()); }
  private static Info str(String s){ return new Info.Str(s,Info.noSpan); }
  private static Info lst(List<Info> items){ return new Info.Lst(items,Info.noSpan); }
  static MainsInfo parse(Path file){
    var text= Fs.readUtf8(file);
    return new Shape(text).all(Info.parse(text,file.toUri()));
  }
  private record Shape(String text){
    MainsInfo all(Info i){
      if (!(i instanceof Info.Obj o)){ throw err(i,"an object {...} mapping each main to [file, [shortcut claims], [openWith claims]]"); }
      return new MainsInfo(o.fields().stream().collect(Collectors.toMap(Info.Obj.Field::key,f->main(f.value()))));
    }
    Main main(Info i){
      var l= lst(i,3,"a main as [file, [shortcut claims], [openWith claims]]");
      return new Main(str(l.get(0)),claims(l.get(1)),claims(l.get(2)));
    }
    List<Claim> claims(Info i){ return lst(i,"a list [...] of claims").stream().map(this::claim).toList(); }
    Claim claim(Info i){
      var s= lst(i,5,"a claim as [icon, diskPath, zipSteps, zipEntry, extension]").stream().map(this::str).toList();
      return new Claim(s.get(0),s.get(1),s.get(2),s.get(3),s.get(4));
    }
    List<Info> lst(Info i, int size, String what){
      var l= lst(i,what);
      if (l.size() != size){ throw err(i,what); }
      return l;
    }
    List<Info> lst(Info i, String what){
      if (i instanceof Info.Lst l){ return l.items(); }
      throw err(i,what);
    }
    String str(Info i){
      if (i instanceof Info.Str s){ return s.value(); }
      throw err(i,"a string \"...\"");
    }
    UserError err(Info i, String what){ return Info.err(text,i.span(),"Expected "+what+" here."); }
  }
  static final TName main= new TName("base.Main",0,Pos.unknown);
  static final List<TName> claims= List.of(new TName("base.OpenWith",1,Pos.unknown),new TName("base.OpenWith",2,Pos.unknown),new TName("base.Shortcut",1,Pos.unknown),new TName("base.Shortcut",2,Pos.unknown));
  static Map<String,Main> of(List<Literal> core, SourceOracle src, SourceOracle stLib){
    var all= AllLs.of(core).values();
    var of= new Of(src,stLib);
    all.stream().sorted(Comparator.comparingInt((Literal l)->l.cs().size()).thenComparing(l->l.name().s())).forEach(of::check);
    var nested= all.stream().filter(l->LiteralDeclarations.has(l.cs(),LiteralDeclarations.captureFree));
    return Stream.concat(core.stream(),nested).filter(MainsInfo::isMain).distinct()
      .collect(Collectors.toUnmodifiableMap(l->l.name().s(),of::main));
  }
  private static boolean isMain(Literal l){
    return LiteralDeclarations.has(l.cs(),main)
      && l.ms().stream().noneMatch(m->m.sig().abs());
  }
  private record Of(SourceOracle src, SourceOracle stLib){
    void check(Literal l){
      var cs= l.cs().stream().filter(c->claims.contains(c.name())).toList();
      if (cs.isEmpty()){ return; }
      if (!LiteralDeclarations.has(l.cs(),main)){ throw Report.claimNotMain(src,l,cs.getFirst().name()); }
      var exts= new HashMap<String,T.C>();
      for (var c: cs){
        if (!(c.ts().getFirst() instanceof T.RCC i && i.c().ts().isEmpty())){ throw Report.claimIconNotConcrete(src,l,c); }
        if (SourceOracleWithAutoload.imageAsset(src,stLib,name(c.ts().getFirst())).isEmpty()){ throw Report.claimIconNotAsset(src,l,c); }
        if (c.ts().size() == 1){ continue; }
        var lit= c.ts().get(1) instanceof T.RCC e ? e.c().name().simpleName() : "";
        if (!lit.startsWith("\"") && !lit.startsWith("`")){ throw Report.claimExtNotStr(src,l,c); }
        var ext= lit.substring(1,lit.length()-1);
        if (!Fs.isExtSeg(ext) || ext.equals("fearless")){ throw Report.claimExtInvalid(src,l,c,ext); }
        var shortcut= c.name().equals(claims.get(3));
        if (shortcut != ext.matches("fapp[0-9]{3}")){ throw shortcut ? Report.claimShortcutNotFapp(src,l,c,ext) : Report.claimOpenWithFapp(src,l,c,ext); }
        var prev= exts.putIfAbsent(ext,c);
        if (prev != null){ throw Report.claimExtTwice(src,l,prev,c,ext); }
      }
    }
    Main main(Literal l){
      var file= l.name().pos().fileName().toString().substring(SourceOracle.root.length());
      return new Main(file,claims(l,"Shortcut"),claims(l,"OpenWith"));
    }
    List<Claim> claims(Literal l, String kind){
      return l.cs().stream()
        .filter(c->claims.contains(c.name()) && c.name().simpleName().equals(kind))
        .map(this::claim).toList();
    }
    Claim claim(T.C c){
      var lit= c.ts().size() == 1 ? "" : name(c.ts().get(1)).simpleName();
      return claim(name(c.ts().getFirst()),lit.isEmpty() ? "" : lit.substring(1,lit.length()-1)).orElseThrow();
    }
    Optional<Claim> claim(TName icon, String ext){
      return SourceOracleWithAutoload.imageAsset(src,stLib,icon).map(t->new Claim(icon.s(),t.diskPath(),t.zipSteps(),t.zipEntry(),ext));
    }
    Main located(Main m){ return new Main(m.file(),located(m.shortcuts()),located(m.openWiths())); }
    List<Claim> located(List<Claim> cs){ return cs.stream().map(c->claim(new TName(c.icon(),0,Pos.unknown),c.extension()).orElseThrow()).toList(); }
    static TName name(T t){ return ((T.RCC)t).c().name(); }
  }
}
