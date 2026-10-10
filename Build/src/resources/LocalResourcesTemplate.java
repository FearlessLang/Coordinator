package resources;

import java.nio.file.Path;

public class LocalResourcesTemplate{ //public class LocalResources {
  //example for windows
  private static Path prefix= Path.of("C:\\").resolve("Users","...","OneDrive","Documents","GitHub");
  //example for linux
  //private static Path prefix= Path.of("/").resolve("home","...","Desktop","Java25");
  //example for mac
  //private static Path prefix= ...

  public static final Path stLibPath= prefix.resolve("StandardLibrary","base");
  public static final Path stLibRTPath= prefix.resolve("StandardLibrary","rt");
  public static final Path stLibDebugOut= prefix.resolve("StandardLibrary","dbgOut");
  public static final Path integrationTests= prefix.resolve("StandardLibrary","integrationTests");
  public static final Path commonsSrc= prefix.resolve("Commons","src");
  public static final Path frontendSrc= prefix.resolve("Frontend","FearlessFrontend","src");
  public static final Path frontendSrcModule= prefix.resolve("Frontend","FearlessFrontend","srcModule");
  public static final Path coordinatorSrc= prefix.resolve("Coordinator","src");
  public static final Path coordinatorSrcModule= prefix.resolve("Coordinator","srcModule");
  public static final Path controllerSrc= prefix.resolve("Controllers","src");
  public static final Path controllerSrcModule= prefix.resolve("Controllers","srcModule");
  public static final Path portableFolderOut= prefix.resolve("StandardLibrary","fearlessArtefact");
  public static final Path managedFolderOut= prefix.resolve("StandardLibrary","fearlessManagedArtefact");
  public static final Path badZipCorpous= prefix.resolve("Coordinator","badZips");
  public static final Path packaging= prefix.resolve("Coordinator","_fearless_packaging");
  public static final Path portableEclipse= prefix.resolve("eclipse");
}
