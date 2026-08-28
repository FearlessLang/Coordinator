package manager;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import tools.FileAssociations;
import userMessages.Violation;

final class FileAssociation{
  private FileAssociation(){}
  static final List<String> extensions= List.of(".fearless");
  static Optional<Path> launcher(){ return ProcessHandle.current().info().command().map(Path::of); }
  ///The launcher names us: one name per Fearless, and the manager's own is not the portable one.
  static String name(Path launcher){
    var file= launcher.getFileName().toString();
    var dot= file.lastIndexOf('.');
    return dot < 0 ? file : file.substring(0, dot);
  }
  //On Windows the icon of a file type is taken from a program, not from a picture:
  //the launcher carries the one jpackage put in it.
  static FileAssociations of(Path launcher){
    return FileAssociations.of(name(launcher), launcher.toString(), launcher, FearlessIcon.file(),
      Violation::fileIsNotYoursToChange,
      Violation::couldNotOpenFearlessProjects,
      Violation::fearlessProjectsStillOpenWith,
      Violation::associationLeftHalfDone,
      Violation::associationFilesHalfThere);
  }
}
