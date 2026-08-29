package manager;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import fileAssociations.FileAssociations;
import fileAssociations.Icon;
import userMessages.Violation;

final class FileAssociation{
  private FileAssociation(){}
  static final Predicate<String> belongsToFamily= s->s.contains("earless");
  static Optional<Path> launcher(){ return ProcessHandle.current().info().command().map(Path::of); }
  ///The launcher names us: one identity per Fearless, and the manager's own is not the portable one.
  static String identity(Path launcher){
    var file= launcher.getFileName().toString();
    var dot= file.lastIndexOf('.');
    return dot < 0 ? file : file.substring(0, dot);
  }
  static List<Icon> extensions(Path launcher){ return List.of(new Icon(".fearless", launcher, FearlessIcon.file())); }
  static void reconcile(Path launcher, List<Icon> extensions){
    var identity= identity(launcher);
    if (!belongsToFamily.test(identity)){ return; }
    FileAssociations.reconcile(identity, belongsToFamily, launcher, extensions, launcher, FearlessIcon.file(),
      reported->Violation.associationsAmbiguous(reported).withRecovery(
        "Remove all Fearless registrations", FileAssociation::eradicateAll),
      Violation::associationUserLocked,
      Violation::associationNotOurs,
      Violation::associationNotWritable,
      Violation::associationLeftHalfDone);
  }
  private static void eradicateAll(){
    FileAssociations.eradicateAll(belongsToFamily, Violation::associationLeftHalfDone);
  }
}
