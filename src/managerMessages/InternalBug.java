package managerMessages;

public final class InternalBug {
  private InternalBug(){}
  public static UserProblem managerWorkerStopped(){
    return UserProblem.managerFatal("""
      %s

      A required part of the manager process stopped, even though it should
      keep running for the whole lifetime of the manager process.

      %s""".formatted(ErrorText.crashed(), ErrorText.reportProblem()));
  }
  public static UserProblem managerWorkerFailed(Throwable cause){
    return UserProblem.managerFatalWithTrace("""
      %s

      A required part of the manager process failed with an unexpected Java
      exception ("exception" is the Java term for a reported failure).

      %s""".formatted(ErrorText.crashed(), ErrorText.reportProblem()), cause);
  }
  public static UserProblem managerInterrupted(InterruptedException cause){
    return UserProblem.managerFatalWithTrace("""
      %s

      Java stopped a part of the manager process unexpectedly ("interruption"
      is the Java term for this stop request).

      %s""".formatted(ErrorText.crashed(), ErrorText.reportProblem()), cause);
  }
  public static UserProblem unhandledThrowable(Throwable cause){
    return UserProblem.managerFatalWithTrace("""
      %s

      An unexpected Java error reached the top-level Fearless error handler.

      %s""".formatted(ErrorText.crashed(), ErrorText.reportProblem()), cause);
  }
}
