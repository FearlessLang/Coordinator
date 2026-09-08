package naiveBackend;

import java.nio.file.Path;
import java.util.List;

import coordinator.CapabilityEnvironment;
import core.E.Literal;
import docBuilder.DocBuilder;

public record BackendTools(String pkgName, List<Literal> decs, Path rootDir, DocBuilder docs, MagicConsistency checks, CapabilityEnvironment capabilities, Path rtPath){
  public static BackendTools of(String pkgName, List<Literal> decs, Path rootDir, DocBuilder docs, Path rtPath, CapabilityEnvironment capabilities){
    return new BackendTools(pkgName, decs, rootDir, docs, new MagicConsistency(rtPath), capabilities, rtPath);
  }
}
