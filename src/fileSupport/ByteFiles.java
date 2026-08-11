package fileSupport;
import static fileSupport.ByteFiles.Kind.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

public final class ByteFiles {
  public enum Op {
    Read("read","reading","read", StandardOpenOption.READ),
    // The write probe deliberately omits CREATE and TRUNCATE_EXISTING: a probe that
    // diagnoses a failed write must not create or empty the very file it inspects.
    Write("write","writing","written", StandardOpenOption.WRITE);
    final String verb;
    final String gerund;
    final String participle;
    private final StandardOpenOption[] probeOptions;
    Op(String verb, String gerund, String participle, StandardOpenOption... probeOptions){ this.verb= verb; this.gerund= gerund; this.participle= participle; this.probeOptions= probeOptions; }
    // The participle token is spelled with the WRITE-side word on purpose: the
    // read participle is spelled like the read verb, and one token for both hides
    // passive sentences that break on the write side ("cannot be write").
    // Plain replace: the text blocks stay literal, with no format-string hazards.
    // Applied exactly once, by FailureText.explain, on the final assembled text, so no
    // individual text can forget it.
    String fill(String text){ return text.replace("«read»",verb).replace("«reading»",gerund).replace("«written»",participle); }
  }
  public enum Kind {
    FileBusy_WindowsSharingViolation,
    FileBusy_WindowsFileRegionLocked,
    FileBusy_PosixTextFileBusy,
    FileBusy_WindowsFileCheckedOut,

    StorageBusy_DeviceOrResourceBusy,
    StorageBusy_DriveLocked,

    StorageFull_NoSpaceOnVolume,
    StorageFull_QuotaExceeded,

    StorageReadOnly_VolumeMountedReadOnly,
    StorageReadOnly_MediaWriteProtected,

    FileLock_WindowsFileRegionLockFailed,

    StorageUnavailable_StaleNetworkFile,
    StorageUnavailable_DeviceNotReady,
    StorageUnavailable_DeviceDisconnected,
    StorageUnavailable_TransportEndpointDisconnected,
    StorageUnavailable_ProviderUnavailable,
    StorageUnavailable_NetworkPathUnavailable,
    StorageUnavailable_NetworkResourceGone,
    StorageUnavailable_NetworkBusy,

    NetworkFailure_BadResponse,
    NetworkFailure_Unexpected,

    ResourceExhausted_TooManyOpenFiles,
    ResourceExhausted_Memory,
    ResourceExhausted_SystemResources,

    FileTooLargeForByteArray,

    PathResolutionFailure_FileNotFound,
    PathResolutionFailure_ParentIsNotAFolder,
    PathResolutionFailure_InvalidName,
    PathResolutionFailure_PathTooLong,
    PathResolutionFailure_SymlinkLoop,
    FileAlreadyExists,

    AccessDenied,
    AccessDenied_Network,
    PathIsFolder,

    MediaIoFailure_Generic,
    MediaIoFailure_CyclicRedundancyCheck,
    MediaIoFailure_SeekFailure,
    MediaIoFailure_SectorNotFound,
    MediaIoFailure_WriteFault,
    MediaIoFailure_ReadFault,
    MediaIoFailure_DeviceNotFunctioning,

    InvalidHandleOrDescriptor,

    InvalidOperationOrParameter_InvalidArgument,
    InvalidOperationOrParameter_IncorrectFunction,
    InvalidOperationOrParameter_InvalidParameter,

    UnsupportedFileSystem,
    UnsupportedFileSystem_DeviceDoesNotRecognizeCommand,

    ChannelClosedByInterrupt,
    IoInterrupted,
    ChannelClosedExternally,

    UnknownOpenFailure,
    UnknownFailureAfterSuccessfulOpen
  }
  // T is the operation's result type: byte[] for read, Void for the writes. In
  // practice every handler throws; the type parameter only makes the throwing
  // handler typecheck against each entry point.
  public interface Handler<T, X extends Throwable> { T failure(Kind kind, Throwable cause) throws X; }
  private interface IoOperation<T> { T run() throws IOException; }

