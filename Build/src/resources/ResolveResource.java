package resources;

import java.nio.file.Path;

/**
This file will have a compile time error on the first git checkout.
This project needs to know how to locate some resources on your machine.
You need to add a file LocalResources.java (that is already in the gitignore)
following the template LocalResourcesTemplate.java
*/
public final class ResolveResource extends LocalResources{
  public static final Path coordinatorJars= coordinatorSrc.getParent().resolve("externalJars");
  public static final Path coordinatorTestJars= coordinatorSrc.getParent().resolve("testJars");
  public static final Path controllerPluginSrc= controllerSrc.getParent().resolve("fearlessPluginProject");
  public static final Path eclipsePlugins= portableEclipse.resolve("plugins");

  public static final String eclipseJavaVersion= "21";
  public static final String versionId= "0_001";
}
