package coordinator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import coordinatorMessages.CacheCorruptionError;
import core.B;
import core.E.Literal;
import core.M;
import core.MName;
import core.RC;
import core.Sig;
import core.Src;
import core.T;
import core.TName;
import core.TSpan;
import utils.Pos;

final class LimitedJsonParser{
  private static final Pattern nameP= Pattern.compile(TName.pkgNameRegex);
  private final String s;
  private final Matcher m;
  private int i= 0;
  Path forErr;
  LimitedJsonParser(String s, Path forErr){ this.s= s; this.forErr= forErr; this.m= nameP.matcher(s); }
  Map<String,Map<String,String>> obj2(){
    var out= obj(this::obj1); ws();
    if (i != s.length()){ throw err("Trailing junk"); }
    return out;
  }
  Map<String,String> obj1(){ return obj(this::name); }
  private <TT> Map<String,TT> obj(Supplier<TT> v){
    req('{'); if (eat('}')){ return Map.of(); }
    var out= new LinkedHashMap<String,TT>();
    for (;;req(',')){
      var k= name();
      req(':');
      if (out.put(k, v.get()) != null){ throw err("Duplicate key "+k); }
      if (eat('}')){ return out; }
    }
  }
  private String name(){
    ws(); req('"');
    m.region(i, s.length());
    if (!m.lookingAt()){ throw err("Expected name"); }
    i= m.end(); req('"');
    return m.group();
  }
  Map<TName,Literal> apiJsonToMap(){
    var xs= arr(); ws();
    if (i != s.length()){ throw err("Trailing junk"); }
    var out= new LinkedHashMap<TName,Literal>();
    for (var x: xs){
      var lit= typeLit(asArr(x));
      if (out.put(lit.name(), lit) != null){ throw err("Duplicate type "+lit.name().s()); }
    }
    return out;
  }
  private Literal typeLit(List<Object> a){
    if (a.size() != 5){ throw err("Bad type record size"); }
    var nameS= asStr(a.get(0));
    var rc= RC.valueOf(asStr(a.get(1)));
    var bs= asArr(a.get(2)).stream().map(x->bFrom(asArr(x))).toList();
    var tn= new TName(nameS, bs.size(), dummyPos());
    var cs= asArr(a.get(3)).stream().map(x->cFrom(asArr(x))).toList();
    var ms= asArr(a.get(4)).stream().map(x->mFrom(asArr(x))).toList();
    //We need also the non public for subtyping reasoning//if (!tn.isPublic()){ throw err("Non-public type in api json: "+nameS); }
    return new Literal(rc, tn, bs, cs, "this", ms, dummySrc(), false);
  }
  private M mFrom(List<Object> a){
    if (a.size() != 8){ throw err("Bad M"); }
    var nameS= asStr(a.get(0));
    var rc= RC.valueOf(asStr(a.get(1)));
    var bs= asArr(a.get(2)).stream().map(x->bFrom(asArr(x))).toList();
    var ts= asArr(a.get(3)).stream().map(x->tFrom(asArr(x))).toList();
    var ret= tFrom(asArr(a.get(4)));
    var originS= asStr(a.get(5));
    int originA= nat(a.get(6));
    var tag= asStr(a.get(7));
    boolean abs= tag.equals("abs");
    if (!tag.equals("abs") && !tag.equals("concrete")){ throw err("Bad abs/concrete tag"); }
    var sig= new Sig(rc, new MName(nameS, ts.size()), bs, ts, ret,
      new TName(originS, originA, dummyPos()), abs, dummySpan());
    var xs= IntStream.range(0, ts.size()).mapToObj(j->"x"+j).toList();
    return new M(sig, xs, Optional.empty());
  }
  private int nat(Object o){
    try{ return Integer.parseInt("+"+asStr(o)); }//to reject negatives
    catch(NumberFormatException nfe){ throw err("Expected unsigned int"); }
  }
  private B bFrom(List<Object> a){
    if (a.size() < 2){ throw err("Bad B"); }
    var x= asStr(a.get(0));
    var rcs= EnumSet.noneOf(RC.class);
    for (int j= 1; j < a.size(); j += 1){ rcs.add(RC.valueOf(asStr(a.get(j)))); }
    if (rcs.isEmpty()){ throw err("Empty B.rcs"); }
    return new B(x, rcs);
  }
  private T.C cFrom(List<Object> a){
    if (a.isEmpty()){ throw err("Bad C"); }
    var nameS= asStr(a.get(0));
    var ts= a.stream().skip(1).map(ai->tFrom(asArr(ai))).toList();
    return new T.C(new TName(nameS, ts.size(), dummyPos()), ts);
  }
  private T tFrom(List<Object> a){
    if (a.size() < 2){ throw err("Bad T"); }
    var k= asStr(a.get(0));
    var rc= asStr(a.get(1));
    if (k.equals("c")){
      if (a.size() < 3){ throw err("Bad c T"); }
      var n= asStr(a.get(2));
      var ts= a.stream().skip(3).map(x->tFrom(asArr(x))).toList();
      var c= new T.C(new TName(n, ts.size(), dummyPos()), ts);
      return new T.RCC(RC.valueOf(rc), c, dummySpan());
    }
    if (!k.equals("x")){ throw err("Bad T kind"); }
    if (a.size() == 2){ return new T.X(asStr(a.get(1)), dummySpan()); }
    if (a.size() != 3){ throw err("Bad x T"); }
    var n= asStr(a.get(2));
    if (rc.equals("read/imm")){ return new T.ReadImmX(new T.X(n, dummySpan())); }
    return new T.RCX(RC.valueOf(rc), new T.X(n, dummySpan()));
  }
  private List<Object> arr(){
    req('['); if (eat(']')){ return List.of(); }
    var out= new ArrayList<Object>();
    for (;;req(',')){ out.add(val()); if (eat(']')){ return out; } }
  }
  private Object val(){
    ws();
    if (i >= s.length()){ throw err("Unexpected end"); }
    char c= s.charAt(i);
    if (c == '"'){ return str(); }
    if (c == '['){ return arr(); }
    throw err("Expected string or array");
  }
  private String str(){
    ws(); req('"');
    int j= i;
    for (; i < s.length() && s.charAt(i) != '"'; i += 1){}
    if (i >= s.length()){ throw err("Unterminated string"); }
    var out= s.substring(j, i);
    i += 1;
    return out;
  }
  private String asStr(Object o){
    if (o instanceof String s){ return s; }
    throw err("Expected string");
  }
  @SuppressWarnings("unchecked")
  private List<Object> asArr(Object o){
    if (o instanceof List<?> xs){ return (List<Object>)xs; }
    throw err("Expected array");
  }
  private Pos dummyPos(){ return Pos.unknown; }
  private TSpan dummySpan(){ return TSpan.fromPos(dummyPos(), 1); }
  private Src dummySrc(){ return Src.syntetic; }

  void ws(){ for (; i < s.length() && (s.charAt(i)==' ' || s.charAt(i)=='\n'); i++); }
  private void req(char c){ if (!eat(c)){ throw err("Expected '"+c+"'"); } }
  private boolean eat(char c){
    ws();
    if (i >= s.length() || s.charAt(i) != c){ return false; }
    i++; return true;
  }
  private RuntimeException err(String msg){
    return CacheCorruptionError.startRepair_invalidVirtualizationMap(forErr,msg+" at "+i);
  }
}