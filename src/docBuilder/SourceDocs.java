package docBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import utils.Pos;

final class SourceDocs{
  final URI uri;
  final List<String> lines;
  final HashMap<Integer,DocOcc> docByLine= new HashMap<>();
  final HashMap<Integer,int[]> depthByLine= new HashMap<>();
  final HashMap<DocOcc,Integer> claimCount= new HashMap<>();
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
    var before= first ? collectBefore(pos.line()) : List.<DocOcc>of();
    var trailing= trailingAfter(pos.line(), pos.column())
      .filter(occ -> Math.max(depthAt(occ.line(), occ.column()), depthAt(pos.line(), 1)) >= depthAt(pos.line(), pos.column()))
      .stream().toList();
    Stream.concat(before.stream(), trailing.stream()).forEach(d -> claimCount.merge(d, 1, Integer::sum));
    return fences(Stream.concat(dedent(trimBlanks(before)).stream(), dedent(trailing).stream()).toList());
  }
  static List<DocOcc> fences(List<DocOcc> ds){
    var res= new ArrayList<DocOcc>();
    var inFence= false;
    for (var d : ds){
      if (d.kind()!=DocKind.Prose){ res.add(d); continue; }
      var fence= fenceLine.matcher(d.text()).matches();
      if (fence){ inFence = !inFence; }
      res.add(new DocOcc(d.file(), d.line(), d.column(), d.textColumn(), d.text(), d.trailing(), fence ? DocKind.Fence : inFence ? DocKind.Fenced : DocKind.Prose));
    }
    return List.copyOf(res);
  }
  private static final Pattern fenceLine= Pattern.compile(" *```[^`]*");
  int depthAt(int line, int column){ return depthByLine.get(line)[column-1]; }
  List<List<DocOcc>> orphanRuns(){
    var lost= docByLine.values().stream()
      .filter(d -> !claimCount.containsKey(d))
      .sorted(bySource)
      .toList();
    var res= new ArrayList<ArrayList<DocOcc>>();
    for (var d : lost){
      var last= res.isEmpty() ? null : res.getLast().getLast();
      if (last!=null && lines.subList(last.line(), d.line()-1).stream().allMatch(String::isBlank) && d.trailing()==last.trailing()){ res.getLast().add(d); continue; }
      res.add(new ArrayList<>(List.of(d)));
    }
    return List.copyOf(res);
  }
  List<DocOcc> ambiguousTrailing(){
    return claimCount.entrySet().stream()
      .filter(e -> e.getValue()>1)
      .map(Map.Entry::getKey)
      .sorted(bySource)
      .map(d -> dedent(List.of(d)).getFirst())
      .toList();
  }
  private static final Comparator<DocOcc> bySource= Comparator.comparingInt(DocOcc::line);
  List<DocOcc> collectBefore(int line){
    var before= new ArrayList<DocOcc>();
    for (int l= line-1; l >= 1; l -= 1){
      var own= ownDoc(l);
      if (own.isEmpty()){ break; }
      before.add(own.get());
    }
    return before.reversed();
  }
  Optional<DocOcc> ownDoc(int line){ return Optional.ofNullable(docByLine.get(line)).filter(d -> !d.trailing()); }
  boolean declarationBelow(int line){
    var l= line+1;
    while (l<=lines.size() && (lines.get(l-1).isBlank() || ownDoc(l).isPresent())){ l += 1; }
    return visitedLines.contains(l);
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
  static boolean blank(DocOcc d){ return d.text().isBlank() && d.kind()!=DocKind.Group; }
  static List<DocOcc> trimBlanks(List<DocOcc> ds){
    var from= 0;
    var to= ds.size();
    while (from<to && blank(ds.get(from))){ from += 1; }
    while (to>from && blank(ds.get(to-1))){ to -= 1; }
    return ds.subList(from, to);
  }
  Optional<DocOcc> trailingAfter(int line, int column){
    return Optional.ofNullable(docByLine.get(line)).filter(d -> d.trailing() && d.column()>=column);
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
          if (!repeated && kind.isPresent()){ add(l, i+1, s.substring(i+3), !s.substring(0, i).isBlank(), kind.get()); }
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
  void add(int line, int column, String text, boolean trailing, DocKind kind){
    var previous= docByLine.put(line, new DocOcc(uri, line, column, column+3, text, trailing, kind));
    assert previous==null;
  }
}
