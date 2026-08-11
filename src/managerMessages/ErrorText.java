package managerMessages;

import tools.FearlessId;

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
  //Fearless is not asked where to keep what it writes, and does not ask the operating
  //system either: it reads where its own code sits and follows the convention of that
  //place. Every message naming the manager folder also states this rule, so that the
  //folder in the message is never a folder out of nowhere.
  static String managerFolderRule(){ return """
    Fearless offers no setting for this folder. It reads where its own program
    folder "%s" sits, and follows the convention of that place:
    - when the program folder sits where this operating system keeps installed
      programs, the manager folder goes where this operating system keeps what
      one program writes for one user;
    - otherwise the manager folder goes next to the program folder, named "%s".
    """.formatted(FearlessId.binFolder, FearlessId.dataFolder);
  }
  static String blockingPrograms(){ return """
    Programs that may use or block this folder include security software
    (antivirus, ransomware protection, endpoint protection), backup tools,
    sync tools, and file preview tools.
    """;
  }
}
