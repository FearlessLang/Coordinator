package naiveBackend;

import static offensiveUtils.Require.*;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import core.*;
import core.E.*;
import tools.Fs;
import utils.Join;
import utils.Pos;

public class Backend{
  public Backend(Path out, String pkgName, List<Literal> decs){
    assert nonNull(out,pkgName,decs);
    assert unmodifiable(decs, "decs");
    this.out= out;
    this.pkgName= pkgName;
    this.decs= decs;
  }
  Path out;
  String pkgName;
  List<Literal> decs;
  List<Consumer<Path>> fixers= new ArrayList<>();
  public List<Consumer<Path>> produceJavaCode(){
    cleanOutFolder();
    decs.forEach(d->generateInterface(d,false));
    writeMainJava();
    return fixers;
  }
  void cleanOutFolder(){
    Fs.ensureDir(out);
    Fs.cleanDirContents(out);
  }
  void generateInterface(Literal l, boolean abstractOnly){
    var iface= ifaceNameFor(l);
    var sb= new BytecodeLineFix(iface, l.pos().fileName())
      .a("package "+pkgName+";\n")
      .a("public interface "+iface+extendsClause(l)+"{\n");
    for (var m:l.ms()){ emitTopMethod(sb, l, m, abstractOnly); }
    var hasInstance= hasInstance(l, abstractOnly);
    if (hasInstance){ sb.a("  "+iface+" instance= new "+iface+"(){};"); }
    Fs.writeUtf8(ifaceFile(l, out), sb.a("}").toString());
    if (hasInstance && implementsBaseMain(l)){ mains.add(iface); }
    fixers.add(sb);
  }
  private boolean hasInstance(Literal l, boolean abstractOnly) {
    if (abstractOnly){ return false; } 
    assert !l.thisName().isEmpty();
    return l.ms().stream().noneMatch(m->m.sig().abs());
  }
  boolean implementsBaseMain(Literal l){ return l.cs().stream().anyMatch(c->c.name().equals(mainName)); }
  private static TName mainName=new TName("base.Main", 0,Pos.unknown);

  String extendsClause(Literal lit){ return Join.of(
    lit.cs().stream().map(c->typeName(c.name())).distinct(),
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
        .a(iface).a(".").a(jName).a("\");\n")
        .a("  }\n");
      return;
    }   
    sb.a("  default Object "+jName+paramsSig(m)+"{\n");
    new ProduceBody(sb,this, iface, l.thisName(), m).emitBody();
  }
  String ifaceNameFor(Literal l){
    if (!l.infName()){ return decTypeName(l.name()); }
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
  String decTypeName(TName n){ return encodeTrailingPrimes(n.simpleName())+"$"+n.arity(); }
  String typeName(TName n){ return encodeTrailingPrimes(n.s())+"$"+n.arity(); }
  Path ifaceFile(Literal l, Path dest){ return dest.resolve(ifaceNameFor(l)+".java"); }
  String mangledMethodName(RC rc, MName m){ return rc.name()+"$"+methodBaseName(m)+"$"+m.arity(); }
  String methodBaseName(MName m){
    var s= m.s();
    if (s.startsWith(".")){ return encodeTrailingPrimes(s.substring(1)); }
    return "$" + mangleOp(s);
  }
  final List<String> mains= new ArrayList<>();
  void writeMainJava(){
    var sb= new StringBuilder(8_000)
      .append("package ").append(pkgName).append(";\n\n")
      .append("public final class Main{\n  public static void main(String[] args){\n");
    mains.stream().sorted().forEach(n->
      sb.append("    base.Util.topLevel(()->").append(n).append(".instance.imm$main$1(new base._System$0()));\n")
    );
    Fs.writeUtf8(out.resolve("Main.java"), sb.append("  }\n}\n").toString());
  }
  String mangleOp(String op){
    var sb= new StringBuilder(op.length()*6);
    for (int i= 0; i < op.length(); i += 1){
      if (i>0){ sb.append('_'); }
      sb.append(opTok(op.charAt(i)));
    }
    return sb.toString();
  }
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