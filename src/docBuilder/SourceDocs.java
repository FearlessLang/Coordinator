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

  List<DocOcc> docsAt(Pos pos, boolean includeBefore){
    assert pos.line() > 0 && pos.line() <= lines.size(): pos;
    var res= new ArrayList<DocOcc>();
    if (includeBefore){ collectBefore(pos.line(),res); }
    inlineAfter(pos.line(),pos.column()).ifPresent(res::add);
    return res;
  }

  void collectBefore(int line, List<DocOcc> res){
    var before= new ArrayList<DocOcc>();
    for (int l= line-1; l >= 1; l -= 1){
      var pure= pureDocs(l);
      if (!pure.isEmpty()){ before.addAll(pure); continue; }
      if (lines.get(l-1).isBlank()){ continue; }
      break;
    }
    Collections.reverse(before);
    res.addAll(before);
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
              add(l,i+1,s.substring(i+3),s.substring(0,i).isBlank());
              i= s.length();
            }
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

  void add(int line, int column, String text, boolean pureLine){
    docsByLine.computeIfAbsent(line,_ -> new ArrayList<>())
      .add(new DocOcc(uri,line,column,clean(text),pureLine));
  }

  static String clean(String s){ return s.startsWith(" ") ? s.substring(1) : s; }

  enum Mode{ Normal, DoubleString, BacktickString, Block }
}