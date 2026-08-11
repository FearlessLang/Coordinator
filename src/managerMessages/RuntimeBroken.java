package managerMessages;

import java.nio.file.Path;

public final class RuntimeBroken {
  private RuntimeBroken(){}
  public static UserProblem vmOrLinkageFailure(Throwable cause){
    return UserProblem.withTrace("""
      Fearless crashed because of a low-level JVM failure.

      Fearless includes its own packaged JVM. The packaged JVM failed, or the
      packaged Fearless code could not be linked correctly.

      %s""".formatted(ErrorText.freshCopyThenReport()), cause);
  }
  //The icon travels inside the program folder: Fearless put it there when it was built.
  //Failing to load it is not a fact about the user's files, it is a fact about this copy
  //of Fearless, so it is reported like the other damaged-copy failures.
  public static UserProblem couldNotLoadIcon(Path icon, Throwable cause){
    return UserProblem.processExitsWithTrace("""
      Fearless could not load its own icon.

      The icon is part of Fearless and travels inside its program folder:
      %s

      %s

      %s""".formatted(
        ErrorText.path(icon.toString()),
        ErrorText.reported(cause),
        ErrorText.freshCopyThenReport()
      ), cause);
  }
  public static UserProblem couldNotDecodeIcon(Path icon){
    return UserProblem.processExits("""
      Fearless could not load its own icon.

      The icon is part of Fearless and travels inside its program folder:
      %s
      The file is there and could be read, but it does not hold an image this
      Java runtime can decode.

      %s""".formatted(
        ErrorText.path(icon.toString()),
        ErrorText.freshCopyThenReport()
      ));
  }
}
