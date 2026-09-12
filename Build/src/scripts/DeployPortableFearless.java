// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/DeployPortableFearless.java
package scripts;

import java.util.List;
import resources.ResolveResource;
import tools.PortableApp;

public class DeployPortableFearless{
  public static void main(String[] a) throws InterruptedException{
    new PortableApp(
      ResolveResource.packaging,
      ResolveResource.portableFolderOut,//out
      List.of(
        List.of(ResolveResource.commonsSrc),
        List.of(ResolveResource.frontendSrc, ResolveResource.frontendSrcModule),
        List.of(ResolveResource.coordinatorSrc, ResolveResource.coordinatorSrcModule)),
      ResolveResource.stLibPath,//base
      ResolveResource.stLibRTPath,//rt
      ResolveResource.coordinatorJars,
      "fearlessBin"+ResolveResource.versionId,
      ResolveResource.versionId,
      "Coordinator/mainCoordinator.Main"
    ).build();
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorTest();
    ModularBuild.deployBaseCache(ResolveResource.portableFolderOut.resolve("fearlessBin"+ResolveResource.versionId));
  }
}