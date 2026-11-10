package docBuilder;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Consumer;

import core.E;
import core.E.Literal;
import core.M;
import core.OtherPackages;
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
import utils.Pos;

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
      t = new TypeDoc(l, docsAt(l.pos(), true));
      t.outer= outer;
      typeBySrc.put(l.src(), t);
      types.add(t);
    }
    else{ t.addVariant(l); }
    for (var m : l.ms()){
      var p= m.sig().span().pos();
      if (m.sig().origin().equals(l.name())){ t.declared(p, m, docsAt(p, false), inheritedMethods(l, m)); }
      else{ t.imported(m, inheritedMethods(l, m)); }
    }
  }
  public List<TestSource> complete(Function<TName,String> pkgNames, boolean testable, Consumer<Runnable> writes){
    var resolver= new DocResolver(pkgName, types, other, pkgNames);
    var spans= new HashMap<DocOcc,Map<Integer,DocLink>>();
    oracle.allFiles().forEach(f -> sources.computeIfAbsent(f.fearURI(), u -> new SourceDocs(u, f.loadString())));
    var orphans= sources.values().stream().flatMap(s -> s.orphanRuns().stream()).map(this::orphan);
    var misplaced= Stream.concat(orphans, sources.values().stream().flatMap(s -> s.ambiguousInline.stream()).map(this::ambiguousInline)).toList();
    if (!misplaced.isEmpty()){ throw Report.docMisplaced(misplaced); }
    var problems= new ArrayList<String>();
    types.forEach(t -> linkType(resolver, t, spans, problems));
    if (!problems.isEmpty()){ throw Report.docReferences(problems); }
    var renderer= new HtmlDocRenderer(DocNames.uses(Stream.concat(currentByName.keySet().stream(), other.dom().stream()), pkgNames), resolver, spans, baseDocLocation);
    var html= renderer.render();
    var txt= renderer.renderText();
    writes.accept(() -> { Fs.writeUtf8(htmlPath, html); Fs.writeUtf8(htmlPath.resolveSibling(pkgName+".txt"), txt); });
    if (!generatesTests || !testable){ return List.of(); }
    return tests(renderer, pkgNames);
  }
  List<TestSource> tests(HtmlDocRenderer renderer, Function<TName,String> pkgNames){
    var names= renderer.testNames();
    var declared= types.stream().map(TypeDoc::main).collect(Collectors.toMap(l -> l.name().simpleName(), l -> l, (a,_) -> a));
    if (names.perType().isEmpty() || declared.containsKey(names.top())){ return List.of(); }
    names.perType().stream().filter(declared::containsKey).findFirst()
      .ifPresent(n -> { throw Report.generatedTestNameReserved(reservedNameProblem(declared.get(n)), names.top()); });
    return renderer.renderTests(pkgNames);
  }
  String reservedNameProblem(Literal owner){
    var p= owner.pos();
    return message(new Span(p.fileName(), p.line(), p.column(), p.line(), p.column()+owner.name().simpleName().length()-1), "This name is reserved for an auto-generated test suite.");
  }
  String message(Span span, String msg){
    return Message.of(oracle::loadString, List.of(new Frame("the documentation of package "+pkgName, span)), msg);
  }
  void linkType(DocResolver resolver, TypeDoc t, HashMap<DocOcc,Map<Integer,DocLink>> spans, ArrayList<String> problems){
    linkGroup(resolver, new Scope(t.main(), Optional.empty(), t.outer), t.docs, spans, problems);
    t.methods.forEach(m -> linkGroup(resolver, new Scope(t.main(), Optional.of(m.main()), t.outer), m.docs, spans, problems));
  }
  //one group per declaration, carrying the scope its comment is written in: a fenced
  //``` ... ``` block never spans declarations, so "inside a fence" resets per group.
  void linkGroup(DocResolver resolver, Scope scope, List<DocOcc> docs, HashMap<DocOcc,Map<Integer,DocLink>> spans,
      ArrayList<String> problems){
    var inFence= false;
    for (var occ : docs){
      if (occ.fence()){ inFence = !inFence; continue; }
      //a //> line is real Fearless code, not prose: any backtick in it is a genuine
      //raw string literal, never doc-comment markup, so it is never reference-checked.
      if (inFence || occ.kind()!=DocKind.Prose){ continue; }
      link(resolver, occ, scope, spans, problems);
    }
  }
  void link(DocResolver resolver, DocOcc occ, Scope scope,
      HashMap<DocOcc,Map<Integer,DocLink>> spans, ArrayList<String> problems){
    var res= new HashMap<Integer,DocLink>();
    for (var sp : DocRefScanner.refSpans(occ.text())){
      var ref= DocRefScanner.wholeRef(occ.text(), sp);
      if (ref.isEmpty()){ problems.add(problem(occ, sp, notAName)); continue; }
      var link= resolver.resolve(ref.get(), scope);
      if (link.isEmpty()){ problems.add(problem(occ, sp, noSuchName)); continue; }
      res.put(sp.start(), link.get());
    }
    spans.put(occ, res);
  }
  //documentation that reached no declaration is documentation nobody will ever read,
  //and the usual cause is a /// or //> written with one mark too few, which ends the
  //block silently. Reported per run of lines, at the run.
  String orphan(List<DocOcc> run){
    var first= run.getFirst();
    var last= run.getLast();
    var below= sources.get(last.file()).lines.stream().skip(last.line()).filter(l -> !l.isBlank()).findFirst().orElse("");
    var msg= first.inline() ? trailingNotAttached : below.strip().startsWith("//") ? notAttached : nothingDeclaredBelow;
    return message(new Span(first.file(), first.line(), first.column(),
      last.line(), last.textColumn()+last.text().length()), msg);
  }
  String ambiguousInline(DocOcc occ){
    return message(new Span(occ.file(), occ.line(), occ.textColumn(), occ.line(), occ.textColumn()+occ.text().length()), ambiguousTrailingDoc);
  }
  static final String notAttached=
    "This documentation is attached to nothing.\n"
   +"A /// or //> block documents the declaration right below it, and nothing else\n"
   +"may come between them. A line starting with // ends the block, so check whether\n"
   +"one of these was meant to start with /// instead.";
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
  String problem(DocOcc occ, DocRefScanner.CodeSpan sp, String msg){
    return message(new Span(occ.file(), occ.line(), occ.textColumn()+sp.start(),
      occ.line(), occ.textColumn()+sp.end()-1), msg);
  }
  TypeDoc type(Literal l){ return Objects.requireNonNull(typeBySrc.get(l.src())); }
  //owner.cs() is fully flattened, so an overridden ancestor still gets its own entry here:
  //by design, "From:" shows every provider along the chain, shadowed ones included.
  List<MethodRef> inheritedMethods(Literal owner, M m){
    return owner.cs().stream()
      .flatMap(c -> Stream.ofNullable(currentByName.getOrDefault(c.name(), other.__of(c.name()))).flatMap(sup -> matchingMethods(c, sup, m)))
      .toList();
  }
  Stream<MethodRef> matchingMethods(T.C provider, Literal sup, M m){
    return sup.ms().stream()
      .filter(sm -> sm.sig().rc()==m.sig().rc() && sm.sig().m().equals(m.sig().m()))
      .map(sm -> new MethodRef(provider.name(), Optional.of(provider), sm));
  }
  List<DocOcc> docsAt(Pos p, boolean literal){
    if (p.line() == 0){ return List.of(); }
    return sources.computeIfAbsent(p.fileName(), u -> new SourceDocs(u, oracle.loadString(u))).docsAt(p, literal);
  }
}
