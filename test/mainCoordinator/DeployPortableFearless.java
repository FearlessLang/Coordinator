package mainCoordinator;

import tools.PortableApp;
//This can easily be run from command line as follow:
//go in the parent directory (Coordinator/test)
//yourJava --class-path "youPathToCommons/Commons/Commons.jar" mainCoordinator/DeployPortableFearless.java

public class DeployPortableFearless{
  public static void main(String[] a){
    new PortableApp(
      ResolveResource.packaging,
      ResolveResource.portableFolderOut,//out
      ResolveResource.commonsSrc,
      ResolveResource.frontendSrc,
      ResolveResource.frontendSrcModule,
      ResolveResource.coordinatorSrc,
      ResolveResource.coordinatorSrcModule,
      ResolveResource.stLibPath,//base
      ResolveResource.stLibRTPath//rt
    ).build();
  }
}