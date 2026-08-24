package docBuilder;

import static offensiveUtils.Require.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import core.*;
import core.E.*;
import utils.Pos;

final class HtmlDocRenderer{
  HtmlDocRenderer(String pkgName, Map<String,String> uses, List<TypeDoc> types, OtherPackages other,
      Map<DocOcc,List<ResolvedSpan>> spans){
    assert nonNull(pkgName,uses,types,other,spans);
    this.pkgName= pkgName;
    this.uses= uses;
    this.types= types;
    this.spans= spans;
    this.toStr= new ExportedToStr(pkgName, uses);
    this.resolver= new DocResolver(pkgName, types, other);
  }

  final String pkgName;
  final Map<String,String> uses;
  final List<TypeDoc> types;
  final Map<DocOcc,List<ResolvedSpan>> spans;
  //filled by href() as links are emitted, so every ambiguous link that reaches the
  //page also gets its landing section; rendered after all types, when it is complete.
  final Map<String,DocLink.Ambiguous> pages= new LinkedHashMap<>();
  final ExportedToStr toStr;
  final DocResolver resolver;

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
      .append(".type{margin:2em 0;padding:1em;border:1px solid #ccc;border-radius:.5em;display:none}\n")
      .append(".method{margin:.25em 0 0 1.5em;padding:.15em .4em;border-left:3px solid #ddd}\n")
      //only the method's own summary: a descendant selector would also strip the
      //marker off the nested example/variant summaries inside it.
      .append(".method>summary{list-style:none;cursor:text}\n")
      .append(".method>summary::-webkit-details-marker{display:none}\n")
      .append(".examples>summary{list-style:none;cursor:pointer}\n")
      .append(".examples>summary::-webkit-details-marker{display:none}\n")
      .append(".disclosure{display:inline-block;cursor:pointer;margin-right:.35em;transition:transform .1s}\n")
      .append("details[open]>summary .disclosure{transform:rotate(90deg)}\n")
      .append(".sig{font-family:monospace;white-space:pre-wrap;font-size:1.08em;font-weight:600}\n")
      .append(".variant{font-family:monospace;white-space:pre-wrap}\n")
      .append(".example{font-family:monospace;white-space:pre-wrap;background:#f2f2f2;padding:.5em .7em;border-radius:.25em;border:1px solid #e0e0e0;overflow-x:auto}\n")
      .append(".doc{margin:.35em 0 .35em 1.3em;color:#333;font-size:.94em}\n")
      .append(".doc p{white-space:pre-wrap;margin:.5em 0}\n")
      .append(".missing{color:#777;font-style:italic}\n")
      .append(".from,.variants{color:#555}\n")
      .append("details{margin:.5em 0}\n")
      .append("code{background:#f2f2f2;padding:.05em .3em;border-radius:.25em}\n")
      .append("code a{color:#0645ad}\n")
      .append(".type.active{display:block}\n")
      .append("</style>\n</head>\n<body>\n<div class=\"layout\">\n");
    renderToc(sb,shown);
    sb.append("<main>\n<h1>Package ").append(h(pkgName)).append("</h1>\n")
      .append("<p id=\"welcome\">Pick a type from the list on the left.</p>\n");
    shown.forEach(t->renderType(sb,t,claims));
    pages.values().forEach(p->renderPage(sb,p));
    sb.append(router());
    return sb.append("</main>\n</div>\n</body>\n</html>\n").toString();
  }

  //a hash may name a section, or an id nested inside one: show the owning section,
  //then scroll to the precise target once it is no longer hidden.
  static String router(){ return ""
    + "<script>\n"
    + "(function(){\n"
    + "  var secs= document.querySelectorAll('.type');\n"
    + "  var welcome= document.getElementById('welcome');\n"
    + "  function route(){\n"
    + "    var hash= decodeURIComponent(location.hash.slice(1));\n"
    + "    var target= hash ? document.getElementById(hash) : null;\n"
    + "    var active= target && (target.classList.contains('type') ? target : target.closest('.type'));\n"
    + "    secs.forEach(function(el){ el.classList.toggle('active', el===active); });\n"
    + "    welcome.style.display= active ? 'none' : '';\n"
    + "    if (active && target!==active){ target.scrollIntoView(); }\n"
    + "    else { window.scrollTo(0,0); }\n"
    + "  }\n"
    + "  window.addEventListener('hashchange', route);\n"
    + "  route();\n"
    + "  document.querySelectorAll('.method>summary').forEach(function(s){\n"
    + "    s.addEventListener('click', function(e){\n"
    + "      if (!e.target.closest('.disclosure') && !e.target.closest('a')){ e.preventDefault(); }\n"
    + "    });\n"
    + "  });\n"
    + "})();\n"
    + "</script>\n";
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
    return s.m().s()+"/"+s.m().arity()+"/"+s.rc()+"/"+toStr.sig(s);
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
      .append("<summary><span class=\"disclosure\">&#9656;</span><span class=\"sig\">")
      .append(renderSig(m)).append("</span></summary>\n");
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
    return r.provider().map(toStr::typeName).orElseGet(()->toStr.typeNameWithArity(r.owner()));
  }

  //only the types a signature mentions become links: its own selector would just point
  //back at itself. A name that fails to resolve, or that names one of this method's own
  //bound parameters (eg. "R" in ".toOpt[R:*]"), stays plain text and is never an error.
  String renderSig(MethodDoc m){
    var text= toStr.sig(m.main().sig());
    var bound= boundNames(m.owner.main().bs(), m.main().sig().bs());
    var sb= new StringBuilder();
    int i= 0;
    for (var f: DocRefScanner.signatureTypes(text)){
      var t= (DocRef.TypeName)f.ref();
      if (bound.contains(t.simpleName())){ continue; }
      var link= resolver.resolveType(t);
      if (link.isEmpty()){ continue; }
      sb.append(h(text.substring(i,f.start())));
      sb.append("<a href=\"").append(h(href(link.get()))).append("\">")
        .append(h(text.substring(f.start(),f.end()))).append("</a>");
      i= f.end();
    }
    sb.append(h(text.substring(i)));
    return sb.toString();
  }

  static Set<String> boundNames(List<B> a, List<B> b){
    return Stream.concat(a.stream(),b.stream()).map(B::x).collect(Collectors.toSet());
  }

  void renderDoc(StringBuilder sb, Object owner, List<DocOcc> docs, Map<DocOcc,Object> claims){
    var visible= docs.stream()
      .filter(c->!c.inline() || claims.get(c) == owner)
      .toList();
    if (visible.isEmpty()){
      sb.append("<p class=\"doc missing\">No documentation yet.</p>\n");
      return;
    }
    var prose= visible.stream().filter(c->!c.example()).toList();
    if (!prose.isEmpty()){
      sb.append("<div class=\"doc\">\n");
      renderProse(sb,prose);
      sb.append("</div>\n");
    }
    renderExamples(sb,visible.stream().filter(DocOcc::example).map(DocOcc::text).toList());
  }

  void renderExamples(StringBuilder sb, List<String> examples){
    if (examples.isEmpty()){ return; }
    sb.append("<details class=\"examples\"><summary><span class=\"disclosure\">&#9656;</span>runnable example</summary>\n<pre class=\"example\">")
      .append(examples.stream().map(HtmlDocRenderer::h).collect(Collectors.joining("\n")))
      .append("</pre></details>\n");
  }

  //an empty doc line is where the author put a paragraph break; the lines between two
  //of them are one paragraph, kept on their own lines so that a list stays a list and
  //an indented line stays indented (the doc block is rendered with pre-wrap).
  void renderProse(StringBuilder sb, List<DocOcc> prose){
    var para= new java.util.ArrayList<DocOcc>();
    for (var occ: prose){
      if (occ.text().isBlank()){ endParagraph(sb,para); continue; }
      para.add(occ);
    }
    endParagraph(sb,para);
  }

  void endParagraph(StringBuilder sb, List<DocOcc> para){
    if (para.isEmpty()){ return; }
    sb.append("<p>")
      .append(para.stream().map(this::renderText).collect(Collectors.joining("\n")))
      .append("</p>\n");
    para.clear();
  }

  String renderText(DocOcc occ){
    var text= occ.text();
    var codeSpans= DocRefScanner.codeSpans(text);
    if (codeSpans.isEmpty()){ return h(text); }
    var linked= spans.getOrDefault(occ,List.of());
    var sb= new StringBuilder();
    int i= 0;
    for (var cs: codeSpans){
      sb.append(h(text.substring(i,cs.start()-cs.fence())));
      sb.append("<code>").append(renderCode(text,cs,linked)).append("</code>");
      i= cs.end()+cs.fence();
    }
    sb.append(h(text.substring(i)));
    return sb.toString();
  }

  String renderCode(String text, DocRefScanner.CodeSpan cs, List<ResolvedSpan> linked){
    var body= h(text.substring(cs.start(),cs.end()));
    return linked.stream()
      .filter(sp->sp.start()==cs.start() && sp.end()==cs.end())
      .filter(sp->!(sp.link() instanceof DocLink.NoLink))
      .findFirst()
      .map(sp->"<a href=\""+h(href(sp.link()))+"\">"+body+"</a>")
      .orElse(body);
  }

  void renderPage(StringBuilder sb, DocLink.Ambiguous p){
    sb.append("<section class=\"type\" id=\"").append(disambigId(p.pageId())).append("\">\n")
      .append("<h2>Several match ").append(h(p.title())).append("</h2>\n<ul>\n");
    p.options().forEach(c->sb.append("<li>").append(renderCandidate(c)).append("</li>\n"));
    sb.append("</ul>\n</section>\n");
  }

  String renderCandidate(Candidate c){
    var sb= new StringBuilder("<a href=\"").append(h(candidateHref(c))).append("\">")
      .append(h(toStr.typeNameWithArity(c.owner())));
    if (c.selector().isPresent()){
      sb.append(h(c.selector().get()));
      c.arity().ifPresent(n->sb.append(h(arityParens(n))));
    }
    sb.append("</a>");
    c.localMethod().ifPresent(m->m.docs.stream().filter(d->!d.example()).findFirst()
      .ifPresent(d->sb.append(" — ").append(h(d.text()))));
    return sb.toString();
  }

  String href(DocLink link){
    if (link instanceof DocLink.Ambiguous a){
      pages.putIfAbsent(a.pageId(),a);
      return "#"+disambigId(a.pageId());
    }
    return candidateHref(((DocLink.Resolved)link).hit());
  }

  String candidateHref(Candidate c){
    if (c.localMethod().isPresent()){ return "#"+methodId(c.owner(),c.localMethod().get().main()); }
    return linkTo(c.owner());
  }

  static String disambigId(String pageId){ return "disambig-"+id(pageId); }

  static String arityParens(int n){
    return IntStream.range(0,n).mapToObj(_ -> "_").collect(Collectors.joining(",","(",")"));
  }

  void renderVariants(StringBuilder sb, String title, List<?> variants){
    if (variants.size() <= 1){ return; }
    sb.append("<details class=\"variants\"><summary>")
      .append(h(title)).append(": ").append(variants.size()).append("</summary>\n");
    variants.forEach(v->sb.append("<pre class=\"variant\">").append(h(variant(v))).append("</pre>\n"));
    sb.append("</details>\n");
  }

  String variant(Object v){
    if (v instanceof Literal l){ return toStr.lit(l); }
    if (v instanceof M m){ return toStr.sig(m.sig()); }
    return String.valueOf(v);
  }

  String typeTitle(TypeDoc t){
    if (!t.main().infName()){ return typeDeclName(t.main()); }
    return "anonymous literal at "+t.main().pos();
  }
  String typeDeclName(Literal l){
    if (l.bs().isEmpty()){ return toStr.typeNameWithArity(l.name()); }
    return toStr.typeName(l.name())+l.bs().stream()
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
    return "<a href=\""+h(linkTo(c.name()))+"\">"+h(toStr.typeName(c))+"</a>";
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