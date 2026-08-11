package fileSupport;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tools.Fs;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;

// Code to be executed at the very beginning of main.
// Java is forced to Locale.US.
// Windows process UI language is forced to en-US.
// Native C/CRT locale is forced to the invariant C locale.
// No code will set up thread-specific languages.
// Code should fail instead of gracefully degrading.
//
// Native access is now the JDK's own java.lang.foreign (FFM) API; the os.Os
// abstraction is gone. The launcher flags stay the same:
// --enable-native-access=<your.module.name> added by jpackage
// --illegal-native-access=deny
//
// Symbol sources:
// - setlocale: the C runtime. Linker.nativeLinker().defaultLookup() exposes it on
//   every platform (on Windows the default lookup is the Universal C Runtime,
//   ucrtbase.dll; on POSIX it is libc).
// - SetProcessPreferredUILanguages / GetProcessPreferredUILanguages: kernel32.dll,
//   loaded explicitly, Windows only.
// The two kernel32 downcalls capture GetLastError (Linker.Option.captureCallState),
// so a failure reports the Windows error code instead of just "it failed".
public class NativeLocaleForcer {
  private static final int MAX_PROCESS_UI_LANGUAGE_CHARS= 1_000;
  private static final String EN_US= "en-US";
  private static final Linker LINKER= Linker.nativeLinker();

  public static void forceEnglish(){
    Locale.setDefault(Locale.US);
    try {
      if (Fs.isWindows()){ forceWindowsEnglish(); return; }
      forcePosixEnglish();
    }
    //catch (Throwable t){ throw RuntimeBroken.vmOrLinkageFailure(t); }//eventually should be this or something throwing a user level error in Fearless
    catch (Throwable t){ throw new Error(t); }//for now, similarly to other thrown errors.
  }
  private static void forceWindowsEnglish() throws Throwable {
    forceWindowsUiLanguage();
    setCLocale(0);//WINDOWS_LC_ALL
  }
  private static void forcePosixEnglish() throws Throwable {
    if (Fs.isMac()){ setCLocale(0); return; }//MACOS_LC_ALL
    if (Fs.isLinux()){ setCLocale(6); return; }//LINUX_LC_ALL
    throw new Error("Unsupported operating system for POSIX LC_ALL constant: " + System.getProperty("os.name"));
  }
  // char* setlocale(int category, const char* locale);
  // Returns NULL when the request is rejected; per the fail-loudly policy (and unlike
  // the old Os version, which never looked at the result) NULL is now an Error.
  private static void setCLocale(int lcAll) throws Throwable {
    MethodHandle setlocale= LINKER.downcallHandle(
      LINKER.defaultLookup().findOrThrow("setlocale"),
      FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));
    try (var arena= Arena.ofConfined()){
      var res= (MemorySegment) setlocale.invokeExact(lcAll, arena.allocateFrom("C"));
      if (MemorySegment.NULL.equals(res)){ throw new Error("The C runtime rejected setlocale(LC_ALL, \"C\")"); }
    }
  }
  private static void forceWindowsUiLanguage() throws Throwable {
    var kernel32= SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
    var captureLastError= Linker.Option.captureCallState("GetLastError");
    // With captureCallState the handle takes one extra LEADING MemorySegment
    // parameter: the buffer Windows's error code is captured into.
    // BOOL SetProcessPreferredUILanguages(DWORD flags, PCZZWSTR languages, PULONG count);
    MethodHandle set= LINKER.downcallHandle(
      kernel32.findOrThrow("SetProcessPreferredUILanguages"),
      FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS),
      captureLastError);
    // BOOL GetProcessPreferredUILanguages(DWORD flags, PULONG count, PZZWSTR buffer, PULONG bufferChars);
    MethodHandle get= LINKER.downcallHandle(
      kernel32.findOrThrow("GetProcessPreferredUILanguages"),
      FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
      captureLastError);
    try (var arena= Arena.ofConfined()){
      var callState= arena.allocate(Linker.Option.captureStateLayout());
      setUiLanguage(set, arena, callState);
      verifyUiLanguage(get, arena, callState);
    }
  }
  private static void setUiLanguage(MethodHandle set, Arena arena, MemorySegment callState) throws Throwable {
    // The languages argument is a double-null-terminated UTF-16 list. Encoding
    // "en-US\0" and letting allocateFrom append the charset terminator produces
    // exactly "en-US\0\0".
    var languages= arena.allocateFrom(EN_US+"\0", StandardCharsets.UTF_16LE);
    var count= arena.allocate(JAVA_INT);
    int ok= (int) set.invokeExact(callState, 8/*MUI_LANGUAGE_NAME*/, languages, count);
    if (ok == 0){ throw new Error("Windows rejected the process UI language call (GetLastError=" + lastError(callState) + ")"); }
    if (count.get(JAVA_INT, 0) != 1){ throw new Error("Windows accepted process UI language call but did not set exactly one language"); }
  }
  private static void verifyUiLanguage(MethodHandle get, Arena arena, MemorySegment callState) throws Throwable {
    var count= arena.allocate(JAVA_INT);
    var buffer= arena.allocate(JAVA_CHAR, MAX_PROCESS_UI_LANGUAGE_CHARS);
    var bufferChars= arena.allocate(JAVA_INT);
    bufferChars.set(JAVA_INT, 0, MAX_PROCESS_UI_LANGUAGE_CHARS);
    int ok= (int) get.invokeExact(callState, 8/*MUI_LANGUAGE_NAME*/, count, buffer, bufferChars);
    if (ok == 0){
      throw new Error("Windows process UI language read failed (GetLastError=" + lastError(callState) + ")");
    }
    var languages= parseDoubleNullTerminatedUtf16(buffer);
    if (languages.size() == 1 && EN_US.equals(languages.get(0))){ return; }
    throw new Error("Fearless set the Windows process UI language to en-US, but Windows reported the language as " + languages + " instead");
  }
  private static int lastError(MemorySegment callState){
    VarHandle h= Linker.Option.captureStateLayout()
      .varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));
    return (int) h.get(callState, 0L);
  }
  private static List<String> parseDoubleNullTerminatedUtf16(MemorySegment wideList){
    var out= new ArrayList<String>();
    long offset= 0;
    while (true){
      String s= wideList.getString(offset, StandardCharsets.UTF_16LE);//reads up to the next null terminator
      if (s.isEmpty()){ return out; }//the empty string is the list terminator
      out.add(s);
      offset += (s.length()+1)*2L;//chars plus terminator, two bytes each
    }
  }
}