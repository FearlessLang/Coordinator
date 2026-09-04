package managerIcons;

import java.awt.Image;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import userMessages.Violation;

public final class FolderIcon{
  private FolderIcon(){}
  public static Image image(Path folder, int size){
    var f= folder.toAbsolutePath().normalize();
    var name= FolderName.compactName(f);
    var png= iconFile(f,name);
    return Files.isRegularFile(png) ? read(png) : GeneratedIcon.of(name,size);
  }
  static Path iconFile(Path folder, String name){ return folder.resolve(".icons").resolve(name+".png"); }
  public static Image read(Path file){
    try {
      var res= ImageIO.read(file.toFile());
      if (res == null){ throw Violation.couldNotDecodeIcon(file); }//ImageIO answers null when no reader claims the file
      return res;
    }
    catch(IOException e){ throw Violation.couldNotLoadIcon(file, e); }
  }
}
