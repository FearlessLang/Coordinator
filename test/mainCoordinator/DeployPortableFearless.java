package mainCoordinator;

import tools.PortableApp;

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