package managerData;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import core.TName;
import managerData.Info.Obj.Field;
import managerData.ManagerData.Entry;
import managerIcons.FolderName;
import metaParser.Span;
import userMessages.UserError;

public final class EntrySchema{
  private EntrySchema(){}
  private static final List<String> entryKeys= List.of("path","kind","mains","reads","edits");
  private static final Path dummyRoot= Path.of("").toAbsolutePath();
  public static List<Entry> fromInfo(String source, Info root){
    if (!(root instanceof Info.Obj top)){
      throw err(source,root.span(),"The whole file must be an object mapping each project's name to its metadata.");
    }
    var entries= new ArrayList<Entry>();
    for (var field: top.fields()){
      if (!FolderName.isName(dummyRoot,field.key())){
        throw err(source,field.keySpan(),"\""+field.key()+"\" is not a valid project name: use lowercase letters, digits and underscore, starting with a letter or underscore.");
      }
      entries.add(entryOf(source,field));
    }
    checkNoOverlappingPaths(source,top.fields(),entries);
    return entries;
  }
  private static Entry entryOf(String source, Field field){
    if (!(field.value() instanceof Info.Obj obj)){
      throw err(source,field.value().span(),"The metadata for \""+field.key()+"\" must be an object.");
    }
    for (var f: obj.fields()){
      if (!entryKeys.contains(f.key())){
        throw err(source,f.keySpan(),"Unknown project attribute \""+f.key()+"\": expected one of "+entryKeys+".");
      }
    }
    var path= pathOf(source,field.key(),obj);
    var kind= kindOf(source,obj);
    var mains= nameListOf(source,obj,"mains","\"mains\"",EntrySchema::isMainName,mainShape);
    var reads= aliasMapOf(source,obj,"reads");
    var edits= aliasMapOf(source,obj,"edits");
    return new Entry(field.key(),path,kind,mains,reads,edits,-1,-1);
  }
  private static Path pathOf(String source, String alias, Info.Obj obj){
    var field= obj.field("path").orElseThrow(()->err(source,obj.span(),"Project \""+alias+"\" is missing its \"path\"."));
    if (!(field.value() instanceof Info.Str s)){ throw err(source,field.value().span(),"\"path\" must be a string."); }
    if (s.value().isEmpty()){ throw err(source,field.value().span(),"\"path\" cannot be empty."); }
    Path path;
    try{ path= Path.of(s.value()); }
    catch(InvalidPathException e){ throw err(source,field.value().span(),"\"path\" is not a valid file system path: "+e.getMessage()); }
    if (!path.isAbsolute()){ throw err(source,field.value().span(),"\"path\" must be an absolute path."); }
    return path.normalize();
  }
  private static Kind kindOf(String source, Info.Obj obj){
    var field= obj.field("kind");
    if (field.isEmpty()){ return Kind.idle; }
    if (!(field.get().value() instanceof Info.Str s)){ throw err(source,field.get().value().span(),"\"kind\" must be a string."); }
    return Kind.of(s.value()).orElseThrow(()->err(source,field.get().value().span(),
      "\"kind\" must be one of \"idle\", \"code\", \"data:readOnly\" or \"data:readWrite\", not \""+s.value()+"\"."));
  }
  private static List<String> nameListOf(String source, Info.Obj obj, String key, String label, Predicate<String> ok, String shape){
    var field= obj.field(key);
    if (field.isEmpty()){ return List.of(); }
    if (!(field.get().value() instanceof Info.Lst l)){ throw err(source,field.get().value().span(),label+" must be a list."); }
    var seen= new LinkedHashSet<String>();
    var out= new ArrayList<String>();
    for (var item: l.items()){
      var s= nameOf(source,item,label,ok,shape);
      if (!seen.add(s.value())){ throw err(source,s.span(),"\""+s.value()+"\" is repeated in "+label+"."); }
      out.add(s.value());
    }
    return List.copyOf(out);
  }
  private static Map<String,List<String>> aliasMapOf(String source, Info.Obj obj, String key){
    var field= obj.field(key);
    if (field.isEmpty()){ return Map.of(); }
    if (!(field.get().value() instanceof Info.Obj o)){
      throw err(source,field.get().value().span(),"\""+key+"\" must be an object mapping a project name to a list of names.");
    }
    var out= new LinkedHashMap<String,List<String>>();
    for (var f: o.fields()){
      if (!FolderName.isName(dummyRoot,f.key())){ throw err(source,f.keySpan(),"\""+f.key()+"\" in \""+key+"\" is not a valid project name."); }
      out.put(f.key(),nameListOf(source,o,f.key(),"\""+key+"\".\""+f.key()+"\"",TName::isTypeName,typeShape));
    }
    return Collections.unmodifiableMap(out);
  }
  private static final String typeShape= "a valid Fearless type name: it must start with an uppercase letter";
  private static final String mainShape= "a valid Fearless main name: a package name, a dot, then a type name, like \"hello.Hello1\"";
  private static boolean isMainName(String s){
    var dot= s.indexOf('.');
    return dot > 0 && TName.isPkgName(s.substring(0,dot)) && TName.isTypeName(s.substring(dot+1));
  }
  private static Info.Str nameOf(String source, Info value, String label, Predicate<String> ok, String shape){
    if (!(value instanceof Info.Str s)){ throw err(source,value.span(),"Every entry in "+label+" must be a string."); }
    if (!ok.test(s.value())){ throw err(source,s.span(),"\""+s.value()+"\" in "+label+" is not "+shape+"."); }
    return s;
  }
  private static void checkNoOverlappingPaths(String source, List<Field> fields, List<Entry> entries){
    for (var a= 0; a < entries.size(); a+= 1){
      for (var b= a+1; b < entries.size(); b+= 1){
        var pa= entries.get(a).path();
        var pb= entries.get(b).path();
        if (!pa.equals(pb) && !pa.startsWith(pb) && !pb.startsWith(pa)){ continue; }
        var pathSpan= ((Info.Obj)fields.get(b).value()).field("path").orElseThrow().value().span();
        throw err(source,pathSpan,
          "\""+entries.get(b).alias()+"\" has the same path as \""+entries.get(a).alias()+"\", or one is inside the other.");
      }
    }
  }
  public static Info toInfo(List<Entry> entries){
    var fields= entries.stream().map(e->new Field(e.alias(),noSpan,entryToInfo(e))).toList();
    return new Info.Obj(fields,noSpan);
  }
  private static Info entryToInfo(Entry e){
    var fields= new ArrayList<Field>();
    fields.add(new Field("path",noSpan,new Info.Str(e.path().toString().replace('\\','/'),noSpan)));
    fields.add(new Field("kind",noSpan,new Info.Str(e.kind().infoText(),noSpan)));
    if (!e.mains().isEmpty()){ fields.add(new Field("mains",noSpan,strList(e.mains()))); }
    if (!e.reads().isEmpty()){ fields.add(new Field("reads",noSpan,aliasMap(e.reads()))); }
    if (!e.edits().isEmpty()){ fields.add(new Field("edits",noSpan,aliasMap(e.edits()))); }
    return new Info.Obj(fields,noSpan);
  }
  private static Info strList(List<String> xs){ return new Info.Lst(xs.stream().<Info>map(x->new Info.Str(x,noSpan)).toList(),noSpan); }
  private static Info aliasMap(Map<String,List<String>> m){
    return new Info.Obj(m.entrySet().stream().map(e->new Field(e.getKey(),noSpan,strList(e.getValue()))).toList(),noSpan);
  }
  private static final Span noSpan= new Span(URI.create("info:synthetic"),1,1,1,1);
  private static UserError err(String source, Span span, String msg){ return InfoParser.err(source,span,msg); }
}
