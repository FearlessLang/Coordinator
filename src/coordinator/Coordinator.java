package coordinator;

import core.OtherPackages;
import tools.SourceOracle;

public interface Coordinator {
  core.E frontend(String pkgName, SourceOracle oracle, OtherPackages other);
  void backend(String pkgName, SourceOracle oracle, OtherPackages other);
}
