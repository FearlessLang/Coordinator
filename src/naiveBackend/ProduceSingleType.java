package naiveBackend;

import core.*;
import core.E.*;
import utils.Streams;

record ProduceBody(BytecodeLineFix sb, Backend b, String iface, String thisName, M m){
  public void emitBody(){
    if (!"_".equals(thisName)){ sb.a("    var ").a(thisName).a("$= this;\n"); }
    Streams.zipI(m.xs(), m.sig().ts())
      .filter((_,x,_)->!"_".equals(x))
      .forEach((i,x,t)->sb
        .a("    var ").a(b.encodeTrailingPrimes(x))
        .a("$= ").a(optCast(t)).a("p"+i+";\n"));
    sb.a("    return ");
    emitE(m.e().get());
    sb.a(";\n  }\n");
  }
  private String optCast(core.T t){ return switch (t){
    case core.T.RCC rcc -> "("+b.typeName(rcc.c().name())+")";
    default -> "";
  };}
  void emitE(core.E e){ switch(e){
    case X x -> emitX(x);
    case Type t -> emitType(t);
    case Call c -> emitCall(c);
    case Literal lit -> emitLit(lit);
  };}
  private void emitX(X x){
    assert !"_".equals(x.name());
    sb.a(b.encodeTrailingPrimes(x.name())+"$");
  }
  private void emitType(Type t){
    var n= t.type().c().name();
    var lit= n.pkgName().equals("base") && LiteralDeclarations.isPrimitiveLiteral(n.simpleName());
    if (!lit){ sb.a(b.typeName(n)+".instance"); return; }
    var sl= b.typeName(LiteralDeclarations.superLiteral(n));
    sb.a(sl+"Instance.instance("+LiteralDeclarations.toJavaLiteral(n.simpleName())+")");
  }
  private void emitCall(Call c){
    String cast= castedReceiverExpr(c.e());
    if (!cast.isEmpty()){ sb.a("("+cast); }
    emitE(c.e());
    if (!cast.isEmpty()){ sb.a(")"); }
    sb.a(".").a(b.mangledMethodName(c.rc(), c.name()),c.pos());//Is this the right point? the line with the method name?
    //What exactly counts as 'the line of the method call' in Java? we can have a method call spanning many lines!
    sb.a("(\n");
    for (int i= 0; i < c.es().size(); i++){
      emitE(c.es().get(i));
      if (i != c.es().size() - 1){ sb.a(",\n"); }
    }
    sb.a(")");//CHECK THIS: I think this is enough to make sure that there is never two fearless method calls on the same java line (possibly even overkill?)
  }
  private String castedReceiverExpr(core.E recv){ return switch(recv){
    case Call c -> optCast(c.expectedRes().inner);
    case X _ -> "";
    case Literal _ -> "";
    case Type _ -> "";
  };}
  private void emitLit(Literal lit){
    if(!lit.infName()){ b.generateInterface(lit, true); }
    var base= b.ifaceNameFor(lit);
      sb.a("new ").a(base).a("(){");
    for (var m:lit.ms()){
      if (!m.sig().origin().equals(lit.name())){ continue; }
      assert m.e().isPresent();
      var jName= b.mangledMethodName(m.sig().rc(), m.sig().m());
      sb.a("\n    public Object "+jName+b.paramsSig(m)+"{\n");
      new ProduceBody(sb,b, base, lit.thisName(), m).emitBody();
    }
    sb.a("\n}");
  }
}