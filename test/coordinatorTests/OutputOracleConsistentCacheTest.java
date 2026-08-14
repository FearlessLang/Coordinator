package coordinatorTests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import coordinator.OutputOracle;
import core.E.Literal;
import core.RC;
import core.Src;
import core.TName;
import utils.Pos;

// Regression test for a bug in coordinator.OutputHelper.consistent(cachedMap, core):
// it decides whether the api json already on disk still matches `core`, so
// commitPkgApi can skip the rewrite (and the stamp bump) when nothing changed.
// ApiJson.toJSon deliberately writes EVERY type, public and private (see its own
// comment: "we do not filter _names"), so the round-tripped cached map always has one
// entry per type in `core`, private types included. consistent() used to finish with
// `map.size() == core.stream().filter(isPublic).count()` - comparing the full map size
// against the PUBLIC-only count - so a package with a private type could never pass as
// consistent, and every build rewrote its api json and bumped its cache stamp even
// though nothing changed. Fixed by dropping that redundant/wrong final check: the
// earlier size check plus the per-entry eqApi loop already fully establish equality.
final class OutputOracleConsistentCacheTest{
  @Test void unchangedPackageWithAPrivateTypeIsRecognizedAsUnchanged(@TempDir Path tmp){
    OutputOracle out= ()->tmp;
    var core= List.of(new Literal(RC.imm, new TName("_Priv", 0, Pos.unknown),
      List.of(), List.of(), "this", List.of(), Src.syntetic, false));
    long firstStamp= out.commitPkgApi("pkg", core, -1);
    long secondStamp= out.commitPkgApi("pkg", core, firstStamp);
    // Committing the exact same `core` a second time must be a no-op: commitPkgApi
    // returns minExclusiveMillis unchanged when the cache is still consistent.
    assertEquals(firstStamp, secondStamp);
  }
}
