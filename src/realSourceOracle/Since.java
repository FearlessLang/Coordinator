package realSourceOracle;

import java.nio.file.Path;
import java.util.function.Supplier;

import tools.Fs;
import userMessages.Report;

public record Since(long time, String projectName){
  public static final Since anyTime= new Since(Long.MAX_VALUE, "");
  public <T> T read(Path file, Supplier<T> load){
    T res;
    try{ res= load.get(); }
    catch (RuntimeException e){ throw Fs.lastModified(file) > time ? Report.filesUpdated(projectName) : e; }
    if (Fs.lastModified(file) > time){ throw Report.filesUpdated(projectName); }
    return res;
  }
}
