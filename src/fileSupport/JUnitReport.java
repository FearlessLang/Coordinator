package fileSupport;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import tools.Fs;

/// A Fearless unit test log rendered as JUnit XML. `write` is for the Eclipse plugin
/// (FearlessLang/EclipsePlugin), which polls that file into the JUnit view.
public final class JUnitReport{
  private JUnitReport(){}
  private static final Pattern disabled= Pattern.compile("(?m)^PLAN\\|DISABLED\\|([^|\\r\\n]*)\\|([^|\\r\\n]*)\\|([^|\\r\\n]*)\\|([^|\\r\\n]*)$");
  public static Path file(Path reports){ return reports.resolve("report.xml"); }
  public static void write(Path reports, Path folder, String name, Instant since){
    var log= newest(folder).filter(e->e.when().isAfter(since));
    if (log.isEmpty()){ return; }
    Fs.writeUtf8(file(reports), document(suiteOf(name, log.get().path())));
  }
  public static String suite(String name, Path folder){
    return newest(folder).map(e->suiteOf(name, e.path())).orElse("");
  }
  public static String document(String suites){ return """
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
%s</testsuites>
""".formatted(suites);
  }
  private static Optional<LogFiles.Entry> newest(Path folder){
    return LogFiles.list(folder).stream()
      .filter(e->e.path().getFileName().toString().startsWith("unit_test_log"))
      .findFirst();
  }
  private static String suiteOf(String name, Path log){
    var body= disabled.matcher(Fs.readUtf8(log)).replaceAll(JUnitReport::skipped)
      .replaceAll("(?m)^PLAN\\|RUN\\|.*$\\r?\\n?","");
    return """
<testsuite name="%s" tests="%d" failures="%d" errors="0">
%s</testsuite>
""".formatted(name, count(body,"<testcase "), count(body,"<failure>"), body);
  }
  private static int count(String body, String what){ return body.split(what,-1).length-1; }
  private static String skipped(MatchResult mr){
    return "<testcase classname=\""+xmlAttr(mr.group(2))+"\" name=\""+xmlAttr(mr.group(3))
      +"\" file=\""+xmlAttr(mr.group(1))+"\" line=\""+mr.group(4)+"\"><skipped/></testcase>";
  }
  private static String xmlAttr(String s){ return s.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;"); }
}
