package fileSupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.Fs;
import utils.Box;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StringFilesTest{
  @SuppressWarnings("serial")
  private static final class Marker extends RuntimeException{}

  @Test void invalidUtf8MessageStaysWithinWhitelistAndUsesPlainDots(@TempDir Path tmp) throws Exception{
    var prefix= "x".repeat(120).getBytes(StandardCharsets.UTF_8);
    var bytes= Arrays.copyOf(prefix, prefix.length+1);
    bytes[prefix.length]= (byte)0xFF;
    var file= tmp.resolve("bad.fear");
    Files.write(file, bytes);
    var captured= new Box<String>(null);
    assertThrows(Marker.class, ()->
      StringFiles.read(file, (actionTxt,reportTxt)-> { captured.set(actionTxt); throw new Marker(); }));
    var msg= captured.get();
    assertTrue(msg.contains("..."), msg);
    assertTrue(msg.codePoints().allMatch(cp-> cp < 128 && Fs.allowed.indexOf((char)cp) >= 0), msg);
  }
}
