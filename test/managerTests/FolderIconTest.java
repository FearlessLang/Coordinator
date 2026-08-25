package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerIcons.FolderIcon;
import managerIcons.FolderName;
import tools.Fs;

final class FolderIconTest{
  static Path folder(Path dir, String name){
    var res= dir.resolve(name);
    Fs.ensureDir(res);
    return res;
  }
  static void png(Path file, Color color){
    var img= new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB);
    var g= img.createGraphics();
    g.setColor(color);
    g.fillRect(0,0,16,16);
    g.dispose();
    Fs.ensureDir(file.getParent());
    Fs.ofV(()->ImageIO.write(img,"png",file.toFile()));
  }
  private static BufferedImage image(Path folder){ return (BufferedImage)FolderIcon.image(folder,64); }
  @Test void withNothingElseTheFolderNameIsTheName(@TempDir Path dir){
    assertEquals("someProject", FolderName.compactName(folder(dir,"someProject")));
  }
  @Test void theOneTopLevelFearlessFileNamesTheFolder(@TempDir Path dir){
    var project= folder(dir,"someProject");
    Fs.writeUtf8(project.resolve("my_game.fearless"),"");
    assertEquals("my_game", FolderName.compactName(project));
  }
  @Test void withMoreThanOneFearlessFileTheFolderNameIsTheName(@TempDir Path dir){
    var project= folder(dir,"someProject");
    Fs.writeUtf8(project.resolve("my_game.fearless"),"");
    Fs.writeUtf8(project.resolve("other.fearless"),"");
    assertEquals("someProject", FolderName.compactName(project));
  }
  @Test void aNestedFearlessFileDoesNotNameTheFolder(@TempDir Path dir){
    var project= folder(dir,"someProject");
    Fs.writeUtf8(project.resolve("inside").resolve("my_game.fearless"),"");
    assertEquals("someProject", FolderName.compactName(project));
  }
  @Test void theIconIsTheDotIconsPngNamedAfterTheFolder(@TempDir Path dir){
    var project= folder(dir,"someProject");
    png(project.resolve(".icons").resolve("someProject.png"), Color.red);
    var got= image(project);
    assertEquals(16, got.getWidth());
    assertEquals(Color.red.getRGB(), got.getRGB(3,3));
  }
  @Test void aDotIconsPngNamedAfterSomethingElseIsNotTheIcon(@TempDir Path dir){
    var project= folder(dir,"someProject");
    png(project.resolve(".icons").resolve("aaa.png"), Color.red);
    assertEquals(64, image(project).getWidth());
  }
  @Test void theFearlessFileNameAlsoChoosesTheIcon(@TempDir Path dir){
    var project= folder(dir,"someProject");
    Fs.writeUtf8(project.resolve("my_game.fearless"),"");
    png(project.resolve(".icons").resolve("someProject.png"), Color.red);
    png(project.resolve(".icons").resolve("my_game.png"), Color.blue);
    assertEquals(Color.blue.getRGB(), image(project).getRGB(3,3));
  }
  @Test void withNoIconOneIsMadeUpOfTheRightSize(@TempDir Path dir){
    var got= image(folder(dir,"someProject"));
    assertEquals(64, got.getWidth());
    assertEquals(64, got.getHeight());
  }
  @Test void theMadeUpIconOnlyDependsOnTheName(@TempDir Path dir){
    var same= folder(dir,"twin").resolve("someProject");
    Fs.ensureDir(same);
    assertEquals(pixels(image(folder(dir,"someProject"))), pixels(image(same)));
  }
  @Test void differentNamesAreToldApart(@TempDir Path dir){
    assertNotEquals(pixels(image(folder(dir,"someProject"))), pixels(image(folder(dir,"otherProject"))));
  }
  @Test void theMadeUpIconIsFourDifferentColoursUnderReadableLetters(@TempDir Path dir){
    var got= image(folder(dir,"someProject"));
    var corners= new LinkedHashSet<>(List.of(got.getRGB(4,4),got.getRGB(59,4),got.getRGB(4,59),got.getRGB(59,59)));
    assertEquals(4, corners.size());
    var ink= pixels(got).stream().filter(c->c == Color.white.getRGB() || c == Color.black.getRGB()).count();
    assertTrue(ink > 100, "letters drawn on "+ink+" pixels");
  }
  private static List<Integer> pixels(BufferedImage img){
    return Arrays.stream(img.getRGB(0,0,img.getWidth(),img.getHeight(),null,0,img.getWidth())).boxed().toList();
  }
}
