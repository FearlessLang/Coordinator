package docBuilder;

import static offensiveUtils.Require.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
      .append(".method{margin:.7em 0 0 1.5em;padding:.5em;border-left:3px solid #ddd}\n")
      .append(".sig{font-family:monospace;white-space:pre-wrap;font-size:1.08em;font-weight:600}\n")
      .append(".variant{font-family:monospace;white-space:pre-wrap}\n")
      .append(".doc{margin:.4em 0;color:#333;font-size:.94em}\n")
      .append(".missing{color:#777;font-style:italic}\n")
      .append(".imported,.variants{color:#555}\n")
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
          .map(c->typeLink(c.name()))
          .collect(Collectors.joining(", ")))
        .append("</p>\n");
    }
    renderVariants(sb,"Typed literal variants",t.variants);
    sb.append("<h3>Methods</h3>\n");
    visibleMethods(t).forEach(m->renderMethod(sb,m,claims));
    sb.append("</section>\n");
  }

  void renderMethod(StringBuilder sb, MethodDoc m, Map<DocOcc,Object> claims){
    sb.append("<div class=\"method\" id=\"").append(methodId(m.owner,m.main())).append("\">\n")
      .append("<div class=\"sig\">").append(h(sig(m.main().sig()))).append("</div>\n");
    if (m.declared){
      renderDoc(sb,m,m.docs,claims);
      renderInheritedLinks(sb,m);
    }
    else{ renderImported(sb,m); }
    renderVariants(sb,"Typed method variants",m.variants);
    sb.append("</div>\n");
  }

  void renderImported(StringBuilder sb, MethodDoc m){
    sb.append("<p class=\"doc imported\">Inherited from <a href=\"")
      .append(h(linkTo(m.main().sig().origin(),m.main()))).append("\">")
      .append(h(typeName(m.main().sig().origin())))
      .append(h(m.main().sig().m().toString()))
      .append("</a>.</p>\n");
  }

  void renderInheritedLinks(StringBuilder sb, MethodDoc m){
    if (m.inheritedFrom.isEmpty()){ return; }
    sb.append("<p class=\"doc imported\">Overrides ");
    for (int i= 0; i < m.inheritedFrom.size(); i += 1){
      if (i > 0){ sb.append(", "); }
      var r= m.inheritedFrom.get(i);
      sb.append("<a href=\"")
        .append(h(linkTo(r.owner(),r.method()))).append("\">")
        .append(h(typeName(r.owner())))
        .append(h(r.method().sig().m().toString()))
        .append("</a>");
    }
    sb.append(".</p>\n");
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
    if (!t.main().infName()){ return typeName(t.main().name()); }
    return "anonymous literal at "+t.main().pos();
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

  String typeLink(TName n){
    return "<a href=\""+h(linkTo(n))+"\">"+h(typeName(n))+"</a>";
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