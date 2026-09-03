package managerInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import tools.Fs;

public final class LogFiles{
  private static final DateTimeFormatter stampFmt= DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter shown= DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
  public record Entry(Path path, Instant when) implements Comparable<Entry>{
    @Override public String toString(){ return path.getFileName()+"   "+shown.format(when); }
    @Override public int compareTo(Entry o){ return o.when.compareTo(when); }
  }
  private static Path dir(Path folder){ return folder.resolve(".out").resolve("logs"); }
  public static List<Entry> list(Path folder){
    var dir= dir(folder);
    if (!Files.isDirectory(dir)){ return List.of(); }
    return Fs.walk(dir,s->s
      .filter(Files::isRegularFile)
      .filter(p->p.getFileName().toString().endsWith(".log"))
      .map(LogFiles::entry)
      .sorted()
      .toList());
  }
  private static Entry entry(Path p){ return new Entry(p,decode(p).orElseGet(()->Instant.ofEpochMilli(Fs.lastModified(p)))); }
  public static Optional<Instant> decode(Path p){
    var name= p.getFileName().toString();
    var dollar= name.lastIndexOf('$');
    if (dollar < 0){ return Optional.empty(); }
    var stamp= name.substring(dollar+1,name.length()-".log".length());
    try{ return Optional.of(Instant.from(stampFmt.parse(stamp))); }
    catch(DateTimeParseException e){ return Optional.empty(); }
  }
}
