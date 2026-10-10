package resources;

import java.nio.file.Path;

/**
This file will have a compile time error on the first git checkout.
This project needs to know how to locate some resources on your machine.
You need to add a file LocalResources.java (that is already in the gitignore)
following the template LocalResourcesTemplate.java
*/
public final class ResolveResource{
  public static final Path stLibPath= LocalResources.stLibPath;
  public static final Path stLibRTPath= LocalResources.stLibRTPath;
  public static final Path stLibDebugOut= LocalResources.stLibDebugOut;
  public static final Path integrationTests= LocalResources.integrationTests;

  public static final Path commonsSrc= LocalResources.commonsSrc;
  public static final Path frontendSrc= LocalResources.frontendSrc;
  public static final Path frontendSrcModule= LocalResources.frontendSrcModule;
  public static final Path coordinatorSrc= LocalResources.coordinatorSrc;
  public static final Path coordinatorSrcModule= LocalResources.coordinatorSrcModule;
  public static final Path coordinatorJars= coordinatorSrc.getParent().resolve("externalJars");
  public static final Path coordinatorTestJars= coordinatorSrc.getParent().resolve("testJars");
  public static final Path controllerSrc= LocalResources.controllerSrc;
  public static final Path controllerSrcModule= LocalResources.controllerSrcModule;
  public static final Path controllerPluginSrc= controllerSrc.getParent().resolve("fearlessPluginProject");
  public static final Path portableEclipse= LocalResources.portableEclipse;
  public static final Path eclipsePlugins= portableEclipse.resolve("plugins");

  public static final Path portableFolderOut= LocalResources.portableFolderOut;
  public static final Path managedFolderOut= LocalResources.managedFolderOut;
  public static final Path badZipCorpous= LocalResources.badZipCorpous;
  public static final Path packaging= LocalResources.packaging;

  public static final String eclipseJavaVersion= "21";
  public static final String versionId= "0_001";
}
