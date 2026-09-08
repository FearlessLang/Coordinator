package naiveBackend;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import core.TName;
import core.M;
import core.E.Call;
import core.E.Literal;
import core.E.Type;
import tools.NativeOverrides;
import utils.Pos;

final class MagicConsistency{
  private static final TName magicName= new TName("base.Magic", 0,Pos.unknown);
  private final NativeOverrides natives;
  private final Set<String> magicPairs= new HashSet<>();
  MagicConsistency(Path rtPath){ this.natives= NativeOverrides.scan(rtPath); }
  private boolean isMagicBody(M m){
    return m.e().get() instanceof Call c && c.e() instanceof Type t && t.type().c().name().equals(magicName);
  }
  private boolean hasRealBody(Literal l){
    return l.ms().stream().anyMatch(m->m.sig().origin().equals(l.name()) && !m.sig().abs() && !isMagicBody(m));
  }
  void checkTopMethod(M m, String typeName, String jName, boolean hasInstance){
    if (isMagicBody(m)){
      if (!natives.hasFile(typeName)){ magicPairs.add(typeName+"#"+jName); }
    }
    else {
      assert hasInstance || !natives.has(typeName, jName):
        typeName+"."+jName+" has a real Fearless body and a hand-written rt/ override of the same method";
    }
  }
  void checkFileReplacement(Literal l, String typeName){
    if (!natives.hasFile(typeName)){ return; }
    assert l.name().equals(magicName) || !hasRealBody(l):
      typeName+" is fully replaced by a hand-written rt/ file but still has a real (non-abstract, non-Magic!) method of its own";
  }
  void checkMagicFulfilled(){
    for (var pair: magicPairs){
      assert natives.pairs().contains(pair): pair+" is a Magic! method with no hand-written rt/ override";
    }
  }
}
