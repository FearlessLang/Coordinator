package docBuilder;

import java.net.URI;
import java.util.*;

import core.*;
import core.E.Literal;
import utils.Pos;

final class TypeDoc{
  TypeDoc(Literal l, List<DocOcc> docs){
    this.docs= docs;
    variants.add(l);
  }

  final List<Literal> variants= new ArrayList<>();
  final List<DocOcc> docs;
  final List<MethodDoc> methods= new ArrayList<>();
  final Map<DeclaredMethodKey,MethodDoc> declaredByKey= new LinkedHashMap<>();
  final Map<ImportedKey,MethodDoc> importedByKey= new LinkedHashMap<>();

  Literal main(){ return variants.getFirst(); }

  boolean visible(){ return !main().infName() || hasDocs(); }

  boolean hasDocs(){
    return !docs.isEmpty() || methods.stream().anyMatch(MethodDoc::hasDocs);
  }

  void addVariant(Literal l){
    assert variants.stream().noneMatch(v->v.name().equals(l.name())):
      "Repeated literal source with same name: "+l.pos()+" "+l.name();
    variants.add(l);
  }

  void declared(Pos pos, M m, List<DocOcc> docs, List<MethodRef> inheritedFrom){
    oneOf(declaredByKey, new DeclaredMethodKey(pos.fileName(),pos.line(),pos.column(),m.sig().m()), true, docs, inheritedFrom).add(m);
  }

  void imported(M m, List<MethodRef> from){
    oneOf(importedByKey, new ImportedKey(m.sig().origin(),m.sig().rc(),m.sig().m()), false, List.of(), from).add(m);
  }

  private <K> MethodDoc oneOf(Map<K,MethodDoc> byKey, K k, boolean declared, List<DocOcc> docs, List<MethodRef> inheritedFrom){
    var d= byKey.get(k);
    if (d == null){
      d= new MethodDoc(this,declared,docs,inheritedFrom);
      byKey.put(k,d);
      methods.add(d);
    }
    else{ d.addInheritedFrom(inheritedFrom); }
    return d;
  }
}

final class MethodDoc{
  MethodDoc(TypeDoc owner, boolean declared, List<DocOcc> docs, List<MethodRef> inheritedFrom){
    this.owner= owner;
    this.declared= declared;
    this.docs= docs;
    addInheritedFrom(inheritedFrom);
  }

  final TypeDoc owner;
  final boolean declared;
  final List<DocOcc> docs;
  final List<M> variants= new ArrayList<>();
  final List<MethodRef> inheritedFrom= new ArrayList<>();
  final Set<MethodRefKey> inheritedKeys= new LinkedHashSet<>();

  M main(){ return variants.getFirst(); }
  boolean hasDocs(){ return !docs.isEmpty(); }
  boolean visible(){ return declared || !owner.main().infName() || hasDocs(); }
  void add(M m){ variants.add(m); }

  void addInheritedFrom(List<MethodRef> refs){
    refs.forEach(r->{
      if (inheritedKeys.add(MethodRefKey.of(r))){ inheritedFrom.add(r); }
    });
  }
}

/*
 * owner is the declaration used for the hyperlink target.
 * provider is the actual type-use through which the method was found, when we
 * have one. This preserves distinctions such as DataType[_] vs DataType[_,_].
 */
record MethodRef(TName owner, Optional<T.C> provider, M method){
  static MethodRef provider(T.C provider, M method){
    return new MethodRef(provider.name(),Optional.of(provider),method);
  }
  static MethodRef origin(M method){
    return new MethodRef(method.sig().origin(),Optional.empty(),method);
  }
}

record DeclaredMethodKey(URI file, int line, int column, MName m){}
record MethodRefKey(String provider, RC rc, MName m){
  static MethodRefKey of(MethodRef r){
    return new MethodRefKey(r.provider().map(Object::toString).orElse(r.owner().toString()), r.method().sig().rc(), r.method().sig().m());
  }
}
record ImportedKey(TName origin, RC rc, MName m){}

record DocOcc(URI file, int line, int column, int textColumn, String text, boolean pureLine, boolean example, boolean testOnly){
  boolean inline(){ return !pureLine; }
}