  public static <X extends Throwable> byte[] read(Path path, Handler<byte[],X> handler) throws X {
    return attempt(Op.Read, path, () -> Files.readAllBytes(path), handler);
  }
  // Whole-file create: refuses to touch anything already at the location
  // (CREATE_NEW); the refusal classifies as Kind.FileAlreadyExists.
  public static <X extends Throwable> void writeNew(Path path, byte[] bytes, Handler<Void,X> handler) throws X {
    attempt(Op.Write, path, () -> { Files.write(path, bytes, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW); return null; }, handler);
  }
  private static <T, X extends Throwable> T attempt(Op op, Path path, IoOperation<T> operation, Handler<T,X> handler) throws X {
    try { return operation.run(); }
    catch(IOException|UnsupportedOperationException|ClosedFileSystemException|FileSystemNotFoundException e){ return classifyError(op, path, e, handler); }
    catch(OutOfMemoryError e){ return classifyMemoryError(e, handler); }
  }
  private static <T, X extends Throwable> T classifyMemoryError(OutOfMemoryError e, Handler<T,X> handler) throws X {
    if (isArraySizeLimit(e)){ return handler.failure(FileTooLargeForByteArray, e); }
    throw e;
  }
  private static boolean isArraySizeLimit(OutOfMemoryError e){
    var message= e.getMessage();
    return message != null && TextMatch.MFileTooLargeForByteArray.has(message.toLowerCase(Locale.ROOT));
  }
  private static <T, X extends Throwable> T classifyError(Op op, Path path, Throwable firstFailure, Handler<T,X> handler) throws X {
    var kind= kindFromFailure(firstFailure);
    if ((kind == AccessDenied || kind == UnknownFailureAfterSuccessfulOpen) && isFolder(path)){ return handler.failure(PathIsFolder, firstFailure); }
    if (kind != UnknownFailureAfterSuccessfulOpen){ return handler.failure(kind, firstFailure); }
    return checkOpenFailure(op, path, firstFailure, handler);
  }
  private static <T, X extends Throwable> T checkOpenFailure(Op op, Path path, Throwable firstFailure, Handler<T,X> handler) throws X {
    FileChannel channel;
    try { channel= FileChannel.open(path, op.probeOptions); }
    catch(IOException|UnsupportedOperationException|ClosedFileSystemException|FileSystemNotFoundException e){
      e.addSuppressed(firstFailure);
      return handler.failure(UnknownOpenFailure, e);
    }
    try { channel.close(); }
    catch(IOException e){ firstFailure.addSuppressed(e); }
    return handler.failure(UnknownFailureAfterSuccessfulOpen, firstFailure);
  }
  private static boolean isFolder(Path path) {
    try { return Files.readAttributes(path, BasicFileAttributes.class).isDirectory(); }
    catch(IOException|UnsupportedOperationException|ClosedFileSystemException|FileSystemNotFoundException e){ return false; }
  }
  private static Kind kindFromFailure(Throwable cause) {
    return switch(cause){
      case NoSuchFileException _ -> PathResolutionFailure_FileNotFound;
      case FileAlreadyExistsException _ -> FileAlreadyExists;
      case NotDirectoryException _ -> PathResolutionFailure_ParentIsNotAFolder;
      case FileSystemLoopException _ -> PathResolutionFailure_SymlinkLoop;
      case AccessDeniedException _ -> AccessDenied;
      case ClosedByInterruptException _ -> ChannelClosedByInterrupt;
      case InterruptedIOException _ -> IoInterrupted;
      case AsynchronousCloseException _ -> ChannelClosedExternally;
      case ClosedFileSystemException _, FileSystemNotFoundException _ -> StorageUnavailable_ProviderUnavailable;
      case UnsupportedOperationException _ -> UnsupportedFileSystem;
      default -> TextMatch.match(text(cause));
    };
  }
  public static String text(Throwable e) {
    var out= new StringBuilder();
    appendText(out, e);
    return out.toString().toLowerCase(Locale.ROOT);
  }
  private static void appendText(StringBuilder out, Throwable e){
    if (e == null){ return; }
    if (e instanceof FileSystemException fs){
      var reason= fs.getReason();
      if (reason != null ){ append(out, reason); }
      else{ append(out, fs.getMessage()); }
      }
    else{ append(out, e.getMessage()); }
    appendText(out, e.getCause());
    for (var suppressed : e.getSuppressed()){ appendText(out, suppressed); }
  }
  private static void append(StringBuilder out, String text){ if (text != null){ out.append(' ').append(text); } }
}