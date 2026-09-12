package docBuilder;

import java.nio.file.Path;

import core.E.*;
import core.M;

public interface DocBuilder{
  void packageLocation(String pkgName, Path htmlPath, Path testPath);
  void visitLiteral(Literal l);
  void visitDeclaredM(Literal owner, M m);
  void visitImportedM(Literal owner, M m);
  void complete();
}