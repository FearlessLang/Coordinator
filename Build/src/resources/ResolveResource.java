package resources;

import java.nio.file.Path;

/**
This file will have a compile time error on the first git checkout.
This project needs to know how to locate some resources on your machine.
You need to add a file LocalResources.java (that is already in the gitignore)
following the template LocalResourcesTemplate.java
*/
public final class ResolveResource extends LocalResources{
  static public final Path coordinatorJars= coordinatorSrc.getParent().resolve("externalJars");
  static public final Path coordinatorTestJars= coordinatorSrc.getParent().resolve("testJars");
  static public final Path controllerPluginSrc= controllerSrc.getParent().resolve("fearlessPluginProject");
  static public final Path eclipsePlugins= portableEclipse.resolve("plugins");

  static public final String eclipseJavaVersion= "21";
  public static final String versionId= "0_001";
}