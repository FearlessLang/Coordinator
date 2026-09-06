package managerIcons;

import java.awt.Image;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import tools.Fs;
import userMessages.Violation;

public final class FolderIcon{
  private FolderIcon(){}
  public static Image image(Path folder, int size){
    var f= folder.toAbsolutePath().normalize();
    var png= pngIcon(f);
    return png.isPresent() ? read(png.get()) : GeneratedIcon.of(FolderName.compactName(f),size);
  }
  static Path iconDir(Path folder){ return folder.resolve(".config").resolve("icon"); }
  private static Optional<Path> pngIcon(Path folder){
    var dir= iconDir(folder);
    if (!Files.isDirectory(dir)){ return Optional.empty(); }
    var pngs= Fs.of(()->{ try(var s= Files.list(dir)){ return s
      .filter(Files::isRegularFile)
      .filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
      .sorted()
      .toList();
    }});
    if (pngs.isEmpty()){ return Optional.empty(); }
    if (pngs.size() > 1){ throw Violation.multipleIcons(dir,pngs); }
    return Optional.of(pngs.getFirst());
  }
  public static Image read(Path file){
    try {
      var res= ImageIO.read(file.toFile());
      if (res == null){ throw Violation.couldNotDecodeIcon(file); }//ImageIO answers null when no reader claims the file
      return res;
    }
    catch(IOException e){ throw Violation.couldNotLoadIcon(file, e); }
  }
}
