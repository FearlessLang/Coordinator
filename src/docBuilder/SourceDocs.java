package docBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import utils.Pos;

final class SourceDocs{
  final URI uri;
  final List<String> lines;
  final HashMap<Integer,List<DocOcc>> docsByLine= new HashMap<>();
  final HashMap<Integer,int[]> depthByLine= new HashMap<>();
  final HashSet<DocOcc> attached= new HashSet<>();
  final HashMap<DocOcc,Integer> inlineClaimedFromColumn= new HashMap<>();
  final LinkedHashSet<DocOcc> ambiguousInline= new LinkedHashSet<>();
  final HashSet<Integer> visitedLines= new HashSet<>();
  SourceDocs(URI uri, String text){
    this.uri= uri;
    lines= text.lines().toList();
    scan();
  }
  List<DocOcc> docsAt(Pos pos, boolean literal){
    assert pos.line()>0 && pos.line()<=lines.size() : pos;
    var first= visitedLines.add(pos.line());
    if (literal && !first){ return List.of(); }
    var res= new ArrayList<DocOcc>();
    if (first){ collectBefore(pos.line(), res); }
    inlineAfter(pos.line(), pos.column())
      .filter(occ -> Math.max(depthAt(occ.line(), occ.column()), depthAt(pos.line(), 0)) >= depthAt(pos.line(), pos.column()))
      .ifPresent(occ -> {
        var firstColumn= inlineClaimedFromColumn.putIfAbsent(occ, pos.column());
        var shown= dedent(List.of(occ)).getFirst();
        if (firstColumn!=null && firstColumn!=pos.column()){ ambiguousInline.add(shown); }
        attached.add(occ);
        res.add(shown);
      });
    return fences(res);
  }
  static List<DocOcc> fences(List<DocOcc> ds){
    var res= new ArrayList<DocOcc>();
    var inFence= false;
    for (var d : ds){
      if (d.kind()!=DocKind.Prose){ res.add(d); continue; }
      var fence= fenceLine.matcher(d.text()).matches();
      if (fence){ inFence = !inFence; }
      res.add(new DocOcc(d.file(), d.line(), d.column(), d.textColumn(), d.text(), d.pureLine(), fence ? DocKind.Fence : inFence ? DocKind.Fenced : DocKind.Prose));
    }
    return List.copyOf(res);
  }
  private static final Pattern fenceLine= Pattern.compile(" *```[^`]*");
  int depthAt(int line, int column){
    var arr= depthByLine.get(line);
    return (arr==null || column>=arr.length) ? 0 : arr[column];
  }
  //every /// and //> that no declaration took, as runs of lines with only empty lines between them
  List<List<DocOcc>> orphanRuns(){
    var lost= docsByLine.values().stream()
      .flatMap(List::stream)
      .filter(d -> !attached.contains(d))
      .sorted(Comparator.comparingInt(DocOcc::line).thenComparingInt(DocOcc::column))
      .toList();
    var res= new ArrayList<ArrayList<DocOcc>>();
    for (var d : lost){
      var last= res.isEmpty() ? null : res.getLast().getLast();
      if (last!=null && lines.subList(last.line(), d.line()-1).stream().allMatch(String::isBlank) && d.inline()==last.inline()){ res.getLast().add(d); continue; }
      res.add(new ArrayList<>(List.of(d)));
    }
    return List.copyOf(res);
  }
  //An empty line inside a block reads as an empty doc line of whatever kind surrounds
  //it, so it ends a paragraph without ending the block. The ones at either end of the
  //block separate nothing and are dropped. Anything else ends the block, and whatever
  //documentation is left above it is then attached to nothing, which is an error.
  void collectBefore(int line, ArrayList<DocOcc> res){
    var before= new ArrayList<DocOcc>();
    for (int l= line-1; l >= 1; l -= 1){
      var pure= pureDocs(l);
      if (!pure.isEmpty()){ before.addAll(pure); continue; }
      if (!lines.get(l-1).isBlank()){ break; }
      //the line just below the empty one, which is the last one collected walking up
      var below= !before.isEmpty();
      before.add(new DocOcc(uri, l, 1, 1, "", true, below ? before.getLast().kind() : DocKind.Prose));
    }
    Collections.reverse(before);
    //everything collected is attached, including an empty line trimmed off the end:
    //trimming is about what to show, not about what the block reached.
    attached.addAll(before);
    res.addAll(dedent(trimBlanks(before)));
  }
  static List<DocOcc> dedent(List<DocOcc> ds){
    return ds.stream().map(d -> d.drop(indent(ds, d))).toList();
  }
  static int indent(List<DocOcc> ds, DocOcc d){
    return ds.stream()
      .filter(o -> o.kind()==d.kind() && !o.text().isBlank())
      .mapToInt(o -> o.text().length()-o.text().stripLeading().length())
      .min().orElse(0);
  }
  static List<DocOcc> trimBlanks(List<DocOcc> ds){
    var from= 0;
    var to= ds.size();
    while (from<to && ds.get(from).text().isBlank()){ from += 1; }
    while (to>from && ds.get(to-1).text().isBlank()){ to -= 1; }
    return ds.subList(from, to);
  }
  List<DocOcc> pureDocs(int line){
    return docsByLine.getOrDefault(line, List.of()).stream()
      .filter(DocOcc::pureLine)
      .toList();
  }
  Optional<DocOcc> inlineAfter(int line, int column){
    return docsByLine.getOrDefault(line, List.of()).stream()
      .filter(c -> !c.pureLine() && c.column()>=column)
      .findFirst();
  }
  void scan(){
    var closer= "";
    var depth= 0;
    for (int l= 1; l <= lines.size(); l += 1){
      var s= lines.get(l-1);
      var arr= new int[s.length()+1];
      arr[0]= depth;
      for (int i= 0; i < s.length();){
        var start= i;
        if (!closer.isEmpty()){
          if (s.startsWith(closer, i)){ i += closer.length(); closer = ""; }
          else{ i += 1; }
        }
        else if (s.startsWith("//", i)){
          var k= s.length()>i+2 ? s.charAt(i+2) : ' ';
          var repeated= s.length()>i+3 && s.charAt(i+3)==k;
          var kind= DocKind.ofMark(k);
          if (!repeated && kind.isPresent()){ add(l, i+1, s.substring(i+3), s.substring(0, i).isBlank(), kind.get()); }
          i = s.length();
        }
        else if (s.startsWith("/*", i)){ closer = "*/"; i += 2; }
        else if (s.charAt(i)=='"' || s.charAt(i)=='`'){ closer = s.substring(i, i+1); i += 1; }
        else{ depth += s.charAt(i)=='{' ? 1 : s.charAt(i)=='}' ? -1 : 0; i += 1; }
        Arrays.fill(arr, start+1, i+1, depth);
      }
      depthByLine.put(l, arr);
    }
  }
  //textColumn is where the stored text really starts in the line, so an error can put
  //its carets under the offending characters: past the "///" and the indentation dedent drops.
  void add(int line, int column, String text, boolean pureLine, DocKind kind){
    docsByLine.computeIfAbsent(line, _ -> new ArrayList<>())
      .add(new DocOcc(uri, line, column, column+3, text, pureLine, kind));
  }
}
