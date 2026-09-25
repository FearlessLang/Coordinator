package naiveBackend;

import static offensiveUtils.Require.*;

import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import core.*;
import core.E.*;
import tools.Fs;
import utils.Join;
import utils.Pos;
import utils.Range;

public class Backend{
  public Backend(Path out, BackendTools tools){
    assert nonNull(out,tools);
    this.out= out;
    this.tools= tools;
  }
  Path out;
  BackendTools tools;
  List<Consumer<Path>> fixers= new ArrayList<>();
  boolean isRepr(Literal l){ return l.name().equals(new TName("base.Repr",1,Pos.unknown)); }
  public List<Consumer<Path>> produceJavaCode(){
    Fs.ensureDir(out);
    Fs.cleanDirContents(out);
    tools.decs().forEach(d->{tools.docs().visitLiteral(d); generateInterface(d,false); tools.checks().checkFileReplacement(d, decTypeName(d.name()));});
    tools.checks().checkMagicFulfilled();
    writeMainJava();
    return List.copyOf(fixers);
  }
  void generateInterface(Literal l, boolean abstractOnly){
    var iface= decTypeName(l.name());
    var sb= new BytecodeLineFix(iface, l.pos().fileName())
      .a("package _"+tools.pkgName()+";\n")
      .a("public interface "+iface+extendsClause(l)+"{\n");
    for (var m:l.ms()){ emitTopMethod(sb, l, m, abstractOnly); }
    var hasInstance= hasInstance(l, abstractOnly);
    if (hasInstance && implementsType(l,"base.InMemoryLog",1)){
      sb.a("  java.util.ArrayList<Object> _logStore= new java.util.ArrayList<>();\n");
      sb.a("  default java.util.ArrayList<Object> _log(){ return _logStore; }\n");
    }
    if (hasInstance && implementsType(l,"base.FileLog",0)){
      if (l.name().arity() == 0){
        var name= l.name().simpleName();
        sb.a("  _base.AppLog _appLog= _base.AppLog.open(java.nio.file.Path.of(\".out\",\"logs\",\""+tools.pkgName()+"\",\""+name+".log\"), false);\n");
        sb.a("  default _base.AppLog _log(){ return _appLog; }\n");
      } else {
        sb.a("  default _base.AppLog _log(){ return null; }\n");
      }
    }
    if (isRepr(l)){
      sb.a("  Object _reprCacheGet(Object k, java.util.function.Supplier<Object> f, long time);\n");
      sb.a("  void _reprCacheFlush();\n");
    }
    if (hasInstance){ sb.a("  "+iface+" instance= new "+iface+"(){};"+cacheField(l)); }
    Fs.writeUtf8(out.resolve(iface+".java"), sb.a("}").toString());
    if (hasInstance && implementsType(l,"base.Main",0)){ mains.put(l.name().s(), iface); }
    fixers.add(sb);
  }
  private static boolean implementsType(Literal l, String name, int arity){ return LiteralDeclarations.has(l.cs(), new TName(name,arity,Pos.unknown)); }
  private static String cacheField(Literal l){
    for (int shape : Range.of(0,3)){
      var t= "_base.Cache"+shape;
      if (implementsType(l,"base.CacheF",shape+1) || implementsType(l,"base.CacheMemo",shape+1)){ return "  "+t+" _cache= new "+t+"(1, instance);\n  default "+t+" _cache"+shape+"(){ return _cache; }\n"; }
    }
    return "";
  }
  private boolean hasInstance(Literal l, boolean abstractOnly) {
    if (abstractOnly){ return false; } 
    assert !l.thisName().isEmpty() || LiteralDeclarations.has(l.cs(),LiteralDeclarations.captureFree);
    return l.ms().stream().noneMatch(m->m.sig().abs());
  }

