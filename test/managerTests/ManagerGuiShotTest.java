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
import managerData.Kind;
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
      Fs.writeUtf8(project.resolve(name+".fearless"),"");
      data.addRegisteredFolder(name,project);
      data.setKind(project,Kind.code);
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
    Fs.writeUtf8(project.resolve("someProject.fearless"),"");
    FolderFactsTest.cache(project,"hello",FolderFactsTest.after(project));
    data.addRegisteredFolder("someProject",project);
    data.setKind(project,Kind.code);
    data.setCompiled(project,System.currentTimeMillis()-3600000);
    data.setRun(project,System.currentTimeMillis()-60000);
    var shot= shoot(()->new FolderInfo(data,project.toAbsolutePath().normalize(),dir.resolve("reports"),_->{},()->{}).panel(),720,420,"folderInfo");
    assertTrue(colours(shot) > 20, "the facts drew nothing");
  }
  @Test void aFolderWithBrokenNamesOffersItsReport(@TempDir Path dir){
    var data= new MockManagerData();
    var project= FolderFactsTest.project(dir,"brokenProject");
    Fs.writeUtf8(project.resolve("_hello").resolve("Bad.fear"),"");
    Fs.writeUtf8(project.resolve("brokenProject.fearless"),"");
    data.addRegisteredFolder("brokenProject",project);
    data.setKind(project,Kind.code);
    var shot= shoot(()->new FolderInfo(data,project.toAbsolutePath().normalize(),dir.resolve("reports"),_->{},()->{}).panel(),720,420,"folderInfoBroken");
    assertTrue(colours(shot) > 20);
  }
  @Test void aFolderWithLogsListsThemInTheLogsSection(@TempDir Path dir){
    var data= new MockManagerData();
    var project= FolderFactsTest.project(dir,"loggedProject").toAbsolutePath().normalize();
    Fs.writeUtf8(project.resolve(".out").resolve("logs").resolve("_base").resolve("log$20260904_123456_789Z.log"),"hello\n");
    Fs.writeUtf8(project.resolve("loggedProject.fearless"),"");
    data.addRegisteredFolder("loggedProject",project);
    var info= onEdtGet(()->new FolderInfo(data,project,dir.resolve("reports"),_->{},()->{}));
    var shot= shoot(info::panel,720,420,"folderInfoWithLogs");
    assertTrue(colours(shot) > 20);
  }
  @Test void anIdleProjectOffersTheThreeBecomeButtons(@TempDir Path dir){
    var data= new MockManagerData();
    var project= FolderFactsTest.project(dir,"idleProject");
    Fs.writeUtf8(project.resolve("idleProject.fearless"),"");
    data.addRegisteredFolder("idleProject",project);
    var shot= shoot(()->new FolderInfo(data,project.toAbsolutePath().normalize(),dir.resolve("reports"),_->{},()->{}).panel(),720,420,"folderInfoIdle");
    assertTrue(colours(shot) > 20, "the become-buttons drew nothing");
  }
  @Test void aDataReadWriteProjectOffersLinksToRegisteredCodeProjects(@TempDir Path dir){
    var data= new MockManagerData();
    var pub= FolderFactsTest.project(dir,"pub");
    Fs.writeUtf8(pub.resolve("pub.fearless"),"");
    data.addRegisteredFolder("pub",pub);
    data.setKind(pub,Kind.dataReadWrite);
    var code= FolderFactsTest.project(dir,"mycode");
    Fs.writeUtf8(code.resolve("mycode.fearless"),"");
    data.addRegisteredFolder("mycode",code);
    data.setKind(code,Kind.code);
    var shot= shoot(()->new FolderInfo(data,pub.toAbsolutePath().normalize(),dir.resolve("reports"),_->{},()->{}).panel(),720,420,"folderInfoDataReadWrite");
    assertTrue(colours(shot) > 20, "the links row drew nothing");
  }
}
