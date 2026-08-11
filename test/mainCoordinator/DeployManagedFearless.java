package mainCoordinator;

import tools.FearlessId;
import tools.PortableApp;
//This can easily be run from command line as follow:
//go in the parent directory (Coordinator/test)
//yourJava --class-path "youPathToCommons/Commons/Commons.jar" mainCoordinator/DeployManagedFearless.java

//The same app image as DeployPortableFearless, with two differences:
//- it is named fearlessBin+versionId, the name the manager looks for to find its own code;
//- its launchers start the manager, so the first Fearless process of this version becomes
//  the one process the others report to.
//managedFolderOut is then the manager code folder: it holds exactly ONE folder,
//fearlessBin+versionId, and gains a second one, fearless+versionId, the first time the
//manager runs from outside an installed-programs location.
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
      FearlessId.binFolder,
      PortableApp.managerMain
    ).build();
  }
}
