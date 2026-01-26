package apiJson;

import java.util.List;
import java.util.stream.Stream;

import core.B;
import core.E.Literal;
import core.M;
import core.Sig;
import core.T;
import utils.Join;

public final class ApiJson{//We need all the names, because they can appear as meth signatures or subtypes and type system need to reason on them, even if can not be used by name outside pkg
  public static String toJSon(List<Literal> core){ return Join.of(core.stream()/*.filter(l->l.name().isPublic())*/.map(ApiJson::typeJ), "[", ",", "]", "[]"); }
  static String typeJ(Literal l){ return Join.of(List.of(q(l.name().s()), q(l.rc().name()), bsJ(l.bs()), csJ(l.cs()), msJ(l.ms())), "[", ",", "]"); }
  static String bsJ(List<B> bs){ return Join.of(bs.stream().map(ApiJson::bJ), "[", ",", "]", "[]"); }
  static String bJ(B b){ return Join.of(Stream.concat(Stream.of(q(b.x())), b.rcs().stream().map(rc->q(rc.name()))), "[", ",", "]", "[]"); }
  static String csJ(List<T.C> cs){ return Join.of(cs.stream().map(ApiJson::cJ), "[", ",", "]", "[]"); }
  static String cJ(T.C c){ return Join.of(Stream.concat(Stream.of(q(c.name().s())), c.ts().stream().map(ApiJson::tJ)), "[", ",", "]", "[]"); }
  static String msJ(List<M> ms){ return Join.of(ms.stream().map(m->mJ(m.sig())), "[", ",", "]", "[]"); }
  static String mJ(Sig s){ return Join.of(Stream.of(
    q(s.m().s()), q(s.rc().name()), bsJ(s.bs()), tsJ(s.ts()), tJ(s.ret()),
    q(s.origin().s()), q(""+s.origin().arity()), q(s.abs() ?"abs":"concrete")), "[", ",", "]", "[]"); }
  static String tsJ(List<T> ts){ return Join.of(ts.stream().map(ApiJson::tJ), "[", ",", "]", "[]"); }
  static String tJ(T t){
    var es= switch (t){
      case T.X(var n,_) -> Stream.of(q("x"), q(n));
      case T.ReadImmX(var x) -> Stream.of(q("x"), q("read/imm"), q(x.name()));
      case T.RCX(var rc, var x) -> Stream.of(q("x"), q(rc.name()), q(x.name()));
      case T.RCC(var rc, var c,_) -> Stream.concat(
        Stream.of(q("c"), q(rc.name()), q(c.name().s())),
        c.ts().stream().map(ApiJson::tJ));
    };
    return Join.of(es, "[", ",", "]");
  }
  static String q(String s){ assert s.indexOf('"') == -1; return "\""+s+"\""; }
}