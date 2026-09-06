// Builds fearlessManaged, a runnable app-image of the Fearless project manager GUI, real JPMS modules. See development-guide.txt. Run from Coordinator/test:
//   java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/DeployManagedFearless.java
package mainCoordinator;

import tools.PortableApp;

public class DeployManagedFearless{
  public static void main(String[] a) throws Exception{
    new PortableApp(
      ResolveResource.packaging,
      ResolveResource.managedFolderOut,//out
      ResolveResource.commonsSrc,
      ResolveResource.frontendSrc,
      ResolveResource.frontendSrcModule,
      ResolveResource.coordinatorSrc,
      ResolveResource.coordinatorSrcModule,
      ResolveResource.stLibPath,//base
      ResolveResource.stLibRTPath,//rt
      ResolveResource.coordinatorJars,
      "fearlessManaged"+ResolveResource.versionId,
      ResolveResource.versionId,
      "Coordinator/manager.ManagerMain"
    ).build();
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorTest();
    ModularBuild.deployBaseCache(ResolveResource.managedFolderOut.resolve("fearlessManaged"+ResolveResource.versionId));
  }
}
