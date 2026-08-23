package docBuilder;

import static offensiveUtils.Require.*;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.*;
import core.E.*;
import metaParser.Frame;
import metaParser.Message;
import metaParser.Span;
import tools.Fs;
import tools.SourceOracle;
import userMessages.Report;
import utils.Pos;

public final class HtmlDocBuilder implements DocBuilder{
  public HtmlDocBuilder(SourceOracle oracle, OtherPackages other, List<Literal> core){
    assert nonNull(oracle,other,core);
    this.oracle= oracle;
    this.other= other;
    this.core= core;
    this.currentByName= core.stream().collect(Collectors.toUnmodifiableMap(Literal::name,l->l));
  }

  final SourceOracle oracle;
  final OtherPackages other;
  final List<Literal> core;
  final Map<TName,Literal> currentByName;

  String pkgName;
  Path htmlPath;
  Map<String,String> uses= Map.of();

  final List<TypeDoc> types= new java.util.ArrayList<>();
  final IdentityHashMap<Src,TypeDoc> typeBySrc= new IdentityHashMap<>();
  final Map<URI,SourceDocs> sources= new HashMap<>();

  @Override public void packageLocation(String pkgName, Path htmlPath){
    assert nonNull(pkgName,htmlPath);
    assert this.pkgName == null;
    this.pkgName= pkgName;
    this.htmlPath= htmlPath;
    this.uses= DocNames.uses(pkgName,core);
  }

  @Override public void visitLiteral(Literal l){
    assert nonNull(l);
    var t= typeBySrc.get(l.src());
    if (t == null){
      t= new TypeDoc(l,docsForLiteral(l));
      typeBySrc.put(l.src(),t);
      types.add(t);
    }
    else{ t.addVariant(l); }
    for (var m:l.ms()){
      if (m.sig().origin().equals(l.name())){ visitDeclaredM(l,m); }
      else{ visitImportedM(l,m); }
    }
  }

  @Override public void visitDeclaredM(Literal owner, M m){
    assert nonNull(owner,m);
    assert m.sig().origin().equals(owner.name());
    type(owner).declared(methodPos(m),m,methodDocAt(owner,m),inheritedMethods(owner,m));
  }

  @Override public void visitImportedM(Literal owner, M m){
    assert nonNull(owner,m);
    assert !m.sig().origin().equals(owner.name());
    type(owner).imported(m,inheritedMethods(owner,m));
  }

  @Override public void complete(){
    assert pkgName != null;
    assert htmlPath != null;
    var resolver= new DocResolver(pkgName,types,other);
    var spans= new IdentityHashMap<DocOcc,List<ResolvedSpan>>();
    var problems= new ArrayList<String>();
    docGroups().forEach(g->linkGroup(resolver,g,spans,problems));
    if (!problems.isEmpty()){ throw Report.docReferences(problems); }
    Fs.writeUtf8(htmlPath,new HtmlDocRenderer(pkgName,uses,types,other,spans).render());
  }

  //one group per declaration, carrying the scope its comment is written in: a fenced
  //``` ... ``` block never spans declarations, so "inside a fence" resets per group.
  record DocGroup(Scope scope, List<DocOcc> docs){}

  List<DocGroup> docGroups(){
    var res= new ArrayList<DocGroup>();
    types.forEach(t->{
      res.add(new DocGroup(Scope.of(t.main()), t.docs));
      t.methods.forEach(m->res.add(new DocGroup(Scope.of(t.main(), m.main()), m.docs)));
    });
    return res;
  }

  void linkGroup(DocResolver resolver, DocGroup group, Map<DocOcc,List<ResolvedSpan>> spans,
      List<String> problems){
    var inFence= false;
    for (var occ: group.docs()){
      if (occ.text().strip().equals("```")){ inFence= !inFence; continue; }
      //a //> line is real Fearless code, not prose: any backtick in it is a genuine
      //raw string literal, never doc-comment markup, so it is never reference-checked.
      if (inFence || occ.example()){ continue; }
      link(resolver,occ,group.scope(),spans,problems);
    }
  }

  void link(DocResolver resolver, DocOcc occ, Scope scope,
      Map<DocOcc,List<ResolvedSpan>> spans, List<String> problems){
    var found= DocRefScanner.refSpans(occ.text());
    if (found.isEmpty()){ return; }
    var res= new ArrayList<ResolvedSpan>();
    for (var sp: found){
      var ref= DocRefScanner.wholeRef(occ.text(),sp);
      if (ref.isEmpty()){ problems.add(problem(occ,sp,notAName)); continue; }
      var link= resolver.resolve(ref.get(),scope);
      if (link.isEmpty()){ problems.add(problem(occ,sp,noSuchName)); continue; }
      res.add(new ResolvedSpan(sp.start(),sp.end(),link.get()));
    }
    spans.put(occ,res);
  }

  static final String notAName=
    "This is written as a reference, but it is not a name.\n"
   +"Use two backticks or more to show it as code instead.";
  static final String noSuchName=
    "This names no type, no method, and nothing in scope where it is written.\n"
   +"Use two backticks or more to show it as code instead.";

  String problem(DocOcc occ, DocRefScanner.CodeSpan sp, String msg){
    var span= new Span(occ.file(), occ.line(), occ.textColumn()+sp.start(),
      occ.line(), occ.textColumn()+sp.end()-1);
    var frame= new Frame("the documentation of package "+pkgName, span);
    return Message.of(oracle::loadString, List.of(frame), msg);
  }

  TypeDoc type(Literal l){
    var res= typeBySrc.get(l.src());
    assert res != null: "Literal not registered: "+l.pos()+" "+l.name();
    return res;
  }

  Pos methodPos(M m){ return m.sig().span().pos(); }

  //owner.cs() is fully flattened, so an overridden ancestor still gets its own entry here:
  //by design, "From:" shows every provider along the chain, shadowed ones included.
  List<MethodRef> inheritedMethods(Literal owner, M m){
    var res= new LinkedHashMap<MethodRefKey,MethodRef>();
    owner.cs().stream()
      .flatMap(c->literal(c.name()).stream().flatMap(sup->matchingMethods(c,sup,m)))
      .forEach(r->res.putIfAbsent(MethodRefKey.of(r),r));
    return List.copyOf(res.values());
  }

  Stream<MethodRef> matchingMethods(T.C provider, Literal sup, M m){
    return sup.ms().stream()
      .filter(sm->sameMethod(sm,m))
      .map(sm->MethodRef.provider(provider,sm));
  }

  boolean sameMethod(M a, M b){
    return a.sig().rc() == b.sig().rc() && a.sig().m().equals(b.sig().m());
  }

  Optional<Literal> literal(TName n){
    var local= currentByName.get(n);
    if (local != null){ return Optional.of(local); }
    return Optional.ofNullable(other.__of(n));
  }

  List<DocOcc> docsForLiteral(Literal l){
    if (!l.infName()){ return docAt(l.pos()); }
    if (l.pos().line() == 0){ return List.of(); }
    return source(l.pos().fileName()).docsAt(l.pos(),false);
  }

  List<DocOcc> docAt(Pos pos){
    if (pos.line() == 0){ return List.of(); }
    return source(pos.fileName()).docsAt(pos,true);
  }

  List<DocOcc> methodDocAt(Literal owner, M m){
    var p= methodPos(m);
    if (p.line() == 0){ return List.of(); }
    return source(p.fileName()).docsAt(p,p.line() != owner.pos().line());
  }

  SourceDocs source(URI uri){
    return sources.computeIfAbsent(uri,u->new SourceDocs(u,oracle.loadString(u)));
  }
}