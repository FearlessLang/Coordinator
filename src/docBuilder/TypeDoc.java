package docBuilder;

import java.net.URI;
import java.util.ArrayList;
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
  List<DocOcc> visibleDocs(){ return docs.stream().filter(o -> o.kind()!=DocKind.TestOnly && o.kind()!=DocKind.Group).toList(); }
  List<DocOcc> directives(){ return docs.stream().filter(o -> o.kind()==DocKind.Group).toList(); }
  boolean hasDocs(){ return !visibleDocs().isEmpty(); }
  abstract Scope scope();
}
final class TypeDoc extends DeclarationDoc{
  final ArrayList<Literal> variants= new ArrayList<>();
  final Optional<Scope> outer;
  final LinkedHashMap<MethodKey,MethodDoc> byKey= new LinkedHashMap<>();
  final SequencedCollection<MethodDoc> methods= byKey.sequencedValues();
  TypeDoc(Literal l, Optional<Scope> outer, List<DocOcc> docs){
    super(docs);
    this.outer= outer;
    variants.add(l);
  }
  Literal main(){ return variants.getFirst(); }
  Scope scope(){ return new Scope(main(), Optional.empty(), outer); }
  boolean visible(){ return !main().infName() || hasDocs() || !directives().isEmpty() || methods.stream().anyMatch(MethodDoc::hasDocs); }
  void addVariant(Literal l){
    assert variants.stream().noneMatch(v -> v.name().equals(l.name())) :
      "Repeated literal source with same name: "+l.pos()+" "+l.name();
    variants.add(l);
  }
  void declared(Pos pos, M m, Supplier<List<DocOcc>> docs, List<MethodRef> inheritedFrom){
    oneOf(new MethodKey.Declared(pos.fileName(), pos.line(), pos.column(), m.sig().m()), docs, inheritedFrom).add(m);
  }
  void imported(M m, List<MethodRef> from){
    oneOf(new MethodKey.Imported(m.sig().origin(), m.sig().rc(), m.sig().m()), List::of, from).add(m);
  }
  private MethodDoc oneOf(MethodKey k, Supplier<List<DocOcc>> docs, List<MethodRef> inheritedFrom){
    var d= byKey.computeIfAbsent(k, _ -> new MethodDoc(this, k, docs.get()));
    d.inheritedFrom.addAll(inheritedFrom);
    return d;
  }
}
final class MethodDoc extends DeclarationDoc{
  final TypeDoc owner;
  final MethodKey key;
  final ArrayList<M> variants= new ArrayList<>();
  final ArrayList<MethodRef> inheritedFrom= new ArrayList<>();
  MethodDoc(TypeDoc owner, MethodKey key, List<DocOcc> docs){
    super(docs);
    this.owner= owner;
    this.key= key;
  }
  M main(){ return variants.getFirst(); }
  Scope scope(){ return new Scope(owner.main(), Optional.of(main()), owner.outer); }
  boolean declared(){ return key instanceof MethodKey.Declared; }
  boolean visible(){ return declared() || !owner.main().infName(); }
  void add(M m){ variants.add(m); }
}
record MethodRef(T.C provider, M method){}
sealed interface MethodKey{
  record Declared(URI file, int line, int column, MName m) implements MethodKey{}
  record Imported(TName origin, RC rc, MName m) implements MethodKey{}
}
enum DocKind{
  Prose, Fence, Fenced, Example, TestOnly, Group;
  boolean tested(){ return this==Example || this==TestOnly; }
  static Optional<DocKind> ofMark(char mark){
    return switch (mark){
      case '/' -> Optional.of(Prose);
      case '>' -> Optional.of(Example);
      case '-' -> Optional.of(TestOnly);
      case '@' -> Optional.of(Group);
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
