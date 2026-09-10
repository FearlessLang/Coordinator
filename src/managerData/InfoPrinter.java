package managerData;

import utils.Range;

public final class InfoPrinter{
  private InfoPrinter(){}
  public static String print(Info info){
    var sb= new StringBuilder();
    write(info,0,sb);
    return sb.append('\n').toString();
  }
  private static void write(Info info, int indent, StringBuilder sb){
    switch(info){
      case Info.Str s -> quote(s.value(),sb);
      case Info.Lst l -> writeList(l,sb);
      case Info.Obj o -> writeObj(o,indent,sb);
    }
  }
  private static void writeList(Info.Lst l, StringBuilder sb){
    sb.append('[');
    for (int i : Range.of(l.items())){
      if (i > 0){ sb.append(", "); }
      write(l.items().get(i),0,sb);
    }
    sb.append(']');
  }
  private static void writeObj(Info.Obj o, int indent, StringBuilder sb){
    if (o.fields().isEmpty()){ sb.append("{}"); return; }
    sb.append("{\n");
    var pad= "  ".repeat(indent+1);
    for (int i : Range.of(o.fields())){
      var f= o.fields().get(i);
      sb.append(pad);
      quote(f.key(),sb);
      sb.append(": ");
      write(f.value(),indent+1,sb);
      if (i+1 < o.fields().size()){ sb.append(','); }
      sb.append('\n');
    }
    sb.append("  ".repeat(indent)).append('}');
  }
  private static void quote(String value, StringBuilder sb){
    sb.append('"');
    for (int i : Range.of(0,value.length())){
      char c= value.charAt(i);
      switch(c){
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        default -> sb.append(c);
      }
    }
    sb.append('"');
  }
}
