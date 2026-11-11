package realSourceOracle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.nio.charset.StandardCharsets.UTF_16LE;

import java.io.File;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import utils.Bug;

@SuppressWarnings("restricted")
public final class LongPath{
  private static final Linker linker= Linker.nativeLinker();
  private static final SymbolLookup kernel32= SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
  private static final VarHandle lastError= Linker.Option.captureStateLayout().varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));
  private static final MethodHandle getAttributes= call("GetFileAttributesW", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle moveFile= call("MoveFileExW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
  private static final MethodHandle createFolder= call("CreateDirectoryW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
  private static final MethodHandle firstVolume= call("FindFirstVolumeW", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
  private static final MethodHandle nextVolume= call("FindNextVolumeW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
  private static final MethodHandle closeVolumes= call("FindVolumeClose", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle volumePaths= call("GetVolumePathNamesForVolumeNameW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
  private static final MethodHandle queryDevice= call("QueryDosDeviceW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
  private static final SymbolLookup netapi32= SymbolLookup.libraryLookup("netapi32.dll", Arena.global());
  private static final MethodHandle shareEnum= linker.downcallHandle(netapi32.findOrThrow("NetShareEnum"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
  private static final MethodHandle bufferFree= linker.downcallHandle(netapi32.findOrThrow("NetApiBufferFree"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
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
      var code= error(state);
      if (code == 2 || code == 3){ return OptionalInt.empty(); }
      throw failed(shown(path), code);
    }
    catch (IOException e){ throw e; }
    catch (Throwable t){ throw Bug.of(t); }
  }
  public static void move(String from, String to) throws IOException{
    try (var arena= Arena.ofConfined()){
      var state= arena.allocate(Linker.Option.captureStateLayout());
      if ((int)moveFile.invokeExact(state, wide(arena, from), wide(arena, to), 0) == 0){ throw failed(shown(to), error(state)); }
    }
    catch (IOException e){ throw e; }
    catch (Throwable t){ throw Bug.of(t); }
  }
  public static void makeFolder(String path) throws IOException{
    try (var arena= Arena.ofConfined()){
      var state= arena.allocate(Linker.Option.captureStateLayout());
      if ((int)createFolder.invokeExact(state, wide(arena, path), MemorySegment.NULL) == 0){ throw failed(shown(path), error(state)); }
    }
    catch (IOException e){ throw e; }
    catch (Throwable t){ throw Bug.of(t); }
  }
  public static Set<Path> mountPoints() throws IOException{
    try (var arena= Arena.ofConfined()){
      var state= arena.allocate(Linker.Option.captureStateLayout());
      var volume= arena.allocate(JAVA_CHAR, 64);
      var found= (MemorySegment)firstVolume.invokeExact(state, volume, 64);
      if (found.address() == -1L){ throw failed("the volumes of this system", error(state)); }
      try{
        var res= new HashSet<Path>();
        do{ res.addAll(paths(arena, state, volume)); }
        while ((int)nextVolume.invokeExact(state, found, volume, 64) != 0);
        if (error(state) != 18){ throw failed("the volumes of this system", error(state)); }
        return res;
      }
      finally{ var _= (int)closeVolumes.invokeExact(state, found); }
    }
    catch (IOException e){ throw e; }
    catch (Throwable t){ throw Bug.of(t); }
  }
  private static List<Path> paths(Arena arena, MemorySegment state, MemorySegment volume) throws Throwable{
    var names= arena.allocate(JAVA_CHAR, 32768);
    if ((int)volumePaths.invokeExact(state, volume, names, 32768, arena.allocate(JAVA_INT)) == 0){ throw failed(volume.getString(0, UTF_16LE), error(state)); }
    var res= new ArrayList<Path>();
    var at= 0L;
    while (names.get(JAVA_CHAR, at) != 0){
      var s= names.getString(at, UTF_16LE);
      res.add(Path.of(s));
      at += 2L*(s.length()+1);
    }
    return res;
  }
  public static List<Path> shownFolders() throws IOException{
    try (var arena= Arena.ofConfined()){
      var state= arena.allocate(Linker.Option.captureStateLayout());
      var target= arena.allocate(JAVA_CHAR, 32768);
      var res= new ArrayList<Path>();
      for (var root : File.listRoots()){
        var drive= root.toString().substring(0, 2);
        if ((int)queryDevice.invokeExact(state, wide(arena, drive), target, 32768) == 0){ throw failed(drive, error(state)); }
        var t= target.getString(0, UTF_16LE);
        if (t.startsWith("\\??\\")){ res.add(Path.of(shown("\\\\?\\"+t.substring(4)))); }
      }
      var shares= arena.allocate(ADDRESS);
      var count= arena.allocate(JAVA_INT);
      var status= (int)shareEnum.invokeExact(MemorySegment.NULL, 2, shares, -1, count, arena.allocate(JAVA_INT), MemorySegment.NULL);
      if (status == 2114){ return res; }
      if (status != 0){ throw failed("the shared folders of this machine", status); }
      var n= count.get(JAVA_INT, 0L);
      var all= shares.get(ADDRESS, 0L).reinterpret(56L*n);
      try{
        for (var i= 0; i < n; i += 1){
          if ((all.get(JAVA_INT, 56L*i+8) & 0xFF) == 0){ res.add(Path.of(all.get(ADDRESS, 56L*i+40).reinterpret(Long.MAX_VALUE).getString(0, UTF_16LE))); }
        }
      }
      finally{ var _= (int)bufferFree.invokeExact(all); }
      return res;
    }
    catch (IOException e){ throw e; }
    catch (Throwable t){ throw Bug.of(t); }
  }
  public static String shown(String path){ return path.startsWith("\\\\?\\UNC\\") ? "\\\\"+path.substring(8) : path.substring(4); }
  private static MemorySegment wide(Arena arena, String s){ return arena.allocateFrom(JAVA_CHAR, (s+"\0").toCharArray()); }
  private static int error(MemorySegment state){ return (int)lastError.get(state, 0L); }
  private static IOException failed(String shown, int code){
    if (code == 80 || code == 183){ return new FileAlreadyExistsException(shown); }
    return new FileSystemException(shown, null, "Windows error "+code);
  }
}
