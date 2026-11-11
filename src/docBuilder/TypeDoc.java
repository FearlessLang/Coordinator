package docBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.function.Supplier;

import core.E.Literal;
import core.M;
import core.MName;
import core.RC;
import core.T;
import core.TName;
import utils.Pos;

sealed abstract class DeclarationDoc permits TypeDoc, MethodDoc{
  final List<DocOcc> docs;
  DeclarationDoc(List<DocOcc> docs){ this.docs= docs; }
  List<DocOcc> visibleDocs(){ return docs.stream().filter(o -> o.kind()!=DocKind.TestOnly).toList(); }
  boolean hasDocs(){ return !visibleDocs().isEmpty(); }
  abstract Scope scope();
}
final class TypeDoc extends DeclarationDoc{
  final ArrayList<Literal> variants= new ArrayList<>();
  final Optional<Scope> outer;
  final LinkedHashMap<Object,MethodDoc> byKey= new LinkedHashMap<>();
  final SequencedCollection<MethodDoc> methods= byKey.sequencedValues();
  TypeDoc(Literal l, Optional<Scope> outer, List<DocOcc> docs){
    super(docs);
    this.outer= outer;
    variants.add(l);
  }
  Literal main(){ return variants.getFirst(); }
  Scope scope(){ return new Scope(main(), Optional.empty(), outer); }
  boolean visible(){ return !main().infName() || hasDocs() || methods.stream().anyMatch(MethodDoc::hasDocs); }
  void addVariant(Literal l){
    assert variants.stream().noneMatch(v -> v.name().equals(l.name())) :
      "Repeated literal source with same name: "+l.pos()+" "+l.name();
    variants.add(l);
  }
  void declared(Pos pos, M m, Supplier<List<DocOcc>> docs, List<MethodRef> inheritedFrom){
    oneOf(new DeclaredMethodKey(pos.fileName(), pos.line(), pos.column(), m.sig().m()), true, docs, inheritedFrom).add(m);
  }
  void imported(M m, List<MethodRef> from){
    oneOf(new ImportedKey(m.sig().origin(), m.sig().rc(), m.sig().m()), false, List::of, from).add(m);
  }
  private MethodDoc oneOf(Object k, boolean declared, Supplier<List<DocOcc>> docs, List<MethodRef> inheritedFrom){
    var d= byKey.computeIfAbsent(k, _ -> new MethodDoc(this, declared, docs.get()));
    d.addInheritedFrom(inheritedFrom);
    return d;
  }
}
final class MethodDoc extends DeclarationDoc{
  final TypeDoc owner;
  final boolean declared;
  final ArrayList<M> variants= new ArrayList<>();
  final LinkedHashMap<MethodRefKey,MethodRef> inheritedByKey= new LinkedHashMap<>();
  final Collection<MethodRef> inheritedFrom= inheritedByKey.values();
  MethodDoc(TypeDoc owner, boolean declared, List<DocOcc> docs){
    super(docs);
    this.owner= owner;
    this.declared= declared;
  }
  M main(){ return variants.getFirst(); }
  Scope scope(){ return new Scope(owner.main(), Optional.of(main()), owner.outer); }
  boolean visible(){ return declared || !owner.main().infName(); }
  void add(M m){ variants.add(m); }
  void addInheritedFrom(List<MethodRef> refs){
    refs.forEach(r -> inheritedByKey.putIfAbsent(MethodRefKey.of(r), r));
  }
}
/*
 * provider is the actual type-use through which the method was found.
 * This preserves distinctions such as DataType[_] vs DataType[_,_].
 */
record MethodRef(T.C provider, M method){}
record DeclaredMethodKey(URI file, int line, int column, MName m){}
record MethodRefKey(String provider, RC rc, MName m){
  static MethodRefKey of(MethodRef r){
    return new MethodRefKey(r.provider().toString(), r.method().sig().rc(), r.method().sig().m());
  }
}
record ImportedKey(TName origin, RC rc, MName m){}
enum DocKind{
  Prose, Fence, Fenced, Example, TestOnly;
  boolean tested(){ return this==Example || this==TestOnly; }
  static Optional<DocKind> ofMark(char mark){
    return switch (mark){
      case '/' -> Optional.of(Prose);
      case '>' -> Optional.of(Example);
      case '-' -> Optional.of(TestOnly);
      default -> Optional.empty();
    };
  }
}
record DocOcc(URI file, int line, int column, int textColumn, String text, boolean trailing, DocKind kind){
  DocOcc drop(int n){
    var k= Math.min(n, text.length());
    return new DocOcc(file, line, column, textColumn+k, text.substring(k), trailing, kind);
  }
}
