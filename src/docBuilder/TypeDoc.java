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
  final Map<PosKey,MethodDoc> declaredByPos= new LinkedHashMap<>();
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
    var k= PosKey.of(pos);
    var d= declaredByPos.get(k);
    if (d == null){
      d= new MethodDoc(this,true,docs,inheritedFrom);
      declaredByPos.put(k,d);
      methods.add(d);
    }
    else{ d.addInheritedFrom(inheritedFrom); }
    d.add(m);
  }

  void imported(M m){
    var k= ImportedKey.of(m);
    var d= importedByKey.get(k);
    if (d == null){
      d= new MethodDoc(this,false,List.of(),List.of());
      importedByKey.put(k,d);
      methods.add(d);
    }
    d.add(m);
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

record MethodRef(TName owner, M method){}

record MethodRefKey(URI file, int line, int column, String selector, int arity){
  static MethodRefKey of(MethodRef r){
    var p= r.method().sig().span().pos();
    return new MethodRefKey(
      p.fileName(),
      p.line(),
      p.column(),
      r.method().sig().m().s(),
      r.method().sig().m().arity());
  }
}
record PosKey(URI file, int line, int column){
  static PosKey of(Pos p){ return new PosKey(p.fileName(),p.line(),p.column()); }
}

record ImportedKey(String origin, String rc, String name, int arity){
  static ImportedKey of(M m){
    return new ImportedKey(
      m.sig().origin().toString(),
      m.sig().rc().name(),
      m.sig().m().s(),
      m.sig().m().arity());
  }
}

record DocOcc(URI file, int line, int column, String text, boolean pureLine){
  boolean inline(){ return !pureLine; }
}