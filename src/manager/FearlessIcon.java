package manager;

import java.awt.Image;

import managerIcons.FolderIcon;
import tools.JavacTool;
import userMessages.Violation;

final class FearlessIcon {
  private static Image image= FolderIcon.read(JavacTool.reqAppDir(Violation::mustUseLauncher).resolve("icon.png"));
  static Image image(){ return image; }//TODO: refactor this file into an enum with needed resources
}
