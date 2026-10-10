package fileSupport;
import static fileSupport.ByteFiles.Kind.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import fileSupport.ByteFiles.Kind;
import fileSupport.ByteFiles.Op;
import metaParser.Message;
import utils.Bug;

public final class StringFiles {
  public static String read(Path path, BiConsumer<String,String> onError){
    byte[] bytes= ByteFiles.read(path,(k,c)->
      fail(onError,requiresReport.contains(k),FailureText.explain(Op.Read,k,path),c));
    var input= ByteBuffer.wrap(bytes);
    try { return StandardCharsets.UTF_8.newDecoder().decode(input).toString(); }
    catch(CharacterCodingException e){
      return fail(onError,false,invalidUtf8(path,bytes,input.position(),((MalformedInputException)e).getInputLength()),e);
    }
  }
  //Whole-file create with UTF-8 content: refuses to touch anything already at the location.
  public static void writeNew(Path path, String text, BiConsumer<String,String> onError){
    ByteFiles.writeNew(path,utf8(text),(k,c)->
      fail(onError,requiresReport.contains(k),FailureText.explain(Op.Write,k,path),c));
  }
  //Strict, mirroring the strict decode in read: String.getBytes(UTF_8) would silently
  //REPLACE an unpaired surrogate with U+FFFD, and silently altering the user's data on
  //the way to disk is against the design. Unlike the bytes read from a file (user
  //data, reported through onError), the String here was produced by our caller:
  //an unencodable String reaching this point is a bug on the Fearless side, so it
  //propagates as an Error, like the other observed-bug throws.
  private static byte[] utf8(String text){
    try {
      var buffer= StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text));
      var bytes= new byte[buffer.remaining()];
      buffer.get(bytes);
      return bytes;
    }
    catch(CharacterCodingException e){ throw new Error("The text to write is not valid UTF-16, so it cannot be encoded as UTF-8",e); }
  }
  private static final Set<Kind> requiresReport= EnumSet.of(UnknownFailureAfterSuccessfulOpen, InvalidHandleOrDescriptor,
    InvalidOperationOrParameter_InvalidArgument, InvalidOperationOrParameter_InvalidParameter, UnsupportedFileSystem,
    ChannelClosedByInterrupt, IoInterrupted, ChannelClosedExternally, UnknownOpenFailure);
  private static <T> T fail(
      BiConsumer<String,String> onError,
      boolean report,
      Explanation explanation,
      Throwable cause){
    var errors= explanation.suppressed();
    var details= !report && errors.isEmpty() ? "" : "\nOriginal failure:\n"+stackTrace(cause)
      +IntStream.range(0,errors.size()).mapToObj(i->"\nSuppressed error "+i+":\n"+stackTrace(errors.get(i))).collect(Collectors.joining());
    onError.accept(report ? "" : explanation.text(),(report ? explanation.text() : "")+details);
    throw Bug.unreachable();
  }
  private static Explanation invalidUtf8(Path path, byte[] bytes, int offset, int length){
    var lines= new String(bytes,0,offset,StandardCharsets.UTF_8).split("\r\n|\r|\n",-1);
    var last= lines[lines.length-1];
    var suppressed= new Suppressed();
    var text= CommonInfo.of(Op.Read,path,suppressed)+"""
The file's bytes were read successfully, but they do not form valid UTF-8 text.

The first invalid UTF-8 sequence begins at:
  Line:        %d
  Column:      %d
  Byte offset: %d (counting from zero)

Valid text immediately before it on that line:
  %s

Invalid bytes: %s
Nearby bytes:  %s
""".formatted(
      lines.length,
      last.codePoints().count()+1,
      offset,
      Message.displayString(last.replaceFirst("(?s).+(.{80})\\z","...$1")),
      hex(bytes,offset,Math.min(bytes.length,offset+length)),
      hex(bytes,Math.max(0,offset-8),Math.min(bytes.length,offset+length+8)));
    return new Explanation(text,suppressed.toList());
  }
  private static String hex(byte[] bytes, int from, int to){
    return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(bytes,from,to);
  }
  private static String stackTrace(Throwable error){
    var out= new StringWriter();
    error.printStackTrace(new PrintWriter(out));
    return out.toString();
  }
}