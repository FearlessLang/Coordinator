package realSourceOracle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.OptionalInt;

import utils.Bug;

@SuppressWarnings("restricted")
public final class LongPath{
  private static final Linker linker= Linker.nativeLinker();
  private static final SymbolLookup kernel32= SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
  private static final VarHandle lastError= Linker.Option.captureStateLayout().varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));
  private static final MethodHandle getAttributes= call("GetFileAttributesW", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle moveFile= call("MoveFileExW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
  private static final MethodHandle createFolder= call("CreateDirectoryW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
  private static MethodHandle call(String name, FunctionDescriptor d){ return linker.downcallHandle(kernel32.findOrThrow(name), d, Linker.Option.captureCallState("GetLastError")); }
  public static String of(Path folder, String name){
    var f= folder.toAbsolutePath().toString();
    var start= f.startsWith("\\\\") ? "\\\\?\\UNC\\"+f.substring(2) : "\\\\?\\"+f;
    return start+(f.endsWith("\\") ? "" : "\\")+name.replace('/', '\\');
  }
  public static String of(Path p){ return of(p.getParent(), p.getFileName().toString()); }
  public static OptionalInt attributes(String path) throws IOException{
    try (var arena= Arena.ofConfined()){
      var state= arena.allocate(Linker.Option.captureStateLayout());
      var res= (int)getAttributes.invokeExact(state, wide(arena, path));
      if (res != -1){ return OptionalInt.of(res); }
      var code= (int)lastError.get(state, 0L);
      if (code == 2 || code == 3){ return OptionalInt.empty(); }
      throw failed(path, code);
    }
    catch (IOException e){ throw e; }
    catch (Throwable t){ throw Bug.of(t); }
  }
  public static void move(String from, String to) throws IOException{
    try (var arena= Arena.ofConfined()){
      var state= arena.allocate(Linker.Option.captureStateLayout());
      if ((int)moveFile.invokeExact(state, wide(arena, from), wide(arena, to), 0) == 0){ throw failed(to, (int)lastError.get(state, 0L)); }
    }
    catch (IOException e){ throw e; }
    catch (Throwable t){ throw Bug.of(t); }
  }
  public static void makeFolder(String path) throws IOException{
    try (var arena= Arena.ofConfined()){
      var state= arena.allocate(Linker.Option.captureStateLayout());
      if ((int)createFolder.invokeExact(state, wide(arena, path), MemorySegment.NULL) == 0){ throw failed(path, (int)lastError.get(state, 0L)); }
    }
    catch (IOException e){ throw e; }
    catch (Throwable t){ throw Bug.of(t); }
  }
  public static String shown(String path){ return path.startsWith("\\\\?\\UNC\\") ? "\\\\"+path.substring(8) : path.substring(4); }
  private static MemorySegment wide(Arena arena, String s){
    var res= arena.allocate(JAVA_CHAR, s.length()+1);
    for (var i= 0; i<s.length(); i += 1){ res.setAtIndex(JAVA_CHAR, i, s.charAt(i)); }
    return res;
  }
  private static IOException failed(String path, int code){
    if (code == 80 || code == 183){ return new FileAlreadyExistsException(shown(path)); }
    return new FileSystemException(shown(path), null, "Windows error "+code);
  }
}
