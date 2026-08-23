package manager;

import java.awt.Image;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import tools.JavacTool;
import userMessages.Violation;

final class FearlessIcon {
  private static Image image= read(JavacTool.reqAppDir(Violation::mustUseLauncher).resolve("icon.png"));
  static Image image(){ return image; }//TODO: refactor this file into an enum with needed resources
  private static Image read(Path file){
    try {
      var res= ImageIO.read(file.toFile());
      if (res == null){ throw Violation.couldNotDecodeIcon(file); }//ImageIO answers null when no reader claims the file
      return res;
    }
    catch(IOException e){ throw Violation.couldNotLoadIcon(file, e); }
  }
}
