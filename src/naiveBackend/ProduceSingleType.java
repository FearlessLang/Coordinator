package naiveBackend;

import core.*;
import core.E.*;
import utils.Join;
import utils.Streams;

record ProduceBody(StringBuilder sb, Backend b, String iface, String thisName, M m){
  public void emitBody(){
    if (!"_".equals(thisName)){ sb.append("    var ").append(thisName).append("$= this;\n"); }
    Streams.zipI(m.xs(), m.sig().ts())
      .filter((_,x,_)->!"_".equals(x))
      .forEach((i,x,t)->sb
        .append("    var ").append(b.encodeTrailingPrimes(x))
        .append("$= ").append(optCast("p"+i,t)).append(";\n"));
    sb.append("    return "+emitE(m.e().get())+";\n  }\n");
  }
  private String optCast(String exp,core.T t){ return switch (t){
    case core.T.RCC rcc -> "("+b.typeName(rcc.c().name())+")"+exp;
    default -> exp;
  };}
  String emitE(core.E e){ return switch(e){
    case X x -> emitX(x);
    case Type t -> emitType(t);
    case Call c -> emitCall(c);
    case Literal lit -> emitLit(lit);
  };}
  private String emitX(X x){
    assert !"_".equals(x.name());
    return b.encodeTrailingPrimes(x.name())+"$";
  }
  private String emitType(Type t){
    var n= t.type().c().name();
    var lit= n.pkgName().equals("base") && LiteralDeclarations.isPrimitiveLiteral(n.simpleName());
    if (!lit){ return b.typeName(n)+".instance"; }
    var sl= b.typeName(LiteralDeclarations.superLiteral(n));
    return sl+"Instance.instance("+LiteralDeclarations.toJavaLiteral(n.simpleName())+")";
  }
  private String emitCall(Call c){
    var recv= c.e();
    var recvE= emitE(recv);
    var recvExpr= castedReceiverExpr(recv, recvE);
    var jName= b.mangledMethodName(c.rc(), c.name());
    var args= Join.of(c.es().stream().map(this::emitE), "(", ", ", ")", "()");
    return recvExpr+"."+jName+args;
  }
  private String castedReceiverExpr(core.E recv, String recvE){ return switch(recv){
    case Call c -> "("+optCast(recvE,c.expectedRes().inner)+")";
    case X _ -> recvE;
    case Literal _ -> recvE;
    case Type _ -> recvE;
  };}
  private String emitLit(Literal lit){
    if(!lit.infName()){ b.generateInterface(lit, true); }
    var base= b.ifaceNameFor(lit);
    var sb= new StringBuilder(8_000)
      .append("new ").append(base).append("(){");
    for (var m:lit.ms()){
      if (!m.sig().origin().equals(lit.name())){ continue; }
      assert m.e().isPresent();
      var jName= b.mangledMethodName(m.sig().rc(), m.sig().m());
      sb.append("\n    public Object "+jName+b.paramsSig(m)+"{\n");
      new ProduceBody(sb,b, base, lit.thisName(), m).emitBody();
    }
    return sb.append("\n}").toString();
  }
}