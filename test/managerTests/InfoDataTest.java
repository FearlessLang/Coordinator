package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerData.InfoData;
import managerData.Kind;
import managerData.ManagerData;
import tools.Fs;
import userMessages.UserError;

final class InfoDataTest{
  private static InfoData data(Path dir){ return new InfoData(dir); }
  private static Path folder(Path dir, String name){
    var res= dir.resolve(name);
    Fs.ensureDir(res);
    return res;
  }
  private static String unixPath(Path p){ return p.toAbsolutePath().normalize().toString().replace('\\','/'); }
  @Test void noFileYetIsNoRegisteredFolder(@TempDir Path dir){
    assertEquals(List.of(), data(dir).registered());
  }
  @Test void addedFolderIsIdleWithNoTimesAndNoLinks(@TempDir Path dir){
    var project= folder(dir,"someproject");
    data(dir).addRegisteredFolder("someproject",project);
    assertEquals(
      List.of(new ManagerData.Entry("someproject",project.toAbsolutePath().normalize(),Kind.idle,List.of(),Map.of(),Map.of(),-1,-1)),
      data(dir).registered());
  }
  @Test void addingTheSameFolderTwiceRegistersItOnce(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var d= data(dir);
    d.addRegisteredFolder("someproject",project);
    d.addRegisteredFolder("again",project.resolve("..").resolve("someproject"));
    assertEquals(1, data(dir).registered().size());
  }
  @Test void compileAndRunTimesSurviveAReRead(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var d= data(dir);
    d.addRegisteredFolder("someproject",project);
    d.setCompiled(project,111);
    d.setRun(project,222);
    var reread= data(dir).registered().getFirst();
    assertEquals(111, reread.compiled());
    assertEquals(222, reread.run());
  }
  @Test void mainsSurviveAReRead(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var d= data(dir);
    d.addRegisteredFolder("someproject",project);
    d.setSelectedMains(project,List.of("hello.Hello1","hello.Hello3"));
    assertEquals(List.of("hello.Hello1","hello.Hello3"), data(dir).registered().getFirst().mains());
  }
  @Test void forgettingAFolderRemovesOnlyThatOne(@TempDir Path dir){
    var kept= folder(dir,"kept");
    var gone= folder(dir,"gone");
    var d= data(dir);
    d.addRegisteredFolder("kept",kept);
    d.addRegisteredFolder("gone",gone);
    d.removeRegisteredFolder(gone);
    assertEquals(List.of(kept.toAbsolutePath().normalize()), d.registered().stream().map(ManagerData.Entry::path).toList());
    assertTrue(d.isRegistered(kept));
    assertFalse(d.isRegistered(gone));
  }
  @Test void awkwardFolderNamesSurviveAReRead(@TempDir Path dir){
    var project= folder(dir,"a name with spaces");
    var d= data(dir);
    d.addRegisteredFolder("spacey",project);
    assertEquals(project.toAbsolutePath().normalize(), data(dir).registered().getFirst().path());
  }
  @Test void aFolderInsideOrAroundARegisteredOneIsFound(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var d= data(dir);
    d.addRegisteredFolder("someproject",project);
    assertEquals(project, d.nestedWith(project.resolve("inside")).orElseThrow());
    assertEquals(project, d.nestedWith(dir).orElseThrow());
    assertEquals(Optional.empty(), d.nestedWith(folder(dir,"otherproject")));
  }
  @Test void aRegisteredFolderIsNotNestedWithItself(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var d= data(dir);
    d.addRegisteredFolder("someproject",project);
    assertEquals(Optional.empty(), d.nestedWith(project));
    d.addRegisteredFolder("someproject2",project);
    assertEquals(1, d.registered().size());
  }
  @Test void aSiblingNamedLikeAPrefixIsNotNested(@TempDir Path dir){
    var d= data(dir);
    d.addRegisteredFolder("some",folder(dir,"some"));
    assertEquals(Optional.empty(), d.nestedWith(folder(dir,"someproject")));
  }
  @Test void aCorruptedFileRefusesToLoadWithARichError(@TempDir Path dir){
    Fs.writeUtf8(dir.resolve("projects.info"), "not info at all");
    var e= assertThrows(UserError.class, ()->data(dir).registered());
    assertTrue(e.getMessage().contains("Expected a string, a list [...] or an object {...}"), e.getMessage());
  }
  @Test void commitInfoTextWritesAValidatedFile(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var d= data(dir);
    var text= "{\n  \"someproject\": {\"path\": \""+unixPath(project)+"\", \"kind\": \"code\"}\n}\n";
    d.commitInfoText(text);
    var entry= d.registered().getFirst();
    assertEquals("someproject", entry.alias());
    assertEquals(Kind.code, entry.kind());
    assertEquals(project.toAbsolutePath().normalize(), entry.path());
  }
  @Test void commitInfoTextRejectsAndLeavesDiskUnchanged(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var d= data(dir);
    d.addRegisteredFolder("someproject",project);
    var before= d.infoText();
    var badText= "{\n  \"someproject\": {\"path\": \""+unixPath(project)+"\", \"kind\": \"nonsense\"}\n}\n";
    assertThrows(UserError.class, ()->d.commitInfoText(badText));
    assertEquals(before, d.infoText());
  }
  @Test void commitInfoTextRejectsOverlappingPaths(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var d= data(dir);
    var text= "{\n"
      +"  \"a\": {\"path\": \""+unixPath(project)+"\"},\n"
      +"  \"b\": {\"path\": \""+unixPath(project)+"\"}\n"
      +"}\n";
    var e= assertThrows(UserError.class, ()->d.commitInfoText(text));
    assertTrue(e.getMessage().contains("has the same path as"), e.getMessage());
  }
  @Test void setKindSurvivesAReRead(@TempDir Path dir){
    var project= folder(dir,"someproject");
    var d= data(dir);
    d.addRegisteredFolder("someproject",project);
    d.setKind(project,Kind.dataReadWrite);
    assertEquals(Kind.dataReadWrite, data(dir).registered().getFirst().kind());
  }
  @Test void setLinksSurvivesAReReadAndKeepsReadsAndEditsIndependent(@TempDir Path dir){
    var code= folder(dir,"mycode");
    var pub= folder(dir,"pub");
    var d= data(dir);
    d.addRegisteredFolder("mycode",code);
    d.addRegisteredFolder("pub",pub);
    d.setKind(code,Kind.code);
    d.setKind(pub,Kind.dataReadWrite);
    d.setLinks(code,Map.of("pub",List.of("Data1","Data2")),Map.of("pub",List.of("Data1")));
    var reread= data(dir).registered().stream().filter(e->e.alias().equals("mycode")).findFirst().orElseThrow();
    assertEquals(Map.of("pub",List.of("Data1","Data2")), reread.reads());
    assertEquals(Map.of("pub",List.of("Data1")), reread.edits());
  }
  @Test void turningACodeProjectIntoDataKeepsItsMainsReadsAndEdits(@TempDir Path dir){
    var code= folder(dir,"mycode");
    var pub= folder(dir,"pub");
    var d= data(dir);
    d.commitInfoText("""
      {
      "pub": {"path": "%s", "kind": "data:readWrite"},
      "mycode": {"path": "%s", "kind": "code", "mains": ["my.Main1"], "reads": {"pub": ["Data1"]}}
      }
      """.formatted(unixPath(pub),unixPath(code)));
    d.commitInfoText(d.infoText().replace("\"kind\": \"code\"","\"kind\": \"idle\""));
    var entry= d.registered().stream().filter(e->e.alias().equals("mycode")).findFirst().orElseThrow();
    assertEquals(Kind.idle, entry.kind());
    assertEquals(List.of("my.Main1"), entry.mains());
    assertEquals(Map.of("pub",List.of("Data1")), entry.reads());
  }
}
