// java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/DeployPortableFearless.java
package mainCoordinator;

import tools.PortableApp;

public class DeployPortableFearless{
  public static void main(String[] a) throws InterruptedException{
    new PortableApp(
      ResolveResource.packaging,
      ResolveResource.portableFolderOut,//out
      ResolveResource.commonsSrc,
      ResolveResource.frontendSrc,
      ResolveResource.frontendSrcModule,
      ResolveResource.coordinatorSrc,
      ResolveResource.coordinatorSrcModule,
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