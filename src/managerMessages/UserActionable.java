package managerMessages;

import java.nio.file.Path;

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

      Move or unpack Fearless into a folder with write permission, then start
      Fearless again.""".formatted(
        ErrorText.path(dir.toString()),
        ErrorText.managerFolderIntro(),
        ErrorText.reported(cause)
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
      """.formatted(
        ErrorText.managerFolderIntro(),
        ErrorText.path(ErrorContext.lockFile()),
        ErrorText.reported(cause),
        ErrorText.blockingPrograms()
      ), cause);
  }
}
