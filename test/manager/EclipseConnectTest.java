package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerData.Kind;
import managerData.ManagerData;
import tools.Fs;

final class EclipseConnectTest{
  private static ManagerData.Entry entry(String alias, Path path){
    return new ManagerData.Entry(alias,path,Kind.code,List.of(),Map.of(),Map.of(),-1,-1);
  }
  @Test void everyKnownProjectIsPublishedAsItsAliasThenItsPath(@TempDir Path dir){
    ManagerMain.managerDir= dir;
    var one= dir.resolve("one");
    var two= dir.resolve("with space");
    EclipseConnect.publish(List.of(entry("one",one),entry("two",two)));
    assertEquals("one "+one+"\ntwo "+two+"\n", Fs.readUtf8(dir.resolve("eclipse").resolve("projects.txt")));
    assertEquals(dir.resolve("eclipse").resolve("two"), EclipseConnect.reports("two"));
  }
  @Test void publishingAgainReplacesTheListing(@TempDir Path dir){
    ManagerMain.managerDir= dir;
    EclipseConnect.publish(List.of(entry("one",dir.resolve("one"))));
    EclipseConnect.publish(List.of());
    assertEquals("", Fs.readUtf8(dir.resolve("eclipse").resolve("projects.txt")));
  }
}
