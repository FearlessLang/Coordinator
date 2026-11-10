package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import utils.Bug;

public final class ZipDirectory{
  public record Directory(String hash, List<String> files){}
  private static final int endSize= 22;
  private static final int locatorSize= 20;
  private static final int zip64EndSize= 56;
  private static final int maxComment= 65_535;
  public static Optional<Directory> of(Path zip, long size) throws IOException{
    try (var ch= Files.newByteChannel(zip)){
      var tailLength= (int)Math.min(size, endSize+maxComment);
      var tail= read(ch, size-tailLength, tailLength);
      var end= endRecord(tail);
      if (end < 0){ return Optional.empty(); }
      var endAt= size-tailLength+end;
      var zip64= endAt >= locatorSize && u32(read(ch, endAt-locatorSize, 4), 0) == 0x07064b50L;
      if (zip64){ return of64(ch, endAt, size); }
      var length= u32(tail, end+12);
      var start= u32(tail, end+16);
      return start+length == endAt ? Optional.of(directory(ch, start, length, size)) : Optional.empty();
    }
  }
  private static Optional<Directory> of64(SeekableByteChannel ch, long endAt, long size) throws IOException{
    var locatorAt= endAt-locatorSize;
    var recordAt= u64(read(ch, locatorAt, locatorSize), 8);
    var bad= recordAt < 0 || recordAt+zip64EndSize > locatorAt;
    if (bad){ return Optional.empty(); }
    var record= read(ch, recordAt, zip64EndSize);
    var length= u64(record, 40);
    var start= u64(record, 48);
    var ok= u32(record, 0) == 0x06064b50L && start >= 0 && length >= 0 && start+length == recordAt;
    return ok ? Optional.of(directory(ch, start, length, size)) : Optional.empty();
  }
  private static Directory directory(SeekableByteChannel ch, long start, long length, long size) throws IOException{
    var bytes= read(ch, start, Math.toIntExact(size-start));
    MessageDigest d;
    try{ d= MessageDigest.getInstance("SHA-256"); }
    catch (NoSuchAlgorithmException e){ throw Bug.of(e); }
    return new Directory(HexFormat.of().formatHex(d.digest(bytes)), files(bytes, (int)length));
  }
  private static List<String> files(byte[] dir, int length){
    var res= new ArrayList<String>();
    for (var i= 0; i < length;){
      var bad= i+46 > length || u32(dir, i) != 0x02014b50L;
      if (bad){ return List.of(); }
      var name= u16(dir, i+28);
      var next= i+46+name+u16(dir, i+30)+u16(dir, i+32);
      if (next > length){ return List.of(); }
      if (name > 0 && dir[i+45+name] != '/'){ res.add(new String(dir, i+46, name, UTF_8)); }
      i = next;
    }
    return Collections.unmodifiableList(res);
  }
  private static int endRecord(byte[] tail){
    for (int i= tail.length-endSize; i >= 0; i -= 1){
      if (u32(tail, i) == 0x06054b50L && i+endSize+u16(tail, i+20) == tail.length){ return i; }
    }
    return -1;
  }
  private static byte[] read(SeekableByteChannel ch, long at, int length) throws IOException{
    var res= ByteBuffer.allocate(length);
    ch.position(at);
    while (res.hasRemaining() && ch.read(res) >= 0){}
    return res.array();
  }
  private static int u16(byte[] b, int i){ return (b[i] & 0xFF) | (b[i+1] & 0xFF) << 8; }
  private static long u32(byte[] b, int i){ return u16(b, i) | (long)u16(b, i+2) << 16; }
  private static long u64(byte[] b, int i){ return u32(b, i) | u32(b, i+4) << 32; }
}
