package docBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import core.E.Literal;
import core.M;
import core.OtherPackages;
import core.T;
import core.TName;
import utils.DistinctBy;

/// Where a doc comment is written: the type it documents, and the method when it
/// documents one. This is what makes "this" and the parameter names mean something.
record Scope(Literal owner, Optional<M> method){
  static Scope of(Literal owner){ return new Scope(owner, Optional.empty()); }
  static Scope of(Literal owner, M method){ return new Scope(owner, Optional.of(method)); }
}

/// Resolves a scanned DocRef to the declarations it could name.
final class DocResolver{
  DocResolver(String pkgName, List<TypeDoc> types, OtherPackages other){
    this.pkgName= pkgName;
    this.types= types;
    this.other= other;
  }
  final String pkgName;
  final List<TypeDoc> types;
  final OtherPackages other;

  Optional<DocLink> resolve(DocRef ref, Scope scope){
    if (ref instanceof DocRef.TypeName t){ return resolveType(t); }
    if (ref instanceof DocRef.LocalName l){ return resolveLocal(l, scope); }
    return resolveMethod((DocRef.MethodName)ref, scope);
  }

  //"this" and the method's parameters. A parameter typed by a type variable is in
  //scope but has no page to point at, so it is a reference with no link.
  private Optional<DocLink> resolveLocal(DocRef.LocalName ref, Scope scope){
    if (!inScope(ref.name(), scope)){ return Optional.empty(); }
    return Optional.of(localType(ref.name(), scope)
      .<DocLink>map(n->new DocLink.Resolved(Candidate.ofType(n)))
      .orElseGet(DocLink.NoLink::new));
  }

  private boolean inScope(String name, Scope scope){
    if (name.equals(scope.owner().thisName())){ return true; }
    return scope.method().map(m->m.xs().contains(name)).orElse(false);
  }

  private Optional<TName> localType(String name, Scope scope){
    if (name.equals(scope.owner().thisName())){ return Optional.of(scope.owner().name()); }
    var m= scope.method().get();
    var t= m.sig().ts().get(m.xs().indexOf(name));
    if (t instanceof T.RCC rcc){ return Optional.of(rcc.c().name()); }
    return Optional.empty();
  }

  Optional<DocLink> resolveType(DocRef.TypeName ref){
    var cands= typeCandidates(ref).stream().map(Candidate::ofType).toList();
    return pack("type-"+ref.simpleName(), "Type "+ref.simpleName(), cands);
  }

  //a name declared here always wins over a same-named one elsewhere; only when nothing
  //local matches do the other packages get a say, and then all of them do, so a valid
  //cross-package reference is a link (or a disambiguation) rather than an error.
  private List<TName> typeCandidates(DocRef.TypeName ref){
    var pkg= ref.pkg();
    if (pkg.isEmpty() || pkg.get().equals(pkgName)){
      var local= matching(localTypeNames().stream(), ref);
      if (!local.isEmpty() || pkg.isPresent()){ return local; }
    }
    return matching(other.dom().stream().filter(n->pkg.isEmpty() || n.pkgName().equals(pkg.get())), ref);
  }

  private List<TName> matching(Stream<TName> names, DocRef.TypeName ref){
    return names
      .filter(n->n.simpleName().equals(ref.simpleName()))
      .filter(n->ref.arity().isEmpty() || n.arity()==ref.arity().getAsInt())
      .toList();
  }

  private List<TName> localTypeNames(){
    return types.stream().map(TypeDoc::main).filter(l->!l.infName()).map(Literal::name).toList();
  }

  private Optional<DocLink> resolveMethod(DocRef.MethodName ref, Scope scope){
    if (ref.owner().isEmpty()){ return resolveBareSelector(ref.selector(), ref.arity()); }
    if (ref.owner().get() instanceof DocRef.LocalName l){ return onLocal(l, ref, scope); }
    return onType((DocRef.TypeName)ref.owner().get(), ref.selector(), ref.arity());
  }

  private Optional<DocLink> onLocal(DocRef.LocalName l, DocRef.MethodName ref, Scope scope){
    if (!inScope(l.name(), scope)){ return Optional.empty(); }
    var owner= localType(l.name(), scope);
    if (owner.isEmpty()){ return Optional.of(new DocLink.NoLink()); }
    return onOwners(owner.get().simpleName(), List.of(owner.get()), ref.selector(), ref.arity());
  }

