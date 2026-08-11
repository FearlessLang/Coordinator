package managerMessages;

import java.nio.file.Path;

import tools.FearlessId;

public final class UserActionable {
  private UserActionable(){}
  public static UserProblem couldNotCreateManagerFolder(Path dir, Throwable cause){
    return UserProblem.processExitsWithTrace("""
      Fearless could not create its manager folder.

      Fearless tried to create this folder:
      %s

      %s
      The manager folder needs write permission.

      %s

      %s
      Move or unpack Fearless into a folder with write permission, then start
      Fearless again.""".formatted(
        ErrorText.path(dir.toString()),
        ErrorText.managerFolderIntro(),
        ErrorText.reported(cause),
        ErrorText.managerFolderRule()
      ), cause);
  }
  public static UserProblem couldNotUseInstanceLock(Throwable cause){
    return UserProblem.processExitsWithTrace("""
      Fearless could not use the file that marks the manager process.

      %s
      While a manager process runs, it keeps this file reserved, so that any
      new Fearless process can tell that a manager already exists. This
      feature is called file locking. Fearless tried to open and reserve:
      %s

      %s

      Common fixes are to use a manager folder with write permission, or to
      close another program that is using or blocking this folder.
      %s
      %s""".formatted(
        ErrorText.managerFolderIntro(),
        ErrorText.path(ErrorContext.lockFile()),
        ErrorText.reported(cause),
        ErrorText.blockingPrograms(),
        ErrorText.managerFolderRule()
      ), cause);
  }
  //The launcher told Fearless where it started it from, and no folder of the expected
  //name contains that location. This is about the shape of this copy of Fearless, which
  //the user controls, so the text states the shape instead of blaming the copy.
  public static UserProblem programFolderNotFound(Path startedFrom){
    return UserProblem.processExits("""
      Fearless could not find its own program folder.

      Fearless keeps all of its code inside one folder, named:
      %s
      Everything Fearless writes is then placed relative to that folder.

      Fearless was started from:
      %s
      and no folder named "%s" contains that location.

      This copy of Fearless has been renamed, or parts of it have been moved
      out of it. Replace it with a fresh copy, keeping the folder name.""".formatted(
        ErrorText.path(FearlessId.binFolder),
        ErrorText.path(startedFrom.toString()),
        FearlessId.binFolder
      ));
  }
}
