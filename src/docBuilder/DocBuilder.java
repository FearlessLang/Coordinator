package docBuilder;

import java.nio.file.Path;

import core.E.*;
import core.M;

public interface DocBuilder{
  void packageLocation(String pkgName, Path htmlPath);
  void visitLiteral(Literal l);
  void visitDeclaredM(Literal owner, M m);
  void visitImportedM(Literal owner, M m);
  void complete();

  static DocBuilder none(){ return new DocBuilder(){
    public void packageLocation(String pkgName, Path htmlPath){}
    public void visitLiteral(Literal l){}
    public void visitDeclaredM(Literal owner, M m){}
    public void visitImportedM(Literal owner, M m){}
    public void complete(){}
  };}
}