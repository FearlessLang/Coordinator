package managerMessages;


//The pieces of text that more than one message needs, written once.
final class ErrorText {
  private ErrorText(){}
  static String reported(Throwable cause){ return "Reported reason:\n"+cause.getMessage(); }
  static String path(String path){ return "  "+path; }
  static String crashed(){ return "Fearless crashed."; }
  static String reportProblem(){ return "Please report this problem."; }
  static String associatedPrograms(){ return "  <Will return the set of active and monitored Fearless programs>"; }//TODO: later
  static String freshCopyThenReport(){ return """
    Replace this Fearless folder with a fresh copy.
    If this keeps happening, report the problem.
    """;
  }
  static String managerFolderIntro(){ return """
    Fearless uses one process (called the manager process)
    to keep track of other Fearless processes (the user processes).
    The manager folder is the folder used by the manager process to store
    coordination information into files.
    """;
  }
  static String blockingPrograms(){ return """
    Programs that may use or block this folder include security software
    (antivirus, ransomware protection, endpoint protection), backup tools,
    sync tools, and file preview tools.
    """;
  }
}
