package docBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.B;
import core.ExportedToStr;
import core.M;
import core.MName;
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
  final ArrayList<DocLink.Ambiguous> pages= new ArrayList<>();
  final ExportedToStr toStr;
  final DocResolver resolver;
  final HashMap<String,Pattern> namePatterns= new HashMap<>();
  final HashSet<String> ids= new HashSet<>();
  final TreeMap<String,ArrayList<TypeDoc>> names= new TreeMap<>();
  final TreeMap<String,TreeSet<String>> groups= new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  final TreeMap<String,TreeSet<String>> under= new TreeMap<>();
  HtmlDocRenderer(Map<String,String> uses, DocResolver resolver, Optional<Path> baseDocLocation){
    assert Require.nonNull(uses, resolver, baseDocLocation);
    pkgName= resolver.pkgName();
    types= resolver.types();
    this.baseDocLocation= baseDocLocation;
    toStr= new ExportedToStr(pkgName, uses);
    this.resolver= resolver;
    sortedTypes().stream().filter(t -> resolver.shown().contains(t.main().name())).forEach(this::place);
  }
  void place(TypeDoc t){
    var name= t.main().name().simpleName();
    names.computeIfAbsent(name, _ -> new ArrayList<>()).add(t);
    for (var d : t.directives()){
      switch (resolver.directive(d, t.scope())){
        case Directive.Named n -> groups.computeIfAbsent(n.group(), _ -> new TreeSet<>()).add(name);
        case Directive.Under u -> under.computeIfAbsent(u.parent(), _ -> new TreeSet<>()).add(name);
        case Directive.Broken _ -> throw Bug.unreachable();
      }
    }
  }
  Collection<String> under(String name){ return under.getOrDefault(name, new TreeSet<>()); }
  static Stream<String> holders(Map<String,TreeSet<String>> m, String name){
    return m.entrySet().stream().filter(e -> e.getValue().contains(name)).map(Map.Entry::getKey);
  }
  List<String> loose(){
    return names.keySet().stream().filter(n -> Stream.concat(holders(groups, n), holders(under, n)).findAny().isEmpty()).toList();
  }
  String render(){
    var sb= new StringBuilder(16_000)
      .append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n")
      .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
      .append("<title>").append(h(pkgName)).append("</title>\n")
      .append("<style>\n").append(css).append("</style>\n")
      .append("</head>\n<body>\n<div class=\"layout\">\n");
    renderToc(sb);
    sb.append("<main>\n<header class=\"hdr\">").append(mark)
      .append("<div><p class=\"eyebrow\">package</p><h1><a href=\"#top\">").append(h(pkgName)).append("</a></h1></div></header>\n")
      .append("<section class=\"view\" id=\"top\">\n");
    groups.forEach((g, ns) -> sb.append(entry("#"+groupId(g), g, "<span class=\"brief\">"+h(String.join(", ", ns))+"</span>")));
    loose().forEach(n -> sb.append(nameEntry(n)));
    sb.append("</section>\n");
    groups.forEach((g, ns) -> renderGroup(sb, g, ns));
    names.forEach((n, ts) -> renderName(sb, n, ts));
    for (var i= 0; i < pages.size(); i += 1){ renderPage(sb, pages.get(i)); }
    sb.append(router);
    return sb.append("</main>\n</div>\n</body>\n</html>\n").toString();
  }
  String renderText(){
    var sb= new StringBuilder(4_000).append("package ").append(pkgName).append('\n');
    for (var g : groups.entrySet()){
      sb.append("  \"").append(g.getKey()).append("\"\n");
      g.getValue().forEach(n -> renderNameText(sb, "    ", n));
    }
    loose().forEach(n -> renderNameText(sb, "  ", n));
    sb.append('\n');
    names.values().forEach(ts -> ts.forEach(t -> renderTypeText(sb, t)));
    return sb.toString();
  }
  void renderNameText(StringBuilder sb, String indent, String name){
    var brief= documented(name).flatMap(t -> paragraphs(t.visibleDocs()).stream().findFirst()).map(p -> ": "+joined(p).replace("\n", " ")).orElse("");
    sb.append(indent).append(name).append(brief).append('\n');
    under(name).forEach(n -> renderNameText(sb, indent+"  ", n));
  }
  Optional<TypeDoc> documented(String name){ return names.get(name).stream().filter(TypeDoc::hasDocs).findFirst(); }
  record Suite(String name, List<List<DocOcc>> groups){}
  List<Suite> suites(){
    return sortedTypes().stream()
      .map(t -> new Suite(suiteName(t.main().name()), testGroups(t)))
      .filter(s -> !s.groups().isEmpty())
      .toList();
  }
  List<List<DocOcc>> testGroups(TypeDoc t){
    return Stream.<DeclarationDoc>concat(Stream.of(t), t.methods.stream())
      .map(d -> d.docs.stream().filter(o -> o.kind().tested()).toList())
      .filter(g -> !g.isEmpty())
      .distinct()
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
    sb.append(typeTitle(t)).append(Join.of(supers(t).stream().map(toStr::typeName), " : ", ", ", "", "")).append('\n');
    renderDocText(sb, "  ", t);
    for (var e : entries(t)){
      docGroups(e).forEach(g -> renderMethodText(sb, g, holder(g.getFirst(), e), fromRefs(g)));
    }
    supers(t).forEach(c -> homed(t, c).forEach(m -> renderMethodText(sb, List.of(m), m, List.of(resolver.home(m).get()))));
    sb.append('\n');
  }
  void renderMethodText(StringBuilder sb, List<MethodDoc> ms, MethodDoc holder, List<MethodRef> from){
    ms.forEach(m -> sb.append('\t').append(sig(m.main())).append('\n'));
    renderDocText(sb, "    ", holder);
    sb.append(Join.of(from.stream().map(this::fromName), "    from: ", ", ", "\n", ""));
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
.toc ul{list-style:none; margin:0; padding:0}
.toc>ul{overflow:auto; flex:1}
#tree ul{display:none; padding-left:.9em}
#tree li.open>ul{display:block}
.toc li{margin:1px 0}
.toc a{
  display:block; padding:.28em .55em; border-radius:.35em; border-left:3px solid transparent;
  color:var(--fg-muted); font-size:.93em; overflow-wrap:anywhere;
}
.toc a:hover{background:var(--border-soft); color:var(--fg); text-decoration:none}
.toc a.sel{background:var(--bg); border-left-color:var(--orange); color:var(--blue); font-weight:600}
#tree>li.folder>a{color:var(--fg); font-weight:600}
#tree li.folder>a::before{content:"\\25B8"; display:inline-block; width:1em; color:var(--fg-faint)}
#tree li.folder.open>a::before{content:"\\25BE"}
.toc li:not(.folder)>a{padding-left:1.55em}
main{flex:1; min-width:0; padding:2.2em 2.6em 6em; max-width:64em}
.hdr{display:flex; align-items:center; gap:.85em; padding-bottom:1em; border-bottom:1px solid var(--border)}
.hdr h1{margin:0; font-size:1.65em; letter-spacing:-.01em}
.hdr h1 a{color:inherit}
.eyebrow{
  margin:0; font-size:.7em; font-weight:600; letter-spacing:.12em;
  text-transform:uppercase; color:var(--fg-faint);
}
.view{display:none; margin:1.6em 0}
.view.active{display:block}
.view>h2{
  margin:.2em 0 .1em; font-size:1.3em; color:var(--blue-deep);
  font-family:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
}
.view>h2~h2{margin-top:2.2em; padding-top:1em; border-top:1px solid var(--border)}
.view>h2.plain{font-family:inherit; font-size:1.15em; color:var(--fg)}
.view>h3{
  margin:1.8em 0 .4em; font-size:.78em; font-weight:600; letter-spacing:.1em;
  text-transform:uppercase; color:var(--fg-faint); border-bottom:1px solid var(--border-soft);
  padding-bottom:.35em;
}
.where,.extends{margin:.5em 0; font-size:.92em; color:var(--fg-muted)}
.entry{margin:0; padding:.5em .2em; border-top:1px solid var(--border-soft)}
.entry>a{font-weight:600}
.brief{
  display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden;
  color:var(--fg-muted); font-size:.93em;
}
.method{margin:0; padding:.45em .2em; border-top:1px solid var(--border-soft)}
h3+.method,h3+.entry,.where+.entry{border-top:none}
.method:target,.extends a:target{background:var(--bg-soft); border-radius:.4em}
.method>summary,div.method{display:grid; grid-template-columns:1.1em 1fr}
.method>summary{list-style:none; cursor:text}
.method>summary::-webkit-details-marker{display:none}
.method .brief{grid-column:2; cursor:pointer}
.method[open] .brief{display:none}
.examples>summary{list-style:none; cursor:pointer; color:var(--orange); font-size:.86em; font-weight:600}
.examples>summary::-webkit-details-marker{display:none}
.disclosure{
  display:inline-block; width:1em; cursor:pointer; color:var(--fg-faint);
  transition:transform .12s ease; user-select:none;
}
.disclosure:hover{color:var(--blue)}
details[open]>summary .disclosure{transform:rotate(90deg)}
.sig,.of{
  font-family:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
  white-space:pre-wrap; font-size:.95em; font-weight:600; color:var(--fg);
}
.of{margin:.8em 0 0 1.6em; font-size:.85em; font-weight:400; color:var(--fg-faint)}
.doc{margin:.3em 0 .3em 1.6em; color:var(--fg-muted); font-size:.95em}
.doc p{white-space:pre-wrap; margin:.55em 0}
.from{color:var(--fg-faint); font-size:.88em}
details{margin:.4em 0}
code{
  font-family:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
  font-size:.92em; background:var(--bg-code); border:1px solid var(--border-soft);
  padding:.05em .32em; border-radius:.3em; overflow-wrap:anywhere;
}
.example,.fenced{
  font-family:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
  white-space:pre-wrap; font-size:.88em; margin:.4em 0 .2em;
  background:var(--bg-code); border:1px solid var(--border-soft);
  border-left:3px solid var(--orange); border-radius:.3em;
  padding:.6em .8em; overflow-x:auto; color:var(--fg);
}
.fenced{border-left-color:var(--border)}
@media (max-width:48em){
  .layout{display:block}
  .toc{width:auto; height:auto; max-height:40vh; position:static; border-right:none; border-bottom:1px solid var(--border)}
  main{padding:1.2em 1em 4em}
}
""";
  static final String router= """
<script>
(function(){
  var views= document.querySelectorAll('.view');
  var tree= document.getElementById('tree');
  var found= document.getElementById('found');
  var methods= document.getElementById('methods');
  var filter= document.getElementById('filter');
  var clicked= null;
  function route(){
    var hash= decodeURIComponent(location.hash.slice(1));
    var target= hash ? document.getElementById(hash) : null;
    var active= (target && target.closest('.view')) || document.getElementById('top');
    views.forEach(function(el){ el.classList.toggle('active', el===active); });
    var a= pick('#'+active.id);
    if (a || active.id==='top'){ tree.querySelectorAll('.open,.sel').forEach(function(el){ el.classList.remove('open','sel'); }); }
    for (var li= a && a.parentElement; li; li= li.parentElement.closest('li')){ li.classList.add('open'); }
    if (a){ a.classList.add('sel'); a.scrollIntoView({block:'nearest'}); }
    clicked= null;
    var opened= target && target.closest('details');
    if (opened){ opened.open= true; }
    if (history.state){ window.scrollTo(0, history.state.y); }
    else if (target && target!==active){ target.scrollIntoView(); }
    else { window.scrollTo(0,0); }
  }
  function pick(href){
    if (clicked && clicked.getAttribute('href')===href){ return clicked; }
    var q= 'a[href="'+href+'"]';
    var sel= tree.querySelector('a.sel');
    for (var li= sel && sel.parentElement; li; li= li.parentElement.closest('li')){
      var hit= li.querySelector(q);
      if (hit){ return hit; }
    }
    return tree.querySelector(q);
  }
  document.addEventListener('click', function(e){
    if (e.target.closest('a')){ history.replaceState({y:window.scrollY}, ''); }
  }, true);
  tree.addEventListener('click', function(e){
    var a= e.target.closest('a');
    if (!a){ return; }
    clicked= a;
    if (a.classList.contains('sel')){ a.parentElement.classList.toggle('open'); }
    else if (a.getAttribute('href')===location.hash){ route(); }
  });
  window.addEventListener('hashchange', route);
  window.addEventListener('load', route);
  route();
  filter.addEventListener('input', function(){
    var q= filter.value.toLowerCase();
    var at= q.search(/[^a-z0-9_]/);
    var list= at<0 ? found : methods;
    var type= at<0 ? q : q.slice(0, at);
    tree.hidden= !!q;
    found.hidden= !q || list!==found;
    methods.hidden= list!==methods;
    list.querySelectorAll('a').forEach(function(a){
      a.parentElement.hidden= a.firstChild.textContent.toLowerCase().indexOf(type)<0 || a.lastChild.textContent.toLowerCase().indexOf(q.slice(type.length))<0;
    });
  });
  document.querySelectorAll('.method>summary').forEach(function(s){
    s.addEventListener('click', function(e){
      if (e.detail && e.target.closest('.sig') && !e.target.closest('a')){ e.preventDefault(); }
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
  void renderToc(StringBuilder sb){
    sb.append("<nav class=\"toc\">\n")
      .append("<input id=\"filter\" type=\"search\" placeholder=\"Find a Type or a .method\" autocomplete=\"off\">\n")
      .append("<ul id=\"tree\">\n");
    groups.forEach((g, ns) -> sb.append(folder("#"+groupId(g), g, ns)));
    loose().forEach(n -> sb.append(folder(n)));
    sb.append("</ul>\n<ul id=\"found\" hidden>\n");
    names.keySet().forEach(n -> sb.append("<li>").append(nameLink(n)).append("</li>\n"));
    sb.append("</ul>\n<ul id=\"methods\" hidden>\n");
    names.values().stream().flatMap(List::stream).flatMap(t -> entries(t).stream()).map(List::getFirst).sorted(byMName).forEach(m -> sb.append(methodItem(m)));
    sb.append("</ul>\n</nav>\n");
  }
  String methodItem(MethodDoc m){
    var owner= m.owner.main().name();
    return "<li><a href=\""+methodHref(owner, m.main().sig())+"\">"+h(toStr.typeNameWithArity(owner))+"<b>"+h(DocResolver.shape(m.main().sig().m()))+"</b></a></li>\n";
  }
  static final Comparator<MethodDoc> byMName= Comparator.comparing((MethodDoc m) -> m.main().sig().m().s()).thenComparingInt(m -> m.main().sig().m().arity());
  String folder(String name){ return folder(nameHref(name), name, under(name)); }
  String folder(String href, String title, Collection<String> inside){
    var link= "<a href=\""+href+"\">"+h(title)+"</a>";
    if (inside.isEmpty()){ return "<li>"+link+"</li>\n"; }
    return "<li class=\"folder\">"+link+"<ul>\n"+inside.stream().map(this::folder).collect(Collectors.joining())+"</ul></li>\n";
  }
  static String entry(String href, String title, String brief){
    return "<p class=\"entry\"><a href=\""+h(href)+"\">"+h(title)+"</a>"+brief+"</p>\n";
  }
  String nameEntry(String name){ return entry(nameHref(name), name, documented(name).map(this::brief).orElse("")); }
  String nameLink(String name){ return "<a href=\""+nameHref(name)+"\">"+h(name)+"</a>"; }
  String nameHref(String name){ return "#"+typeId(names.get(name).getFirst().main().name()); }
  String brief(DeclarationDoc d){
    return paragraphs(d.visibleDocs()).stream().findFirst()
      .map(p -> "<span class=\"brief\">"+renderParagraph(p, d.scope()).replace("\n", " ")+"</span>").orElse("");
  }
  void renderGroup(StringBuilder sb, String group, Collection<String> inside){
    sb.append("<section class=\"view\" id=\"").append(groupId(group)).append("\">\n")
      .append("<h2 class=\"plain\">").append(h(group)).append("</h2>\n");
    inside.forEach(n -> sb.append(nameEntry(n)));
    sb.append("</section>\n");
  }
  void renderName(StringBuilder sb, String name, List<TypeDoc> ts){
    var in= Stream.concat(holders(groups, name).map(g -> "<a href=\"#"+groupId(g)+"\">"+h(g)+"</a>"), holders(under, name).map(this::nameLink));
    sb.append("<section class=\"view\"").append(anchor(typeId(ts.getFirst().main().name()))).append(">\n")
      .append(Join.of(in, "<p class=\"where\">In: ", ", ", "</p>\n", ""));
    ts.forEach(t -> renderType(sb, t));
    if (!under(name).isEmpty()){ sb.append("<h3>Under ").append(h(name)).append("</h3>\n"); }
    under(name).forEach(n -> sb.append(nameEntry(n)));
    sb.append("</section>\n");
  }
  String anchor(String id){ return ids.add(id) ? " id=\""+id+"\"" : ""; }
  void renderType(StringBuilder sb, TypeDoc t){
    sb.append("<h2").append(anchor(typeId(t.main().name()))).append(">").append(wrap(typeTitle(t))).append("</h2>\n");
    renderDoc(sb, t);
    var own= entries(t);
    if (!own.isEmpty()){ sb.append("<h3>Methods</h3>\n"); }
    own.forEach(e -> renderEntry(sb, e));
    if (!supers(t).isEmpty()){ sb.append("<h3>Extends</h3>\n"); }
    supers(t).forEach(c -> sb.append("<p class=\"extends\">").append(typeLink(c))
      .append(Join.of(homed(t, c).stream().gather(DistinctBy.<MethodDoc,MName>of(m -> m.main().sig().m())).map(this::homedLink), ": ", ", ", "", "")).append("</p>\n"));
  }
  List<T.C> supers(TypeDoc t){ return t.main().cs().stream().filter(c -> resolver.shown().contains(c.name())).toList(); }
  List<MethodDoc> homed(TypeDoc t, T.C c){
    return t.methods.stream()
      .filter(m -> resolver.home(m).filter(r -> r.provider().equals(c)).isPresent())
      .sorted(byMName)
      .toList();
  }
  String homedLink(MethodDoc m){
    var s= m.main().sig();
    return "<a"+anchor(methodId(m.owner.main().name(), s))+" href=\""+h(methodHref(resolver.home(m).get().provider().name(), s))+"\">"+h(DocResolver.shape(s.m()))+"</a>";
  }
  Collection<List<MethodDoc>> entries(TypeDoc t){
    var cs= t.main().cs().stream().map(T.C::name).toList();
    return t.methods.stream()
      .filter(m -> resolver.home(m).isEmpty())
      .sorted(Comparator.comparingInt((MethodDoc m) -> cs.indexOf(m.main().sig().origin()))
        .thenComparingInt(m -> m.main().sig().span().pos().line())
        .thenComparingInt(m -> m.main().sig().span().pos().column()))
      .collect(Collectors.groupingBy(m -> m.main().sig().m(), LinkedHashMap::new, Collectors.toList())).values();
  }
  MethodDoc holder(MethodDoc m, List<MethodDoc> entry){
    return Stream.concat(Stream.of(m), entry.stream()).flatMap(this::inherited).filter(MethodDoc::hasDocs).findFirst().orElse(m);
  }
  Stream<MethodDoc> inherited(MethodDoc m){
    var key= new MethodKey.Declared(m.main().sig().rc(), m.main().sig().m());
    return Stream.concat(Stream.of(m), m.inheritedFrom.stream()
      .flatMap(r -> resolver.localTypeDoc(r.provider().name()).stream())
      .flatMap(p -> Stream.ofNullable(p.byKey.get(key))));
  }
  Collection<List<MethodDoc>> docGroups(List<MethodDoc> entry){
    return entry.stream().collect(Collectors.groupingBy(m -> holder(m, entry).visibleDocs().stream().map(DocOcc::text).toList(), LinkedHashMap::new, Collectors.toList())).values();
  }
  void renderEntry(StringBuilder sb, List<MethodDoc> entry){
    var first= entry.getFirst();
    var groups= docGroups(entry);
    var body= new StringBuilder();
    for (var g : groups){
      if (groups.size() > 1){ body.append("<p class=\"of\">").append(h(Join.of(g.stream().map(m -> sig(m.main())), "", "\n", ""))).append("</p>\n"); }
      renderDoc(body, holder(g.getFirst(), entry));
    }
    body.append(Join.of(fromRefs(entry).stream().map(this::fromLink), "<p class=\"doc from\">From: ", ", ", ".</p>\n", ""));
    var open= " class=\"method\""+anchor(methodId(first.owner.main().name(), first.main().sig()))+">";
    var sigs= "<span class=\"sig\">"+Join.of(entry.stream().map(this::renderSig), "", "\n", "")+"</span>";
    if (body.isEmpty()){ sb.append("<div").append(open).append("<span></span>").append(sigs).append("</div>\n"); return; }
    sb.append("<details").append(open).append("<summary><span class=\"disclosure\">&#9656;</span>").append(sigs)
      .append(brief(holder(first, entry))).append("</summary>\n").append(body).append("</details>\n");
  }
  String fromLink(MethodRef r){
    return "<a href=\""+h(methodHref(r.provider().name(), r.method().sig()))+"\">"+h(fromName(r))+"</a>";
  }
  List<MethodRef> fromRefs(List<MethodDoc> ms){
    return ms.stream().flatMap(m -> m.inheritedFrom.stream()).filter(resolver::shows).gather(DistinctBy.<MethodRef,String>of(this::fromName)).toList();
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
    for (var n : typeNames(sig).toList()){
      var printed= toStr.typeName(n);
      var at= namePatterns.computeIfAbsent(printed, p -> Pattern.compile("(?<![\\p{L}\\p{Nd}_.])"+Pattern.quote(p)+"(?![\\p{L}\\p{Nd}_'])")).matcher(text);
      var found= at.find(from);
      assert found;
      from = at.end();
      if (!resolver.shown().contains(n)){ continue; }
      sb.append(wrap(text.substring(i, at.start())));
      sb.append("<a href=\"").append(h(typeHref(n))).append("\">").append(h(printed)).append("</a>");
      i = from;
    }
    sb.append(wrap(text.substring(i)));
    return sb.toString();
  }
  static Stream<TName> typeNames(Sig s){ return Stream.concat(s.ts().stream(), Stream.of(s.ret())).flatMap(HtmlDocRenderer::typeNames); }
  static Stream<TName> typeNames(T t){
    if (!(t instanceof T.RCC rcc)){ return Stream.of(); }
    return Stream.concat(Stream.of(rcc.c().name()), rcc.c().ts().stream().flatMap(HtmlDocRenderer::typeNames));
  }
  void renderDoc(StringBuilder sb, DeclarationDoc d){
    for (var run : runs(d.visibleDocs(), o -> o.kind()==DocKind.Example)){
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
    var written= text.substring(cs.start(), cs.end());
    var body= h(written);
    if (cs.fence()!=1){ return body; }
    return href(resolver.resolve(DocRefScanner.wholeRef(written).orElseThrow(), scope).orElseThrow())
      .map(href -> "<a href=\""+h(href)+"\">"+body+"</a>").orElse(body);
  }
  void renderPage(StringBuilder sb, DocLink.Ambiguous p){
    sb.append("<section class=\"view\" id=\"").append(pageId(p)).append("\">\n")
      .append("<h2 class=\"plain\">Several match ").append(h(p.title())).append("</h2>\n");
    p.options().stream()
      .sorted(Comparator.comparing((Candidate c) -> c.owner().s()).thenComparingInt(c -> c.owner().arity())
        .thenComparingInt(c -> c instanceof Candidate.Method m ? m.sig().m().arity() : 0))
      .forEach(c -> sb.append(renderCandidate(c)));
    sb.append("</section>\n");
  }
  String pageId(DocLink.Ambiguous p){
    if (!pages.contains(p)){ pages.add(p); }
    return "disambig-"+id(p.title())+"-"+pages.indexOf(p);
  }
  String renderCandidate(Candidate c){
    var owner= toStr.typeNameWithArity(c.owner());
    return switch (c){
      case Candidate.Type _ -> entry(candidateHref(c), owner, "");
      case Candidate.Method(_, var s, var local) -> entry(candidateHref(c), owner+DocResolver.shape(s.m()),
        local.map(m -> brief(holder(m, List.of()))).orElse(""));
    };
  }
  Optional<String> href(DocLink link){
    return switch (link){
      case DocLink.Ambiguous a -> Optional.of("#"+pageId(a));
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
  String typeTitle(TypeDoc t){
    return toStr.typeName(t.main().name())+Join.of(t.main().bs().stream().map(B::compactToString), "[", ",", "]", "");
  }
  String typeLink(T.C c){
    return "<a href=\""+h(typeHref(c.name()))+"\">"+h(toStr.typeName(c))+"</a>";
  }
  String typeHref(TName n){ return prefix(n.pkgName())+"#"+typeId(n); }
  String methodHref(TName owner, Sig s){ return prefix(owner.pkgName())+"#"+methodId(owner, s); }
  String prefix(String pkg){
    if (pkg.equals(pkgName)){ return ""; }
    if (pkg.equals("base")){ return baseDocLocation.map(p -> p.toUri().toString()).orElse("_base.html"); }
    return "_"+pkg+".html";
  }
  static String groupId(String group){ return "group-"+id(group); }
  static String typeId(TName n){
    return "type-"+id(n.s())+"-"+n.arity();
  }
  static String methodId(TName owner, Sig s){
    return "method-"+id(owner.s())+"-"+owner.arity()+"-"+id(s.m().s())+"-"+s.m().arity();
  }
  private static final Pattern notPlain= Pattern.compile("[^a-zA-Z0-9]");
  static String id(String s){ return notPlain.matcher(s).replaceAll(m -> "_"+Integer.toHexString(m.group().codePointAt(0))+"_"); }
  static String wrap(String s){ return h(s).replace(",", ",<wbr>"); }
  static String h(String s){
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
