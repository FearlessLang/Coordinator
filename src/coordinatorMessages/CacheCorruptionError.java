package coordinatorMessages;

import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("serial")
public final class CacheCorruptionError extends RuntimeException{
  private CacheCorruptionError(String msg){ super(msg); }
  /*
  CacheCorruptionError = problems in generated outputs under ".fearless_out".
  These require multiple actors or abnormal termination:
  - another process touched ".fearless_out"
  - a previous run stopped mid-build after deleting (some of the) outputs
  - the filesystem returned partial writes / stale directory state

  Policy: Fearless performs exactly ONE automatic cache repair for these errors:
  - preserve the current output folder by renaming it to ".fearless_out.bad.<stamp>"
  - create a fresh ".fearless_out"
  - rebuild from sources in rank order
  If the rebuild fails again, Fearless stops and reports the preserved folder path.
  */

  // Trigger: build needs depPkg API file, but it is missing in ".fearless_out".
  // Action: Fearless starts automatic cache repair immediately.
  public static CacheCorruptionError startRepair_missingPkgApiFile(Path apiJson){
    Path outDir= apiJson.getParent();
    Path preservedOutDir=null;//TODO: compute AND do the saving
    return new CacheCorruptionError("""
      Build cache is missing a generated package API file.

      That metadata should be stored in:
        "%s"

      Fearless now repairs the build cache automatically:
        1) We preserve the output folder by renaming:
           "%s"
           to
           "%s"
        2) We rebuild from a clean "%s".

      If the rebuild fails again, Fearless prints another cache error log and then stops.
      """.formatted(apiJson, outDir, preservedOutDir, outDir));
  }

  // Trigger: depPkg API file exists but cannot be parsed.
  // Action: Fearless starts automatic cache repair immediately.
  public static CacheCorruptionError startRepair_invalidPkgApiFile(
      String depPkg, Path apiJson, String parseErr, Path outDir, Path preservedOutDir){
    return new CacheCorruptionError("""
      Build cache contains an invalid package API file.

      Fearless tried to read the compiled package metadata of "%s" from:
        "%s"
      but the file content is not valid.

      Parse error:
        %s

      Fearless now repairs the build cache automatically:
        1) We preserve the output folder by renaming:
           "%s"
           to
           "%s"
        2) We rebuild from a clean "%s".

      If the rebuild fails again, Fearless prints another cache error log and then stops.
      """.formatted(depPkg, apiJson, parseErr, outDir, preservedOutDir, outDir));
  }

  // Trigger: "_map.json" exists but cannot be parsed.
  // Action: Fearless starts automatic cache repair immediately.
  public static CacheCorruptionError startRepair_invalidVirtualizationMap( Path mapJson, String parseErr){
    Path outDir= mapJson.getParent();
    Path preservedOutDir=null;//TODO: compute AND do the saving
    return new CacheCorruptionError("""
      Build cache contains an invalid virtualization map.

      Fearless tried to read:
        "%s"
      but the file content is not valid.

      Parse error:
        %s

      Fearless now repairs the build cache automatically:
        1) We preserve the output folder by renaming:
           "%s"
           to
           "%s"
        2) We rebuild from a clean "%s".

      If the rebuild fails again, Fearless prints another cache error log and then stops.
      """.formatted(mapJson, parseErr, outDir, preservedOutDir, outDir));
  }

  // Trigger: the automatic repair already ran once, and the rebuild still failed with cache corruption.
  // Action: none (Fearless stops). Evidence is preserved at preservedOutDir.
  public static CacheCorruptionError repairAlreadyTried(Path preservedOutDir){
    return new CacheCorruptionError("""
      Build cache repair failed.

      Fearless already rebuilt the cache once, and the output folder became inconsistent again.
      The previous outputs are preserved here:
        "%s"

      This usually means another process is touching ".fearless_out" while Fearless runs
      (cleanup tools, IDE indexers, antivirus, sync services, concurrent builds).

      Fearless stops now.
      """.formatted(preservedOutDir));
  }

  // Trigger: any output write failed (permissions, disk full, etc.).
  // Action: none (Fearless stops). Automatic repair is NOT attempted.
  public static CacheCorruptionError cannotWrite(Path path, String why){
    return new CacheCorruptionError("""
      Cannot write build output.

      Fearless could not write:
        "%s"

      Reason:
        %s

      Fearless stops now. No automatic cache repair is attempted for write failures.
      """.formatted(path, why));
  }
  public static CacheCorruptionError nestedZipOverAGiga(String string){//no?
    return new CacheCorruptionError("""
TODO
""");}

  public static CacheCorruptionError canNotFindExpected(String string){
    return new CacheCorruptionError("""
TODO
""");}

  public static CacheCorruptionError canNotFindExpected(Path diskZip, List<String> steps, String entryName){
    return new CacheCorruptionError("""
TODO, this method is for can not find entry in zip (that was found before)
""");}
  public static CacheCorruptionError interruptedWhileWaitingForProject(){ return new CacheCorruptionError(
    "Interrupted while waiting for the project to open."
  );}

}