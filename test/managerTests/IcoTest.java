package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerIcons.Ico;
import tools.Fs;

final class IcoTest{
  private static Path png(Path dir, String name, int size, Color color){
    var img= new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
    var g= img.createGraphics();
    g.setColor(color);
    g.fillRect(0,0,size,size);
    g.dispose();
    var file= dir.resolve(name);
    Fs.ofV(()->ImageIO.write(img,"png",file.toFile()));
    return file;
  }
  private static IcoDir read(Path ico){ return new IcoDir(Fs.of(()->Files.readAllBytes(ico))); }

  @Test void writeResizedProducesOneEntryPerRequestedSize(@TempDir Path dir){
    var source= png(dir,"source.png",256,Color.red);
    var out= dir.resolve("out.ico");
    Ico.writeResized(source,out,List.of(16,32,256));
    var got= read(out);
    assertEquals(List.of(16,32,256), got.sizes());
    got.checkSelfConsistent();
  }

  @Test void writeEmbedsEachSourceAtItsNativeSize(@TempDir Path dir){
    var small= png(dir,"small.png",16,Color.blue);
    var big= png(dir,"big.png",48,Color.green);
    var out= dir.resolve("out.ico");
    Ico.write(List.of(big,small),out);
    var got= read(out);
    assertEquals(List.of(16,48), got.sizes());
    got.checkSelfConsistent();
  }

  @Test void aSizeOf256IsStoredAsZeroPerTheIcoFormatButReadsBackAs256(@TempDir Path dir){
    var source= png(dir,"source.png",256,Color.yellow);
    var out= dir.resolve("out.ico");
    Ico.writeResized(source,out,List.of(256));
    var bytes= Fs.of(()->Files.readAllBytes(out));
    assertEquals(0, bytes[6]&0xFF);
    assertEquals(256, read(out).sizes().getFirst());
  }

  @Test void nonSquareFramesAreRejected(@TempDir Path dir){
    var wide= dir.resolve("wide.png");
    var img= new BufferedImage(32,16,BufferedImage.TYPE_INT_ARGB);
    Fs.ofV(()->ImageIO.write(img,"png",wide.toFile()));
    assertThrows(IllegalArgumentException.class, ()->Ico.write(List.of(wide),dir.resolve("out.ico")));
  }

  //re-parses the bytes Ico just wrote, independent of the writer, to confirm the
  //container is actually well-formed rather than merely "whatever Ico happened to emit"
  private static final class IcoDir{
    final ByteBuffer buf;
    final int count;
    IcoDir(byte[] bytes){
      buf= ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      assertEquals(0, buf.getShort(0)&0xFFFF);
      assertEquals(1, buf.getShort(2)&0xFFFF);
      count= buf.getShort(4)&0xFFFF;
    }
    List<Integer> sizes(){ return java.util.stream.IntStream.range(0,count).mapToObj(this::width).toList(); }
    private int width(int i){ var w= buf.get(6+16*i)&0xFF; return w == 0 ? 256 : w; }
    void checkSelfConsistent(){
      for (int i= 0; i < count; i++){ checkEntry(i); }
    }
    private void checkEntry(int i){
      var base= 6+16*i;
      var side= width(i);
      assertEquals(side == 256 ? 0 : side, buf.get(base+1)&0xFF, "height byte");
      assertEquals(1, buf.getShort(base+4)&0xFFFF, "planes");
      assertEquals(32, buf.getShort(base+6)&0xFFFF, "bitCount");
      var size= buf.getInt(base+8);
      var offset= buf.getInt(base+12);
      assertTrue(offset >= 0 && size >= 0 && offset+size <= buf.capacity(), "entry in bounds");
      var bytes= buf.array();
      assertEquals((byte)0x89, bytes[offset], "PNG signature byte 0");
      assertEquals('P', bytes[offset+1]);
      var decoded= Fs.of(()->ImageIO.read(new ByteArrayInputStream(bytes,offset,size)));
      assertEquals(side, decoded.getWidth());
      assertEquals(side, decoded.getHeight());
    }
  }
}
