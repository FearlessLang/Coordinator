package managerData;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import managerData.ManagerData.Entry;
import realSourceOracle.RealSourceOracleWithZip;
import userMessages.UserError;

public final class LinkCheck{
  private LinkCheck(){}
  public static Optional<String> problem(Entry e, List<Entry> all){
    if (e.kind() != Kind.code){ return Optional.empty(); }
    var byAlias= all.stream().collect(Collectors.toMap(Entry::alias,Function.identity(),(a,_)->a));
    return problemIn(e.reads(),"reads",false,byAlias).or(()->problemIn(e.edits(),"edits",true,byAlias));
  }
  private static Optional<String> problemIn(Map<String,List<String>> links, String field, boolean needsWrite, Map<String,Entry> byAlias){
    for (var alias: links.keySet()){
      var target= byAlias.get(alias);
      if (target == null){
        return Optional.of("\""+field+"\" refers to \""+alias+"\", but no project with that name is registered.");
      }
      var wrongKind= needsWrite ? target.kind() != Kind.dataReadWrite : !target.kind().isData();
      if (wrongKind){
        var needed= needsWrite ? "\"data:readWrite\"" : "a data project";
        return Optional.of("\""+field+"\" refers to \""+alias+"\", but that project's kind is \""+target.kind().infoText()+"\", not "+needed+".");
      }
      try{ new RealSourceOracleWithZip(target.path()); }
      catch(UserError err){ return Optional.of("\""+field+"\" refers to \""+alias+"\", which is itself invalid:\n"+err.getMessage()); }
    }
    return Optional.empty();
  }
}
