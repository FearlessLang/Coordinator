package mainCoordinator;

import tools.JavacTool;
import tools.PortableApp;
//This can easily be run from command line as follow:
//go in the parent directory (Coordinator/test)
//yourJava --class-path "youPathToCommons/Commons/Commons.jar" mainCoordinator/DeployManagedFearless.java

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
      JavacTool.managedAppNameFor(ResolveResource.versionId),
      ResolveResource.versionId,
      "Coordinator/manager.ManagerMain"
    ).build();
  }
}
