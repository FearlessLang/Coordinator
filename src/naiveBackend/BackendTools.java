package naiveBackend;

import static offensiveUtils.Require.unmodifiable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import coordinator.CapabilityEnvironment;
import core.E.Literal;
import docBuilder.DocBuilder;

public record BackendTools(String pkgName, List<Literal> decs, Path rootDir, DocBuilder docs, MagicConsistency checks, CapabilityEnvironment capabilities, Path rtPath){
  public BackendTools{
    assert unmodifiable(decs, "decs");
    assert Files.exists(rtPath): "Missing extra folder: "+rtPath;
  }
  public static BackendTools of(String pkgName, List<Literal> decs, Path rootDir, DocBuilder docs, Path rtPath, CapabilityEnvironment capabilities){
    return new BackendTools(pkgName, decs, rootDir, docs, new MagicConsistency(rtPath), capabilities, rtPath);
  }
}
