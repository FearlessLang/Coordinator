package naiveBackend;

import java.nio.file.Path;

import coordinator.CapabilityEnvironment;
import docBuilder.DocBuilder;

public record BackendTools(DocBuilder docs, MagicConsistency checks, CapabilityEnvironment capabilities){
  public static BackendTools of(DocBuilder docs, Path rtPath, CapabilityEnvironment capabilities){
    return new BackendTools(docs, new MagicConsistency(rtPath), capabilities);
  }
}
