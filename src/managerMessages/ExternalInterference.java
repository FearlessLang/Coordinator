package managerMessages;

public final class ExternalInterference {
  private ExternalInterference(){}
  //Reading a message file is delegated to fileSupport; this covers the
  //remaining folder operations of a drain: listing, and removing files.
  public static UserProblem couldNotDrainMessageFolder(Throwable cause){
    return UserProblem.managerFatalWithTrace("""
      Fearless could not list its manager folder, or could not remove a
      message file from it.

      The manager folder is:
      %s
      A file may have been deleted, locked, or changed while Fearless was
      using it, or the folder itself may be blocked.

      %s""".formatted(
        ErrorText.path(ErrorContext.messageFolder()),
        ErrorText.reported(cause)
      ), cause);
  }
  public static UserProblem messageFolderNotWatchable(){
    return UserProblem.managerFatal("""
      Fearless can no longer watch its manager folder.

      %s
      Fearless was watching this folder:
      %s
      Fearless could watch it when the manager process started, but cannot
      anymore. Watching a folder means asking the operating system to tell
      Fearless when files appear in it (file-change notifications).

      The folder may have been removed, replaced, or disconnected, or the
      operating system may have stopped sending file-change notifications
      for it.""".formatted(
        ErrorText.managerFolderIntro(),
        ErrorText.path(ErrorContext.messageFolder())
      ));
  }
}
