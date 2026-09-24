package apiJson;

import java.util.List;
import java.util.stream.Stream;

import core.AllLs;
import core.B;
import core.E.Literal;
import core.Sig;
import core.T;
import utils.Join;

public final class ApiJson{
  //Note: we do not filter _names, because they can still be needed since they can appear as meth signatures or subtypes and type system need to reason on them, even if can not be used by name outside pkg
  public static String toJSon(List<Literal> core){ return arr(AllLs.of(core).values().stream().map(ApiJson::typeJ)); }
  static String arr(Stream<String> es){ return Join.of(es, "[", ",", "]", "[]"); }
  static String typeJ(Literal l){ return arr(Stream.of(q(l.name().s()), q(l.rc().name()), bsJ(l.bs()), arr(l.cs().stream().map(ApiJson::cJ)), arr(l.ms().stream().map(m->mJ(m.sig()))), q(l.thisName()))); }
  static String bsJ(List<B> bs){ return arr(bs.stream().map(ApiJson::bJ)); }
  static String bJ(B b){ return arr(Stream.concat(Stream.of(q(b.x())), b.rcs().stream().map(rc->q(rc.name())))); }
  static String cJ(T.C c){ return arr(Stream.concat(Stream.of(q(c.name().s())), c.ts().stream().map(ApiJson::tJ))); }
  static String mJ(Sig s){ return arr(Stream.of(
    q(s.m().s()), q(s.rc().name()), bsJ(s.bs()), arr(s.ts().stream().map(ApiJson::tJ)), tJ(s.ret()),
    q(s.origin().s()), q(""+s.origin().arity()), q(s.abs() ?"abs":"concrete"))); }
  static String tJ(T t){
    var es= switch (t){
      case T.X(var n,_) -> Stream.of(q("x"), q(n));
      case T.ReadImmX(var x) -> Stream.of(q("x"), q("read/imm"), q(x.name()));
      case T.RCX(var rc, var x) -> Stream.of(q("x"), q(rc.name()), q(x.name()));
      case T.RCC(var rc, var c,_) -> Stream.concat(
        Stream.of(q("c"), q(rc.name()), q(c.name().s())),
        c.ts().stream().map(ApiJson::tJ));
    };
    return arr(es);
  }
  static String q(String s){ assert s.indexOf('"') == -1; return "\""+s+"\""; }
}