  private Optional<DocLink> onType(DocRef.TypeName ownerRef, String selector, OptionalInt arity){
    return onOwners(ownerRef.simpleName(), typeCandidates(ownerRef), selector, arity);
  }

  private Optional<DocLink> onOwners(String shown, List<TName> owners, String selector, OptionalInt arity){
    var cands= owners.stream().flatMap(o->methodCandidatesOn(o,selector,arity).stream()).toList();
    return pack("method-"+shown+selector, shown+selector, cands);
  }

  private List<Candidate> methodCandidatesOn(TName owner, String selector, OptionalInt arity){
    var local= localTypeDoc(owner);
    if (local.isPresent()){
      return local.get().methods.stream()
        .filter(MethodDoc::visible)
        .filter(m->m.main().sig().m().s().equals(selector))
        .filter(m->arity.isEmpty() || m.main().sig().m().arity()==arity.getAsInt())
        .gather(DistinctBy.<MethodDoc,Integer>of(m->m.main().sig().m().arity()))
        .map(m->Candidate.ofLocalMethod(owner,m))
        .toList();
    }
    var lit= other.__of(owner);
    if (lit == null){ return List.of(); }
    return declaredAndInherited(lit).stream()
      .filter(m->m.sig().m().s().equals(selector))
      .filter(m->arity.isEmpty() || m.sig().m().arity()==arity.getAsInt())
      .gather(DistinctBy.<M,Integer>of(m->m.sig().m().arity()))
      .map(m->Candidate.ofForeignMethod(owner,selector,m.sig().m().arity()))
      .toList();
  }

  private Optional<TypeDoc> localTypeDoc(TName n){
    return types.stream().filter(t->!t.main().infName() && t.main().name().equals(n)).findFirst();
  }

  private List<M> declaredAndInherited(Literal owner){
    var res= new ArrayList<>(owner.ms());
    owner.cs().forEach(c->literalOf(c.name()).ifPresent(sup->res.addAll(declaredAndInherited(sup))));
    return res;
  }

  private Optional<Literal> literalOf(TName n){
    var local= localTypeDoc(n);
    if (local.isPresent()){ return Optional.of(local.get().main()); }
    return Optional.ofNullable(other.__of(n));
  }

  //every type that has the method, not only the one declaring it: this page answers
  //"who has a .foo?". Only what the renderer emits an anchor for can be a candidate,
  //since an invisible type or method has no id on the page to land on.
  private Optional<DocLink> resolveBareSelector(String selector, OptionalInt arity){
    var localCands= types.stream()
      .filter(TypeDoc::visible)
      .flatMap(t->t.methods.stream()
        .filter(MethodDoc::visible)
        .filter(m->m.main().sig().m().s().equals(selector))
        .filter(m->arity.isEmpty() || m.main().sig().m().arity()==arity.getAsInt())
        .map(m->Candidate.ofLocalMethod(t.main().name(),m)));
    var foreignCands= other.dom().stream()
      .flatMap(n->foreignCandidates(n,selector,arity));
    return pack("selector-"+selector, selector, Stream.concat(localCands,foreignCands).toList());
  }

  private Stream<Candidate> foreignCandidates(TName owner, String selector, OptionalInt arity){
    var lit= other.__of(owner);
    if (lit == null){ return Stream.empty(); }
    return lit.ms().stream()
      .filter(m->m.sig().m().s().equals(selector))
      .filter(m->arity.isEmpty() || m.sig().m().arity()==arity.getAsInt())
      .gather(DistinctBy.<M,Integer>of(m->m.sig().m().arity()))
      .map(m->Candidate.ofForeignMethod(owner,selector,m.sig().m().arity()));
  }

  private Optional<DocLink> pack(String pageId, String title, List<Candidate> cands){
    if (cands.isEmpty()){ return Optional.empty(); }
    if (cands.size() == 1){ return Optional.of(new DocLink.Resolved(cands.getFirst())); }
    return Optional.of(new DocLink.Ambiguous(pageId, title, cands));
  }
}
