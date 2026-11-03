package realSourceOracle;

import tools.SourceOracle.Ref;
import userMessages.Report;

public record Since(long time, String projectName){
  public static final Since anyTime= new Since(Long.MAX_VALUE, "");
  byte[] read(byte[] bytes, Ref r){
    if (r.lastModified() > time){ throw Report.filesUpdated(projectName); }
    return bytes;
  }
}
