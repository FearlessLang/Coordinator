package docBuilder;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

import core.MName;
import core.TName;
import utils.Join;

final class DocRefScanner{
  record CodeSpan(int start, int end, int fence){
    boolean covers(int i){ return start-fence<=i && i<end+fence; }
  }
  private static final Pattern fenced= Pattern.compile("(?s)(?<!`)(`+)(?!`)(.*?)(?<!`)\\1(?!`)");
  private static final Pattern backticks= Pattern.compile("`+");
  static List<CodeSpan> codeSpans(String text){
    return fenced.matcher(text).results().map(m -> new CodeSpan(m.start(2), m.end(2), m.group(1).length())).toList();
  }
  static List<CodeSpan> unclosed(String text){
    var spans= codeSpans(text);
    return backticks.matcher(text).results()
      .filter(m -> spans.stream().noneMatch(s -> s.covers(m.start())))
      .map(m -> new CodeSpan(m.start(), m.end(), m.end()-m.start()))
      .toList();
  }
  static List<CodeSpan> refSpans(String text){
    return codeSpans(text).stream().filter(sp -> sp.fence() == 1).toList();
  }
  static Optional<DocRef> wholeRef(String text, CodeSpan sp){
    var p= new Parse(text.substring(sp.start(), sp.end()));
    return p.ref().filter(_ -> p.i==p.s.length());
  }
  private static final Pattern word= Pattern.compile("[\\p{L}\\p{Nd}_']+");
  private static final class Parse{
    final String s;
    int i= 0;
    Parse(String s){ this.s= s; }
    Optional<DocRef> ref(){
      if (startsSelector()){ return method(Optional.empty()); }
      return receiver().flatMap(r -> startsSelector() ? method(Optional.of(r)) : Optional.of(r));
    }
    Optional<DocRef> method(Optional<DocRef.Receiver> owner){
      return selector().map(sel -> new DocRef.MethodName(owner, sel, arity(callArity)));
    }
    boolean startsSelector(){ return s.startsWith(".", i) || !ops(i).isEmpty(); }
    Optional<String> selector(){
      var sel= s.charAt(i)=='.' ? "."+word(i+1) : ops(i);
      if (!MName.isMethodName(sel)){ return Optional.empty(); }
      i += sel.length();
      return Optional.of(sel);
    }
    String ops(int from){ return Join.of(s.substring(from).chars().mapToObj(Character::toString).takeWhile(MName::isMethodName), "", "", "", ""); }
    Optional<DocRef.Receiver> receiver(){
      var first= word(i);
      i += first.length();
      if (TName.isTypeName(first)){ return Optional.of(new DocRef.TypeName(Optional.empty(), first, arity(typeArity))); }
      var second= s.startsWith(".", i) ? word(i+1) : "";
      var named= TName.isTypeName(second) ? TName.isPkgName(first) : MName.isMethodName("."+first);
      if (!named){ return Optional.empty(); }
      if (!TName.isTypeName(second)){ return Optional.of(new DocRef.LocalName(first)); }
      i += 1+second.length();
      return Optional.of(new DocRef.TypeName(Optional.of(first), second, arity(typeArity)));
    }
    String word(int from){
      var m= word.matcher(s).region(Math.min(from, s.length()), s.length());
      return m.lookingAt() ? m.group() : "";
    }
    OptionalInt arity(Pattern p){
      var m= p.matcher(s).region(i, s.length());
      if (!m.lookingAt()){ return OptionalInt.empty(); }
      i = m.end();
      return OptionalInt.of((int)m.group().chars().filter(c -> c=='_').count());
    }
    private static final Pattern typeArity= Pattern.compile("\\[(_(, ?_)*)?]");
    private static final Pattern callArity= Pattern.compile("\\((_(, ?_)*)?\\)");
  }
}
