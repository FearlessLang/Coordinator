package fileSupport;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.function.BiConsumer;

import fileSupport.ByteFiles.Op;
import metaParser.Message;
import utils.Bug;

public final class StringFiles {
  public static String read(Path path, BiConsumer<String,String> onError){
    byte[] bytes= ByteFiles.read(path,(k,c)->
      fail(onError,requiresReport(k),FailureText.explain(Op.Read,k,path),c));
    var input= ByteBuffer.wrap(bytes);
    var output= CharBuffer.allocate(bytes.length);
    var decoder= StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT);
    var result= decoder.decode(input,output,true);
    if (result.isError()){
      var cause= codingException(result);
      output.flip();
      return fail(onError,false,
        invalidUtf8(path,bytes,input.position(),result.length(),output.toString()),cause);
    }
    assert !result.isOverflow();
    result= decoder.flush(output);
    assert result.isUnderflow();
    output.flip();
    return output.toString();
  }
  //Whole-file create with UTF-8 content: refuses to touch anything already at the location.
  public static void writeNew(Path path, String text, BiConsumer<String,String> onError){
    ByteFiles.writeNew(path,utf8(text),(k,c)->
      fail(onError,requiresReport(k),FailureText.explain(Op.Write,k,path),c));
  }
  //Strict, mirroring the strict decode in read: String.getBytes(UTF_8) would silently
  //REPLACE an unpaired surrogate with U+FFFD, and silently altering the user's data on
  //the way to disk is against the design. Unlike the bytes read from a file (user
  //data, reported through onError), the String here was produced by our caller:
  //an unencodable String reaching this point is a bug on the Fearless side, so it
  //propagates as an Error, like the other observed-bug throws.
  private static byte[] utf8(String text){
    try {
      var buffer= StandardCharsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(CharBuffer.wrap(text));
      var bytes= new byte[buffer.remaining()];
      buffer.get(bytes);
      return bytes;
    }
    catch(CharacterCodingException e){ throw new Error("The text to write is not valid UTF-16, so it cannot be encoded as UTF-8",e); }
  }
  private static boolean requiresReport(ByteFiles.Kind kind){
    return switch(kind){
      case UnknownFailureAfterSuccessfulOpen,
           InvalidHandleOrDescriptor,
           InvalidOperationOrParameter_InvalidArgument,
           InvalidOperationOrParameter_InvalidParameter,
           UnsupportedFileSystem,
           ChannelClosedByInterrupt,
           IoInterrupted,
           ChannelClosedExternally,
           UnknownOpenFailure -> true;
      default -> false;
    };
  }
  private static <T> T fail(
      BiConsumer<String,String> onError,
      boolean report,
      Explanation explanation,
      Throwable cause){
    var outAction= new StringBuilder();
    if (!report){ outAction.append(explanation.text()); }
    if (!report && explanation.suppressed().isEmpty()){
      onError.accept(outAction.toString(),"");
      throw Bug.unreachable();
    }
    var outReport= new StringBuilder();
    if (report){ outReport.append(explanation.text()); }
    outReport.append("\nOriginal failure:\n").append(stackTrace(cause));
    for (var i= 0; i < explanation.suppressed().size(); i++){ outReport
      .append("\nSuppressed error ").append(i).append(":\n")
      .append(stackTrace(explanation.suppressed().get(i)));
    }
    onError.accept(outAction.toString(),outReport.toString());
    throw Bug.unreachable();
  }
  private static Explanation invalidUtf8(Path path, byte[] bytes, int offset, int length, String prefix){
    var location= location(prefix);
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
      location.line(),
      location.column(),
      offset,
      Message.displayString(tail(location.linePrefix(),80)),
      hex(bytes,offset,Math.min(bytes.length,offset+length)),
      hex(bytes,Math.max(0,offset-8),Math.min(bytes.length,offset+length+8)));
    return new Explanation(text,suppressed.toList());
  }
  private static CharacterCodingException codingException(CoderResult result){
    try { result.throwException(); throw Bug.unreachable(); }
    catch(CharacterCodingException e){ return e; }
  }
  private static Location location(String prefix){
    var line= 1;
    var start= 0;
    for (var i= 0; i < prefix.length();){
      var c= prefix.charAt(i++);
      if (c == '\r'){
        if (i < prefix.length() && prefix.charAt(i) == '\n'){ i++; }
        line++;
        start= i;
      }
      else if (c == '\n'){
        line++;
        start= i;
      }
    }
    return new Location(line,prefix.codePointCount(start,prefix.length())+1,prefix.substring(start));
  }
  private static String tail(String text, int length){
    var size= text.codePointCount(0,text.length());
    return size <= length ? text : "…"+text.substring(text.offsetByCodePoints(0,size-length));
  }
  private static String hex(byte[] bytes, int from, int to){
    return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(bytes,from,to);
  }
  private static String stackTrace(Throwable error){
    var out= new StringWriter();
    error.printStackTrace(new PrintWriter(out));
    return out.toString();
  }
  private record Location(int line, int column, String linePrefix){}
}