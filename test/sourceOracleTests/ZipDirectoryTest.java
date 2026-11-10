package sourceOracleTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import realSourceOracle.ZipDirectory;
import tools.Fs;

final class ZipDirectoryTest{
  private static Optional<ZipDirectory.Directory> digest(Path tmp, byte[] zip) throws IOException{
    var f= tmp.resolve("a.zip");
    Fs.write(f, zip);
    return ZipDirectory.of(f, zip.length);
  }
  private static List<String> names(int n){ return IntStream.range(0, n).mapToObj(i -> "e%05d.txt".formatted(i)).toList(); }
  private static byte[] concat(byte[] a, byte[] b){
    var res= Arrays.copyOf(a, a.length+b.length);
    System.arraycopy(b, 0, res, a.length, b.length);
    return res;
  }
  @Test void aZip64DirectoryIsHashedWhole(@TempDir Path tmp) throws Exception{
    var names= names(70_000);
    var one= digest(tmp, RealSourceOracleWithZipDslTest.zip(names));
    var renamed= new ArrayList<>(names);
    renamed.set(0, "f00000.txt");
    var two= digest(tmp, RealSourceOracleWithZipDslTest.zip(renamed));
    assertEquals(64, one.orElseThrow().hash().length());
    assertNotEquals(one, two);
  }
  @Test void aZipWithBytesAroundItHasNoDigest(@TempDir Path tmp) throws Exception{
    var zip= RealSourceOracleWithZipDslTest.zip(names(3));
    assertEquals(64, digest(tmp, zip).orElseThrow().hash().length());
    assertEquals(Optional.empty(), digest(tmp, concat(new byte[]{1}, zip)));
    assertEquals(Optional.empty(), digest(tmp, concat(zip, new byte[]{1})));
    assertEquals(Optional.empty(), digest(tmp, new byte[]{1, 2, 3}));
  }
  @Test void aDirectoryNamesItsFilesButNotItsFolders(@TempDir Path tmp) throws Exception{
    assertEquals(List.of("a/b.txt", "c.txt"), digest(tmp, RealSourceOracleWithZipDslTest.zip(List.of("a/", "a/b.txt", "c.txt"))).orElseThrow().files());
  }
  @Test void anEmptyZipHasADigest(@TempDir Path tmp) throws Exception{
    assertEquals(64, digest(tmp, RealSourceOracleWithZipDslTest.zip(List.of())).orElseThrow().hash().length());
  }
}
