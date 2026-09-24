package naiveBackend;

import static offensiveUtils.Require.*;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.instruction.LineNumber;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.function.Consumer;
import tools.Fs;
import utils.Pos;

final class BytecodeLineFix implements Consumer<Path>{
  StringBuilder sb= new StringBuilder(8_000);
  private final String base;
  private final String sourceFile;
  private final TreeMap<Integer,Integer> lineMap= new TreeMap<>();
  private int javaLine= 1;
  BytecodeLineFix(String base,URI sourceFile){ this(base,sourceFile.toString().substring("fear:/".length())); }
  BytecodeLineFix(String base,String sourceFile){
    assert nonNull(base,sourceFile);
    this.base= base;
    this.sourceFile= sourceFile;
  }
  @Override public String toString(){ return sb.toString(); }
  BytecodeLineFix a(String s){ //this call == s does not contain any method call
    sb.append(s);
    javaLine += (int)s.chars().filter(c->c == '\n').count();
    return this;
  }
  BytecodeLineFix a(String s, Pos p){//this call == s contains exactly 1 method call located in pos
    int fl= p.line();
    assert fl >= 1 && fl <= 65535; // LineNumberTable uses u2
    put(javaLine, fl);
    return a(s);
  }
  private void put(int jl, int fl){
    Integer prev= lineMap.putIfAbsent(jl, fl);
    assert prev == null || prev.intValue() == fl:
      "Two fearless lines on one java line "+jl+": "+prev+" vs "+fl
      + sb.toString();
  }
  @Override public void accept(Path classesDir){
    assert nonNull(classesDir);
    var cf= ClassFile.of();
    Fs.walkV(classesDir, s->s
      .filter(p->matches(p.getFileName().toString()))
      .forEach(p->patchOne(cf, p))
    );
  }
  private boolean matches(String n){
    return n.equals(base+".class") || n.endsWith(".class") && n.startsWith(base+"$"); // anon/inner: Foo$1.class etc
  }
  private void patchOne(ClassFile cf, Path classFile){
    byte[] in= Fs.of(()->Files.readAllBytes(classFile));
    var model= cf.parse(in);
    var xform= ClassTransform
      .dropping(e->e instanceof SourceFileAttribute)
      .andThen(ClassTransform.endHandler(b->b.with(SourceFileAttribute.of(sourceFile))))
      .andThen(ClassTransform.transformingMethodBodies(this::patchCode));
    byte[] out= cf.transformClass(model, xform);
    Fs.ofV(()->Files.write(classFile, out));
  }
  private void patchCode(CodeBuilder cb, CodeElement ce){
    if (!(ce instanceof LineNumber ln)){ cb.with(ce); return; }
    cb.with(LineNumber.of(fearlessLine(ln.line())));
  }
  //emitBody()'s this$/parameter-cast preamble lines never call a(String,Pos), so they are
  //never keys of lineMap; such a line can only fail at runtime as a backend bug (the
  //frontend already proved the cast sound), so there is no real fearless line to name.
  //The nearest later mapped line is always this same method's own call, so blaming it there
  //still names the right method; 1000 is a last resort for a file with no mapped line at all.
  private int fearlessLine(int jl){
    Integer exact= lineMap.get(jl);
    if (exact != null){ return exact; }
    var after= lineMap.ceilingEntry(jl);
    if (after != null){ return after.getValue(); }
    var before= lineMap.floorEntry(jl);
    return before != null ? before.getValue() : 1000;
  }
}