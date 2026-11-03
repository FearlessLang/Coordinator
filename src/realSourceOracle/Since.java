package realSourceOracle;

import java.nio.file.Path;
import java.util.function.Supplier;

import tools.Fs;
import userMessages.Report;

public record Since(long time, String projectName){
  public static final Since anyTime= new Since(Long.MAX_VALUE, "");
  public <T> T read(Path file, Supplier<T> load){
    if (equals(anyTime)){ return load.get(); }
    T res;
    try{ res= load.get(); }
    catch (RuntimeException e){ throw updated(file) ? Report.filesUpdated(projectName) : e; }
    if (updated(file)){ throw Report.filesUpdated(projectName); }
    return res;
  }
  private boolean updated(Path file){
    var t= Fs.lastModified(file);
    return t < 0 || t > time;
  }
}
