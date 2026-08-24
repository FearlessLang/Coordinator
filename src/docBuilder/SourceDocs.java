package docBuilder;

import java.net.URI;
import java.util.*;

import utils.Pos;

final class SourceDocs{
  SourceDocs(URI uri, String text){
    this.uri= uri;
    this.lines= text.lines().toList();
    scan();
  }

  final URI uri;
  final List<String> lines;
  final Map<Integer,List<DocOcc>> docsByLine= new HashMap<>();
  final Set<DocOcc> attached= Collections.newSetFromMap(new IdentityHashMap<>());

  List<DocOcc> docsAt(Pos pos, boolean includeBefore){
    assert pos.line() > 0 && pos.line() <= lines.size(): pos;
    var res= new ArrayList<DocOcc>();
    if (includeBefore){ collectBefore(pos.line(),res); }
    inlineAfter(pos.line(),pos.column()).ifPresent(res::add);
    attached.addAll(res);
    return res;
  }

  //every /// and //> that no declaration took, as runs of consecutive lines
  List<List<DocOcc>> orphanRuns(){
    var lost= docsByLine.values().stream()
      .flatMap(List::stream)
      .filter(d->!attached.contains(d))
      .sorted(Comparator.comparingInt(DocOcc::line).thenComparingInt(DocOcc::column))
      .toList();
    var res= new ArrayList<List<DocOcc>>();
    for (var d: lost){
      var last= res.isEmpty() ? null : res.getLast().getLast();
      if (last != null && d.line()-last.line() <= 1){ res.getLast().add(d); continue; }
      res.add(new ArrayList<>(List.of(d)));
    }
    return res;
  }

  //An empty line inside a block reads as an empty doc line of whatever kind surrounds
  //it, so it ends a paragraph without ending the block. The ones at either end of the
  //block separate nothing and are dropped. Anything else ends the block, and whatever
  //documentation is left above it is then attached to nothing, which is an error.
  void collectBefore(int line, List<DocOcc> res){
    var before= new ArrayList<DocOcc>();
    for (int l= line-1; l >= 1; l -= 1){
      var pure= pureDocs(l);
      if (!pure.isEmpty()){ before.addAll(pure); continue; }
      if (!lines.get(l-1).isBlank()){ break; }
      before.add(new DocOcc(uri,l,1,1,"",true,contextIsExample(before)));
    }
    Collections.reverse(before);
    //everything collected is attached, including an empty line trimmed off the end:
    //trimming is about what to show, not about what the block reached.
    attached.addAll(before);
    res.addAll(trimBlanks(before));
  }

  //the line just below the empty one, which is the last one collected walking up
  static boolean contextIsExample(List<DocOcc> before){
    return !before.isEmpty() && before.getLast().example();
  }

  static List<DocOcc> trimBlanks(List<DocOcc> ds){
    int from= 0;
    int to= ds.size();
    while (from < to && ds.get(from).text().isBlank()){ from += 1; }
    while (to > from && ds.get(to-1).text().isBlank()){ to -= 1; }
    return ds.subList(from,to);
  }

  List<DocOcc> pureDocs(int line){
    return docsByLine.getOrDefault(line,List.of()).stream()
      .filter(DocOcc::pureLine)
      .toList();
  }

  Optional<DocOcc> inlineAfter(int line, int column){
    return docsByLine.getOrDefault(line,List.of()).stream()
      .filter(c->!c.pureLine() && c.column() >= column)
      .findFirst();
  }

  void scan(){
    var mode= Mode.Normal;
    for (int l= 1; l <= lines.size(); l += 1){
      var s= lines.get(l-1);
      for (int i= 0; i < s.length();){
        switch(mode){
          case Normal -> {
            if (s.startsWith("///",i)){
              add(l,i+1,s.substring(i+3),s.substring(0,i).isBlank(),false);
              i= s.length();
            }
            else if (s.startsWith("//>",i)){
              add(l,i+1,s.substring(i+3),s.substring(0,i).isBlank(),true);
              i= s.length();
            }
            else if (s.startsWith("//",i)){ i= s.length(); }
            else if (s.startsWith("/*",i)){ mode= Mode.Block; i += 2; }
            else if (s.charAt(i) == '"'){ mode= Mode.DoubleString; i += 1; }
            else if (s.charAt(i) == '`'){ mode= Mode.BacktickString; i += 1; }
            else{ i += 1; }
          }
          case DoubleString -> {
            if (s.charAt(i) == '"'){ mode= Mode.Normal; }
            i += 1;
          }
          case BacktickString -> {
            if (s.charAt(i) == '`'){ mode= Mode.Normal; }
            i += 1;
          }
          case Block -> {
            if (s.startsWith("*/",i)){ mode= Mode.Normal; i += 2; }
            else{ i += 1; }
          }
        }
      }
    }
  }

  //textColumn is where the stored text really starts in the line, so an error can put
  //its carets under the offending characters: past the "///" and the space clean drops.
  void add(int line, int column, String text, boolean pureLine, boolean example){
    var clean= clean(text);
    docsByLine.computeIfAbsent(line,_ -> new ArrayList<>())
      .add(new DocOcc(uri,line,column,column+3+(text.length()-clean.length()),clean,pureLine,example));
  }

  static String clean(String s){ return s.startsWith(" ") ? s.substring(1) : s; }

  enum Mode{ Normal, DoubleString, BacktickString, Block }
}