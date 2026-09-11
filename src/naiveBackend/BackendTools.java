package naiveBackend;

import static offensiveUtils.Require.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import coordinator.CapabilityEnvironment;
import core.E.Literal;
import core.OtherPackages;
import docBuilder.DocBuilder;
import docBuilder.HtmlDocBuilder;
import tools.SourceOracle;

public record BackendTools(String pkgName, List<Literal> decs, Path rootDir, DocBuilder docs, MagicConsistency checks, CapabilityEnvironment capabilities, Path rtPath){
  public BackendTools{
    assert nonNull(pkgName,rootDir,docs,checks,capabilities);
    assert unmodifiable(decs, "decs");
    assert Files.exists(rtPath): "Missing extra folder: "+rtPath;
  }
  public static BackendTools of(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, Path rootDir, Optional<Path> baseCachePath, Path rtPath, CapabilityEnvironment capabilities){
    var testFileDest= rootDir.getParent().resolve("auto_tests","_"+pkgName,pkgName+"_test.fear");
    return of(pkgName, oracle, other, core, rootDir, baseCachePath, testFileDest, rtPath, capabilities);
  }
  public static BackendTools of(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, Path rootDir, Optional<Path> baseCachePath, Path testFileDest, Path rtPath, CapabilityEnvironment capabilities){
    var docs= new HtmlDocBuilder(oracle,other,core,baseCachePath.map(p->p.resolve("base.html")));
    docs.packageLocation(pkgName, rootDir.resolve("gen_java",pkgName+".html"), testFileDest);
    return new BackendTools(pkgName, core, rootDir, docs, new MagicConsistency(rtPath), capabilities, rtPath);
  }
}
