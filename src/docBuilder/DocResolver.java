package docBuilder;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.E.Literal;
import core.M;
import core.OtherPackages;
import core.T;
import core.TName;
import tools.Fs;
import utils.DistinctBy;
import utils.Join;
import utils.OneOr;

record Scope(Literal owner, Optional<M> method, Optional<Scope> outer){
  boolean bindsTypeParameter(String x){
    var bs= Stream.concat(owner.bs().stream(), method.stream().flatMap(m -> m.sig().bs().stream()));
    return bs.anyMatch(b -> b.x().equals(x)) || outer.filter(o -> o.bindsTypeParameter(x)).isPresent();
  }
  Optional<Optional<TName>> localType(String name){
    if (name.equals(owner.thisName())){ return Optional.of(Optional.of(owner.name())); }
    var m= method.filter(mi -> mi.names().contains(name));
    if (m.isEmpty()){ return outer.flatMap(o -> o.localType(name)); }
    return Optional.of(m.get().sig().ts().get(m.get().names().indexOf(name)) instanceof T.RCC rcc ? Optional.of(rcc.c().name()) : Optional.empty());
  }
}
record DocResolver(String pkgName, List<TypeDoc> types, OtherPackages other, Function<TName,String> names, Map<TName,TypeDoc> byName, List<TName> foreign, Map<String,List<MethodDoc>> declared, Map<String,Long> anonLines){
  DocResolver(String pkgName, List<TypeDoc> types, OtherPackages other, Function<TName,String> names){
    this(pkgName, types, other, names, types.stream().collect(Collectors.toUnmodifiableMap(t -> t.main().name(), t -> t)),
      other.dom().stream().sorted(Comparator.comparing(TName::s).thenComparing(TName::arity)).toList(),
      types.stream().filter(TypeDoc::visible).flatMap(t -> t.methods.stream()).filter(m -> m.declared).collect(Collectors.groupingBy(m -> m.main().sig().m().s())),
      types.stream().filter(TypeDoc::visible).map(TypeDoc::main).filter(Literal::infName).collect(Collectors.groupingBy(DocResolver::fileLine, Collectors.counting())));
  }
  static String fileLine(Literal l){ return Fs.fileNameWithoutExtension(l.pos().fileName().toString())+":"+l.pos().line(); }
  String anonTitle(Literal l){ return "Anon@"+(anonLines.getOrDefault(fileLine(l), 0L)>1 ? l.pos() : fileLine(l)); }
  Optional<DocLink> resolve(DocRef ref, Scope scope){
    return switch (ref){
      case DocRef.TypeName t -> typeParameter(t, scope) ? Optional.of(new DocLink.NoLink()) : resolveType(t);
      case DocRef.LocalName l -> resolveLocal(l, scope);
      case DocRef.MethodName m -> resolveMethod(m, scope);
    };
  }
  private Optional<DocLink> resolveLocal(DocRef.LocalName ref, Scope scope){
    return scope.localType(ref.name()).map(t -> t.filter(this::onPage)
      .<DocLink>map(n -> new DocLink.Resolved(Candidate.ofType(n)))
      .orElseGet(DocLink.NoLink::new));
  }
  private boolean typeParameter(DocRef.TypeName t, Scope scope){
    return t.pkg().isEmpty() && t.arity().isEmpty() && scope.bindsTypeParameter(t.simpleName());
  }
  Optional<DocLink> resolveType(DocRef.TypeName ref){
    var cands= typeCandidates(ref).stream().map(Candidate::ofType).toList();
    return pack("Type "+ref.pkg().map(p -> p+".").orElse("")+ref.simpleName()+shape(ref.arity(), "[", "]"), cands);
  }
  private List<TName> typeCandidates(DocRef.TypeName ref){
    var pkg= ref.pkg();
    if (pkg.isEmpty() || pkg.get().equals(pkgName)){
      var local= matching(types.stream().map(TypeDoc::main).filter(l -> !l.infName()).map(Literal::name), TName::simpleName, ref);
      if (!local.isEmpty() || pkg.isPresent()){ return local; }
      var used= matching(foreign.stream(), names, ref);
      if (!used.isEmpty()){ return used; }
    }
    return matching(foreign.stream().filter(n -> pkg.isEmpty() || written(n.pkgName()).equals(pkg.get())), TName::simpleName, ref);
  }
  String written(String pkg){
    return OneOr.opt("two names for "+pkg, other.virtualizationMap().getOrDefault(pkgName, Map.of()).entrySet().stream()
      .filter(e -> e.getValue().split(" ")[0].equals(pkg)).map(Map.Entry::getKey)).orElse(pkg);
  }
  private List<TName> matching(Stream<TName> ns, Function<TName,String> written, DocRef.TypeName ref){
    return ns
      .filter(n -> written.apply(n).equals(ref.simpleName()))
      .filter(n -> ref.arity().isEmpty() || n.arity()==ref.arity().getAsInt())
      .toList();
  }
  private Optional<DocLink> resolveMethod(DocRef.MethodName ref, Scope scope){
    if (ref.owner().isEmpty()){ return resolveBareSelector(ref.selector(), ref.arity()); }
    var o= owners(ref.owner().get(), scope);
    return onOwners(o.getKey(), o.getValue(), ref.selector(), ref.arity());
  }
  private Map.Entry<String,List<TName>> owners(DocRef.Receiver r, Scope scope){
    return switch (r){
      case DocRef.LocalName l -> scope.localType(l.name()).flatMap(t -> t).map(o -> Map.entry(title(o), List.of(o))).orElse(Map.entry("", List.of()));
      case DocRef.TypeName t -> Map.entry(t.simpleName(), typeCandidates(t));
    };
  }
  Optional<String> noSuchMethod(DocRef ref, Scope scope){
    if (!(ref instanceof DocRef.MethodName(var owner, var selector, var arity)) || owner.isEmpty()){ return Optional.empty(); }
    var o= owners(owner.get(), scope);
    if (o.getValue().isEmpty()){ return Optional.empty(); }
    var forms= o.getValue().stream().flatMap(n -> methodCandidatesOn(n, selector, OptionalInt.empty()).stream())
      .map(c -> "\""+o.getKey()+selector+shape(OptionalInt.of(c.sig().get().m().arity()), "(", ")")+"\"").distinct().sorted().toList();
    var missing= "Type \""+o.getKey()+"\" has no method \""+selector+shape(arity, "(", ")")+"\".";
    if (forms.isEmpty()){ return Optional.of(missing); }
    return Optional.of(missing+"\n"+(forms.size()==1 ? "There is a method " : "There are methods ")+Join.of(forms, "", " and ", "")+",\nbut with a different number of arguments.");
  }
  private String title(TName n){
    return localTypeDoc(n).map(TypeDoc::main).filter(Literal::infName).map(this::anonTitle).orElse(n.simpleName());
  }
  private Optional<DocLink> onOwners(String shown, List<TName> owners, String selector, OptionalInt arity){
    var cands= owners.stream().flatMap(o -> methodCandidatesOn(o, selector, arity).stream()).toList();
    return pack(shown+selector+shape(arity, "(", ")"), cands);
  }
  private List<Candidate> methodCandidatesOn(TName owner, String selector, OptionalInt arity){
    var local= localTypeDoc(owner);
    if (local.isPresent()){
      return local.get().methods.stream()
        .filter(m -> named(m.main(), selector, arity))
        .gather(DistinctBy.<MethodDoc,String>of(m -> HtmlDocRenderer.methodId(owner, m.main().sig())))
        .map(m -> m.visible() ? Candidate.ofLocalMethod(owner, m) : new Candidate(m.main().sig().origin(), Optional.of(m.main().sig()), Optional.empty()))
        .toList();
    }
    return foreignCandidates(owner, other.__of(owner).ms(), selector, arity).toList();
  }
  private boolean onPage(TName n){ return localTypeDoc(n).map(TypeDoc::visible).orElse(true); }
  Optional<TypeDoc> localTypeDoc(TName n){ return Optional.ofNullable(byName.get(n)); }
  private Optional<DocLink> resolveBareSelector(String selector, OptionalInt arity){
    var local= declared.getOrDefault(selector, List.of()).stream()
      .filter(m -> named(m.main(), selector, arity))
      .map(m -> Candidate.ofLocalMethod(m.owner.main().name(), m))
      .toList();
    var cands= !local.isEmpty() ? local : foreign.stream()
      .filter(TName::isPublic)
      .flatMap(n -> foreignCandidates(n, other.__of(n).ms().stream().filter(m -> offers(n, m.sig().origin())).toList(), selector, arity))
      .toList();
    return pack(selector+shape(arity, "(", ")"), cands);
  }
  private static boolean offers(TName n, TName origin){
    return origin.equals(n) || !origin.isPublic() && origin.pkgName().equals(n.pkgName());
  }
  private Stream<Candidate> foreignCandidates(TName owner, List<M> ms, String selector, OptionalInt arity){
    return ms.stream()
      .filter(m -> named(m, selector, arity))
      .gather(DistinctBy.<M,Integer>of(m -> m.sig().m().arity()))
      .map(m -> new Candidate(owner, Optional.of(m.sig()), Optional.empty()));
  }
  private static boolean named(M m, String selector, OptionalInt arity){
    return m.sig().m().s().equals(selector) && (arity.isEmpty() || m.sig().m().arity()==arity.getAsInt());
  }
  static String shape(OptionalInt arity, String open, String close){
    return arity.isEmpty() ? "" : Join.of(Collections.nCopies(arity.getAsInt(), "_"), open, ",", close, open+close);
  }
  private Optional<DocLink> pack(String title, List<Candidate> cands){
    if (cands.isEmpty()){ return Optional.empty(); }
    if (!cands.stream().map(Candidate::owner).allMatch(this::onPage)){ return Optional.of(new DocLink.NoLink()); }
    if (cands.size() == 1){ return Optional.of(new DocLink.Resolved(cands.getFirst())); }
    return Optional.of(new DocLink.Ambiguous(title, cands));
  }
}
