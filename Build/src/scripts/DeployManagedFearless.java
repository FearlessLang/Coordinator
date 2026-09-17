// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/DeployManagedFearless.java
package scripts;

import java.util.List;
import resources.ResolveResource;

public class DeployManagedFearless{
  public static void main(String[] a) throws InterruptedException{
    ModularBuild.deploy(ResolveResource.managedFolderOut,
      List.of(
        List.of(ResolveResource.commonsSrc),
        List.of(ResolveResource.frontendSrc, ResolveResource.frontendSrcModule),
        List.of(ResolveResource.coordinatorSrc, ResolveResource.coordinatorSrcModule),
        List.of(ResolveResource.controllerSrc, ResolveResource.controllerSrcModule)),
      "fearlessManaged"+ResolveResource.versionId,
      "Controller/controller.Main",
      true);
  }
}
