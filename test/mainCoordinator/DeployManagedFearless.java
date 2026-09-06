package mainCoordinator;

import tools.PortableApp;

public class DeployManagedFearless{
  public static void main(String[] a){
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
    BaseCacheBuilder.deployInto(ResolveResource.managedFolderOut.resolve("fearlessManaged"+ResolveResource.versionId));
  }
}
