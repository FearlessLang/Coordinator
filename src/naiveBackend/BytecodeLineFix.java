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
import java.util.HashMap;
import java.util.function.Consumer;
import tools.Fs;
import utils.Pos;

final class BytecodeLineFix implements Consumer<Path>{
  StringBuilder sb= new StringBuilder(8_000);
  private final String base;
  private final String sourceFile;
  private final HashMap<Integer,Integer> lineMap= new HashMap<>(2048);
  private int javaLine= 1;
  BytecodeLineFix(String base,URI sourceFile){ this(base,sourceFile.toString().substring("fear:/".length())); }
  BytecodeLineFix(String base,String sourceFile){
    assert nonNull(base,sourceFile);
    this.base= base;
    this.sourceFile= sourceFile;
  }
  String emitString(){ return sb.toString(); }
  @Override public String toString(){ return sb.toString(); }
  BytecodeLineFix a(String s){ //this call == s does not contain any method call
    sb.append(s);
    advanceLines(s);
    return this;
  }
  BytecodeLineFix a(String s, Pos p){//this call == s contains exactly 1 method call located in pos
    int fl= p.line();
    assert fl >= 1 && fl <= 65535; // LineNumberTable uses u2
    put(javaLine, fl);
    return a(s);
  }
  private void advanceLines(String s){
    for (int i= 0; i < s.length(); i++){
      if (s.charAt(i) == '\n'){ javaLine++; }
    }
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
    if (!n.endsWith(".class")){ return false; }
    if (n.equals(base+".class")){ return true; }
    return n.startsWith(base+"$"); // anon/inner: Foo$1.class etc
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
    Integer fl= lineMap.getOrDefault(ln.line(),1000);
    cb.with(LineNumber.of(fl));
  }
}