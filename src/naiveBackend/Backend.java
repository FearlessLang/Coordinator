package naiveBackend;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import coordinator.MainsInfo;
import core.AllLs;
import core.E.Literal;
import core.LiteralDeclarations;
import core.M;
import core.MName;
import core.RC;
import core.TName;
import offensiveUtils.Require;
import realSourceOracle.SourceOracleWithAutoload;
import tools.Fs;
import utils.Join;
import utils.Range;
import utils.Pos;

public final class Backend{
  final Path out;
  final BackendTools tools;
  final ArrayList<Consumer<List<Path>>> fixers= new ArrayList<>();
  final TreeMap<String,String> mains= new TreeMap<>();
  final TreeMap<String,String> preloaded= new TreeMap<>();
  final Map<TName,Literal> local;
  public Backend(Path out, BackendTools tools){
    assert Require.nonNull(out, tools);
    this.out= out;
    this.tools= tools;
    local= AllLs.of(tools.decs());
  }
  boolean isRepr(Literal l){ return l.name().equals(new TName("base.Repr", 1, Pos.unknown)); }
  public List<Consumer<List<Path>>> produceJavaCode(){
    Fs.cleanDir(out);
    for (var d : tools.decs()){ generateInterface(d, false); tools.checks().checkFileReplacement(d, decTypeName(d.name())); }
    writeMainJava();
    return List.copyOf(fixers);
  }
  void generateInterface(Literal l, boolean abstractOnly){
    var iface= decTypeName(l.name());
    var sb= new BytecodeLineFix(iface, l.pos().fileName())
      .a("package _"+tools.pkgName()+";\n")
      .a("public interface "+iface+extendsClause(l)+"{\n");
    for (var m : l.ms()){ emitTopMethod(sb, l, m, abstractOnly); }
    var hasInstance= !abstractOnly && hasInstance(l);
    if (hasInstance && implementsType(l, "base.FileLog", 0)){
      assert l.name().arity()==0;
      sb.a("  _base.AppLog _appLog= _base.AppLog.open(java.nio.file.Path.of(\".out\",\"logs\",\"_"+tools.pkgName()+"\",\""+Fs.fileNameOfType(l.name().simpleName()).orElseThrow()+".log\"), false);\n  default _base.AppLog _log(){ return _appLog; }\n");
    }
    if (isRepr(l)){
      sb.a("  Object _reprCacheGet(Object k, java.util.function.Supplier<Object> f, long time);\n");
      sb.a("  void _reprCacheFlush();\n");
    }
    if (!abstractOnly){ sb.a(nameMethod(l, "default")); }
    if (hasInstance){ sb.a("  "+iface+" instance= new _Instance();\n  final class _Instance implements "+iface+selfNorm(l)+"}\n"+cacheField(l)); }
    Fs.writeUtf8(out.resolve(iface+".java"), sb.a("}").toString());
    if (hasInstance && MainsInfo.isMain(l)){ mains.put(l.name().s(), iface); preloaded.put(l.name().s(), preloaded(l)); }
    if (!tools.checks().replaced(iface)){ fixers.add(sb); }
  }
  private String preloaded(Literal l){ return Join.of(MainsInfo.icons(l.cs()).stream().map(n -> javaStrLit(n.s())+", "+typeName(n)+".instance"), "", ", ", ""); }
  String nameMethod(Literal l, String modifier){
    var named= new TName("base.Named", 0, Pos.unknown);
    var fromNamed= l.ms().stream().anyMatch(m -> m.sig().m().equals(new MName(".name", 0)) && m.sig().origin().equals(named));
    if (!fromNamed){ return ""; }
    var name= l.name().s()+Join.of(Collections.nCopies(l.name().arity(), "_"), "[", ",", "]", "");
    return "  "+modifier+" Object read$name$0(){ return _base.Str$c$0Instance.instance("+javaStrLit(name)+"); }\n";
  }
  private static boolean implementsType(Literal l, String name, int arity){ return LiteralDeclarations.has(l.cs(), new TName(name, arity, Pos.unknown)); }
  private static String cacheField(Literal l){
    for (int shape : Range.of(0, 3)){
      var t= "_base.Cache"+shape;
      if (implementsType(l, "base.CacheF", shape+1) || implementsType(l, "base.CacheMemo", shape+1)){ return "  "+t+" _cache= new "+t+"(1, instance);\n  default "+t+" _cache"+shape+"(){ return _cache; }\n"; }
    }
    return "";
  }
  private static String selfNorm(Literal l){
    if (!LiteralDeclarations.has(l.cs(), LiteralDeclarations.captureFree)){ return "{"; }
    return ", _base.Norm$o$1.Own{ final Object _norm= _base.Norm$o$1.of(this); public Object _norm(){ return _norm; }";
  }
  private boolean hasInstance(Literal l){
    assert !l.thisName().isEmpty() || LiteralDeclarations.has(l.cs(), LiteralDeclarations.captureFree);
    return l.ms().stream().noneMatch(m -> m.sig().abs() && m.sig().rc() != RC.mut);
  }

