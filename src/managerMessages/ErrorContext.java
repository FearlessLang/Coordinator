package managerMessages;

import java.nio.file.Path;

/// The paths this run actually used, so that a message always shows the folders that
/// were really involved, whether they came from the per-user location of this operating
/// system or from next to the program folder.
/// ManagerMain calls set(..) as soon as it knows them; the same pattern as
/// coordinatorMessages.UserExit.root. A failure before that point carries its own path
/// as an argument, so the placeholders below are never what a user reads.
public final class ErrorContext {
  private ErrorContext(){}
  private static Path msgDir= Path.of("<not yet known>");
  private static Path lock= Path.of("<not yet known>");
  public static void set(Path msgDir, Path lock){ ErrorContext.msgDir= msgDir; ErrorContext.lock= lock; }
  static String messageFolder(){ return msgDir.toString(); }
  static String lockFile(){ return lock.toString(); }
}
