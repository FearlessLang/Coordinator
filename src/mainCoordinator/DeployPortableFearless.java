package mainCoordinator;

import tools.PortableApp;
import utils.ResolveResource;

public class DeployPortableFearless{
  public static void main(String[]a){
    new PortableApp(
      ResolveResource.commonsSrc,
      ResolveResource.frontendSrc,
      ResolveResource.frontendSrcModule,
      ResolveResource.coordinatorSrc,
      ResolveResource.coordinatorSrcModule,
      ResolveResource.stLibPath,//base
      ResolveResource.stLibRTPath,//rt
      ResolveResource.portableFolderOut//out
    ).build();
  }
}