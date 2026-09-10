package managerInfo;

import java.nio.file.Path;
import java.util.regex.Pattern;

import tools.Fs;

/// Written for the Eclipse plugin (FearlessLang/EclipsePlugin), which polls this file
/// and turns it into one Problems view marker: relative file path, line number, then
/// the message. Nothing in the Coordinator reads it back.
public final class ProblemReport{
  private ProblemReport(){}
  private static final Pattern at= Pattern.compile("(?m)^In file: fear:/(\\S+)\\n\\n(\\d+)\\| ");
  public static Path file(Path reports){ return reports.resolve("problems.txt"); }
  public static void write(Path reports, String message){ Fs.writeUtf8(file(reports), body(message)); }
  private static String body(String message){
    var m= at.matcher(message);
    return m.find() ? m.group(1)+"\n"+m.group(2)+"\n"+message : "";
  }
}
