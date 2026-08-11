package managerMessages;

public final class EnvironmentBusy {
  private EnvironmentBusy(){}
  public static UserProblem couldNotLeaveStartMessage(Throwable cause){
    //Written by EVERY starting process, before we know whether a manager
    //already exists; the text must not claim that one does.
    //Only the final atomic rename is reported here: writing the message file
    //itself is delegated to fileSupport, which explains its own failures.
    return UserProblem.processExitsWithTrace("""
      Fearless could not leave its start message.

      %s
      Every starting Fearless process writes one small message file into this
      folder:
      %s
      The manager process (an already running one, or the process that is
      starting right now) then reads and removes those files. The message
      file was written, but renaming it to its final name failed.

      %s

      A manager process may still be running: do not delete the manager
      folder. If the problem repeats, close programs that may be using or
      blocking the folder.
      %s""".formatted(
        ErrorText.managerFolderIntro(),
        ErrorText.path(ErrorContext.messageFolder()),
        ErrorText.reported(cause),
        ErrorText.blockingPrograms()
      ), cause);
  }
}
