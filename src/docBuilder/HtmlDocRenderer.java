package docBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import core.B;
import core.ExportedToStr;
import core.M;
import core.Sig;
import core.T;
import core.TName;
import main.FrontendLogicMain.TestSource;
import offensiveUtils.Require;
import utils.Bug;
import utils.DistinctBy;
import utils.Join;
import utils.Pos;
import utils.Streams;

final class HtmlDocRenderer{
  final String pkgName;
  final List<TypeDoc> types;
  final Optional<Path> baseDocLocation;
  final LinkedHashMap<DocLink.Ambiguous,String> pages= new LinkedHashMap<>();
  final ExportedToStr toStr;
  final DocResolver resolver;
  final HashMap<String,Pattern> namePatterns= new HashMap<>();
  final TreeMap<String,ArrayList<TypeDoc>> groups= new TreeMap<>(Comparator.comparing(HtmlDocRenderer::groupKey, String.CASE_INSENSITIVE_ORDER));
  final HashMap<TypeDoc,ArrayList<TypeDoc>> parents= new HashMap<>();
  final HashMap<TypeDoc,ArrayList<TypeDoc>> children= new HashMap<>();
  final List<TypeDoc> shown;
  HtmlDocRenderer(Map<String,String> uses, DocResolver resolver, Optional<Path> baseDocLocation){
    assert Require.nonNull(uses, resolver, baseDocLocation);
    pkgName= resolver.pkgName();
    types= resolver.types();
    this.baseDocLocation= baseDocLocation;
    toStr= new ExportedToStr(pkgName, uses);
    this.resolver= resolver;
    var visible= sortedTypes().stream().filter(TypeDoc::visible).toList();
    visible.forEach(this::place);
    var res= new LinkedHashSet<TypeDoc>();
    groups.values().forEach(ms -> ms.forEach(m -> visit(m, res)));
    shown= List.copyOf(res);
    assert shown.size() == visible.size();
  }
  void place(TypeDoc t){
    var named= new ArrayList<String>();
    for (var d : t.directives()){
      switch (resolver.directive(d, t.scope())){
        case Directive.Named n -> named.add(n.group());
        case Directive.Under u -> { parents.computeIfAbsent(t, _ -> new ArrayList<>()).add(u.parent()); children.computeIfAbsent(u.parent(), _ -> new ArrayList<>()).add(t); }
        case Directive.Broken _ -> throw Bug.unreachable();
      }
    }
    var standard= named.isEmpty() && !parents.containsKey(t);
    if (standard){ named.add(t.main().name().isPublic() ? "public" : "private"); }
    named.forEach(g -> groups.computeIfAbsent(g, _ -> new ArrayList<>()).add(t));
  }
  void visit(TypeDoc t, LinkedHashSet<TypeDoc> res){
    if (res.add(t)){ related(children, t).forEach(c -> visit(c, res)); }
  }
  static List<TypeDoc> related(HashMap<TypeDoc,ArrayList<TypeDoc>> m, TypeDoc t){ return m.getOrDefault(t, new ArrayList<>()); }
  static String groupKey(String g){ return g.equals("public") ? "1" : g.equals("private") ? "2" : "0"+g; }
  String render(){
    var sb= new StringBuilder(16_000)
      .append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n")
      .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
      .append("<title>").append(h(pkgName)).append("</title>\n")
      .append("<style>\n").append(css).append("</style>\n")
      .append("</head>\n<body>\n<div class=\"layout\">\n");
    renderToc(sb);
    sb.append("<main>\n");
    renderHeader(sb);
    shown.forEach(t -> renderType(sb, t));
    pages.forEach((p,id) -> renderPage(sb, p, id));
    sb.append(router);
    return sb.append("</main>\n</div>\n</body>\n</html>\n").toString();
  }
  String renderText(){
    var sb= new StringBuilder(4_000).append("package ").append(pkgName).append("\n\n");
    groups.forEach((g, ms) -> sb.append("group ").append(g).append('\n').append(Join.of(ms.stream().map(this::typeTitle), "  ", "\n  ", "\n")));
    sb.append('\n');
    shown.forEach(t -> renderTypeText(sb, t));
    return sb.toString();
  }
  record Suite(String name, List<List<DocOcc>> groups){}
  List<Suite> suites(){
    return sortedTypes().stream()
      .map(t -> new Suite(suiteName(t.main().name()), testGroups(t)))
      .filter(s -> !s.groups().isEmpty())
      .toList();
  }
  Stream<DeclarationDoc> declarations(TypeDoc t){ return Stream.concat(Stream.of(t), visibleMethods(t).stream()); }
  List<List<DocOcc>> testGroups(TypeDoc t){
    return declarations(t)
      .map(d -> d.docs.stream().filter(o -> o.kind().tested()).toList())
      .filter(g -> !g.isEmpty())
      .sorted(Comparator.comparingInt((List<DocOcc> g) -> g.getFirst().line()))
      .toList();
  }
  String topSuiteName(){ return "AllAutoTests_"+pkgName; }
  static String suiteName(TName n){
    var s= n.simpleName();
    var noPrimes= s.replaceAll("'+$", "");
    return "_"+noPrimes+"_Examples"+(n.arity()==0 ? "" : "_"+n.arity())+s.substring(noPrimes.length());
  }
  List<TestSource> renderTests(List<Suite> suites, Function<TName,String> pkgNames){
    var test= pkgNames.apply(new TName("base.Test", 0, Pos.unknown));
    var first= suites.getFirst().groups().getFirst().getFirst();
    var top= new TestLines();
    top.wrapper(topSuiteName()+": "+pkgNames.apply(new TName("base.UnitTests", 0, Pos.unknown))+" {::", first.line(), first.textColumn());
    suites.forEach(s -> top.wrapper("  .test "+s.name(), first.line(), first.textColumn()));
    top.wrapper("  }", first.line(), first.textColumn());
    return Stream.concat(suites.stream().map(s -> renderSuite(s, test)), Stream.of(top.source(first.file()))).toList();
  }
  static TestSource renderSuite(Suite s, String test){
    var groups= s.groups();
    var first= groups.getFirst().getFirst();
    var last= groups.getLast().getLast();
    assert groups.stream().flatMap(List::stream).allMatch(d -> d.file().equals(first.file()));
    var lines= new TestLines();
    lines.wrapper(s.name()+": "+test+" {::", first.line(), first.textColumn());
    for (var g : groups){
      lines.wrapper("  .test "+test+"{::", g.getFirst().line(), g.getFirst().textColumn());
      g.forEach(lines::example);
      lines.wrapper("  }", g.getLast().line(), g.getLast().textColumn()+g.getLast().text().length());
    }
    lines.wrapper("  }", last.line(), last.textColumn()+last.text().length());
    return lines.source(first.file());
  }
  void renderTypeText(StringBuilder sb, TypeDoc t){
    sb.append(typeTitle(t)).append(Join.of(t.main().cs().stream().map(toStr::typeName), " : ", ", ", "", "")).append('\n');
    sb.append(Join.of(related(parents, t).stream().map(this::typeTitle), "  under: ", ", ", "\n", ""));
    sb.append(Join.of(related(children, t).stream().map(this::typeTitle), "  see also: ", ", ", "\n", ""));
    renderDocText(sb, "  ", t);
    visibleMethods(t).forEach(m -> renderMethodText(sb, m));
    sb.append('\n');
  }
  void renderMethodText(StringBuilder sb, MethodDoc m){
    m.variants.stream().map(this::sig).distinct().forEach(s -> sb.append('\t').append(s).append('\n'));
    if (m.declared){ renderDocText(sb, "    ", m); }
    sb.append(Join.of(fromRefs(m).stream().map(this::fromName), "    from: ", ", ", "\n", ""));
  }
  void renderDocText(StringBuilder sb, String indent, DeclarationDoc d){
    for (var run : runs(d.visibleDocs(), o -> o.kind()==DocKind.Example)){
      var example= run.getFirst().kind()==DocKind.Example;
      if (example){ sb.append(indent).append("example:\n"); }
      run.forEach(c -> appendIndented(sb, example ? indent+"  " : indent, c.text()));
    }
  }
  static <K> List<List<DocOcc>> runs(List<DocOcc> ds, Function<DocOcc,K> key){
    var res= new ArrayList<ArrayList<DocOcc>>();
    for (var d : ds){
      if (res.isEmpty() || !key.apply(res.getLast().getLast()).equals(key.apply(d))){ res.add(new ArrayList<>()); }
      res.getLast().add(d);
    }
    return List.copyOf(res);
  }
  static void appendIndented(StringBuilder sb, String indent, String line){
    if (!line.isBlank()){ sb.append(indent); }
    sb.append(line).append('\n');
  }
  void renderHeader(StringBuilder sb){
    sb.append("<header class=\"hdr\">").append(mark)
      .append("<div><p class=\"eyebrow\">package</p><h1>").append(h(pkgName)).append("</h1></div>")
      .append("</header>\n")
      .append("<p id=\"welcome\" class=\"welcome\">")
      .append("Types in the list: ").append(groups.values().stream().flatMap(List::stream).distinct().count()).append(". Pick one.</p>\n");
  }
  static final String mark= ""
    +"<svg class=\"mark\" viewBox=\"0 0 64 64\" width=\"38\" height=\"38\" aria-hidden=\"true\">"
    +"<defs><linearGradient id=\"fearless\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0.3\">"
    +"<stop offset=\"0\" stop-color=\"#2068a8\"/><stop offset=\"1\" stop-color=\"#e88028\"/>"
    +"</linearGradient></defs>"
    +"<rect x=\"1\" y=\"1\" width=\"62\" height=\"62\" rx=\"14\" fill=\"url(#fearless)\"/>"
    +"<rect x=\"18\" y=\"12\" width=\"10\" height=\"40\" rx=\"5\" fill=\"#fff\"/>"
    +"<rect x=\"18\" y=\"12\" width=\"30\" height=\"10\" rx=\"5\" fill=\"#fff\"/>"
    +"<rect x=\"18\" y=\"27\" width=\"24\" height=\"10\" rx=\"5\" fill=\"#fff\"/>"
    +"</svg>";
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
.toc{
  width:19em; flex:none; position:sticky; top:0; height:100vh; display:flex; flex-direction:column;
  padding:1.1em .9em; background:var(--bg-soft); border-right:1px solid var(--border);
}
.toc input{
  width:100%; padding:.45em .6em; margin-bottom:.9em; font:inherit; font-size:.92em;
  color:var(--fg); background:var(--bg); border:1px solid var(--border); border-radius:.4em;
}
.toc input:focus{outline:none; border-color:var(--blue)}
.toc ul{list-style:none; margin:0; padding:0; overflow:auto; flex:1}
.toc li{margin:1px 0}
.toc li.group{
  margin:.9em 0 .25em; padding:.55em .55em 0; border-top:1px solid var(--border);
  font-size:.74em; font-weight:700; letter-spacing:.09em; text-transform:uppercase; color:var(--orange);
}
.toc li.group:first-child{margin-top:0; padding-top:0; border-top:none}
.toc a{
  display:block; padding:.28em .55em; border-radius:.35em; border-left:3px solid transparent;
  color:var(--fg-muted); font-size:.93em; padding-left:1.4em; text-indent:-.85em; overflow-wrap:anywhere;
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
  padding:.05em .32em; border-radius:.3em; overflow-wrap:anywhere;
}
.example,.variant,.fenced{
  font-family:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
  white-space:pre-wrap; font-size:.88em; margin:.4em 0 .2em;
  background:var(--bg-code); border:1px solid var(--border-soft);
  border-left:3px solid var(--orange); border-radius:.3em;
  padding:.6em .8em; overflow-x:auto; color:var(--fg);
}
.variant,.fenced{border-left-color:var(--border)}
.options{list-style:none; margin:.6em 0; padding:0}
.options li{padding:.4em .1em; border-top:1px solid var(--border-soft)}
.options li:first-child{border-top:none}
.opt-doc{color:var(--fg-muted); font-size:.92em}
.disambig>h2{font-family:inherit; font-size:1.15em; color:var(--fg)}
@media (max-width:48em){
  .layout{display:block}
  .toc{width:auto; height:auto; max-height:40vh; position:static; border-right:none; border-bottom:1px solid var(--border)}
  main{padding:1.2em 1em 4em}
}
""";
  static final String router= """
<script>
(function(){
  var secs= document.querySelectorAll('.type');
  var links= document.querySelectorAll('#toclist a');
  var items= document.querySelectorAll('#toclist li:not(.group)');
  var groups= document.querySelectorAll('#toclist li.group');
  var filter= document.getElementById('filter');
  var welcome= document.getElementById('welcome');
  function route(){
    var hash= decodeURIComponent(location.hash.slice(1));
    var target= hash ? document.getElementById(hash) : null;
    var active= target && (target.classList.contains('type') ? target : target.closest('.type'));
    secs.forEach(function(el){ el.classList.toggle('active', el===active); });
    var sel= active ? inToc([active]) : [];
    links.forEach(function(a){ a.classList.toggle('sel', sel.indexOf(a.getAttribute('href'))>=0); });
    showSel();
    welcome.style.display= active ? 'none' : '';
    var opened= target && target.closest('details');
    if (opened){ opened.open= true; }
    if (history.state){ window.scrollTo(0, history.state.y); }
    else if (active && target!==active){ target.scrollIntoView(); }
    else { window.scrollTo(0,0); }
  }
  function inToc(ts){
    var hrefs= ts.map(function(t){ return '#'+t.id; });
    var hit= hrefs.filter(function(h){ return document.querySelector('#toclist a[href="'+h+'"]'); });
    if (hit.length || !ts.length){ return hit; }
    var up= [];
    ts.forEach(function(t){ t.querySelectorAll('.under a').forEach(function(a){ up.push(document.getElementById(a.getAttribute('href').slice(1))); }); });
    return inToc(up);
  }
  function showSel(){
    var sel= document.querySelector('#toclist a.sel');
    var list= document.getElementById('toclist');
    if (sel){ list.scrollTop= sel.offsetTop-list.offsetTop-list.clientHeight/2; }
  }
  document.addEventListener('click', function(e){
    if (e.target.closest('a')){ history.replaceState({y:window.scrollY}, ''); }
  }, true);
  window.addEventListener('hashchange', route);
  window.addEventListener('load', route);
  route();
  filter.addEventListener('input', function(){
    var q= filter.value.toLowerCase();
    items.forEach(function(li){
      li.style.display= li.textContent.split('[')[0].toLowerCase().indexOf(q) < 0 ? 'none' : '';
    });
    groups.forEach(function(g){
      var n= g.nextElementSibling;
      while (n && !n.classList.contains('group') && n.style.display==='none'){ n= n.nextElementSibling; }
      g.style.display= n && !n.classList.contains('group') ? '' : 'none';
    });
    document.getElementById('toclist').scrollTop= 0;
    showSel();
  });
  document.querySelectorAll('.method>summary').forEach(function(s){
    s.addEventListener('click', function(e){
      if (e.detail && !e.target.closest('.disclosure') && !e.target.closest('a')){ e.preventDefault(); }
    });
  });
})();
</script>
""";
  List<TypeDoc> sortedTypes(){
    return types.stream()
      .sorted(Comparator.comparing((TypeDoc t) -> t.main().infName() ? "1:"+t.main().pos().fileName() : "0:"+toStr.typeName(t.main().name()))
        .thenComparingInt(t -> t.main().infName() ? t.main().pos().line() : t.main().name().arity())
        .thenComparingInt(t -> t.main().pos().column()))
      .toList();
  }
  List<MethodDoc> visibleMethods(TypeDoc t){
    return t.methods.stream()
      .filter(MethodDoc::visible)
      .sorted(Comparator.comparing((MethodDoc m) -> m.main().sig().m().s())
        .thenComparingInt(m -> m.main().sig().m().arity())
        .thenComparing(m -> m.main().sig().rc()+"/"+toStr.sig(m.main().sig())))
      .toList();
  }
  void renderToc(StringBuilder sb){
    sb.append("<nav class=\"toc\">\n")
      .append("<input id=\"filter\" type=\"search\" placeholder=\"Filter types\" autocomplete=\"off\">\n")
      .append("<ul id=\"toclist\">\n");
    groups.forEach((g, ms) -> renderTocGroup(sb, g, ms));
    sb.append("</ul>\n</nav>\n");
  }
  void renderTocGroup(StringBuilder sb, String group, List<TypeDoc> members){
    sb.append("<li class=\"group\">").append(h(group)).append("</li>\n");
    members.forEach(t -> sb.append("<li>").append(typeDocLink(t)).append("</li>\n"));
  }
  String typeDocLink(TypeDoc t){ return "<a href=\"#"+typeId(t)+"\">"+wrap(typeTitle(t))+"</a>"; }
  void renderType(StringBuilder sb, TypeDoc t){
    sb.append("<section class=\"type\" id=\"").append(typeId(t)).append("\">\n")
      .append("<h2>").append(wrap(typeTitle(t))).append("</h2>\n");
    renderDoc(sb, t);
    sb.append(Join.of(related(parents, t).stream().map(this::typeDocLink), "<p class=\"extends under\"><b>Under:</b> ", ", ", "</p>\n", ""));
    sb.append(Join.of(related(children, t).stream().map(this::typeDocLink), "<p class=\"extends\"><b>See also:</b> ", ", ", "</p>\n", ""));
    sb.append(Join.of(t.main().cs().stream().map(this::typeLink), "<p class=\"extends\"><b>Extends:</b> ", ", ", "</p>\n", ""));
    renderVariants(sb, "Typed literal variants", t.variants, toStr::lit);
    var ms= visibleMethods(t);
    if (!ms.isEmpty()){ sb.append("<h3>Methods</h3>\n"); }
    ms.forEach(m -> renderMethod(sb, m));
    sb.append("</section>\n");
  }
  void renderMethod(StringBuilder sb, MethodDoc m){
    var ids= m.variants.stream().map(v -> methodId(m.owner.main().name(), v.sig())).distinct().toList();
    sb.append("<details class=\"method\" id=\"").append(ids.getFirst()).append("\">\n")
      .append("<summary><span class=\"disclosure\">&#9656;</span>");
    ids.stream().skip(1).forEach(id -> sb.append("<span id=\"").append(id).append("\"></span>"));
    sb.append("<span class=\"sig\">").append(renderSig(m)).append("</span></summary>\n");
    if (m.declared){ renderDoc(sb, m); }
    sb.append(Join.of(fromRefs(m).stream().map(this::fromLink), "<p class=\"doc from\">From: ", ", ", ".</p>\n", ""));
    renderVariants(sb, "Typed method variants", m.variants, this::sig);
    sb.append("</details>\n");
  }
  String fromLink(MethodRef r){
    return "<a href=\""+h(methodHref(r.provider().name(), r.method().sig()))+"\">"+h(fromName(r))+"</a>";
  }
  List<MethodRef> fromRefs(MethodDoc m){
    return m.inheritedFrom.stream().gather(DistinctBy.<MethodRef,String>of(this::fromName)).toList();
  }
  String sig(M m){
    var s= m.sig();
    var ps= Streams.zip(m.names(), s.ts()).map((x,t) -> (x.equals("_") ? "" : x+":")+toStr.type(t));
    return s.rc().toStrSpace()+s.m().s()+Join.of(s.bs().stream().map(B::compactToString), "[", ",", "]", "")+Join.of(ps, "(", ",", ")", "")+":"+toStr.type(s.ret());
  }
  String fromName(MethodRef r){
    return toStr.typeName(r.provider())+r.method().sig().m();
  }
  String renderSig(MethodDoc m){
    var sig= m.main().sig();
    var text= sig(m.main());
    var sb= new StringBuilder();
    var i= 0;
    var from= 0;
    for (var n : Stream.concat(sig.ts().stream(), Stream.of(sig.ret())).flatMap(HtmlDocRenderer::typeNames).toList()){
      var printed= toStr.typeName(n);
      var at= namePatterns.computeIfAbsent(printed, p -> Pattern.compile("(?<![\\p{L}\\p{Nd}_.])"+Pattern.quote(p)+"(?![\\p{L}\\p{Nd}_'])")).matcher(text);
      var found= at.find(from);
      assert found;
      from = at.end();
      if (!resolver.byName().containsKey(n) && !resolver.other().dom().contains(n)){ continue; }
      sb.append(wrap(text.substring(i, at.start())));
      sb.append("<a href=\"").append(h(typeHref(n))).append("\">").append(h(printed)).append("</a>");
      i = from;
    }
    sb.append(wrap(text.substring(i)));
    return sb.toString();
  }
  static Stream<TName> typeNames(T t){
    if (!(t instanceof T.RCC rcc)){ return Stream.of(); }
    return Stream.concat(Stream.of(rcc.c().name()), rcc.c().ts().stream().flatMap(HtmlDocRenderer::typeNames));
  }
  void renderDoc(StringBuilder sb, DeclarationDoc d){
    var visible= d.visibleDocs();
    if (visible.isEmpty()){
      sb.append("<p class=\"doc missing\">No documentation yet.</p>\n");
      return;
    }
    for (var run : runs(visible, o -> o.kind()==DocKind.Example)){
      if (run.getFirst().kind()==DocKind.Example){ renderExamples(sb, run.stream().map(DocOcc::text).toList()); continue; }
      sb.append("<div class=\"doc\">\n");
      runs(run, DocOcc::kind).forEach(b -> renderBlock(sb, b, d.scope()));
      sb.append("</div>\n");
    }
  }
  void renderExamples(StringBuilder sb, List<String> examples){
    sb.append(Join.of(examples.stream().map(HtmlDocRenderer::h), "<details class=\"examples\"><summary><span class=\"disclosure\">&#9656;</span>runnable example</summary>\n<pre class=\"example\">", "\n", "</pre></details>\n", ""));
  }
  void renderBlock(StringBuilder sb, List<DocOcc> block, Scope scope){
    var kind= block.getFirst().kind();
    if (kind==DocKind.Fence){ return; }
    if (kind==DocKind.Fenced){ sb.append("<pre class=\"fenced\">").append(Join.of(block.stream().map(o -> h(o.text())), "", "\n", "", "")).append("</pre>\n"); return; }
    paragraphs(block).forEach(r -> sb.append("<p>").append(renderParagraph(r, scope)).append("</p>\n"));
  }
  static boolean inParagraph(DocOcc o){ return o.kind()==DocKind.Prose && !o.text().isBlank(); }
  static List<List<DocOcc>> paragraphs(List<DocOcc> ds){
    return runs(ds, HtmlDocRenderer::inParagraph).stream().filter(r -> inParagraph(r.getFirst())).toList();
  }
  static String joined(List<DocOcc> p){ return Join.of(p.stream().map(DocOcc::text), "", "\n", "", ""); }
  String renderParagraph(List<DocOcc> p, Scope scope){
    var text= joined(p);
    var sb= new StringBuilder();
    var i= 0;
    for (var cs : DocRefScanner.codeSpans(text)){
      sb.append(h(text.substring(i, cs.start()-cs.fence())));
      sb.append("<code>").append(renderCode(text, cs, scope)).append("</code>");
      i = cs.end()+cs.fence();
    }
    sb.append(h(text.substring(i)));
    return sb.toString();
  }
  String renderCode(String text, DocRefScanner.CodeSpan cs, Scope scope){
    var body= h(text.substring(cs.start(), cs.end()));
    if (cs.fence()!=1){ return body; }
    return href(resolver.resolve(DocRefScanner.wholeRef(text, cs).orElseThrow(), scope).orElseThrow())
      .map(href -> "<a href=\""+h(href)+"\">"+body+"</a>").orElse(body);
  }
  void renderPage(StringBuilder sb, DocLink.Ambiguous p, String anchor){
    sb.append("<section class=\"type disambig\" id=\"").append(anchor).append("\">\n")
      .append("<h2>Several match ").append(h(p.title())).append("</h2>\n<ul class=\"options\">\n");
    p.options().stream()
      .sorted(Comparator.comparing((Candidate c) -> c.owner().s()).thenComparingInt(c -> c.owner().arity())
        .thenComparingInt(c -> c instanceof Candidate.Method m ? m.sig().m().arity() : 0))
      .forEach(c -> sb.append("<li>").append(renderCandidate(c)).append("</li>\n"));
    sb.append("</ul>\n</section>\n");
  }
  String renderCandidate(Candidate c){
    var owner= h(resolver.localTypeDoc(c.owner()).filter(t -> t.main().infName()).map(this::typeTitle).orElseGet(() -> toStr.typeNameWithArity(c.owner())));
    var link= "<a href=\""+h(candidateHref(c))+"\">";
    return switch (c){
      case Candidate.Type _ -> link+owner+"</a>";
      case Candidate.Method(_, var s, var local) -> link+s.rc().toStrSpace()+owner+h(s.m().s()+DocResolver.shape(OptionalInt.of(s.m().arity()), "(", ")"))+"</a>"
        +local.flatMap(this::renderFirstParagraph).map(p -> " <span class=\"opt-doc\">\u2014 "+p.replace("\n", " ")+"</span>").orElse("");
    };
  }
  Optional<String> renderFirstParagraph(MethodDoc m){
    return paragraphs(m.visibleDocs()).stream().findFirst().map(p -> renderParagraph(p, m.scope()));
  }
  Optional<String> href(DocLink link){
    return switch (link){
      case DocLink.Ambiguous a -> Optional.of("#"+pages.computeIfAbsent(a, _ -> "disambig-"+id(a.title())+"-"+pages.size()));
      case DocLink.Resolved r -> Optional.of(candidateHref(r.hit()));
      case DocLink.NoLink _ -> Optional.empty();
    };
  }
  String candidateHref(Candidate c){
    return switch (c){
      case Candidate.Type t -> typeHref(t.owner());
      case Candidate.Method m -> methodHref(m.owner(), m.sig());
    };
  }
  <V> void renderVariants(StringBuilder sb, String title, List<V> variants, Function<V,String> show){
    if (variants.size() <= 1){ return; }
    var shown= variants.stream().map(show).distinct().toList();
    if (shown.size() <= 1){ return; }
    sb.append("<details class=\"variants\"><summary>")
      .append(h(title)).append(": ").append(shown.size()).append("</summary>\n");
    shown.forEach(v -> sb.append("<pre class=\"variant\">").append(h(v)).append("</pre>\n"));
    sb.append("</details>\n");
  }
  String typeTitle(TypeDoc t){
    var l= t.main();
    if (l.infName()){ return resolver.anonTitle(l); }
    return toStr.typeName(l.name())+Join.of(l.bs().stream().map(B::compactToString), "[", ",", "]", "");
  }
  String typeLink(T.C c){
    return "<a href=\""+h(typeHref(c.name()))+"\">"+h(toStr.typeName(c))+"</a>";
  }
  String typeHref(TName n){ return resolver.localTypeDoc(n).map(t -> "#"+typeId(t)).orElseGet(() -> prefix(n.pkgName())+"#"+typeId(n)); }
  String methodHref(TName owner, Sig s){ return prefix(owner.pkgName())+"#"+methodId(owner, s); }
  String prefix(String pkg){
    if (pkg.equals(pkgName)){ return ""; }
    if (pkg.equals("base")){ return baseDocLocation.map(p -> p.toUri().toString()).orElse("base.html"); }
    return pkg+".html";
  }
  static String typeId(TypeDoc t){
    if (!t.main().infName()){ return typeId(t.main().name()); }
    var p= t.main().pos();
    return "literal-"+id(p.fileName().toString())+"-"+p.line()+"-"+p.column();
  }
  static String typeId(TName n){
    return "type-"+id(n.s())+"-"+n.arity();
  }
  static String methodId(TName owner, Sig s){
    return "method-"+id(owner.s())+"-"+owner.arity()+"-"+id(s.rc().name())+"-"+id(s.m().s())+"-"+s.m().arity();
  }
  private static final Pattern notPlain= Pattern.compile("[^a-zA-Z0-9]");
  static String id(String s){ return notPlain.matcher(s).replaceAll(m -> "_"+Integer.toHexString(m.group().codePointAt(0))+"_"); }
  static String wrap(String s){ return h(s).replace(",", ",<wbr>"); }
  static String h(String s){
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
