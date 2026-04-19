package docBuilder;

import static offensiveUtils.Require.*;

import java.net.URI;
import java.nio.file.Path;
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
import tools.Fs;
import tools.SourceOracle;
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
  List<DocOcc> docsForLiteral(Literal l){
    if (!l.infName()){ return docAt(l.pos()); }
    if (l.pos().line() == 0){ return List.of(); }
    return source(l.pos().fileName()).docsAt(l.pos(),false);
  }
  @Override public void visitDeclaredM(Literal owner, M m){
    assert nonNull(owner,m);
    assert m.sig().origin().equals(owner.name());
    type(owner).declared(methodPos(m),m,methodDocAt(owner,m),inheritedMethods(owner,m));
  }
  @Override public void visitImportedM(Literal owner, M m){
    assert nonNull(owner,m);
    assert !m.sig().origin().equals(owner.name());
    type(owner).imported(m);
  }
  List<MethodRef> inheritedMethods(Literal owner, M m){
    var res= new LinkedHashMap<MethodRefKey,MethodRef>();
    owner.cs().stream()
      .map(c->literal(c.name()))
      .flatMap(Optional::stream)
      .flatMap(sup->matchingMethods(sup,m))
      .forEach(r->res.putIfAbsent(MethodRefKey.of(r),r));
    return List.copyOf(res.values());
  }
  Stream<MethodRef> matchingMethods(Literal sup, M m){
    return sup.ms().stream()
      .filter(sm->sameSelector(sm,m))
      .map(sm->new MethodRef(sm.sig().origin(),sm));
  }
  boolean sameSelector(M a, M b){
    return a.sig().m().s().equals(b.sig().m().s())
      && a.sig().m().arity() == b.sig().m().arity();
  }
  Optional<Literal> literal(TName n){
    var local= currentByName.get(n);
    if (local != null){ return Optional.of(local); }
    return Optional.ofNullable(other.__of(n));
  }
  @Override public void complete(){
    assert pkgName != null;
    assert htmlPath != null;
    Fs.writeUtf8(htmlPath,new HtmlDocRenderer(pkgName,uses,types).render());
  }

  TypeDoc type(Literal l){
    var res= typeBySrc.get(l.src());
    assert res != null: "Literal not registered: "+l.pos()+" "+l.name();
    return res;
  }

  Pos methodPos(M m){ return m.sig().span().pos(); }

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