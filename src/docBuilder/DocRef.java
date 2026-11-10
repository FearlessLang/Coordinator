package docBuilder;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import core.Sig;
import core.TName;

/// What a backtick reference names, before resolution. A receiver is a type, or a name
/// bound where the comment is written: "this", or a parameter of the method. An absent
/// arity means "any arity", which is what makes a reference ambiguous when several
/// declarations share the name.
sealed interface DocRef{
  sealed interface Receiver extends DocRef{}
  record TypeName(Optional<String> pkg, String simpleName, OptionalInt arity) implements Receiver{}
  record LocalName(String name) implements Receiver{}
  record MethodName(Optional<Receiver> owner, String selector, OptionalInt arity) implements DocRef{}
}
/// One thing a reference could mean. localMethod is set only for a method of a type
/// rendered on this page, and carries the MethodDoc so the option can show its documentation.
record Candidate(TName owner, Optional<Sig> sig, Optional<MethodDoc> localMethod){
  static Candidate ofType(TName owner){
    return new Candidate(owner, Optional.empty(), Optional.empty());
  }
  static Candidate ofLocalMethod(TName owner, MethodDoc method){
    return new Candidate(owner, Optional.of(method.main().sig()), Optional.of(method));
  }
}
sealed interface DocLink{
  record Resolved(Candidate hit) implements DocLink{}
  record Ambiguous(String title, List<Candidate> options) implements DocLink{}
  /// A real reference with nothing to point at: a parameter whose type is a type
  /// variable. Shown as code, never an error.
  record NoLink() implements DocLink{}
}
