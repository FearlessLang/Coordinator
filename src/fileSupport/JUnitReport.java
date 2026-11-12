package fileSupport;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import tools.Fs;

/// A Fearless unit test log rendered as JUnit XML.
public final class JUnitReport{
  private static final Pattern disabled= Pattern.compile("(?m)^PLAN\tDISABLED\t([^\t\\r\\n]*)\t([^\t\\r\\n]*)\t([^\t\\r\\n]*)\t([^\t\\r\\n]*)$");
  public static String suite(String name, Path folder){
    return newest(folder).map(e -> suiteOf(name, e.path())).orElse("");
  }
  public static String document(String suites){ return """
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
%s</testsuites>
""".formatted(suites);
  }
  private static Optional<LogFiles.Entry> newest(Path folder){ return unitTestLogs(folder).stream().findFirst(); }
  /// the unit test logs of the project, newest first
  public static List<LogFiles.Entry> unitTestLogs(Path folder){
    var base= folder.resolve(LogFiles.runDir).resolve("logs").resolve("_base");
    return LogFiles.list(folder).stream()
      .filter(e -> e.path().getParent().equals(base) && e.path().getFileName().toString().matches("unit_test_log(\\$.*)?\\.log"))
      .toList();
  }
  public static String suiteOf(String name, Path log){
    var body= disabled.matcher(complete(log)).replaceAll(JUnitReport::skipped)
      .replaceAll("(?m)^PLAN\tRUN\t.*$\\r?\\n?", "");
    return """
<testsuite name="%s" tests="%s" failures="%s" errors="0">
%s</testsuite>
""".formatted(name, count(body, "<testcase "), count(body, "<failure>"), body);
  }
  private static String complete(Path log){
    var bytes= Fs.of(() -> Fs.readAllBytes(log));
    var end= bytes.length;
    while (end>0 && bytes[end-1]!='\n'){ end -= 1; }
    var whole= ByteBuffer.wrap(bytes, 0, end);
    var text= Fs.of(() -> UTF_8.newDecoder().decode(whole).toString());
    var open= Pattern.compile("(?dm)^<testcase ").matcher(text);
    return open.find(text.lastIndexOf("</testcase>\n")+1) ? text.substring(0, open.start()) : text;
  }
  private static int count(String body, String what){ return body.split(what, -1).length-1; }
  private static String skipped(MatchResult mr){
    return "<testcase classname=\""+xmlAttr(mr.group(2))+"\" name=\""+xmlAttr(mr.group(3))
      +"\" file=\""+xmlAttr(mr.group(1))+"\" line=\""+mr.group(4)+"\"><skipped/></testcase>";
  }
  private static String xmlAttr(String s){ return s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;"); }
}
