// java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/DeployManagedFearless.java
package mainCoordinator;

import java.util.List;
import tools.PortableApp;

public class DeployManagedFearless{
  public static void main(String[] a) throws InterruptedException{
    var appRoot= ResolveResource.managedFolderOut.resolve("fearlessManaged"+ResolveResource.versionId);
    new PortableApp(
      ResolveResource.packaging,
      ResolveResource.managedFolderOut,//out
      List.of(
        List.of(ResolveResource.commonsSrc),
        List.of(ResolveResource.frontendSrc, ResolveResource.frontendSrcModule),
        List.of(ResolveResource.coordinatorSrc, ResolveResource.coordinatorSrcModule),
        List.of(ResolveResource.controllerSrc, ResolveResource.controllerSrcModule)),
      ResolveResource.stLibPath,//base
      ResolveResource.stLibRTPath,//rt
      ResolveResource.coordinatorJars,
      "fearlessManaged"+ResolveResource.versionId,
      ResolveResource.versionId,
      "Controller/manager.ManagerMain"
    ).build();
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorTest();
    ModularBuild.deployBaseCache(appRoot);
    ModularBuild.deployEclipsePlugin(appRoot);
  }
}