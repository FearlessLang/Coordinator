package managerMessages;

public final class EnvironmentBroken {
  private EnvironmentBroken(){}
  public static UserProblem couldNotWatchMessageFolder(Throwable cause){
    return UserProblem.processExitsWithTrace("""
      Fearless could not watch its manager folder.

      %s
      Fearless asked the operating system to tell it when files appear in this
      folder:
      %s
      This feature is called file-change notifications, and the request failed.

      %s""".formatted(
        ErrorText.managerFolderIntro(),
        ErrorText.path(ErrorContext.messageFolder()),
        ErrorText.reported(cause)
      ), cause);
  }
  public static UserProblem couldNotStartGui(Throwable cause){
    return UserProblem.processExitsWithTrace("""
      Fearless could not open its window.

      Fearless needs to show a window, but opening the window failed.

      %s""".formatted(ErrorText.reported(cause)), cause);
  }
  //Reached only where Fearless must name the folder this operating system reserves for
  //one program's per-user data: there is no honest guess to make for a system whose
  //conventions Fearless does not know.
  public static UserProblem unsupportedOperatingSystem(){
    return UserProblem.processExits("""
      Fearless does not know this operating system.

      Fearless runs on Windows, macOS and Linux. It needs to know which one it
      is running on to find the folder this system keeps for what one program
      writes for one user.

      The operating system reported itself as:
      %s""".formatted(ErrorText.path(System.getProperty("os.name","<not reported>"))));
  }
}
