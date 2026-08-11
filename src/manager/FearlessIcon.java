package manager;

import java.awt.Image;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import mainCoordinator.LauncherProps;
import managerMessages.RuntimeBroken;

//The Fearless icon, the same file and the same place Fearless's own window reads it from
//(mainCoordinator.Main): the deploy copies it into $APPDIR next to stdLib, so it travels
//inside the program folder and cannot fall out of sync with the packaged icons.
//Loaded once, on purpose, at a moment ManagerMain chose (see Manager.runOwner): a copy of
//Fearless with no readable icon is a damaged copy, and says so before opening a window
//rather than from inside the window code, where the failure would read as a GUI failure.
final class FearlessIcon {
  private FearlessIcon(){}
  private static Image image;
  static void load(){ image= read(LauncherProps.reqAppDir().resolve("icon.png")); }
  static Image image(){
    assert image != null: "load() runs before any window exists";
    return image;
  }
  private static Image read(Path file){
    try {
      var res= ImageIO.read(file.toFile());
      if (res == null){ throw RuntimeBroken.couldNotDecodeIcon(file); }//ImageIO answers null when no reader claims the file
      return res;
    }
    catch(IOException e){ throw RuntimeBroken.couldNotLoadIcon(file, e); }
  }
}
