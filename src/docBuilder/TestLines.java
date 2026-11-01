package docBuilder;

import java.net.URI;
import java.util.ArrayList;

import main.FrontendLogicMain.TestSource;
import metaParser.Layout;

final class TestLines implements Layout{
  private record Origin(int line, int column, boolean shifted){}
  private final ArrayList<String> texts= new ArrayList<>();
  private final ArrayList<Origin> origins= new ArrayList<>();
  void wrapper(String text, int line, int column){ texts.add(text); origins.add(new Origin(line, column, false)); }
  void example(DocOcc d){ texts.add(d.text()); origins.add(new Origin(d.line(), d.textColumn(), true)); }
  @Override public int line(int line){ return origins.get(line-1).line(); }
  @Override public int column(int line, int column){
    var o= origins.get(line-1);
    return o.shifted() ? o.column()+column-1 : o.column();
  }
  TestSource source(URI file){ return new TestSource(file, String.join("\n", texts), this); }
}
