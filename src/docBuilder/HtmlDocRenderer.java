package docBuilder;

import static offensiveUtils.Require.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import core.*;
import core.E.*;
import message.CompactPrinter;
import message.TypeNamePrinter;
import utils.Pos;

final class HtmlDocRenderer{
  HtmlDocRenderer(String pkgName, Map<String,String> uses, List<TypeDoc> types){
    assert nonNull(pkgName,uses,types);
    this.pkgName= pkgName;
    this.uses= uses;
    this.types= types;
  }

  final String pkgName;
  final Map<String,String> uses;
  final List<TypeDoc> types;

  CompactPrinter printer(){ return new CompactPrinter(pkgName,uses,false); }
  TypeNamePrinter names(){ return new TypeNamePrinter(false,pkgName,uses); }

  String expr(E e){ return printer().limit(e,220); }
  String sig(Sig s){ return printer().sig(s).stripLeading(); }
  String lit(Literal l){ return expr(l); }
  String typeName(TName n){ return names().ofFull(n); }

  String typeNameWithArity(TName n){
    var base= typeName(n);
    if (n.arity() == 0){ return base; }
    return base+"["+IntStream.range(0,n.arity()).mapToObj(_ -> "_").collect(Collectors.joining(","))+"]";
  }
  
  String typeName(T.C c){
    if (c.ts().isEmpty()){ return typeNameWithArity(c.name()); }
    return typeName(c.name())+"["+c.ts().stream().map(this::type).collect(Collectors.joining(","))+"]";
  }

  String type(T t){ return switch(t){
    case T.X x -> x.name();
    case T.RCX x -> x.rc()+" "+x.x().name();
    case T.ReadImmX x -> "read/imm "+x.x().name();
    case T.RCC r -> r.rc().toStrSpace()+typeName(r.c());
  };}

  String render(){
    var shown= visibleTypes();
    var claims= inlineClaims(shown);
    var sb= new StringBuilder(16_000)
      .append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n")
      .append("<title>").append(h(pkgName)).append("</title>\n")
      .append("<style>\n")
      .append("body{font-family:sans-serif;margin:0;line-height:1.35}\n")
      .append(".layout{display:flex;min-height:100vh}\n")
      .append(".toc{width:18em;max-height:100vh;overflow:auto;position:sticky;top:0;padding:1.2em;border-right:1px solid #ccc;box-sizing:border-box}\n")
      .append(".toc h2{margin-top:0}\n")
      .append(".toc ul{list-style:none;padding-left:0}\n")
      .append(".toc li{margin:.35em 0}\n")
      .append(".toc a{text-decoration:none;color:#0645ad}\n")
      .append("main{padding:2em;max-width:80em;box-sizing:border-box}\n")
      .append("h1{border-bottom:1px solid #bbb;padding-bottom:.3em}\n")
      .append(".type{margin:2em 0;padding:1em;border:1px solid #ccc;border-radius:.5em}\n")
      .append(".method{margin:.25em 0 0 1.5em;padding:.15em .4em;border-left:3px solid #ddd}\n")
      .append(".method summary{cursor:pointer;list-style-position:outside}\n")
      .append(".method summary::-webkit-details-marker{margin-right:.35em}\n")
      .append(".sig{font-family:monospace;white-space:pre-wrap;font-size:1.08em;font-weight:600}\n")
      .append(".variant{font-family:monospace;white-space:pre-wrap}\n")
      .append(".doc{margin:.35em 0 .35em 1.3em;color:#333;font-size:.94em}\n")
      .append(".missing{color:#777;font-style:italic}\n")
      .append(".from,.variants{color:#555}\n")
      .append("details{margin:.5em 0}\n")
      .append("</style>\n</head>\n<body>\n<div class=\"layout\">\n");
    renderToc(sb,shown);
    sb.append("<main>\n<h1>Package ").append(h(pkgName)).append("</h1>\n");
    shown.forEach(t->renderType(sb,t,claims));
    return sb.append("</main>\n</div>\n</body>\n</html>\n").toString();
  }

  List<TypeDoc> visibleTypes(){
    return types.stream()
      .filter(TypeDoc::visible)
      .sorted(Comparator.comparing(this::typeSortKey))
      .toList();
  }

  String typeSortKey(TypeDoc t){
    return (t.main().infName() ? "1:" : "0:")+typeTitle(t);
  }

  List<MethodDoc> visibleMethods(TypeDoc t){
    return t.methods.stream()
      .filter(MethodDoc::visible)
      .sorted(Comparator.comparing(this::methodSortKey))
      .toList();
  }

  String methodSortKey(MethodDoc m){
    var s= m.main().sig();
    return s.m().s()+"/"+s.m().arity()+"/"+s.rc()+"/"+sig(s);
  }

  Map<DocOcc,Object> inlineClaims(List<TypeDoc> shown){
    var res= new HashMap<DocOcc,Object>();
    for (var t:shown){
      t.docs.stream().filter(DocOcc::inline).forEach(c->res.put(c,t));
      visibleMethods(t).forEach(m->
        m.docs.stream().filter(DocOcc::inline).forEach(c->res.put(c,m)));
    }
    return res;
  }

  void renderToc(StringBuilder sb, List<TypeDoc> shown){
    sb.append("<nav class=\"toc\">\n<h2>Types</h2>\n<ul>\n");
    shown.forEach(t->sb.append("<li><a href=\"#")
      .append(typeId(t)).append("\">")
      .append(h(shortTitle(t))).append("</a></li>\n"));
    sb.append("</ul>\n</nav>\n");
  }

