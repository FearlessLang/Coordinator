package managerTests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.JComponent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mainCoordinator.ResolveResource;
import managerInfo.FolderInfo;
import managerList.FolderList;
import tools.Fs;

final class ManagerGuiShotTest{
  static final Path shots= ResolveResource.coordinatorSrc.getParent().resolve(".out").resolve("guiShots");
  private static BufferedImage shoot(JComponent c, int width, int height, String name){
    c.setSize(width,height);
    layout(c);
    var res= new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
    var g= res.createGraphics();
    g.setColor(Color.white);
    g.fillRect(0,0,width,height);
    c.printAll(g);
    g.dispose();
    Fs.ensureDir(shots);
    Fs.ofV(()->ImageIO.write(res,"png",shots.resolve(name+".png").toFile()));
    return res;
  }
  private static void layout(Component c){
    if (!(c instanceof Container p)){ return; }
    p.doLayout();
    for (var kid: p.getComponents()){ layout(kid); }
  }
  private static long colours(BufferedImage img){
    return java.util.Arrays.stream(img.getRGB(0,0,img.getWidth(),img.getHeight(),null,0,img.getWidth())).distinct().count();
  }
  private static MockManagerData tenProjects(Path dir){
    var data= new MockManagerData();
    for (var name: new String[]{"someProject","otherProject","map_editor","webShop","hello","sudoku","payroll","tetris","notes","weather"}){
      var project= FolderFactsTest.project(dir,name);
      data.addRegisteredFolder(project);
      data.setCompiled(project,System.currentTimeMillis()-60000);
    }
    data.setRun(dir.resolve("tetris"),System.currentTimeMillis());
    return data;
  }
  @Test void theRegisteredFoldersLookLikeAGridOfTiles(@TempDir Path dir){
    var list= new FolderList(tenProjects(dir),_->{});
    var shot= shoot(list,760,420,"folderList");
    assertTrue(colours(shot) > 40, "the tiles drew nothing");
  }
  @Test void orderingByNameIsTheSameTilesInAnotherOrder(@TempDir Path dir){
    var list= new FolderList(tenProjects(dir),_->{});
    list.sortBy(FolderList.Sort.Name);
    var byName= shoot(list,760,420,"folderListByName");
    list.sortBy(FolderList.Sort.Run);
    var byRun= shoot(list,760,420,"folderListByRun");
    assertTrue(colours(byName) > 40);
    assertTrue(colours(byRun) > 40);
  }
  @Test void oneFolderShowsItsFactsAndWhatCanBeDoneToIt(@TempDir Path dir){
    var data= new MockManagerData();
    var project= FolderFactsTest.project(dir,"someProject");
    Fs.writeUtf8(project.resolve("my_game.fearless"),"");
    FolderFactsTest.cache(project,"hello",FolderFactsTest.after(project));
    data.addRegisteredFolder(project);
    data.setCompiled(project,System.currentTimeMillis()-3600000);
    data.setRun(project,System.currentTimeMillis()-60000);
    var info= new FolderInfo(data,project.toAbsolutePath().normalize(),_->{},()->{}).panel();
    var shot= shoot(info,720,420,"folderInfo");
    assertTrue(colours(shot) > 20, "the facts drew nothing");
  }
  @Test void aFolderWithBrokenNamesOffersItsReport(@TempDir Path dir){
    var data= new MockManagerData();
    var project= FolderFactsTest.project(dir,"brokenProject");
    Fs.writeUtf8(project.resolve("_hello").resolve("Bad.fear"),"");
    data.addRegisteredFolder(project);
    var info= new FolderInfo(data,project.toAbsolutePath().normalize(),_->{},()->{}).panel();
    var shot= shoot(info,720,420,"folderInfoBroken");
    assertTrue(colours(shot) > 20);
  }
}
