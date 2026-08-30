package managerTests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mainCoordinator.ResolveResource;
import managerInfo.FolderInfo;
import managerList.FolderList;
import tools.Fs;
import utils.Box;
import utils.ThrowingConsumer;

final class ManagerGuiShotTest{
  static final Path shots= ResolveResource.coordinatorSrc.getParent().resolve(".out").resolve("guiShots");
  private static void onEdt(Runnable r){ ThrowingConsumer.of(SwingUtilities::invokeAndWait).accept(r); }
  private static <T> T onEdtGet(Supplier<T> f){
    var out= new Box<T>(null);
    onEdt(()->out.set(f.get()));
    return out.get();
  }
  private static BufferedImage shoot(Supplier<JComponent> make, int width, int height, String name){
    return onEdtGet(()->draw(make.get(),width,height,name));
  }
  private static BufferedImage draw(JComponent c, int width, int height, String name){
    assert SwingUtilities.isEventDispatchThread();
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
  private static FolderList sorted(FolderList list, FolderList.Sort by){
    list.sortBy(by);
    return list;
  }
  @Test void theRegisteredFoldersLookLikeAGridOfTiles(@TempDir Path dir){
    var data= tenProjects(dir);
    var shot= shoot(()->new FolderList(data,_->false,_->{}),760,420,"folderList");
    assertTrue(colours(shot) > 40, "the tiles drew nothing");
  }
  @Test void orderingByNameIsTheSameTilesInAnotherOrder(@TempDir Path dir){
    var data= tenProjects(dir);
    var list= onEdtGet(()->new FolderList(data,_->false,_->{}));
    var byName= shoot(()->sorted(list,FolderList.Sort.Name),760,420,"folderListByName");
    var byRun= shoot(()->sorted(list,FolderList.Sort.Run),760,420,"folderListByRun");
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
    var shot= shoot(()->new FolderInfo(data,project.toAbsolutePath().normalize(),_->{},()->{}).panel(),720,420,"folderInfo");
    assertTrue(colours(shot) > 20, "the facts drew nothing");
  }
  @Test void aFolderWithBrokenNamesOffersItsReport(@TempDir Path dir){
    var data= new MockManagerData();
    var project= FolderFactsTest.project(dir,"brokenProject");
    Fs.writeUtf8(project.resolve("_hello").resolve("Bad.fear"),"");
    data.addRegisteredFolder(project);
    var shot= shoot(()->new FolderInfo(data,project.toAbsolutePath().normalize(),_->{},()->{}).panel(),720,420,"folderInfoBroken");
    assertTrue(colours(shot) > 20);
  }
}
