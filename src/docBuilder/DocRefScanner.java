package docBuilder;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

import core.MName;
import core.TName;

/// Reads the backtick spans of a doc comment. A single backtick span is a reference:
/// all of it must be a name, so an unmarked word (a sentence starting with "As") is
/// never a candidate and no denylist of English words is needed. Two backticks or more
/// are just code to show, and are never read as a reference.
/// What counts as a name is asked of core (TName.isTypeName, TName.isPkgName,
/// MName.isMethodName), so this never grows its own idea of the grammar.
final class DocRefScanner{
  private DocRefScanner(){}

  record Found(int start, int end, DocRef ref){}
  record CodeSpan(int start, int end, int fence){}

  private static final Pattern fenced= Pattern.compile("(?s)(?<!`)(`+)(?!`)(.*?)(?<!`)\\1(?!`)");
  static List<CodeSpan> codeSpans(String text){
    return fenced.matcher(text).results().map(m->new CodeSpan(m.start(2), m.end(2), m.group(1).length())).toList();
  }

  static List<CodeSpan> refSpans(String text){
    return codeSpans(text).stream().filter(sp->sp.fence()==1).toList();
  }

  //the whole span must be the name: "`Foo.bar`" is one, "`this.foo(x)`" is not, and
  //the caller reports that rather than linking part of it.
  static Optional<DocRef> wholeRef(String text, CodeSpan sp){
    var p= new Parse(text.substring(sp.start(), sp.end()));
    var res= p.ref();
    return p.done() ? res : Optional.empty();
  }

  //a rendered signature is code, not prose: every maximal word that is a type name is
  //offered, and nothing here can be an error. Taking maximal words is what keeps
  //"ieeeEq" from being read as ending in the type "Eq".
  static List<Found> signatureTypes(String text){
    return word.matcher(text).results()
      .filter(w->TName.isTypeName(w.group()))
      .map(w->new Found(w.start(), w.end(), new DocRef.TypeName(Optional.empty(), w.group(), OptionalInt.empty())))
      .toList();
  }
  private static final Pattern word= Pattern.compile("[\\p{L}\\p{Nd}_']+");

  static boolean isWordChar(char c){
    return Character.isLetterOrDigit(c) || c == '_' || c == '\'';
  }

  /// One reference, read left to right: an optional receiver, then an optional
  /// selector. Each name is handed to core to be judged.
  private static final class Parse{
    Parse(String s){ this.s= s; }
    final String s;
    int i= 0;
    boolean ok= true;

    boolean done(){ return ok && i == s.length(); }

    Optional<DocRef> ref(){
      var receiver= startsSelector() ? Optional.<DocRef.Receiver>empty() : receiver();
      if (!ok){ return Optional.empty(); }
      var sel= selector();
      if (!ok){ return Optional.empty(); }
      if (sel.isEmpty()){ return receiver.map(r->(DocRef)r); }
      return Optional.of(new DocRef.MethodName(receiver, sel.get(), arity(callArity)));
    }

    boolean startsSelector(){ return i < s.length() && (s.charAt(i) == '.' || opChar(i)); }

    //".foo" or an operator such as "++": an operator method is named like any other,
    //so "Foo++(_,_)" reads the same way as "Foo.foo(_,_)".
    Optional<String> selector(){
      if (!startsSelector()){ return Optional.empty(); }
      var sel= s.charAt(i) == '.' ? "."+word(i+1) : ops(i);
      if (!MName.isMethodName(sel)){ ok= false; return Optional.empty(); }
      i += sel.length();
      return Optional.of(sel);
    }

    //core decides what an operator character is: a one character operator is itself a
    //method name, while a letter, a digit or a "." is not.
    boolean opChar(int at){
      return at < s.length() && MName.isMethodName(String.valueOf(s.charAt(at)));
    }

    String ops(int from){
      int end= from;
      while (opChar(end)){ end += 1; }
      return s.substring(from, end);
    }

    //a type, possibly package qualified, or a name bound where the comment is written
    Optional<DocRef.Receiver> receiver(){
      var first= word(i);
      if (first.isEmpty()){ ok= false; return Optional.empty(); }
      i += first.length();
      if (TName.isTypeName(first)){ return Optional.of(type(Optional.empty(), first)); }
      var second= i < s.length() && s.charAt(i) == '.' ? word(i+1) : "";
      if (!TName.isTypeName(second)){ return Optional.of(new DocRef.LocalName(first)); }
      if (!TName.isPkgName(first)){ ok= false; return Optional.empty(); }
      i += 1+second.length();
      return Optional.of(type(Optional.of(first), second));
    }

    DocRef.Receiver type(Optional<String> pkg, String name){
      return new DocRef.TypeName(pkg, name, arity(typeArity));
    }

    String word(int from){
      int end= from;
      while (end < s.length() && isWordChar(s.charAt(end))){ end += 1; }
      return s.substring(Math.min(from,s.length()), end);
    }

    //"[_,_]" or "()" only: a real argument list like "[Int]" is not an arity marker,
    //and leaves the arity unspecified with nothing consumed.
    OptionalInt arity(Pattern p){
      var m= p.matcher(s).region(i,s.length());
      if (!m.lookingAt()){ return OptionalInt.empty(); }
      i= m.end();
      return OptionalInt.of(m.group(1) == null ? 0 : (m.group(1).length()+1)/2);
    }
    private static final Pattern typeArity= Pattern.compile("\\[(_(,_)*)?]");
    private static final Pattern callArity= Pattern.compile("\\((_(,_)*)?\\)");
  }
}
