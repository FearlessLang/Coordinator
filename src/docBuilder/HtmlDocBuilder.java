package docBuilder;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  public void visitAll(E e){ visitAll(e, Optional.empty()); }
  void visitAll(E e, Optional<Scope> outer){
    if (!(e instanceof Literal l)){ e.children().forEach(c -> visitAll(c, outer)); return; }
    visitLiteral(l, outer);
    l.ms().forEach(m -> m.e().ifPresent(b -> visitAll(b, Optional.of(new Scope(l, Optional.of(m), outer)))));
  }
  void visitLiteral(Literal l, Optional<Scope> outer){
    assert Require.nonNull(l, outer);
    var t= typeBySrc.get(l.src());
    if (t == null){
      t = new TypeDoc(l, outer, docsAt(l.pos(), true));
      typeBySrc.put(l.src(), t);
      types.add(t);
    }
    else{ t.addVariant(l); }
    for (var m : l.ms()){
      var p= m.sig().span().pos();
      var from= inheritedMethods(l, m);
      if (m.sig().origin().equals(l.name())){ t.declared(p, m, () -> docsAt(p, false), from); }
      else{ t.imported(written(l, m, from), from); }
    }
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
  List<DocOcc> docsAt(Pos p, boolean literal){
    if (p.line() == 0){ return List.of(); }
    return sources.computeIfAbsent(p.fileName(), u -> new SourceDocs(u, oracle.loadString(u))).docsAt(p, literal);
  }
}
