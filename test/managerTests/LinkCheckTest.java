package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerData.Kind;
import managerData.LinkCheck;
import managerData.ManagerData.Entry;
import tools.Fs;

final class LinkCheckTest{
  private static Path folder(Path dir, String name){
    var res= dir.resolve(name);
    Fs.writeUtf8(res.resolve("readme"),"hi\n");
    return res;
  }
  private static Entry entry(String alias, Path path, Kind kind, Map<String,List<String>> reads, Map<String,List<String>> edits){
    return new Entry(alias,path,kind,List.of(),reads,edits,-1,-1);
  }
  @Test void aNonCodeEntryNeverHasALinkProblem(@TempDir Path dir){
    var e= entry("a",folder(dir,"a"),Kind.idle,Map.of("missing",List.of("X")),Map.of());
    assertEquals(java.util.Optional.empty(), LinkCheck.problem(e,List.of(e)));
  }
  @Test void readingFromARegisteredReadOnlyDataProjectIsFine(@TempDir Path dir){
    var pub= entry("pub",folder(dir,"pub"),Kind.dataReadOnly,Map.of(),Map.of());
    var code= entry("code",folder(dir,"code"),Kind.code,Map.of("pub",List.of("Data1")),Map.of());
    assertEquals(java.util.Optional.empty(), LinkCheck.problem(code,List.of(pub,code)));
  }
  @Test void editingARegisteredReadWriteDataProjectIsFine(@TempDir Path dir){
    var pub= entry("pub",folder(dir,"pub"),Kind.dataReadWrite,Map.of(),Map.of());
    var code= entry("code",folder(dir,"code"),Kind.code,Map.of(),Map.of("pub",List.of("Data1")));
    assertEquals(java.util.Optional.empty(), LinkCheck.problem(code,List.of(pub,code)));
  }
  @Test void readingFromAMissingAliasIsADeadLink(@TempDir Path dir){
    var code= entry("code",folder(dir,"code"),Kind.code,Map.of("nosuchproject",List.of("Data1")),Map.of());
    var problem= LinkCheck.problem(code,List.of(code));
    assertTrue(problem.orElseThrow().contains("no project with that name is registered"), problem.orElseThrow());
  }
  @Test void editingAReadOnlyDataProjectIsRejected(@TempDir Path dir){
    var pub= entry("pub",folder(dir,"pub"),Kind.dataReadOnly,Map.of(),Map.of());
    var code= entry("code",folder(dir,"code"),Kind.code,Map.of(),Map.of("pub",List.of("Data1")));
    var problem= LinkCheck.problem(code,List.of(pub,code));
    assertTrue(problem.orElseThrow().contains("not \"data:readWrite\""), problem.orElseThrow());
  }
  @Test void readingFromACodeProjectIsRejected(@TempDir Path dir){
    var other= entry("other",folder(dir,"other"),Kind.code,Map.of(),Map.of());
    var code= entry("code",folder(dir,"code"),Kind.code,Map.of("other",List.of("Data1")),Map.of());
    var problem= LinkCheck.problem(code,List.of(other,code));
    assertTrue(problem.orElseThrow().contains("not a data project"), problem.orElseThrow());
  }
  @Test void readingFromAStructurallyInvalidDataProjectIsADeadLink(@TempDir Path dir){
    var pub= dir.resolve("pub");
    Fs.writeUtf8(pub.resolve("Bad Name.txt"),"hi\n");
    var pubEntry= entry("pub",pub,Kind.dataReadOnly,Map.of(),Map.of());
    var code= entry("code",folder(dir,"code"),Kind.code,Map.of("pub",List.of("Data1")),Map.of());
    var problem= LinkCheck.problem(code,List.of(pubEntry,code));
    assertTrue(problem.orElseThrow().contains("which is itself invalid"), problem.orElseThrow());
  }
}
