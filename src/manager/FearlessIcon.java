package manager;

import java.awt.Image;
import java.nio.file.Path;

import managerIcons.FolderIcon;
import tools.JavacTool;
import userMessages.Violation;

final class FearlessIcon {
  private static Image image= FolderIcon.read(file());
  static Image image(){ return image; }
  static Path file(){ return JavacTool.reqAppDir(Violation::mustUseLauncher).resolve("icon.png"); }//TODO: refactor this file into an enum with needed resources
}
