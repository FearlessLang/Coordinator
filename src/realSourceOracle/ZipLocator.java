package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import coordinatorMessages.CacheCorruptionError;
import utils.IoErr;

final class ZipLocator{
  public static List<String> entryNames(Path diskZip, List<String> steps){
    return ZipLocator.fetch(diskZip, steps, zin -> IoErr.of(()->entryNamesAux(zin)));
  }
  static private List<String> entryNamesAux(ZipInputStream zin) throws IOException{
    var res= new ArrayList<String>();
    for(ZipEntry e; (e= zin.getNextEntry())!=null; zin.closeEntry()){
      var n= e.getName(); if (!n.endsWith("/")){ res.add(n); }
    }
    return Collections.unmodifiableList(res);
  }
  public static byte[] entryBytes(Path diskZip, List<String> steps, String entryName){
    return ZipLocator.fetch(diskZip, steps, zin -> IoErr.of(()->readEntryBytes(zin, entryName)));
  }
  public static <T> T fetch(Path diskZip, List<String> steps, Function<ZipInputStream,T> onZip){
    return IoErr.of(()->fetchAux(diskZip, steps, onZip));
  }
  private static <T> T fetchAux(Path diskZip, List<String> steps, Function<ZipInputStream, T> onZip) throws IOException{
    byte[] bytes= null;
    for (var step: steps){
      try(var zin= zipStream(diskZip, bytes)){ bytes = readEntryBytes(zin, step); }
    }
    try(var zin= zipStream(diskZip, bytes)){ return onZip.apply(zin); }
  }
  private static ZipInputStream zipStream(Path diskZip, byte[] bytes) throws IOException{
    return bytes == null
      ? new ZipInputStream(Files.newInputStream(diskZip), UTF_8)
      : new ZipInputStream(new ByteArrayInputStream(bytes), UTF_8);
  }
  private static byte[] readEntryBytes(ZipInputStream zin, String expectedName) throws IOException{
    for (ZipEntry e; (e= zin.getNextEntry()) != null; zin.closeEntry()){
      var n= e.getName(); if (n.endsWith("/") || !n.equals(expectedName)){ continue; }
      try{ return zin.readAllBytes(); }
      catch(OutOfMemoryError oom){ throw CacheCorruptionError.nestedZipTooBig(expectedName); }
      //Note: this can never handle zips over 2gb anyway because of size limits of arrays
    }
    throw CacheCorruptionError.canNotFindExpected(expectedName);
  }
}