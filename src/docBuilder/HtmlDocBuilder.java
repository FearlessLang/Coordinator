package docBuilder;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.function.Consumer;

import core.B;
import core.E;
import core.E.Literal;
import core.M;
import core.OtherPackages;
import core.Sig;
import core.Src;
import core.T;
import core.TName;
import main.FrontendLogicMain.TestSource;
import metaParser.Frame;
import metaParser.Message;
import metaParser.Span;
import offensiveUtils.Require;
import tools.Fs;
import tools.SourceOracle;
import userMessages.Report;
import utils.Bug;
import utils.Join;
import utils.OneOr;
import utils.Pos;
import utils.Streams;

public final class HtmlDocBuilder{
  final Optional<Path> baseDocLocation;
  final SourceOracle oracle;
  final OtherPackages other;
  final Map<TName,Literal> currentByName;
  final String pkgName;
  final Path htmlPath;
  final boolean generatesTests;
  final ArrayList<TypeDoc> types= new ArrayList<>();
  final IdentityHashMap<Src,TypeDoc> typeBySrc= new IdentityHashMap<>();
  final LinkedHashMap<URI,SourceDocs> sources= new LinkedHashMap<>();
  final HashSet<String> namedLines= new HashSet<>();
  public HtmlDocBuilder(SourceOracle oracle, OtherPackages other, List<Literal> core, Optional<Path> baseDocLocation, String pkgName, Path htmlPath, boolean generatesTests){
    assert Require.nonNull(oracle, other, core, baseDocLocation, pkgName, htmlPath);
    this.oracle= oracle;
    this.other= other;
    this.baseDocLocation= baseDocLocation;
    this.pkgName= pkgName;
    this.htmlPath= htmlPath;
    this.generatesTests= generatesTests;
    currentByName= core.stream().collect(Collectors.toUnmodifiableMap(Literal::name, l -> l));
  }
  public void visitAll(E e){ addNamedLines(e); visitAll(e, Optional.empty()); }
  void addNamedLines(E e){
    if (e instanceof Literal l && !l.infName()){ namedLines.add(lineKey(l.pos())); }
    e.children().forEach(this::addNamedLines);
  }
  static String lineKey(Pos p){ return p.fileName()+":"+p.line(); }
  void visitAll(E e, Optional<Scope> outer){
    if (!(e instanceof Literal l)){ e.children().forEach(c -> visitAll(c, outer)); return; }
    visitLiteral(l, outer);
    l.ms().forEach(m -> m.e().ifPresent(b -> visitAll(b, Optional.of(new Scope(l, Optional.of(m), outer)))));
  }
  void visitLiteral(Literal l, Optional<Scope> outer){
    assert Require.nonNull(l, outer);
    var t= typeBySrc.get(l.src());
    var yields= l.infName() && namedLines.contains(lineKey(l.pos()));
    if (t == null){
      t = new TypeDoc(l, outer, yields ? List.of() : docsAt(anchor(l.pos(), outer), l.infName()));
      typeBySrc.put(l.src(), t);
      types.add(t);
    }
    else{ t.addVariant(l); }
    for (var m : l.ms()){
      var p= m.sig().span().pos();
      var from= inheritedMethods(l, m);
      var mute= yields && p.line() == l.pos().line();
      if (m.sig().origin().equals(l.name())){ t.declared(p, m, () -> mute ? List.of() : docsAt(p, false), from); }
      else{ t.imported(written(l, m, from), from); }
    }
  }
  static Pos anchor(Pos p, Optional<Scope> outer){
    return outer.filter(s -> s.owner().infName() && lineKey(s.owner().pos()).equals(lineKey(p))).map(s -> anchor(s.owner().pos(), s.outer())).orElse(p);
  }
  M written(Literal l, M m, List<MethodRef> from){
    var s= m.sig();
    var ws= OneOr.of("one origin", from.stream().filter(r -> r.provider().name().equals(s.origin())).map(r -> B.xs(r.method().sig().bs())).distinct());
    var xs= B.xs(s.bs());
    if (ws.equals(xs)){ return m; }
    var used= Stream.concat(Stream.concat(s.ts().stream(), Stream.of(s.ret())).flatMap(HtmlDocRenderer::typeNames), currentByName.keySet().stream()).map(TName::simpleName);
    if (Stream.concat(B.xs(l.bs()).stream(), used).anyMatch(ws::contains)){ return m; }
    var bs= Streams.zip(s.bs(), ws).map((b, w) -> new B(w, b.rcs())).toList();
    return m.withSig(new Sig(s.rc(), s.m(), bs, s.ts().stream().map(t -> renamed(t, xs, ws)).toList(), renamed(s.ret(), xs, ws), s.origin(), s.abs(), s.span()));
  }
  static T renamed(T t, List<String> xs, List<String> ws){
    return switch (t){
      case T.X x -> xs.contains(x.name()) ? new T.X(ws.get(xs.indexOf(x.name())), x.span()) : x;
      case T.RCX(var rc, var x) -> renamed(x, xs, ws).withRC(rc);
      case T.ReadImmX(var x) -> renamed(x, xs, ws).readImm();
      case T.RCC rcc -> rcc.withTs(rcc.c().ts().stream().map(ti -> renamed(ti, xs, ws)).toList());
    };
  }
  public List<TestSource> complete(Function<TName,String> pkgNames, boolean testable, Consumer<Runnable> writes){
    var resolver= new DocResolver(pkgName, types, other, pkgNames);
    oracle.allFiles().forEach(f -> sources.computeIfAbsent(f.fearURI(), u -> new SourceDocs(u, f.loadString())));
    var orphans= sources.values().stream().flatMap(s -> s.orphanRuns().stream()).map(this::orphan);
    var misplaced= Stream.concat(orphans, sources.values().stream().flatMap(s -> s.ambiguousTrailing().stream()).map(this::ambiguousTrailing)).toList();
    if (!misplaced.isEmpty()){ throw Report.docMisplaced(misplaced); }
    var markup= new ArrayList<String>();
    var problems= new ArrayList<String>();
    types.stream().flatMap(t -> Stream.<DeclarationDoc>concat(Stream.of(t), t.methods.stream())).forEach(d -> checkDeclaration(resolver, d, markup, problems));
    if (!markup.isEmpty()){ throw Report.docBackticks(markup); }
    if (!problems.isEmpty()){ throw Report.docReferences(problems); }
    var groups= checkGroups(resolver);
    if (!groups.isEmpty()){ throw Report.docGroups(groups); }
    var renderer= new HtmlDocRenderer(DocNames.uses(Stream.concat(currentByName.keySet().stream(), other.dom().stream()), pkgNames), resolver, baseDocLocation);
    var html= renderer.render();
    var txt= renderer.renderText();
    writes.accept(() -> { Fs.writeUtf8(htmlPath, html); Fs.writeUtf8(htmlPath.resolveSibling(pkgName+".txt"), txt); });
    if (!generatesTests || !testable){ return List.of(); }
    return tests(renderer, pkgNames);
  }
  List<TestSource> tests(HtmlDocRenderer renderer, Function<TName,String> pkgNames){
    var suites= renderer.suites();
    var declared= types.stream().map(TypeDoc::main).collect(Collectors.toMap(l -> l.name().simpleName(), l -> l, (a,_) -> a));
    if (suites.isEmpty() || declared.containsKey(renderer.topSuiteName())){ return List.of(); }
    suites.stream().map(HtmlDocRenderer.Suite::name).filter(declared::containsKey).findFirst()
      .ifPresent(n -> { throw Report.generatedTestNameReserved(reservedNameProblem(declared.get(n)), renderer.topSuiteName()); });
    return renderer.renderTests(suites, pkgNames);
  }
  String reservedNameProblem(Literal owner){
    var p= owner.pos();
    return message(new Span(p.fileName(), p.line(), p.column(), p.line(), p.column()+owner.name().simpleName().length()-1), "This name is reserved for an auto-generated test suite.");
  }
  String message(Span span, String msg){
    return Message.of(oracle::loadString, List.of(new Frame("the documentation of package "+pkgName, span)), msg);
  }
  void checkDeclaration(DocResolver resolver, DeclarationDoc d, ArrayList<String> markup, ArrayList<String> problems){
    HtmlDocRenderer.runs(d.docs, o -> o.kind().tested() || o.trailing()).stream()
      .map(r -> r.stream().filter(o -> o.kind()==DocKind.Fence).toList())
      .filter(fs -> fs.size()%2==1)
      .findFirst()
      .ifPresent(fs -> markup.add(problem(List.of(fs.getLast()), 0, fs.getLast().text().length()-1, unclosedBlock)));
    HtmlDocRenderer.paragraphs(d.visibleDocs()).forEach(p -> check(resolver, p, d.scope(), markup, problems));
  }
  void check(DocResolver resolver, List<DocOcc> p, Scope scope, ArrayList<String> markup, ArrayList<String> problems){
    var text= HtmlDocRenderer.joined(p);
    DocRefScanner.unclosed(text).forEach(r -> markup.add(problem(p, r.start(), r.end()-1, unclosedSpan)));
    for (var sp : DocRefScanner.refSpans(text)){
      if (text.substring(sp.start(), sp.end()).contains("\n")){ markup.add(problem(p, sp.start()-1, sp.end(), referenceOverLines)); continue; }
      var ref= DocRefScanner.wholeRef(text, sp);
      if (ref.isEmpty()){ problems.add(problem(p, sp.start(), sp.end()-1, notAName)); continue; }
      if (resolver.resolve(ref.get(), scope).isEmpty()){ problems.add(problem(p, sp.start(), sp.end()-1, resolver.unresolved(ref.get(), scope).orElse(noSuchName))); }
    }
  }
  List<String> checkGroups(DocResolver resolver){
    var problems= new ArrayList<String>();
    types.stream().flatMap(t -> t.methods.stream()).flatMap(m -> m.directives().stream()).forEach(d -> problems.add(line(d, onMethod)));
    var parents= new LinkedHashMap<TypeDoc,List<Directive.Under>>();
    var names= new HashMap<String,String>(Map.of("public", "public", "private", "private"));
    types.stream().filter(TypeDoc::visible).forEach(t -> parents.put(t, checkType(resolver, t, names, problems)));
    if (!problems.isEmpty()){ return problems; }
    var done= new HashSet<TypeDoc>();
    parents.keySet().forEach(t -> cycles(resolver, t, new ArrayList<>(), done, parents, problems));
    return problems;
  }
  List<Directive.Under> checkType(DocResolver resolver, TypeDoc t, HashMap<String,String> names, ArrayList<String> problems){
    t.docs.stream().dropWhile(o -> o.kind()==DocKind.Group).filter(o -> o.kind()==DocKind.Group).findFirst()
      .ifPresent(o -> problems.add(line(o, groupAfterDocs)));
    var ds= t.directives().stream().map(d -> resolver.directive(d, t.scope())).toList();
    ds.forEach(d -> checkDirective(resolver, t, d, problems));
    var seen= new HashSet<Object>();
    ds.stream().filter(d -> !seen.add(key(d))).forEach(d -> problems.add(line(d.at(), repeatedGroup)));
    ds.stream().<Directive.Named>mapMulti((d, out) -> { if (d instanceof Directive.Named n){ out.accept(n); } }).forEach(n -> checkCase(n, names, problems));
    var unders= ds.stream().<Directive.Under>mapMulti((d, out) -> { if (d instanceof Directive.Under u){ out.accept(u); } }).toList();
    var named= ds.stream().anyMatch(Directive.Named.class::isInstance);
    var onlyPrivate= t.main().name().isPublic() && !named && !unders.isEmpty() && unders.stream().noneMatch(u -> u.parent().main().name().isPublic());
    if (onlyPrivate){ problems.add(line(unders.getFirst().at(), publicUnderPrivate.formatted(resolver.named(t), Join.of(unders.stream().map(u -> resolver.named(u.parent())), "", ", ", "")))); }
    return unders;
  }
  static Object key(Directive d){
    return switch (d){
      case Directive.Named n -> n.group();
      case Directive.Under u -> u.parent();
      case Directive.Broken b -> b;
    };
  }
  void checkCase(Directive.Named n, HashMap<String,String> names, ArrayList<String> problems){
    var first= names.putIfAbsent(n.group().toLowerCase(Locale.ROOT), n.group());
    if (first != null && !first.equals(n.group())){ problems.add(line(n.at(), caseOnly.formatted(n.group(), first))); }
  }
  void checkDirective(DocResolver resolver, TypeDoc t, Directive d, ArrayList<String> problems){
    var isPublic= t.main().name().isPublic();
    var wrong= switch (d){
      case Directive.Broken b -> Optional.of(b.problem());
      case Directive.Named(_, var g) when g.equals("private") && isPublic -> Optional.of(publicInPrivate.formatted(resolver.named(t)));
      case Directive.Named(_, var g) when g.equals("public") && !isPublic -> Optional.of(privateInPublic.formatted(resolver.named(t)));
      case Directive.Named _, Directive.Under _ -> Optional.<String>empty();
    };
    wrong.ifPresent(w -> problems.add(line(d.at(), w)));
  }
  void cycles(DocResolver resolver, TypeDoc t, ArrayList<TypeDoc> path, HashSet<TypeDoc> done, Map<TypeDoc,List<Directive.Under>> parents, ArrayList<String> problems){
    if (done.contains(t)){ return; }
    var at= path.indexOf(t);
    if (at >= 0){ problems.add(cycle(resolver, path.subList(at, path.size()), parents)); return; }
    path.add(t);
    parents.get(t).forEach(u -> cycles(resolver, u.parent(), path, done, parents, problems));
    path.removeLast();
    done.add(t);
  }
  String cycle(DocResolver resolver, List<TypeDoc> ring, Map<TypeDoc,List<Directive.Under>> parents){
    var n= ring.size();
    var steps= IntStream.range(0, n).mapToObj(i -> "  "+resolver.named(ring.get(i))+" is under "+resolver.named(ring.get((i+1) % n)));
    var first= parents.get(ring.getFirst()).stream().filter(u -> u.parent() == ring.get(1 % n)).findFirst().orElseThrow();
    return line(first.at(), underItself.formatted(resolver.named(ring.getFirst()), Join.of(steps, "", "\n", "")));
  }
  String line(DocOcc d, String msg){
    return message(new Span(d.file(), d.line(), d.column(), d.line(), d.textColumn()+d.text().length()-1), msg);
  }
  String orphan(List<DocOcc> run){
    var first= run.getFirst();
    var last= run.getLast();
    var source= sources.get(last.file());
    var below= source.lines.stream().skip(last.line()).filter(l -> !l.isBlank()).findFirst().orElse("");
    var msg= first.trailing() ? trailingNotAttached : source.declarationBelow(last.line()) ? emptyLineEnds : below.strip().startsWith("//") ? notAttached : nothingDeclaredBelow;
    return message(new Span(first.file(), first.line(), first.column(),
      last.line(), last.textColumn()+last.text().length()), msg);
  }
  String ambiguousTrailing(DocOcc occ){
    return message(new Span(occ.file(), occ.line(), occ.textColumn(), occ.line(), occ.textColumn()+occ.text().length()), ambiguousTrailingDoc);
  }
  static final String notAttached=
    "This documentation is attached to nothing.\n"
   +"A /// or //> block documents the declaration right below it, and nothing else\n"
   +"may come between them. A line starting with // ends the block, so check whether\n"
   +"one of these was meant to start with /// instead.";
  static final String emptyLineEnds=
    "This documentation is attached to nothing.\n"
   +"A /// or //> block documents the declaration right below it, and an empty line\n"
   +"ends the block. Delete the empty line below this block, or write /// on it to\n"
   +"keep an empty line in the documentation.";
  static final String nothingDeclaredBelow=
    "This documentation is attached to nothing.\n"
   +"A /// or //> block documents the declaration right below it, but no declaration\n"
   +"starts right below this block. Move this block right above the declaration it\n"
   +"documents, or turn it into a plain // comment.";
  static final String trailingNotAttached=
    "This documentation is attached to nothing.\n"
   +"A /// or //> written after code documents the declaration that starts earlier\n"
   +"on the same line. No declaration starts earlier on this line, so move this\n"
   +"documentation to its own line, right above the declaration it documents.";
  static final String ambiguousTrailingDoc=
    "This documentation follows more than one declaration on the same line.\n"
   +"Move it directly after the one declaration it documents, or put each declaration\n"
   +"on its own line.";
  static final String onMethod=
    "This //@Group line documents a method.\n"
   +"A //@Group line puts a type in a group or under another type, and a method is\n"
   +"neither. Move this line above the declaration of the type.";
  static final String publicInPrivate=
    "%s is public, so it cannot be in the group \"private\".\n"
   +"The group \"private\" holds only private types: the types whose name starts with _.";
  static final String privateInPublic=
    "%s is private, so it cannot be in the group \"public\".\n"
   +"The group \"public\" holds only public types: the types whose name does not start with _.";
  static final String publicUnderPrivate=
    "%s is public, but every type it is under is private: %s.\n"
   +"A public type must be in a group, or under at least one public type.";
  static final String groupAfterDocs=
    "This //@Group line comes after other documentation of the same declaration.\n"
   +"Move every //@Group line above the first ///, //> and //- line of the documentation.";
  static final String repeatedGroup=
    "This //@Group line repeats an earlier //@Group line of the same type.\n"
   +"Delete this line.";
  static final String caseOnly=
    "The group \"%s\" differs from the group \"%s\" only by case.\n"
   +"Write the name of a group the same way every time.";
  static final String underItself=
    "These //@Group lines put %s under itself:\n"
   +"%s\n"
   +"A type cannot be under itself, not even through other types.";
  static final String notAName=
    "This is written as a reference, but it is not a name.\n"
   +"Use two backticks or more to show it as code instead.";
  static final String noSuchName=
    "This names no type, no method, and nothing in scope where it is written.\n"
   +"Use two backticks or more to show it as code instead.";
  static final String unclosedSpan=
    "This code span is never closed.\n"
   +"Backticks open a code span, and the next run of exactly as many backticks in the\n"
   +"same paragraph closes it. A paragraph ends at an empty doc line, at a //> line\n"
   +"and at a ``` line. To show a backtick as text, put it in a code span opened by\n"
   +"more backticks: `` ` `` shows one backtick.";
  static final String referenceOverLines=
    "This reference does not end on the line where it starts.\n"
   +"A single backtick marks a reference, and a reference is one name on one line.\n"
   +"Use two backticks or more to show code over several lines.";
  static final String unclosedBlock=
    "This code block is never closed.\n"
   +"A doc line starting with ``` opens a code block, and the next such line closes\n"
   +"it, within the same run of /// lines: a //> line, a //- line and the end of the\n"
   +"documentation all end that run. Close this code block with a ``` line before them.";
  String problem(List<DocOcc> p, int from, int to, String msg){
    var a= locate(p, from);
    var b= locate(p, to);
    return message(new Span(a.fileName(), a.line(), a.column(), b.line(), b.column()), msg);
  }
  static Pos locate(List<DocOcc> p, int offset){
    var at= offset;
    for (var o : p){
      if (at<o.text().length()){ return new Pos(o.file(), o.line(), o.textColumn()+at); }
      at -= o.text().length()+1;
    }
    throw Bug.unreachable();
  }
  List<MethodRef> inheritedMethods(Literal owner, M m){
    return owner.cs().stream()
      .flatMap(c -> Stream.ofNullable(currentByName.getOrDefault(c.name(), other.__of(c.name()))).flatMap(sup -> matchingMethods(c, sup, m)))
      .toList();
  }
  Stream<MethodRef> matchingMethods(T.C provider, Literal sup, M m){
    return sup.ms().stream()
      .filter(sm -> sm.sig().rc()==m.sig().rc() && sm.sig().m().equals(m.sig().m()))
      .map(sm -> new MethodRef(provider, sm));
  }
  List<DocOcc> docsAt(Pos p, boolean anonymous){
    if (p.line() == 0){ return List.of(); }
    return sources.computeIfAbsent(p.fileName(), u -> new SourceDocs(u, oracle.loadString(u))).docsAt(p, anonymous);
  }
}