  String extendsClause(Literal lit){ return Join.of(
    Stream.concat(lit.cs().stream().map(c->typeName(c.name())).distinct(), lit.onlyImmCapture().inner ? Stream.of("_base.OnlyImmCapture") : Stream.of()),
    " extends ",", ","",""
  );}
  String paramsSig(M m){ return Join.of(
    IntStream.range(0, m.xs().size()).mapToObj(i->"Object p"+i),
    "(",", ",")","()"
  );}
  void emitTopMethod(BytecodeLineFix sb, Literal l, M m, boolean abstractOnly){
    if (!m.sig().origin().equals(l.name())){ return ; }
    String iface=ifaceNameFor(l);
    var jName= mangledMethodName(m.sig().rc(), m.sig().m());
    if (abstractOnly || m.sig().abs()){
      sb.a("  default Object ").a(jName).a(paramsSig(m)).a("{\n")
        .a("    throw new AssertionError(\"Uncallable method: ")
        .a(iface).a(".").a(jName).a("\"+this.getClass().getName());\n")
        .a("  }\n");
      return;
    }
    tools.checks().checkTopMethod(m, decTypeName(l.name()), jName, hasInstance(l, abstractOnly));
    sb.a("  default Object "+jName+paramsSig(m)+"{\n");
    new ProduceBody(sb,this, iface, l.thisName(), m).emitBody();
  }
  String ifaceNameFor(Literal l){
    if (!l.infName() || l.onlyImmCapture().inner){ return decTypeName(l.name()); }
    if (l.cs().isEmpty()){ return "Object"; }
    return typeName(l.cs().getFirst().name());
  }
  String encodeTrailingPrimes(String s){
    int k= 0;
    while (k < s.length() && s.charAt(s.length() - 1 - k) == '\''){ k++; }
    if (k == 0){ return s; }
    var head= s.substring(0, s.length() - k);
    assert head.indexOf('\'')==-1: "prime (') must be trailing only: "+s;
    return head + "$p" + k;
  }  
  String decTypeName(TName n){ return encodeTrailingPrimes(n.simpleName())+"$"+caseTag(n.simpleName())+"$"+n.arity(); }
  String typeName(TName n){ return "_"+encodeTrailingPrimes(n.s())+"$"+caseTag(n.simpleName())+"$"+n.arity(); }
  static String caseTag(String s){
    var bits= new StringBuilder("1");
    for (int i : Range.of(0,s.length())){
      char c= s.charAt(i);
      if ('A' <= c && c <= 'Z'){ bits.append('1'); }
      if ('a' <= c && c <= 'z'){ bits.append('0'); }
    }
    return new BigInteger(bits.toString(),2).toString(36);
  }
  String mangledMethodName(RC rc, MName m){ return rc.name()+"$"+methodBaseName(m)+"$"+m.arity(); }
  String methodBaseName(MName m){
    var s= m.s();
    if (s.startsWith(".")){ return encodeTrailingPrimes(s.substring(1)); }
    return "$" + mangleOp(s);
  }
  final Map<String,String> mains= new TreeMap<>();
  void writeMainJava(){
    var all= Join.of(mains.keySet().stream().map(n->"\""+n+"\""),"{",",","}","{}");
    var assets= Join.of(tools.capabilities().autoloadedAssets().stream().map(Backend::assetLiteral),"{",",","}","{}");
    var sb= new StringBuilder(8_000)
      .append("package _").append(tools.pkgName()).append(";\n\n")
      .append("public final class Main{\n")
      .append("  static{ _base.Util.installParentLifeline(); }\n")
      .append("  static final String[] all= ").append(all).append(";\n")
      .append("  public static final String[][] autoloadedAssets= ").append(assets).append(";\n")
      .append("  public static void main(String[] args){ for (String n: args.length == 0 ? all : args){ run(n); } }\n")
      .append("  static void run(String n){\n");
    mains.forEach((n,iface)->sb
      .append("    if (n.equals(\"").append(n).append("\")){ _base.Util.topLevel(()->")
      .append(iface).append(".instance.imm$main$1(new ").append(typeName(new TName("base._System",0,Pos.unknown))).append("())); return; }\n"));
    sb.append("    throw new AssertionError(\"No main called \"+n+\" in package ").append(tools.pkgName()).append("\");\n  }\n}\n");
    Fs.writeUtf8(out.resolve("Main.java"), sb.toString());
  }
  static String assetLiteral(realSourceOracle.SourceOracleWithAutoload.Triple t){
    return "{"+javaStrLit(t.diskPath())+","+javaStrLit(t.zipSteps())+","+javaStrLit(t.zipEntry())+"}";
  }
  static String javaStrLit(String s){
    assert s.indexOf('"') < 0 && s.indexOf('\\') < 0 && s.indexOf('\n') < 0 && s.indexOf('\r') < 0;
    return "\""+s+"\"";
  }
  String mangleOp(String op){ return op.chars().mapToObj(c->opTok((char)c)).collect(Collectors.joining("_")); }
  static String opTok(char c){ return switch(c){
    case '+' -> "plus";
    case '-' -> "dash";
    case '*' -> "star";
    case '/' -> "slash";
    case '%' -> "pct";
    case '<' -> "lt";
    case '>' -> "gt";
    case '=' -> "eq";
    case '!' -> "bang";
    case '&' -> "and";
    case '|' -> "or";
    case '^' -> "xor";
    case '~' -> "tilde";
    case '?' -> "q";
    case '#' -> "hash";
    case '\\' -> "bslash";
    default -> throw utils.Bug.unreachable();
  };}
}