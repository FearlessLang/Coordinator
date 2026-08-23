package docBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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

  static List<CodeSpan> codeSpans(String text){
    var res= new ArrayList<CodeSpan>();
    int i= 0;
    while (i < text.length()){
      if (text.charAt(i) != '`'){ i += 1; continue; }
      int fence= runLength(text,i);
      int close= closingFence(text, i+fence, fence);
      if (close < 0){ i += fence; continue; }
      res.add(new CodeSpan(i+fence, close, fence));
      i= close+fence;
    }
    return res;
  }

  static List<CodeSpan> refSpans(String text){
    return codeSpans(text).stream().filter(sp->sp.fence()==1).toList();
  }

  private static int runLength(String text, int at){
    int n= 0;
    while (at+n < text.length() && text.charAt(at+n) == '`'){ n += 1; }
    return n;
  }

  private static int closingFence(String text, int from, int fence){
    int i= from;
    while (i < text.length()){
      if (text.charAt(i) != '`'){ i += 1; continue; }
      int n= runLength(text,i);
      if (n == fence){ return i; }
      i += n;
    }
    return -1;
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
    var res= new ArrayList<Found>();
    int i= 0;
    while (i < text.length()){
      if (!isWordChar(text.charAt(i))){ i += 1; continue; }
      int end= i;
      while (end < text.length() && isWordChar(text.charAt(end))){ end += 1; }
      var word= text.substring(i,end);
      if (TName.isTypeName(word)){ res.add(new Found(i, end, new DocRef.TypeName(Optional.empty(), word, OptionalInt.empty()))); }
      i= end;
    }
    return res;
  }

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
      return Optional.of(new DocRef.MethodName(receiver, sel.get(), arity('(',')')));
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
      return new DocRef.TypeName(pkg, name, arity('[',']'));
    }

    String word(int from){
      int end= from;
      while (end < s.length() && isWordChar(s.charAt(end))){ end += 1; }
      return s.substring(Math.min(from,s.length()), end);
    }

    //"[_,_]" or "()" only: a real argument list like "[Int]" is not an arity marker,
    //and leaves the arity unspecified with nothing consumed.
    OptionalInt arity(char open, char close){
      if (i >= s.length() || s.charAt(i) != open){ return OptionalInt.empty(); }
      if (i+1 < s.length() && s.charAt(i+1) == close){ i += 2; return OptionalInt.of(0); }
      int count= 0;
      int at= i+1;
      while (true){
        if (at >= s.length() || s.charAt(at) != '_'){ return OptionalInt.empty(); }
        count += 1;
        at += 1;
        if (at < s.length() && s.charAt(at) == close){ i= at+1; return OptionalInt.of(count); }
        if (at >= s.length() || s.charAt(at) != ','){ return OptionalInt.empty(); }
        at += 1;
      }
    }
  }
}
