package docBuilder;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import core.Sig;
import core.TName;

sealed interface DocRef{
  sealed interface Receiver extends DocRef{}
  record TypeName(Optional<String> pkg, String simpleName, OptionalInt arity) implements Receiver{}
  record LocalName(String name) implements Receiver{}
  record MethodName(Optional<Receiver> owner, String selector, OptionalInt arity) implements DocRef{}
}
sealed interface Candidate{
  TName owner();
  record Type(TName owner) implements Candidate{}
  record Method(TName owner, Sig sig, Optional<MethodDoc> localMethod) implements Candidate{
    static Method ofLocal(TName owner, MethodDoc method){ return new Method(owner, method.main().sig(), Optional.of(method)); }
  }
}
sealed interface DocLink{
  record Resolved(Candidate hit) implements DocLink{}
  record Ambiguous(String title, List<Candidate> options) implements DocLink{}
  record NoLink() implements DocLink{}
}
sealed interface Directive{
  DocOcc at();
  record Named(DocOcc at, String group) implements Directive{}
  record Under(DocOcc at, TypeDoc parent) implements Directive{}
  record Broken(DocOcc at, String problem) implements Directive{}
}
