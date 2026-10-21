package naiveBackend;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import core.E.Literal;
import core.OtherPackages;
import docBuilder.HtmlDocBuilder;
import offensiveUtils.Require;
import tools.Fs;
import tools.SourceOracle;

public record BackendTools(String pkgName, List<Literal> decs, Path rootDir, HtmlDocBuilder docs, MagicConsistency checks, CapabilityEnvironment capabilities, Path rtPath){
  public BackendTools{
    assert Require.nonNull(pkgName, rootDir, docs, checks, capabilities);
    assert Require.unmodifiable(decs, "decs");
    assert Fs.exists(rtPath);
  }
  public static BackendTools of(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, Path rootDir, Optional<Path> baseCachePath, Path rtPath, CapabilityEnvironment capabilities){
    return of(pkgName, oracle, other, core, rootDir, baseCachePath, Optional.of(rootDir.getParent()), rtPath, capabilities);
  }
  public static BackendTools of(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, Path rootDir, Optional<Path> baseCachePath, Optional<Path> testsRoot, Path rtPath, CapabilityEnvironment capabilities){
    var docs= new HtmlDocBuilder(oracle, other, core, baseCachePath.map(p -> p.resolve("base.html")));
    docs.packageLocation(pkgName, Coordinator.genJava(rootDir).resolve(pkgName+".html"), testsRoot);
    return new BackendTools(pkgName, core, rootDir, docs, new MagicConsistency(pkgName, rtPath), capabilities, rtPath);
  }
}
