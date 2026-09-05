package docBuilder;

import static offensiveUtils.Require.*;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    this(pkgName,uses,types,other,spans,Optional.empty());
  }
  HtmlDocRenderer(String pkgName, Map<String,String> uses, List<TypeDoc> types, OtherPackages other,
      Map<DocOcc,List<ResolvedSpan>> spans, Optional<Path> baseDocLocation){
    assert nonNull(pkgName,uses,types,other,spans,baseDocLocation);
    this.pkgName= pkgName;
    this.uses= uses;
    this.types= types;
    this.spans= spans;
    this.baseDocLocation= baseDocLocation;
    this.toStr= new ExportedToStr(pkgName, uses);
    this.resolver= new DocResolver(pkgName, types, other);
  }

  final String pkgName;
  final Map<String,String> uses;
  final List<TypeDoc> types;
  final Map<DocOcc,List<ResolvedSpan>> spans;
  final Optional<Path> baseDocLocation;
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
      .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
      .append("<title>").append(h(pkgName)).append("</title>\n")
      .append("<style>\n").append(css).append("</style>\n")
      .append("</head>\n<body>\n<div class=\"layout\">\n");
    renderToc(sb,shown);
    sb.append("<main>\n");
    renderHeader(sb,shown);
    shown.forEach(t->renderType(sb,t,claims));
    pages.values().forEach(p->renderPage(sb,p));
    sb.append(router());
    return sb.append("</main>\n</div>\n</body>\n</html>\n").toString();
  }

  String renderText(){
    var shown= visibleTypes();
    var claims= inlineClaims(shown);
    var sb= new StringBuilder(4_000).append("package ").append(pkgName).append("\n\n");
    shown.forEach(t->renderTypeText(sb,t,claims));
    return sb.toString();
  }

  void renderTypeText(StringBuilder sb, TypeDoc t, Map<DocOcc,Object> claims){
    sb.append(typeTitle(t));
    if (!t.main().cs().isEmpty()){
      sb.append(" : ").append(t.main().cs().stream().map(toStr::typeName).collect(Collectors.joining(", ")));
    }
    sb.append('\n');
    renderDocText(sb,"  ",t,t.docs,claims);
    visibleMethods(t).forEach(m->renderMethodText(sb,m,claims));
    sb.append('\n');
  }

  void renderMethodText(StringBuilder sb, MethodDoc m, Map<DocOcc,Object> claims){
    sb.append("  ").append(toStr.sig(m.main().sig())).append('\n');
    if (m.declared){ renderDocText(sb,"    ",m,m.docs,claims); }
    var refs= fromRefs(m);
    if (refs.isEmpty()){ return; }
    sb.append("    from: ").append(refs.stream()
      .map(r->refName(r)+r.method().sig().m())
      .collect(Collectors.joining(", "))).append('\n');
  }

  void renderDocText(StringBuilder sb, String indent, Object owner, List<DocOcc> docs, Map<DocOcc,Object> claims){
    var visible= docs.stream().filter(c->!c.inline() || claims.get(c) == owner).toList();
    if (visible.isEmpty()){ return; }
    visible.stream().filter(c->!c.example()).forEach(c->appendIndented(sb,indent,c.text()));
    var examples= visible.stream().filter(DocOcc::example).map(DocOcc::text).toList();
    if (examples.isEmpty()){ return; }
    sb.append(indent).append("example:\n");
    examples.forEach(e->appendIndented(sb,indent+"  ",e));
  }

  static void appendIndented(StringBuilder sb, String indent, String line){
    if (!line.isBlank()){ sb.append(indent); }
    sb.append(line).append('\n');
  }

  void renderHeader(StringBuilder sb, List<TypeDoc> shown){
    sb.append("<header class=\"hdr\">").append(mark)
      .append("<div><p class=\"eyebrow\">package</p><h1>").append(h(pkgName)).append("</h1></div>")
      .append("</header>\n")
      .append("<p id=\"welcome\" class=\"welcome\">")
      .append(shown.size()).append(" types. Pick one from the list on the left.</p>\n");
  }

  //the Fearless mark: the blue to orange square of the icon, with its white F
  static final String mark= ""
    + "<svg class=\"mark\" viewBox=\"0 0 64 64\" width=\"38\" height=\"38\" aria-hidden=\"true\">"
    + "<defs><linearGradient id=\"fearless\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0.3\">"
    + "<stop offset=\"0\" stop-color=\"#2068a8\"/><stop offset=\"1\" stop-color=\"#e88028\"/>"
    + "</linearGradient></defs>"
    + "<rect x=\"1\" y=\"1\" width=\"62\" height=\"62\" rx=\"14\" fill=\"url(#fearless)\"/>"
    + "<rect x=\"18\" y=\"12\" width=\"10\" height=\"40\" rx=\"5\" fill=\"#fff\"/>"
    + "<rect x=\"18\" y=\"12\" width=\"30\" height=\"10\" rx=\"5\" fill=\"#fff\"/>"
    + "<rect x=\"18\" y=\"27\" width=\"24\" height=\"10\" rx=\"5\" fill=\"#fff\"/>"
    + "</svg>";

  static final String css= """
:root{
  --blue:#2068a8; --blue-deep:#1a5486; --orange:#c07020; --orange-bright:#e88028;
  --fg:#1f2328; --fg-muted:#57606a; --fg-faint:#818b98;
  --bg:#ffffff; --bg-soft:#f6f8fa; --bg-code:#f1f4f7;
  --border:#d6dde4; --border-soft:#e8ecf0;
}
@media (prefers-color-scheme: dark){
  :root{
    --blue:#69b0ee; --blue-deep:#8ec7f5; --orange:#e0954f; --orange-bright:#f0a860;
    --fg:#e6edf3; --fg-muted:#9fadba; --fg-faint:#7d8894;
    --bg:#0f1419; --bg-soft:#161c23; --bg-code:#1b222b;
    --border:#2d3742; --border-soft:#232b34;
  }
}
*{box-sizing:border-box}
body{
  margin:0; line-height:1.55; color:var(--fg); background:var(--bg);
  font-family:system-ui,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;
  font-size:15px;
}
a{color:var(--blue); text-decoration:none}
a:hover{text-decoration:underline}
.layout{display:flex; align-items:flex-start}
/* the list of types, its own scroll so the page never scrolls it away */
.toc{
  width:19em; flex:none; position:sticky; top:0; height:100vh; overflow:auto;
  padding:1.1em .9em; background:var(--bg-soft); border-right:1px solid var(--border);
}
.toc input{
  width:100%; padding:.45em .6em; margin-bottom:.9em; font:inherit; font-size:.92em;
  color:var(--fg); background:var(--bg); border:1px solid var(--border); border-radius:.4em;
}
.toc input:focus{outline:none; border-color:var(--blue)}
.toc ul{list-style:none; margin:0; padding:0}
.toc li{margin:1px 0}
.toc a{
  display:block; padding:.28em .55em; border-radius:.35em; border-left:3px solid transparent;
  color:var(--fg-muted); font-size:.93em; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;
}
.toc a:hover{background:var(--border-soft); color:var(--fg); text-decoration:none}
.toc a.sel{background:var(--bg); border-left-color:var(--orange); color:var(--blue); font-weight:600}
main{flex:1; min-width:0; padding:2.2em 2.6em 6em; max-width:64em}
.hdr{display:flex; align-items:center; gap:.85em; padding-bottom:1em; border-bottom:1px solid var(--border)}
.hdr h1{margin:0; font-size:1.65em; letter-spacing:-.01em}
.eyebrow{
  margin:0; font-size:.7em; font-weight:600; letter-spacing:.12em;
  text-transform:uppercase; color:var(--fg-faint);
}
.welcome{color:var(--fg-muted)}
/* one type at a time: the router shows the section the hash names */
.type{display:none; margin:1.6em 0}
.type.active{display:block}
.type>h2{
  margin:.2em 0 .1em; font-size:1.3em; color:var(--blue-deep);
  font-family:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
}
.type>h3{
  margin:1.8em 0 .4em; font-size:.78em; font-weight:600; letter-spacing:.1em;
  text-transform:uppercase; color:var(--fg-faint); border-bottom:1px solid var(--border-soft);
  padding-bottom:.35em;
}
.extends{margin:.5em 0 0; font-size:.92em; color:var(--fg-muted)}
.method{margin:0; padding:.45em .2em; border-top:1px solid var(--border-soft)}
.method:first-of-type{border-top:none}
.method:target{background:var(--bg-soft); border-radius:.4em}
.method>summary{list-style:none; cursor:text}
.method>summary::-webkit-details-marker{display:none}
.examples>summary{list-style:none; cursor:pointer; color:var(--orange); font-size:.86em; font-weight:600}
.examples>summary::-webkit-details-marker{display:none}
.variants>summary{cursor:pointer}
.disclosure{
  display:inline-block; width:1em; cursor:pointer; color:var(--fg-faint);
  transition:transform .12s ease; user-select:none;
}
.disclosure:hover{color:var(--blue)}
details[open]>summary .disclosure{transform:rotate(90deg)}
.sig{
  font-family:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
  white-space:pre-wrap; font-size:.95em; font-weight:600; color:var(--fg);
}
.doc{margin:.3em 0 .3em 1.6em; color:var(--fg-muted); font-size:.95em}
.doc p{white-space:pre-wrap; margin:.55em 0}
.missing{color:var(--fg-faint); font-style:italic}
.from,.variants{color:var(--fg-faint); font-size:.88em}
details{margin:.4em 0}
code{
  font-family:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
  font-size:.92em; background:var(--bg-code); border:1px solid var(--border-soft);
  padding:.05em .32em; border-radius:.3em;
}
.example,.variant{
  font-family:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
  white-space:pre-wrap; font-size:.88em; margin:.4em 0 .2em;
  background:var(--bg-code); border:1px solid var(--border-soft);
  border-left:3px solid var(--orange); border-radius:.3em;
  padding:.6em .8em; overflow-x:auto; color:var(--fg);
}
.variant{border-left-color:var(--border)}
/* every declaration a name could mean, when it names more than one */
.options{list-style:none; margin:.6em 0; padding:0}
.options li{padding:.4em .1em; border-top:1px solid var(--border-soft)}
.options li:first-child{border-top:none}
.opt-doc{color:var(--fg-muted); font-size:.92em}
.disambig>h2{font-family:inherit; font-size:1.15em; color:var(--fg)}
""";

  //a hash may name a section, or an id nested inside one: show the owning section,
  //then scroll to the precise target once it is no longer hidden.
  static String router(){ return ""
    + "<script>\n"
    + "(function(){\n"
    + "  var secs= document.querySelectorAll('.type');\n"
    + "  var links= document.querySelectorAll('#toclist a');\n"
    + "  var items= document.querySelectorAll('#toclist li');\n"
    + "  var filter= document.getElementById('filter');\n"
    + "  var welcome= document.getElementById('welcome');\n"
    + "  function route(){\n"
    + "    var hash= decodeURIComponent(location.hash.slice(1));\n"
    + "    var target= hash ? document.getElementById(hash) : null;\n"
    + "    var active= target && (target.classList.contains('type') ? target : target.closest('.type'));\n"
    + "    secs.forEach(function(el){ el.classList.toggle('active', el===active); });\n"
    + "    links.forEach(function(a){\n"
    + "      a.classList.toggle('sel', !!active && a.getAttribute('href')==='#'+active.id);\n"
    + "    });\n"
    + "    welcome.style.display= active ? 'none' : '';\n"
    + "    if (active && target!==active){ target.scrollIntoView(); }\n"
    + "    else { window.scrollTo(0,0); }\n"
    + "  }\n"
    + "  window.addEventListener('hashchange', route);\n"
    + "  route();\n"
    + "  filter.addEventListener('input', function(){\n"
    + "    var q= filter.value.toLowerCase();\n"
    + "    items.forEach(function(li){\n"
    + "      li.style.display= li.textContent.toLowerCase().indexOf(q) < 0 ? 'none' : '';\n"
    + "    });\n"
    + "  });\n"
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
    sb.append("<nav class=\"toc\">\n")
      .append("<input id=\"filter\" type=\"search\" placeholder=\"Filter types\" autocomplete=\"off\">\n")
      .append("<ul id=\"toclist\">\n");
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
      sb.append("<p class=\"extends\"><b>Extends:</b> ")
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
    sb.append("<section class=\"type disambig\" id=\"").append(disambigId(p.pageId())).append("\">\n")
      .append("<h2>Several match ").append(h(p.title())).append("</h2>\n<ul class=\"options\">\n");
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
      .ifPresent(d->sb.append(" <span class=\"opt-doc\">— ").append(h(d.text())).append("</span>")));
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

  String linkTo(TName n){ return prefix(n.pkgName())+"#"+typeId(n); }

  String linkTo(TName owner, M m){ return prefix(owner.pkgName())+"#"+methodId(owner,m); }

  String prefix(String pkg){
    if (pkg.equals(pkgName)){ return ""; }
    if (pkg.equals("base")){ return baseDocLocation.map(p->p.toUri().toString()).orElse("base.html"); }
    return pkg+".html";
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