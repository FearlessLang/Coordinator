package docBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.E.Literal;
import core.M;
import core.MName;
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
record DocResolver(String pkgName, List<TypeDoc> types, OtherPackages other, Function<TName,String> names, Map<TName,TypeDoc> byName, List<TName> foreign, HashSet<TName> shown, HashMap<String,ArrayList<MethodDoc>> declared){
  DocResolver(String pkgName, List<TypeDoc> types, OtherPackages other, Function<TName,String> names){
    this(pkgName, types, other, names, types.stream().collect(Collectors.toUnmodifiableMap(t -> t.main().name(), t -> t)),
      other.dom().stream().sorted(Comparator.comparing(TName::s).thenComparing(TName::arity)).toList(), new HashSet<>(), new HashMap<>());
    Stream.concat(byName.keySet().stream(), foreign.stream()).filter(TName::isPublic).forEach(this::show);
    types.stream().flatMap(t -> t.methods.stream()).filter(m -> home(m).isEmpty())
      .forEach(m -> declared.computeIfAbsent(m.main().sig().m().s(), _ -> new ArrayList<>()).add(m));
  }
  private void show(TName n){
    var l= localTypeDoc(n).map(TypeDoc::main).orElseGet(() -> other.__of(n));
    if (l == null || !shown.add(n)){ return; }
    l.ms().stream().flatMap(m -> HtmlDocRenderer.typeNames(m.sig())).forEach(this::show);
  }
  boolean shows(MethodRef r){
    var origin= r.method().sig().origin();
    return shown.contains(r.provider().name()) && (!shown.contains(origin) || origin.equals(r.provider().name()));
  }
  Optional<MethodRef> home(MethodDoc m){
    return m.inheritedFrom.stream().filter(r -> shows(r) && r.method().sig().origin().equals(m.main().sig().origin())).findFirst();
  }
  Optional<DocLink> resolve(DocRef ref, Scope scope){
    return switch (ref){
      case DocRef.TypeName t -> typeParameter(t, scope) ? Optional.of(new DocLink.NoLink()) : resolveType(t);
      case DocRef.LocalName l -> resolveLocal(l, scope);
      case DocRef.MethodName m -> resolveMethod(m, scope);
    };
  }
  private Optional<DocLink> resolveLocal(DocRef.LocalName ref, Scope scope){
    return scope.localType(ref.name()).map(t -> t.filter(shown::contains)
      .<DocLink>map(n -> new DocLink.Resolved(new Candidate.Type(n)))
      .orElseGet(DocLink.NoLink::new));
  }
  private boolean typeParameter(DocRef.TypeName t, Scope scope){
    return t.pkg().isEmpty() && t.arity().isEmpty() && scope.bindsTypeParameter(t.simpleName());
  }
  Optional<DocLink> resolveType(DocRef.TypeName ref){
    var cands= typeCandidates(ref).stream().sorted(Comparator.comparingInt(TName::arity)).gather(DistinctBy.<TName,String>of(TName::s)).map(Candidate.Type::new).toList();
    return pack("Type "+shown(ref), cands);
  }
  private static String shown(DocRef.TypeName t){ return t.pkg().map(p -> p+".").orElse("")+t.simpleName()+shape(t.arity(), "[", "]"); }
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
  Directive directive(DocOcc d, Scope scope){
    var named= namedGroup.matcher(d.text());
    if (named.matches()){ return new Directive.Named(d, named.group(1)); }
    var under= underType.matcher(d.text());
    if (!under.matches()){ return new Directive.Broken(d, notADirective); }
    var ref= DocRefScanner.wholeRef(under.group(1));
    if (!(ref.orElse(null) instanceof DocRef.TypeName t)){ return new Directive.Broken(d, notAType.formatted(under.group(1), ref.orElse(null) instanceof DocRef.MethodName ? "names a method, not a type" : "does not name a type")); }
    if (typeParameter(t, scope)){ return new Directive.Broken(d, typeParameterParent.formatted(t.simpleName())); }
    var cands= typeCandidates(t);
    if (cands.isEmpty()){ return new Directive.Broken(d, unresolved(t, scope).orElse("There is no type \""+shown(t)+"\".")); }
    var parent= cands.getFirst();
    if (!byName.containsKey(parent)){ return new Directive.Broken(d, foreignParent.formatted(named(parent), parent.pkgName(), pkgName)); }
    if (!shown.contains(parent)){ return new Directive.Broken(d, privateParent.formatted(named(parent))); }
    return new Directive.Under(d, parent.simpleName());
  }
  String named(TName n){ return "\""+title(n)+(n.arity()==0 ? "" : shape(OptionalInt.of(n.arity()), "[", "]"))+"\""; }
  private static final Pattern namedGroup= Pattern.compile("Group \"([^\" ](?:[^\"]*[^\" ])?)\"");
  private static final Pattern underType= Pattern.compile("Group `([^`]+)`");
  static final String notADirective=
    "This is not a group directive.\n"
   +"A //@ line is either //@Group \"Name\", to put the type it documents in the\n"
   +"group Name, or //@Group `Type`, to put the type it documents under the type Type.\n"
   +"A group name is not empty, and does not start or end with a space.\n"
   +"Nothing follows the closing \" or `, not even a space.";
  static final String notAType=
    "\"%s\" %s.\n"
   +"In a //@Group `Type` line, the reference must name a type of this package.";
  static final String typeParameterParent=
    "\"%s\" is a generic type parameter.\n"
   +"In a //@Group `Type` line, the reference must name a type of this package.";
  static final String foreignParent=
    "%s is a type of package %s.\n"
   +"A //@Group line can only put a type under a type of the same package, %s.";
  static final String shownPrivate=
    "The documentation shows a private type only when a method of a type that the\n"
   +"documentation shows has that private type in its signature.\n";
  static final String privateParent=
    "The documentation does not show the private type %s.\n"
   +shownPrivate
   +"A //@Group line can only put a type under a type that the documentation shows.";
  static String choices(List<String> fs){ return Join.of(fs.subList(0, fs.size()-1), "", ", ", " and ", "")+fs.getLast(); }
  private Optional<DocLink> resolveMethod(DocRef.MethodName ref, Scope scope){
    if (ref.owner().isEmpty()){ return resolveBareSelector(ref.selector(), ref.arity()); }
    var o= owners(ref.owner().get(), scope);
    var cands= o.names().stream().flatMap(n -> methodCandidatesOn(n, ref.selector(), ref.arity()).stream()).toList();
    return pack(o.title()+ref.selector()+shape(ref.arity(), "(", ")"), cands);
  }
  private record Owners(String title, List<TName> names){}
  private Owners owners(DocRef.Receiver r, Scope scope){
    return switch (r){
      case DocRef.LocalName l -> scope.localType(l.name()).flatMap(t -> t).map(o -> new Owners(title(o), List.of(o))).orElse(new Owners("", List.of()));
      case DocRef.TypeName t -> new Owners(shown(t), typeParameter(t, scope) ? List.of() : typeCandidates(t));
    };
  }
  Optional<String> unresolved(DocRef ref, Scope scope){
    return switch (ref){
      case DocRef.LocalName _ -> Optional.empty();
      case DocRef.TypeName t -> Optional.of(typeCandidates(new DocRef.TypeName(t.pkg(), t.simpleName(), OptionalInt.empty()))).filter(ns -> !ns.isEmpty())
        .map(ns -> others("There is no type \""+shown(t)+"\".", "type", ns.stream().map(n -> shown(new DocRef.TypeName(t.pkg(), t.simpleName(), n.arity()==0 ? OptionalInt.empty() : OptionalInt.of(n.arity())))), "generic type parameters"));
      case DocRef.MethodName(var owner, var selector, var arity) when owner.isEmpty() -> Optional.of(bareCandidates(selector, OptionalInt.empty())).filter(cs -> !cs.isEmpty())
        .map(cs -> others("No type has a method \""+selector+shape(arity, "(", ")")+"\".", "method", cs.stream().map(c -> title(c.owner())+shape(c.sig().m())), "arguments"));
      case DocRef.MethodName(var owner, var selector, var arity) -> onOwner(owner.get(), selector, arity, scope);
    };
  }
  private Optional<String> onOwner(DocRef.Receiver r, String selector, OptionalInt arity, Scope scope){
    var o= owners(r, scope);
    var noMethods= " is a generic type parameter, and a generic type parameter has no methods.";
    if (o.names().isEmpty()){
      return switch (r){
        case DocRef.TypeName t when typeParameter(t, scope) -> Optional.of("\""+t.simpleName()+"\""+noMethods);
        case DocRef.LocalName l when scope.localType(l.name()).isPresent() -> Optional.of("The type of \""+l.name()+"\""+noMethods);
        default -> unresolved(r, scope);
      };
    }
    var forms= o.names().stream().flatMap(n -> methodCandidatesOn(n, selector, OptionalInt.empty()).stream()).map(c -> o.title()+shape(c.sig().m()));
    return Optional.of(others("Type \""+o.title()+"\" has no method \""+selector+shape(arity, "(", ")")+"\".", "method", forms, "arguments"));
  }
  private static String others(String missing, String kind, Stream<String> forms, String counted){
    var fs= forms.map(f -> "\""+f+"\"").distinct().sorted(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())).toList();
    if (fs.isEmpty()){ return missing; }
    return missing+"\n"+(fs.size()==1 ? "There is a "+kind+" " : "There are "+kind+"s ")+choices(fs)+", but with a different number of "+counted+".";
  }
  private String title(TName n){
    return localTypeDoc(n).map(t -> t.main().infName() ? anonTitle(t.main()) : n.simpleName()).orElseGet(() -> names.apply(n));
  }
  private static String anonTitle(Literal l){ return "Anon@"+Fs.fileNameWithoutExtension(l.pos().fileName().toString())+":"+l.pos().line(); }
  private List<Candidate.Method> methodCandidatesOn(TName owner, String selector, OptionalInt arity){
    var local= localTypeDoc(owner);
    if (local.isPresent()){
      return local.get().methods.stream()
        .filter(m -> named(m.main(), selector, arity))
        .gather(DistinctBy.<MethodDoc,String>of(m -> HtmlDocRenderer.methodId(owner, m.main().sig())))
        .map(m -> Candidate.Method.ofLocal(owner, m))
        .toList();
    }
    return foreignCandidates(owner, other.__of(owner).ms(), selector, arity).toList();
  }
  Optional<TypeDoc> localTypeDoc(TName n){ return Optional.ofNullable(byName.get(n)); }
  private Optional<DocLink> resolveBareSelector(String selector, OptionalInt arity){
    return pack(selector+shape(arity, "(", ")"), bareCandidates(selector, arity));
  }
  private List<Candidate.Method> bareCandidates(String selector, OptionalInt arity){
    var local= declared.getOrDefault(selector, new ArrayList<>()).stream()
      .filter(m -> named(m.main(), selector, arity))
      .gather(DistinctBy.<MethodDoc,String>of(m -> HtmlDocRenderer.methodId(m.owner.main().name(), m.main().sig())))
      .map(m -> Candidate.Method.ofLocal(m.owner.main().name(), m))
      .toList();
    var ours= local.stream().filter(c -> shown.contains(c.owner())).toList();
    if (!ours.isEmpty()){ return ours; }
    var others= foreign.stream()
      .filter(shown::contains)
      .flatMap(n -> foreignCandidates(n, other.__of(n).ms().stream().filter(m -> offers(n, m.sig().origin())).toList(), selector, arity))
      .toList();
    return others.isEmpty() ? local : others;
  }
  private boolean offers(TName n, TName origin){
    return origin.equals(n) || !shown.contains(origin) && origin.pkgName().equals(n.pkgName());
  }
  private Stream<Candidate.Method> foreignCandidates(TName owner, List<M> ms, String selector, OptionalInt arity){
    return ms.stream()
      .filter(m -> named(m, selector, arity))
      .gather(DistinctBy.<M,String>of(m -> HtmlDocRenderer.methodId(owner, m.sig())))
      .map(m -> new Candidate.Method(owner, m.sig(), Optional.empty()));
  }
  private static boolean named(M m, String selector, OptionalInt arity){
    return m.sig().m().s().equals(selector) && (arity.isEmpty() || m.sig().m().arity()==arity.getAsInt());
  }
  static String shape(MName m){ return m.s()+shape(OptionalInt.of(m.arity()), "(", ")"); }
  static String shape(OptionalInt arity, String open, String close){
    return arity.isEmpty() ? "" : Join.of(Collections.nCopies(arity.getAsInt(), "_"), open, ",", close, open+close);
  }
  private Optional<DocLink> pack(String title, List<? extends Candidate> cands){
    if (cands.isEmpty()){ return Optional.empty(); }
    if (!cands.stream().map(Candidate::owner).allMatch(shown::contains)){ return Optional.of(new DocLink.NoLink()); }
    if (cands.size() == 1){ return Optional.of(new DocLink.Resolved(cands.getFirst())); }
    return Optional.of(new DocLink.Ambiguous(title, List.copyOf(cands)));
  }
}
