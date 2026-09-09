package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.Fs;

final class EclipseConnectTest{
  @Test void aProjectFolderBecomesAnEclipseProjectUnderTheNameTheManagerGaveIt(@TempDir Path project){
    EclipseConnect.writeDescription(project,"my_app");
    assertTrue(Fs.readUtf8(project.resolve(".project")).contains("<name>my_app</name>"));
  }
  @Test void anEclipseProjectTheUserAlreadyHasIsLeftAlone(@TempDir Path project){
    Fs.writeUtf8(project.resolve(".project"),"mine\n");
    EclipseConnect.writeDescription(project,"my_app");
    assertEquals("mine\n", Fs.readUtf8(project.resolve(".project")));
  }
}
