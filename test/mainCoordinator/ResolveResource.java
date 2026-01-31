package mainCoordinator;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;

/**
This file will have a compile time error on the first git checkout.
This project needs to know how to locate some resources on your machine.
You need to add a file LocalResources.java (that is already in the gitignore)
following the template LocalResourcesTemplate.java
*/
public record ResolveResource(Path assetRoot, Path artefactRoot, Optional<Path> testsRoot, FileSystem virtualFs){
  static public final Path stLibPath= LocalResources.stLibPath;
  static public final Path stLibRTPath= LocalResources.stLibRTPath;
  static public final Path stLibDebugOut= LocalResources.stLibDebugOut;
  static public final Path integrationTests= LocalResources.integrationTests;

  static public final Path commonsSrc= LocalResources.commonsSrc;
  static public final Path frontendSrc= LocalResources.frontendSrc; 
  static public final Path frontendSrcModule= LocalResources.frontendSrcModule; 
  static public final Path coordinatorSrc= LocalResources.coordinatorSrc; 
  static public final Path coordinatorSrcModule= LocalResources.coordinatorSrcModule; 

  static public final Path portableFolderOut= LocalResources.portableFolderOut;
  static public final Path badZipCorpous= LocalResources.badZipCorpous;
  static public final Path packaging= LocalResources.packaging;

  static public final String javaVersion= LocalResources.javaVersion;
}
