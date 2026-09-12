package naiveBackend;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import core.TName;
import core.M;
import core.E.Call;
import core.E.Literal;
import core.E.Type;
import tools.NativeOverrides;
import utils.Pos;

public final class MagicConsistency{
  private static final TName magicName= new TName("base.Magic", 0,Pos.unknown);
  private final Optional<NativeOverrides> natives;
  private final Set<String> magicPairs= new HashSet<>();
  MagicConsistency(String pkgName, Path rtPath){
    this.natives= pkgName.equals("base") ? Optional.of(NativeOverrides.scan(rtPath)) : Optional.empty();
  }
  private boolean isMagicBody(M m){
    return m.e().get() instanceof Call c && c.e() instanceof Type t && t.type().c().name().equals(magicName);
  }
  private boolean hasRealBody(Literal l){
    return l.ms().stream().anyMatch(m->m.sig().origin().equals(l.name()) && !m.sig().abs() && !isMagicBody(m));
  }
  void checkTopMethod(M m, String typeName, String jName, boolean hasInstance){
    if (natives.isEmpty()){ return; }
    var nat= natives.get();
    if (isMagicBody(m)){
      if (!nat.hasFile(typeName)){ magicPairs.add(typeName+"#"+jName); }
    }
    else {
      assert hasInstance || !nat.has(typeName, jName):
        typeName+"."+jName+" has a real Fearless body and a hand-written rt/ override of the same method";
    }
  }
  void checkFileReplacement(Literal l, String typeName){
    if (natives.isEmpty() || !natives.get().hasFile(typeName)){ return; }
    assert l.name().equals(magicName) || !hasRealBody(l):
      typeName+" is fully replaced by a hand-written rt/ file but still has a real (non-abstract, non-Magic!) method of its own";
  }
  void checkMagicFulfilled(){
    if (natives.isEmpty()){ return; }
    for (var pair: magicPairs){
      assert natives.get().pairs().contains(pair): pair+" is a Magic! method with no hand-written rt/ override";
    }
  }
}
