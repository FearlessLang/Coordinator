package docBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import utils.Join;
import utils.OneOr;

record Scope(Literal owner, Optional<M> method){
  static Scope of(Literal owner){ return new Scope(owner, Optional.empty()); }
  static Scope of(Literal owner, M method){ return new Scope(owner, Optional.of(method)); }
}
record DocResolver(String pkgName, List<TypeDoc> types, OtherPackages other){
  Optional<DocLink> resolve(DocRef ref, Scope scope){
    return switch (ref){
      case DocRef.TypeName t -> resolveType(t);
      case DocRef.LocalName l -> resolveLocal(l, scope);
      case DocRef.MethodName m -> resolveMethod(m, scope);
    };
  }
  private Optional<DocLink> resolveLocal(DocRef.LocalName ref, Scope scope){
    if (!inScope(ref.name(), scope)){ return Optional.empty(); }
    return Optional.of(localType(ref.name(), scope)
      .<DocLink>map(n -> new DocLink.Resolved(Candidate.ofType(n)))
      .orElseGet(DocLink.NoLink::new));
  }
  private boolean inScope(String name, Scope scope){
    if (name.equals(scope.owner().thisName())){ return true; }
    return scope.method().map(m -> m.xs().contains(name)).orElse(false);
  }
  private Optional<TName> localType(String name, Scope scope){
    if (name.equals(scope.owner().thisName())){ return Optional.of(scope.owner().name()); }
    var m= scope.method().get();
    return m.sig().ts().get(m.xs().indexOf(name)) instanceof T.RCC rcc ? Optional.of(rcc.c().name()) : Optional.empty();
  }
  Optional<DocLink> resolveType(DocRef.TypeName ref){
    var cands= typeCandidates(ref).stream().map(Candidate::ofType).toList();
    return pack("Type "+ref.pkg().map(p -> p+".").orElse("")+ref.simpleName()+shape(ref.arity(), "[", "]"), cands);
  }
  private List<TName> typeCandidates(DocRef.TypeName ref){
    var pkg= ref.pkg();
    if (pkg.isEmpty() || pkg.get().equals(pkgName)){
      var local= matching(types.stream().map(TypeDoc::main).filter(l -> !l.infName()).map(Literal::name), ref);
      if (!local.isEmpty() || pkg.isPresent()){ return local; }
    }
    return matching(foreign().filter(n -> pkg.isEmpty() || n.pkgName().equals(pkg.get())), ref);
  }
  private Stream<TName> foreign(){
    return other.dom().stream().filter(n -> !other.__of(n).infName()).sorted(Comparator.comparing(TName::s).thenComparing(TName::arity));
  }
  private List<TName> matching(Stream<TName> names, DocRef.TypeName ref){
    return names
      .filter(n -> n.simpleName().equals(ref.simpleName()))
      .filter(n -> ref.arity().isEmpty() || n.arity()==ref.arity().getAsInt())
      .toList();
  }
  private Optional<DocLink> resolveMethod(DocRef.MethodName ref, Scope scope){
    if (ref.owner().isEmpty()){ return resolveBareSelector(ref.selector(), ref.arity()); }
    return switch (ref.owner().get()){
      case DocRef.LocalName l -> onLocal(l, ref, scope);
      case DocRef.TypeName t -> onOwners(t.simpleName(), typeCandidates(t), ref.selector(), ref.arity());
    };
  }
  private Optional<DocLink> onLocal(DocRef.LocalName l, DocRef.MethodName ref, Scope scope){
    if (!inScope(l.name(), scope)){ return Optional.empty(); }
    var owner= localType(l.name(), scope);
    if (owner.isEmpty()){ return Optional.of(new DocLink.NoLink()); }
    return onOwners(owner.get().simpleName(), List.of(owner.get()), ref.selector(), ref.arity());
  }
  private Optional<DocLink> onOwners(String shown, List<TName> owners, String selector, OptionalInt arity){
    var cands= owners.stream().flatMap(o -> methodCandidatesOn(o, selector, arity).stream()).toList();
    return pack(shown+selector+shape(arity, "(", ")"), cands);
  }
  private List<Candidate> methodCandidatesOn(TName owner, String selector, OptionalInt arity){
    var local= localTypeDoc(owner);
    if (local.isPresent()){
      return local.get().methods.stream()
        .filter(MethodDoc::visible)
        .filter(m -> named(m.main(), selector, arity))
        .gather(DistinctBy.<MethodDoc,String>of(m -> HtmlDocRenderer.methodId(owner, m.main())))
        .map(m -> Candidate.ofLocalMethod(owner, m))
        .toList();
    }
    return literalOf(owner).stream().flatMap(lit -> foreignCandidates(owner, declaredAndInherited(lit), selector, arity)).toList();
  }
  private Optional<TypeDoc> localTypeDoc(TName n){
    return OneOr.opt("type "+n, types.stream().filter(t -> !t.main().infName() && t.main().name().equals(n)));
  }
  private List<M> declaredAndInherited(Literal owner){
    var res= new ArrayList<>(owner.ms());
    owner.cs().forEach(c -> literalOf(c.name()).ifPresent(sup -> res.addAll(declaredAndInherited(sup))));
    return res;
  }
  private Optional<Literal> literalOf(TName n){
    return localTypeDoc(n).map(TypeDoc::main).or(() -> Optional.ofNullable(other.__of(n)));
  }
  private Optional<DocLink> resolveBareSelector(String selector, OptionalInt arity){
    var localCands= types.stream()
      .filter(TypeDoc::visible)
      .flatMap(t -> t.methods.stream()
        .filter(MethodDoc::visible)
        .filter(m -> named(m.main(), selector, arity))
        .map(m -> Candidate.ofLocalMethod(t.main().name(), m)));
    var foreignCands= foreign()
      .flatMap(n -> foreignCandidates(n, other.__of(n).ms(), selector, arity));
    return pack(selector+shape(arity, "(", ")"), Stream.concat(localCands, foreignCands).toList());
  }
  private Stream<Candidate> foreignCandidates(TName owner, List<M> ms, String selector, OptionalInt arity){
    return ms.stream()
      .filter(m -> named(m, selector, arity))
      .gather(DistinctBy.<M,Integer>of(m -> m.sig().m().arity()))
      .map(m -> new Candidate(owner, Optional.of(selector), OptionalInt.of(m.sig().m().arity()), Optional.empty()));
  }
  private static boolean named(M m, String selector, OptionalInt arity){
    return m.sig().m().s().equals(selector) && (arity.isEmpty() || m.sig().m().arity()==arity.getAsInt());
  }
  static String shape(OptionalInt arity, String open, String close){
    return arity.isEmpty() ? "" : Join.of(Collections.nCopies(arity.getAsInt(), "_"), open, ",", close, open+close);
  }
  private Optional<DocLink> pack(String title, List<Candidate> cands){
    if (cands.isEmpty()){ return Optional.empty(); }
    if (cands.size() == 1){ return Optional.of(new DocLink.Resolved(cands.getFirst())); }
    return Optional.of(new DocLink.Ambiguous(title, cands));
  }
}