  void renderType(StringBuilder sb, TypeDoc t, Map<DocOcc,Object> claims){
    sb.append("<section class=\"type\" id=\"").append(typeId(t)).append("\">\n")
      .append("<h2>").append(h(typeTitle(t))).append("</h2>\n");
    renderDoc(sb,t,t.docs,claims);
    if (!t.main().cs().isEmpty()){
      sb.append("<p><b>Extends:</b> ")
        .append(t.main().cs().stream()
          .map(c->typeLink(c))
          .collect(Collectors.joining(", ")))
        .append("</p>\n");
    }
    renderVariants(sb,"Typed literal variants",t.variants);
    sb.append("<h3>Methods</h3>\n");
    visibleMethods(t).forEach(m->renderMethod(sb,m,claims));
    sb.append("</section>\n");
  }
  void renderMethod(StringBuilder sb, MethodDoc m, Map<DocOcc,Object> claims){
    sb.append("<details class=\"method\" id=\"").append(methodId(m.owner,m.main())).append("\">\n")
      .append("<summary><span class=\"sig\">").append(h(sig(m.main().sig()))).append("</span></summary>\n");
    if (m.declared){ renderDoc(sb,m,m.docs,claims); }
    renderFrom(sb,m);
    renderVariants(sb,"Typed method variants",m.variants);
    sb.append("</details>\n");
  }
  void renderFrom(StringBuilder sb, MethodDoc m){
    var refs= fromRefs(m);
    if (refs.isEmpty()){ return; }
    sb.append("<p class=\"doc from\">From: ");
    for (int i= 0; i < refs.size(); i += 1){
      if (i > 0){ sb.append(", "); }
      var r= refs.get(i);
      sb.append("<a href=\"")
        .append(h(linkTo(r.owner(),r.method()))).append("\">")
        .append(h(refName(r)))
        .append(h(r.method().sig().m().toString()))
        .append("</a>");
    }
    sb.append(".</p>\n");
  }

  List<MethodRef> fromRefs(MethodDoc m){
    if (!m.inheritedFrom.isEmpty()){ return m.inheritedFrom; }
    if (m.declared){ return List.of(); }
    return List.of(MethodRef.origin(m.main()));
  }

  String refName(MethodRef r){
    return r.provider().map(this::typeName).orElseGet(()->typeNameWithArity(r.owner()));
  }

  void renderDoc(StringBuilder sb, Object owner, List<DocOcc> docs, Map<DocOcc,Object> claims){
    var visible= docs.stream()
      .filter(c->!c.inline() || claims.get(c) == owner)
      .map(DocOcc::text)
      .toList();
    if (visible.isEmpty()){
      sb.append("<p class=\"doc missing\">No documentation yet.</p>\n");
      return;
    }
    sb.append("<div class=\"doc\">\n");
    visible.forEach(s->sb.append("<p>").append(h(s)).append("</p>\n"));
    sb.append("</div>\n");
  }

  void renderVariants(StringBuilder sb, String title, List<?> variants){
    if (variants.size() <= 1){ return; }
    sb.append("<details class=\"variants\"><summary>")
      .append(h(title)).append(": ").append(variants.size()).append("</summary>\n");
    variants.forEach(v->sb.append("<pre class=\"variant\">").append(h(variant(v))).append("</pre>\n"));
    sb.append("</details>\n");
  }

  String variant(Object v){
    if (v instanceof Literal l){ return lit(l); }
    if (v instanceof M m){ return sig(m.sig()); }
    return String.valueOf(v);
  }

  String typeTitle(TypeDoc t){
    if (!t.main().infName()){ return typeDeclName(t.main()); }
    return "anonymous literal at "+t.main().pos();
  }
  String typeDeclName(Literal l){
    if (l.bs().isEmpty()){ return typeNameWithArity(l.name()); }
    return typeName(l.name())+l.bs().stream()
      .map(B::compactToString)
      .collect(Collectors.joining(",","[","]"));
  }
  String shortTitle(TypeDoc t){
    if (!t.main().infName()){ return typeTitle(t); }
    var p= t.main().pos();
    return "Anon@"+shortFile(p)+":"+p.line();
  }

  String shortFile(Pos p){
    var s= p.fileName().toString();
    var slash= s.lastIndexOf('/');
    if (slash >= 0){ s= s.substring(slash+1); }
    if (s.endsWith(".fear")){ s= s.substring(0,s.length()-5); }
    return s;
  }

  String typeLink(T.C c){
    return "<a href=\""+h(linkTo(c.name()))+"\">"+h(typeName(c))+"</a>";
  }

  String linkTo(TName n){
    return (n.pkgName().equals(pkgName) ? "" : n.pkgName()+".html")+"#"+typeId(n);
  }

  String linkTo(TName owner, M m){
    return (owner.pkgName().equals(pkgName) ? "" : owner.pkgName()+".html")+"#"+methodId(owner,m);
  }

  static String typeId(TypeDoc t){
    if (!t.main().infName()){ return typeId(t.main().name()); }
    return "literal-"+posId(t.main().pos());
  }

  static String typeId(TName n){
    return "type-"+id(n.s())+"-"+n.arity();
  }

  static String methodId(TypeDoc owner, M m){ return methodId(owner.main().name(),m); }

  static String methodId(TName owner, M m){
    return "method-"+id(owner.s())+"-"+id(m.sig().rc().name())+"-"+id(m.sig().m().s())+"-"+m.sig().m().arity();
  }

  static String posId(Pos p){
    return id(p.fileName().toString())+"-"+p.line()+"-"+p.column();
  }

  static String id(String s){
    var sb= new StringBuilder(s.length()*2);
    for (int i= 0; i < s.length(); i += 1){
      var c= s.charAt(i);
      if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'){ sb.append(c); }
      else{ sb.append('_').append(Integer.toHexString(c)).append('_'); }
    }
    return sb.toString();
  }

  static String h(String s){
    return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
  }
}