  String extendsClause(Literal lit){ return Join.of(
    Stream.of(lit.cs().stream().map(c -> typeName(c.name())).distinct(), lit.onlyImmCapture().inner ? Stream.of("_base.OnlyImmCapture") : Stream.<String>of(), lit.infName() ? Stream.of("_base.InferredName") : Stream.<String>of()).flatMap(s -> s),
    " extends ", ", ", "", ""
  );}
  String paramsSig(M m){ return Join.of(
    IntStream.range(0, m.xs().size()).mapToObj(i -> "Object p"+i),
    "(", ", ", ")", "()"
  );}
  void emitTopMethod(BytecodeLineFix sb, Literal l, M m, boolean abstractOnly){
    var jName= mangledMethodName(m.sig().rc(), m.sig().m());
    var o= m.sig().origin();
    if (!o.equals(l.name())){
      if (clashes(l, m)){ sb.a("  default Object "+jName+paramsSig(m)+"{ return "+typeName(o)+".super."+jName+Join.of(IntStream.range(0, m.xs().size()).mapToObj(i -> "p"+i), "(", ", ", ")", "()")+"; }\n"); }
      return;
    }
    sb.a("  default Object "+jName+paramsSig(m)+"{\n");
    if (abstractOnly || m.sig().abs()){
      sb.a("    throw new AssertionError(\"Uncallable method: "+ifaceNameFor(l)+"."+jName+" on \"+this.getClass().getName());\n  }\n");
      return;
    }
    tools.checks().checkTopMethod(m, decTypeName(l.name()), jName, hasInstance(l));
    new ProduceBody(sb, this, l.thisName(), m).emitBody();
  }
  private boolean clashes(Literal l, M m){
    var o= dec(m.sig().origin());
    return l.cs().stream().map(c -> dec(c.name()))
      .filter(d -> !d.name().equals(o.name()) && !LiteralDeclarations.has(o.cs(), d.name()))
      .anyMatch(d -> d.ms().stream().anyMatch(mi -> mi.sig().origin().equals(d.name()) && mi.sig().rc() == m.sig().rc() && mi.sig().m().equals(m.sig().m())));
  }
  private Literal dec(TName n){ return LiteralDeclarations.from(n, local::get, tools.other()); }
  String ifaceNameFor(Literal l){
    if (!l.infName() || l.onlyImmCapture().inner){ return decTypeName(l.name()); }
    if (l.cs().isEmpty()){ return "Object"; }
    return typeName(l.cs().getFirst().name());
  }
  String encodeTrailingPrimes(String s){
    var k= 0;
    while (k<s.length() && s.charAt(s.length() - 1 - k)=='\''){ k += 1; }
    if (k == 0){ return s; }
    var head= s.substring(0, s.length() - k);
    assert head.indexOf('\'') < 0 : "prime (') must be trailing only: "+s;
    return head+"$p"+k;
  }
  String decTypeName(TName n){ return encodeTrailingPrimes(n.simpleName())+"$"+caseTag(n.simpleName())+"$"+n.arity(); }
  String typeName(TName n){ return "_"+encodeTrailingPrimes(n.s())+"$"+caseTag(n.simpleName())+"$"+n.arity(); }
  static String caseTag(String s){
    var bits= Join.of(s.chars().filter(c -> ('A'<=c && c<='Z') || ('a'<=c && c<='z')).mapToObj(c -> c<'a' ? "1" : "0"), "", "", "", "");
    return new BigInteger("1"+bits, 2).toString(36);
  }
  String mangledMethodName(RC rc, MName m){
    var s= m.s();
    return rc.name()+"$"+(s.startsWith(".") ? encodeTrailingPrimes(s.substring(1)) : "$"+mangleOp(s))+"$"+m.arity();
  }
  void writeMainJava(){
    var all= Join.of(mains.keySet().stream().map(n -> "\""+n+"\""), "{", ",", "}", "{}");
    var assets= Join.of(tools.capabilities().autoloadedAssets().values().stream().map(Backend::assetLiteral), "{", ",", "}", "{}");
    var sb= new StringBuilder(8_000)
      .append("package _").append(tools.pkgName()).append(";\n\n")
      .append("public final class Main{\n")
      .append("  static{ _base.NativeLocale.force(); _base.Util.installParentLifeline(); }\n")
      .append("  static final String[] all= ").append(all).append(";\n")
      .append("  public static final String[][] autoloadedAssets= ").append(assets).append(";\n")
      .append("  public static void main(String[] args){ var ok= true; for (String n: args.length == 0 ? all : args){ ok&= run(n); } if (!ok){ System.exit(1); } }\n")
      .append("  static boolean run(String n){\n");
    mains.forEach((n,iface) -> sb
      .append("    if (n.equals(\"").append(n).append("\")){ _base._FluentGUI.preload(").append(preloaded.get(n)).append("); return _base.Util.topLevel(()->")
      .append(iface).append(".instance.imm$main$1(").append(typeName(new TName("base._CapProcess", 0, Pos.unknown))).append(".system())); }\n"));
    sb.append("    throw new AssertionError(\"No main called \"+n+\" in package ").append(tools.pkgName()).append("\");\n  }\n}\n");
    Fs.writeUtf8(out.resolve("Main.java"), sb.toString());
  }
  static String assetLiteral(SourceOracleWithAutoload.Triple t){
    return "{"+javaStrLit(t.diskPath())+","+javaStrLit(t.zipSteps())+","+javaStrLit(t.zipEntry())+"}";
  }
  static String javaStrLit(String s){
    assert s.indexOf('"')<0 && s.indexOf('\\')<0 && s.indexOf('\n')<0 && s.indexOf('\r')<0;
    return "\""+s+"\"";
  }
  String mangleOp(String op){ return Join.of(op.chars().mapToObj(c -> opNames.get(opChars.indexOf(c))), "", "_", ""); }
  static final String opChars= "+-*/%<>=!&|^~?#\\";
  static final List<String> opNames= List.of("plus", "dash", "star", "slash", "pct", "lt", "gt", "eq", "bang", "and", "or", "xor", "tilde", "q", "hash", "bslash");
}
