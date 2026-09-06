package managerData;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import managerData.Info.Obj.Field;
import metaParser.Frame;
import metaParser.Message;
import metaParser.Span;
import tools.Fs;
import userMessages.Report;
import userMessages.UserError;

public final class InfoParser{
  private final String text;
  private final URI uri;
  private int i= 0;
  private int line= 1;
  private int col= 1;
  private InfoParser(String text, URI uri){ this.text= text; this.uri= uri; }
  public static Info parse(String text, URI uri){ return new InfoParser(text,uri).parseAll(); }
  private Info parseAll(){
    ws();
    var v= value();
    ws();
    if (more()){ throw err(here(),"Unexpected extra text after the end of the value."); }
    return v;
  }
  private Info value(){
    ws();
    if (!more()){ throw err(here(),"The text ends here, but a value (a string, a list [...] or an object {...}) was expected."); }
    char c= peek();
    if (c == '"'){ return str(); }
    if (c == '['){ return list(); }
    if (c == '{'){ return obj(); }
    throw err(here(),"Expected a string, a list [...] or an object {...} here.");
  }
  private Info.Str str(){
    int sLine= line, sCol= col;
    advance();
    var sb= new StringBuilder();
    while(true){
      if (!more()){ throw err(spanAt(sLine,sCol,line,col),"This string is never closed with a matching \"."); }
      char c= peek();
      if (c == '"'){ int eLine= line, eCol= col; advance(); return new Info.Str(sb.toString(),spanAt(sLine,sCol,eLine,eCol)); }
      if (c == '\n'){ throw err(here(),"A string cannot contain a raw newline; use \\n instead."); }
      if (c == '\\'){ advance(); sb.append(escape()); continue; }
      sb.append(c); advance();
    }
  }
  private char escape(){
    if (!more()){ throw err(here(),"Expected an escape character after \\."); }
    int eLine= line, eCol= col;
    char c= advance();
    return switch(c){
      case '"' -> '"';
      case '\\' -> '\\';
      case 'n' -> '\n';
      default -> throw err(spanAt(eLine,eCol,line,col),"Unknown escape \\"+c+": only \\\", \\\\ and \\n are recognized.");
    };
  }
  private Info.Lst list(){
    int sLine= line, sCol= col;
    advance();
    ws();
    var items= new ArrayList<Info>();
    if (more() && peek() == ']'){ int eLine= line, eCol= col; advance(); return new Info.Lst(items,spanAt(sLine,sCol,eLine,eCol)); }
    while(true){
      items.add(value());
      ws();
      if (!more()){ throw err(spanAt(sLine,sCol,line,col),"This list is never closed with a matching ]."); }
      char c= peek();
      if (c == ']'){ int eLine= line, eCol= col; advance(); return new Info.Lst(items,spanAt(sLine,sCol,eLine,eCol)); }
      if (c != ','){ throw err(here(),"Expected ',' or ']' here."); }
      advance(); ws();
    }
  }
  private Info.Obj obj(){
    int sLine= line, sCol= col;
    advance();
    ws();
    var fields= new ArrayList<Field>();
    if (more() && peek() == '}'){ int eLine= line, eCol= col; advance(); return new Info.Obj(fields,spanAt(sLine,sCol,eLine,eCol)); }
    while(true){
      ws();
      if (!more() || peek() != '"'){ throw err(here(),"Expected a quoted key here."); }
      var key= str();
      ws();
      if (!more() || peek() != ':'){ throw err(here(),"Expected ':' after the key."); }
      advance();
      var val= value();
      fields.add(new Field(key.value(),key.span(),val));
      ws();
      if (!more()){ throw err(spanAt(sLine,sCol,line,col),"This object is never closed with a matching }."); }
      char c= peek();
      if (c == '}'){ checkNoDuplicateKeys(fields); int eLine= line, eCol= col; advance(); return new Info.Obj(fields,spanAt(sLine,sCol,eLine,eCol)); }
      if (c != ','){ throw err(here(),"Expected ',' or '}' here."); }
      advance(); ws();
    }
  }
  private void checkNoDuplicateKeys(List<Field> fields){
    var seen= new HashMap<String,Field>();
    for (var f: fields){
      var prev= seen.putIfAbsent(f.key(),f);
      if (prev != null){ throw err(f.keySpan(),"Duplicate key \""+f.key()+"\": this object already has this key."); }
    }
  }
  private void ws(){
    while(more()){
      char c= peek();
      if (c == ' ' || c == '\n'){ advance(); continue; }
      if (c == '/' && i+1 < text.length() && text.charAt(i+1) == '/'){ while(more() && peek() != '\n'){ advance(); } continue; }
      return;
    }
  }
  private boolean more(){ return i < text.length(); }
  private char peek(){ return text.charAt(i); }
  private char advance(){
    var c= text.charAt(i);
    if (Fs.allowed.indexOf(c) < 0){ throw err(here(),"This character is outside Fearless's safe character set."); }
    i+= 1;
    if (c == '\n'){ line+= 1; col= 1; } else { col+= 1; }
    return c;
  }
  private Span here(){ return spanAt(line,col,line,col); }
  private Span spanAt(int sLine, int sCol, int eLine, int eCol){
    return new Span(uri,sLine,sCol,eLine,Math.max(eCol,sCol));
  }
  private UserError err(Span span, String msg){ return err(text,span,msg); }
  public static UserError err(String source, Span span, String msg){
    return Report.infoError(Message.of(_->source,List.of(new Frame("",span)),msg));
  }
}
