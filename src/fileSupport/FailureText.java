package fileSupport;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import fileSupport.ByteFiles.Op;
import metaParser.Message;
import offensiveUtils.Require;
import tools.Fs;
import utils.Bug;

// Style rule for those helpers:
// - call the Java/NIO operations directly;
// - add explicit checks only for documented normal results, such as Path.getRoot()==null;
// - do not add defensive null/blank validation unless the API documents that case as legitimate.
// - explain(...) returns an Explanation record: the text, plus the list of nested errors that were suppressed while building it.
// - when we compute extra info but fail, its failing "piece of text" is replaced by a
//   [[suppressed error N]] marker, and the Throwable is stashed at index N of the list.
//   The caller decides what N means: strip it, inline its message, log it, rethrow, etc.
// - Bug.unreachable() and other 'observed bug' still PROPAGATE. It is a broken invariant, not a nested diagnostic.
//
// OPERATION WORDS: the texts serve every whole-file operation (ByteFiles.Op), and are
// written once. Where a text merely NAMES the operation, it carries the placeholders
// «read»/«reading»/«written» (verb/gerund/past participle); explain(..) substitutes the real operation words (Op.fill)
// exactly once, on the final assembled text, so no individual text can forget it.
// Where the CONTENT of a text depends on the operation - what the failure means, not
// what it is called - the text branches on Op instead: see PathResolutionFileNotFound
// and MediaIoText.writeFault. The «..» markers follow the same reviewer convention as
// [[..]]: judge each sentence as if the concrete word stood there.
//
// AUDIENCE: a Fearless user is fluent in functional programming in an OCaml-like style,
// data structures with occasional mutation, closures, generics, exceptions, 
// anything technical in the mathematical or logical sense.
// They are NOT assumed to know anything about operating systems, shells,
// or files beyond "the thing the Fearless APIs let me read and write in the right conditions":
// the whole point of the Fearless standard library is to isolate them from that world.
// Anything historical - naming and architecture humans settled on decades
// ago and have propagated since - cannot be assumed known; if another OS could
// reasonably have named or shaped a thing differently, the user does not know it.
// These errors necessarily break the abstraction; when they do, the OS concept involved
// must be explained inside the message itself (see Terms.glossary) rather than assumed.
//
// POLICY - information, not advice: the texts state what is known,
// what it means, and who or what controls the condition and until when. They do NOT:
// predict outcomes ("the next attempt may succeed"), speculate to fill gaps between two
// observations, give repair or workaround instructions, or warn against dangerous
// actions the reader was not going to take (naming the action just teaches it).

// TERMINOLOGY: "device", "volume", "storage medium", "server", "mounted", "driver",
// "file system" are the only storage words the texts may use, each with one fixed
// meaning. The user-facing definitions live in Terms.glossary below and are appended to
// every message that uses the words; bare "drive" is banned (Windows uses it for two of
// these meanings at once). Where a Windows error NAME contains "drive" we quote the
// name but explain in the fixed terms.
//
// DRAFT NOTATION: [[A/B/C]](how) marks a spot where the real implementation inserts a
// value discovered at failure time; A, B, C are examples of what could stand there, and
// (how) names the discovery method. Reviewers should judge each sentence as if one
// concrete value stood in place of the whole [[...]](...) block.
//
// OS TAGS: each text carries //[Windows-only], //[POSIX-only], //[any OS] or //[JVM].
// Tally over the 52 kinds: 25 Windows-only, 8 POSIX-only, 15 possible on any OS, 4
// raised by the JVM side rather than the OS. The Windows bias in the texts mirrors that
// tally - Windows reports storage failures in much finer grain - it is not a documentation preference.
final class FailureText {
  static Explanation explain(Op op, ByteFiles.Kind kind, Path path){
    // In the code we control, non-default file systems should never reach these explainers.
    if (!path.getFileSystem().equals(FileSystems.getDefault())){ throw Bug.unreachable(); }
    var suppressed= new Suppressed();
    String text= switch(kind){
      case UnknownFailureAfterSuccessfulOpen -> UnknownFailureAfterSuccessfulOpen.of(op, path, suppressed);

      case FileBusy_WindowsSharingViolation -> FileBusyWindowsSharingViolation.of(op, path, suppressed);
      case FileBusy_WindowsFileRegionLocked -> FileBusyWindowsFileRegionLocked.of(op, path, suppressed);
      case FileBusy_PosixTextFileBusy -> plain(op, path, suppressed, FileBusyText.posixTextFileBusy);
      case FileBusy_WindowsFileCheckedOut -> plain(op, path, suppressed, FileBusyText.windowsFileCheckedOut);

      case StorageBusy_DeviceOrResourceBusy -> plain(op, path, suppressed, StorageBusyText.deviceOrResourceBusy);
      case StorageBusy_DriveLocked -> plain(op, path, suppressed, StorageBusyText.driveLocked);

      case StorageFull_NoSpaceOnVolume -> plain(op, path, suppressed, StorageFullText.noSpaceOnVolume);
      case StorageFull_QuotaExceeded -> plain(op, path, suppressed, StorageFullText.quotaExceeded);

      case StorageReadOnly_VolumeMountedReadOnly -> plain(op, path, suppressed, StorageReadOnlyText.volumeMountedReadOnly);
      case StorageReadOnly_MediaWriteProtected -> plain(op, path, suppressed, StorageReadOnlyText.mediaWriteProtected);

      case FileLock_WindowsFileRegionLockFailed -> FileLockWindowsFileRegionLockFailed.of(op, path, suppressed);

      case StorageUnavailable_StaleNetworkFile -> plain(op, path, suppressed, StorageUnavailableText.staleNetworkFile);
      case StorageUnavailable_DeviceNotReady -> plain(op, path, suppressed, StorageUnavailableText.deviceNotReady);
      case StorageUnavailable_DeviceDisconnected -> plain(op, path, suppressed, StorageUnavailableText.deviceDisconnected);
      case StorageUnavailable_TransportEndpointDisconnected -> plain(op, path, suppressed, StorageUnavailableText.transportEndpointDisconnected);
      case StorageUnavailable_ProviderUnavailable -> plain(op, path, suppressed, StorageUnavailableText.providerUnavailable);
      case StorageUnavailable_NetworkPathUnavailable -> NetworkPathUnavailable.of(op, path, suppressed);
      case StorageUnavailable_NetworkResourceGone -> plain(op, path, suppressed, StorageUnavailableText.networkResourceGone);
      case StorageUnavailable_NetworkBusy -> plain(op, path, suppressed, StorageUnavailableText.networkBusy);

      case NetworkFailure_BadResponse -> plain(op, path, suppressed, NetworkFailureText.badResponse);
      case NetworkFailure_Unexpected -> plain(op, path, suppressed, NetworkFailureText.unexpected);

      case ResourceExhausted_TooManyOpenFiles -> TooManyOpenFiles.of(op, path, suppressed);
      case ResourceExhausted_Memory -> plain(op, path, suppressed, ResourceExhaustedText.memory);
      case ResourceExhausted_SystemResources -> plain(op, path, suppressed, ResourceExhaustedText.systemResources);

      case FileTooLargeForByteArray -> FileTooLargeForByteArray.of(op, path, suppressed);

      case PathResolutionFailure_FileNotFound -> PathResolutionFileNotFound.of(op, path, suppressed);
      case PathResolutionFailure_ParentIsNotAFolder -> PathResolutionParentIsNotAFolder.of(op, path, suppressed);
      case PathResolutionFailure_InvalidName -> PathResolutionInvalidName.of(op, path, suppressed);
      case PathResolutionFailure_PathTooLong -> PathResolutionPathTooLong.of(op, path, suppressed);
      case PathResolutionFailure_SymlinkLoop -> plain(op, path, suppressed, PathResolutionText.symlinkLoop);
      case FileAlreadyExists -> FileAlreadyExists.of(op, path, suppressed);

      case AccessDenied -> AccessDenied.of(op, path, suppressed);
      case AccessDenied_Network -> plain(op, path, suppressed, AccessText.accessDeniedNetwork);
      case PathIsFolder -> PathIsFolder.of(op, path, suppressed);

      case MediaIoFailure_Generic -> plain(op, path, suppressed, MediaIoText.generic);
      case MediaIoFailure_CyclicRedundancyCheck -> plain(op, path, suppressed, MediaIoText.cyclicRedundancyCheck);
      case MediaIoFailure_SeekFailure -> plain(op, path, suppressed, MediaIoText.seekFailure);
      case MediaIoFailure_SectorNotFound -> plain(op, path, suppressed, MediaIoText.sectorNotFound);
      case MediaIoFailure_WriteFault -> plain(op, path, suppressed, MediaIoText.writeFault(op));
      case MediaIoFailure_ReadFault -> plain(op, path, suppressed, MediaIoText.readFault);
      case MediaIoFailure_DeviceNotFunctioning -> plain(op, path, suppressed, MediaIoText.deviceNotFunctioning);

      case InvalidHandleOrDescriptor -> InvalidHandleOrDescriptor.of(op, path, suppressed);

      case InvalidOperationOrParameter_InvalidArgument -> plain(op, path, suppressed, InvalidOperationText.invalidArgument);
      case InvalidOperationOrParameter_IncorrectFunction -> plain(op, path, suppressed, InvalidOperationText.incorrectFunction);
      case InvalidOperationOrParameter_InvalidParameter -> plain(op, path, suppressed, InvalidOperationText.invalidParameter);

      case UnsupportedFileSystem -> plain(op, path, suppressed, UnsupportedText.unsupportedFileSystem);
      case UnsupportedFileSystem_DeviceDoesNotRecognizeCommand -> plain(op, path, suppressed, UnsupportedText.deviceDoesNotRecognizeCommand);

      case ChannelClosedByInterrupt -> plain(op, path, suppressed, InterruptionText.channelClosedByInterrupt);
      case IoInterrupted -> plain(op, path, suppressed, InterruptionText.ioInterrupted);
      case ChannelClosedExternally -> plain(op, path, suppressed, InterruptionText.channelClosedExternally);

      case UnknownOpenFailure -> plain(op, path, suppressed, UnknownText.unknownOpenFailure);
    };
    return new Explanation(op.fill(text), suppressed.toList());
    //fill happens here, once, on the final text. A discovered value (a path, a
    //volume name) could in principle contain a literal «..» marker and be filled
    //too; accepted, like the [[..]] markers would be.
  }
  private static String plain(Op op, Path path, Suppressed suppressed, String msg){
    return CommonInfo.of(op, path, suppressed)+msg;
  }
}
record Explanation(String text, List<Throwable> suppressed){
  Explanation{ assert Require.unmodifiable(suppressed,"suppressed"); }
}
final class Suppressed{
  private final List<Throwable> errors= new ArrayList<>();
  String mark(Throwable error){
    var index= errors.size();
    errors.add(error);
    return "[[suppressed error "+index+"]]";
  }
  List<Throwable> toList(){ return List.copyOf(errors); }
}
class CommonInfo{
  private static final int PortableLinkDepth= 8;  // POSIX guarantees only SYMLOOP_MAX >= 8
  private static final int MaxLinkFollowed= 64;   // backstop so the walk always terminates

  static String of(Op op, Path path, Suppressed suppressed){ return anchor(op, path, suppressed)+chain(path, suppressed); }
  private static String anchor(Op op, Path path, Suppressed suppressed){
    return "Fearless could not "+op.verb+" this file:\n\n  "+located(path, suppressed)+"\n\n";
  }
  private static String rootOf(Path path){
    var root= path.toAbsolutePath().getRoot();//getRoot() may be null when the path has no root component (documented normal result)
    return root != null ? root.toString() : "<no root>";
  }
  private static String located(Path path, Suppressed suppressed){
    return "Path: "+Message.displayString(path.toString())
      +", Root: "+Message.displayString(rootOf(path))
      +", Volume: "+volumeOf(path, suppressed)
      +", Device: [[???]](queries from the volume handle,model, bus, removable flag,DiskArbitration)";
  // Device names the physical hardware behind the volume.
  // Windows: IOCTL_STORAGE_GET_DEVICE_NUMBER / SetupDi queries from the volume handle.
  // Linux: the volume's entry under /sys/block (model, bus, removable flag).
  // macOS: DiskArbitration. Same suppressed-on-failure pattern as volumeOf below.
  }
  private static String volumeOf(Path path, Suppressed suppressed){
    try {
      var store= Files.getFileStore(path);
      return Message.displayString(store.name())+"  "+Message.displayString(store.type());
    }
    catch(IOException e){ return "<reading volume failed>"+suppressed.mark(e); }//documented: getFileStore throws when the file/volume cannot be read
  }
  private static String chain(Path path, Suppressed suppressed){
    var visited= new HashSet<Path>();
    var out= new StringBuilder();
    Path current= path.toAbsolutePath().normalize();//normalize is lexical, no I/O
    var depth= 0;
    try {
      while (Files.isSymbolicLink(current)){//returns false (not throws) on error, ending the walk
        if (depth >= MaxLinkFollowed){ return out.append("\nChain exceeds ").append(MaxLinkFollowed).append(" links; not followed further.\n").toString(); }
        if (!visited.add(current)){ return out.append("\nChain loops back to ").append(Message.displayString(current.toString())).append(".\n").toString(); }
        current= current.getParent().resolve(Files.readSymbolicLink(current)).normalize();//read to advance; relative to the link's own dir; current is absolute and a root cannot be a symlink, so getParent()!=null
        depth++;
        out.append("\n  ").append(located(current, suppressed)).append("\n");//either last is final location or a dedicated message is present
      }
    }
    catch(IOException e){ return out.append("\n<reading next failed>").append(suppressed.mark(e)).append("\n").toString(); }
    if (depth > PortableLinkDepth){ out.append("\nWarning: ").append(depth).append(" links deep; some systems may cap it at 8.\n"); }
    return out.toString();
  }
}

// A given holder is the same program whether it triggered a sharing violation (32) or a lock violation (33)
class Holders{
  // Holder enumeration is per-OS. Windows: Restart Manager / NtQuerySystemInformation.
  // Linux: walk /proc/*/fd (or lsof/fuser). macOS: lsof / proc_pidfdinfo.

  // Contract: an EMPTY list means "confirmed: no program currently holds this file open".
  // A failure of the enumeration itself must be reported by throwing IOException.
  // Per-field failures inside one entry adds to suppressed.
  private static List<String> enumerate(Path path, Suppressed suppressed)throws IOException{
    return List.of(exampleWord, exampleDefender);//example data; the real implementation queries the OS
  }
  // intro: shown only when at least one holder was found, before the list.
  // whenNone: replaces intro+list+outro when the (confirmed) list is empty.
  // outro: shown only when at least one holder was found, after the list.
  static String section(Path path, Suppressed suppressed, String intro, String whenNone, String outro){
    List<String> holders;
    try{ holders= enumerate(path,suppressed); }
    catch(IOException e){ return "\n<reading programs holding this file open failed>"+suppressed.mark(e)+"\n"; }
    if (holders.isEmpty()){ return whenNone; }
    var out= new StringBuilder(intro)
      .append(holders.size())
      .append(holders.size() == 1 ? " program" : " programs")
      .append(" currently holding this file open:\n\n");
    for (var h : holders){ out.append("  ").append(h).append("\n"); }
    out.append(outro);
    return out.toString();
  }
  private static final String exampleWord= """
Microsoft Word
       Executable:     C:\\Program Files\\Microsoft Office\\root\\Office16\\WINWORD.EXE
       Process ID:     8421   (started 2026-07-02 09:14:03)
       Running as:     DESKTOP-7QK2\\ada
       Kind:           desktop application with an open window
       Window title:   "quarterly-report.docx - Word"
""";
  private static final String exampleDefender= """
Antimalware Service Executable
       Executable:     (could not read - the process is protected by Windows)
       Process ID:     3120   (started 2026-07-01 22:03:51)
       Running as:     (could not read - access was denied)
       Kind:           Windows service   (service name: WinDefend)
""";
}

// ---- Shared text pieces and example-data helpers -----------------------------------
// Same pattern as Holders: real implementations query the OS / walk the disk; for now
// they return EXAMPLE data so the final message shape can be reviewed, and the calling
// texts are already wired for the (path, suppressed) discovery style.

class Terms{
  // User-facing glossary (review round 2): the fixed meanings of the storage words,
  // written for the AUDIENCE described at the top - no OS, shell, or file lore assumed.
  // Appended to every text whose wording depends on these words. Refinement for later:
  // filter to only the terms the specific message actually uses.
  static final String glossary= """

Terms used above:
  device:          a physical object that stores data: a disk inside the computer, a
                   USB stick, a memory card, a DVD. If you could, in principle, hold it
                   in your hand, it is a device.
  storage medium:  the material inside a device where the bytes physically live: the
                   magnetic surface of a disk, the memory chips of a USB stick.
  volume:          one organized space of files that this computer shows, such as "C:"
                   or "Macintosh HD". A volume usually lives on one device, but one
                   device can carry several volumes, and a volume can also live on
                   another computer and be used through the network.
  server:          another computer that offers files through the network.
  mounted:         connected into this computer's visible folders. A network volume is
                   "mounted" when it appears here as a normal folder even though its
                   files live on a server.
  driver:          the piece of the operating system that knows how to talk to one
                   specific kind of device.
  file system:     the set of rules a volume uses to organize its files; different
                   kinds of volumes follow different rules.
""";
}

class ReportText{
  static final String please= """

Please report this to the Fearless developers at
https:TODO_TODO,
including the full text of this message.
The details gathered above were collected at the moment of failure and are
what allows the fault to be routed to Fearless, to the JVM, or to the operating system.
""";
}

class NetInfo{
  static final String serverLine=
    "This path is hosted on the server: [[fileserver01]](read from the path itself, or from the mount table for a mounted network volume).\n\n";
}
class FolderInfo{
  // Real implementation, similarNames: if the parent folder exists, list its entries
  // ranked by case difference, addition/removal of space, _ and -; then against
  // edit distance to the requested name; if the parent does not exist either,
  // walking up to the first existing ancestor and rank ITS entries against the missing
  // component would be stupid. Consider instead extra/missing nested directories. Can we add/remove one or two dir access and
  // get to a file with the right name?. Cap the output: over ~20 entries, show the closest matches plus
  // "...and N more entries". Enumeration failure -> suppressed, like Holders.
  static String similarNames(Path path, Suppressed suppressed){ return exampleSimilar; }
  // Real implementation, listing: list the folder itself, same cap and same failure rule.
  static String listing(Path path, Suppressed suppressed){ return exampleListing; }
  private static final String exampleSimilar= """

The folder exists. It holds 4 entries; these have the most similar names:

  quarterly-report.docx        (differs only by '-' where '_' was written)
  quarterly_reports.docx
  quarterlyReport-old.docx

...and 1 more entry.
""";
  private static final String exampleListing= """

The folder holds 3 entries:

  notes.txt
  todo.md
  drafts    (a folder)
""";
}

class PermissionsInfo{
  // Real implementation. POSIX: Files.getPosixFilePermissions plus owner/group,
  // compared with the current user. Windows: read the ACL (AclFileAttributeView) and
  // evaluate it for the current user. Query failure -> suppressed.
  // The evaluated and reported access is the operation's own: Read access for
  // Op.Read, Write access for Op.Write (the example below shows the read shape).
  // Two outcomes, and the closing sentence must name which one holds:
  // - permissions deny the operation: they are the explanation (exampleDenied below);
  // - permissions allow the operation: something else blocked it, typically security
  //   software, and the closing sentence must say that instead.
  static String section(Op op, Path path, Suppressed suppressed){ return exampleDenied; }
  private static final String exampleDenied= """

File permissions: [[Read/Write/Execute]](easy on linux, how to do on win?, use full names not single letters)

  Owner:          "DESKTOP-7QK2", "bob", [[..]](more info if user by user permissions)
  You are:        "DESKTOP-7QK2", "ada", [[..]](more info if user by user permissions)
  Read access:    denied for "ada"   (allowed for: "bob", Administrators)

""";
}

class NetworkProbe{
  // Real implementation: take the server from the path or the mount table, then check
  // in order: does the name resolve (DNS/NetBIOS)? does the machine answer
  // (ping / TCP 445)? does it accept the share name? Report the FIRST failing step,
  // so the message states one concrete finding instead of a list of possibilities.
  static String section(Path path, Suppressed suppressed){ return example; }
  private static final String example= """

Fearless checked the server just now:

  Server name:    fileserver01
  Name lookup:    ok   (resolves to 192.168.1.40)
  Reachable:      NO   (no answer within 2 seconds)

Explanation of what 'name lookup' is.
""";
}

class OpenFilesInfo{
  // Real implementation. POSIX: getrlimit(RLIMIT_NOFILE) for the limit, /proc/self/fd
  // for the list (readable by the process itself). Windows: GetProcessHandleCount plus
  // the C-runtime stream limit. Group by folder if many files inside a folder.
  static String section(Path path, Suppressed suppressed){ return example; }
  private static final String example= """

This Fearless process may hold at most 1022 files open, and all 1022 are in use.
The open files, grouped by folder:

  1019  files under  /home/ada/project/build/cache/
     3  files under  /home/ada/project/src/

//NO:     2  other        (standard input/output)
//From the user perspective standard input/output are not files, need to be subtracted from total allowed etc etc.
""";
}

class InterferenceInfo{
  //Apparently, on most OS/FS naming WHICH software interfered on THIS operation is not possible.
  //So we fall back to name WHO WAS IN A POSITION to interfere (readable right now, while the context is alive).
  // Real implementation. Windows: installed security products (WMI SecurityCenter2),
  // file-system filter drivers attached to this volume (the FilterManager APIs).
  // Linux: active LSMs, fanotify listeners on the mount. macOS: endpoint-security clients.
  static String section(Path path, Suppressed suppressed){ return example; }
  private static final String example= """

Software in a position to interfere on this program's open files:

  Windows Defender             (security product, real-time protection on)
  WdFilter                     (component watching all file operations on this volume)
  CorpShield Agent             (security product, third party)

""";
}
// ---- end of shared pieces and example-data helpers ----------------------------------

//[any OS]
class UnknownFailureAfterSuccessfulOpen{
  static String of(Op op, Path path, Suppressed suppressed){
    var holders= Holders.section(path, suppressed,
      "\nThese programs also have the file open.\n\n",
      "\nNo other program currently has this file open.\n",
      "");
    return CommonInfo.of(op, path, suppressed)+msg+holders+Terms.glossary;
  }
  private static final String msg= """
Fearless opened the file, but «reading» its contents failed without a clear reported reason.
Fearless cannot tell whether the problem is with the file or with the device or volume that contains it.
""";
  //Trying reading a sibling file on the same volume to distinguish "this file" from "this volume"
  //chosen not to do: a successful probe proves little (different sectors/regions)
}

// Error 32, ERROR_SHARING_VIOLATION: an open-stage failure. Some existing handle was opened with a share mode that omits read-sharing (or write-sharing, for a write).
//[Windows-only]
class FileBusyWindowsSharingViolation{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+intro+Holders.section(path, suppressed, "", whenNone, outro);
  }
  private static final String intro= """
Another program has this file open in a mode that does not permit other programs to
«read» it while it is open. A program can keep a file open and still allow others to «read» it;
one of the programs below has chosen not to. Windows reported a sharing violation (error 32).

""";//Above, is it correct always program or should be always process or a mix? and why?
//should the difference program/process be added to the terminology message?

  private static final String whenNone= """
The open failed because some program had this file open in a non-sharing mode,
yet this check (some moments after), found no program holding the file open at all.
""";
  private static final String outro= "";//correctly empty
//Was: The file can be read again once the program(s) reserving it release it.
//but this is against the design, it reads like a help to solve not a description of the current problem.
}

// Error 33, ERROR_LOCK_VIOLATION: a failure at the read/write stage. The open succeeded (share modes were compatible), then the operation overlapped a byte-range lock.
// Deliberately does NOT probe for the locked range.
//[Windows-only]
class FileBusyWindowsFileRegionLocked{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+intro+Holders.section(path, suppressed, "", whenNone, outro);
  }
  private static final String intro= """
Another program has locked a portion of this file, and the range Fearless tried to
«read» overlaps that lock. Windows reported a lock violation (error 33).

""";
//should the terminology section discuss those two kinds of locks? (file and range)
//also, program or process?

  private static final String whenNone= """
The «read» failed because it overlapped a lock,
yet this check (some moments after), found no program holding the file open at all.
""";
//Removed since verbose and pointless:
//"Each observation is true only at its own moment; Fearless cannot see what happened in between."

  private static final String outro= """
One of the programs above placed the lock; Windows does not report which, so every
program holding the file open is listed.
""";
//again, removed since verbose and adds nothing
//"This list is a snapshot: it describes only the moment the check was made."
}

// Error 167, ERROR_LOCK_FAILED: Fearless (or the layer below it) tried to place a lock and Windows could not.
//Likely DEAD CODE. Those errors comes from Files.readAllBytes(path)/Files.write(path,..) and it seems windows will never trigger this from either.
//Needs more checking, and could come back for other IO reporting.
//[Windows-only]
class FileLockWindowsFileRegionLockFailed{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+intro+Holders.section(path, suppressed, "", whenNone, "");
  }
  private static final String intro= """
To «read» this file, Fearless needed to lock a portion of it, and Windows refused to place the lock (error 167, ERROR_LOCK_FAILED).
Windows refuses a lock when it conflicts with a lock another program already holds.

""";//CHANGED: was "Reading this file required locking...": reworded so the operation
//word is not sentence-initial (fill substitutes lowercase words only).
  private static final String whenNone= """
The lock failed because of a conflicting lock,
yet this check (some moments after), found no program holding the file open at all.
""";
}//should the terminology explain what conflicting locks really means on the various OS+Fs?

class FileBusyText{
  //[POSIX-only]
  //ETXTBSY is raised for a WRITE-open of a running program; a plain read does not
  //trigger it, so in practice this text serves Op.Write. Wording already neutral.
  static final String posixTextFileBusy= """
This file is a program that is currently running, and the operating system does not
allow it to be accessed while it runs ("text file busy", ETXTBSY).
The file can be accessed again once the running program exits.
""";
  //[Windows-only]
  static final String windowsFileCheckedOut= """
This file is managed by a document-management system: software that coordinates one
shared document among several people ( examples: SharePoint/OneDrive work this way).
Such a system can record that one person has taken the file for editing - "checked it out" -
and until that person hands it back - "checks it in" - Windows will not open the file
for anyone else (error 220, ERROR_FILE_CHECKED_OUT).
""";//Currently windows do not offer a reliable way to identify the name of the document management system doing this lock
//Overall, this looks like a kind of lock, I wonder if we can (without introducing abstractions and lose details)
//cluster together the messages related to locks like constructs and make some more uniform terminology, followed by the concrete details.
}
class StorageBusyText{
  //[POSIX-only]
  static final String deviceOrResourceBusy= """
The device or volume holding this file is busy with another operation and refused
this one ("device or resource busy", EBUSY). EBUSY describes a momentary state, not a
property of the file: the refusal lasts only as long as the other operation does.
The other operation is run by [[the program "backup-daemon", process 2214]](found by
listing who is using the device or volume - the same walk Holders does, pointed at
the mount point instead of the file).
"""+Terms.glossary;

  //[Windows-only]
  static final String driveLocked= """
The volume named above is locked by a program that needs exclusive access to the
whole volume - commonly a disk check, a format, or a backup or recovery tool
(error 108, ERROR_DRIVE_LOCKED).
"""+Terms.glossary;
//Removed since redundant "The volume becomes readable again when that operation finishes or is cancelled."
//Question: what happens if a symlink points to a locked volume in our current set up? what error would we get?
}
class StorageFullText{
  //[any OS]
  static final String noSpaceOnVolume= """
The volume named above has no room left for this file's data
("no space left on device" / error 112, ERROR_DISK_FULL / error 39, ERROR_HANDLE_DISK_FULL).
This is a state of the whole volume, not a property of this file: right now, every
program storing new data on this volume is refused the same way.
Space on this volume: [[0 bytes free, of 512,105,932,800 in total]](asked to the
system: FileStore.getUsableSpace and getTotalSpace on the volume named above).
"""+Terms.glossary;
//"free" here is free FOR THIS USER: getUsableSpace already subtracts space this user
//may not consume (reserved blocks, quota holds). Worth a phrase once real numbers land?
//Also: a "full" volume can still hold a SMALLER write; stating the size of the failed
//write next to the free space would make the refusal checkable. Discoverable? The
//byte count is known to ByteFiles; would need to flow into the explain call.

  //[any OS]
  static final String quotaExceeded= """
This write was refused because this user's allowance of storage on this volume is
used up ("disk quota exceeded").
A quota is a limit an administrator sets on how much one user account may store on a
volume. It is separate from the volume's own free space: the volume can have plenty
of room while this user's allowance has none.
The allowance applies to [[the user account "ada"]](asked to the system: the quota
subject this volume reports, where the system exposes one).
"""+Terms.glossary;
//"user account" is not in the glossary; is it assumable for this audience, or does
//the glossary need an entry?
}
class StorageReadOnlyText{
  //[POSIX-only] (Windows reports the write-protect flavor below instead)
  static final String volumeMountedReadOnly= """
The volume named above is mounted read-only ("read-only file system").
While it is mounted this way, nothing anywhere on this volume can be written, by any program.
It is mounted read-only because [[that was requested when it was mounted/the
operating system switched it to read-only after finding damage on it]](asked to the
system: the mount options recorded for this volume, and the system log).
"""+Terms.glossary;

  //[Windows-only]
  static final String mediaWriteProtected= """
The device named above refuses all writes: its storage medium is write protected (error 19, ERROR_WRITE_PROTECT).
On some devices this is a physical switch (the small slider on the side of an SD
card); on others it is a setting stored on the device itself.
"""+Terms.glossary;
}
class StorageUnavailableText{
  //[POSIX-only]
  static final String staleNetworkFile= NetInfo.serverLine+"""
The file's bytes live on the server named above. To «read» such a file, this computer
first asks the server for it and receives back a reference, then uses that reference
for the actual «reading». 
The server has stopped honoring the reference this computer holds for this file ("stale file handle").
Servers do that when the file behind a reference was deleted, moved, or replaced.
"""+Terms.glossary;

  //[Windows-only]
  static final String deviceNotReady= """
The device behind the volume named above is not ready (error 21, ERROR_NOT_READY).
The device is [[a memory-card reader, and no card is inserted/a disc drive, and no
disc is inserted/a disk that is still powering up]](asked to Windows through the
device-kind queries: GetDriveType, the IOCTL_STORAGE family).
"""+Terms.glossary;

  //[Windows-only]
  static final String deviceDisconnected= """
The device behind the volume named above is no longer connected to this computer
(error 1167, ERROR_DEVICE_NOT_CONNECTED). The device is [[a USB storage device/a disk
in a dock that lost power]](asked to Windows: device kind, plus the disconnection
event in the system log). The volume it carried is unreachable until the device is
connected again.
"""+Terms.glossary;

  //[POSIX-only]
  static final String transportEndpointDisconnected= NetInfo.serverLine+"""
This file is on a mounted network volume, and the connection between this computer
and the server has been lost ("transport endpoint is not connected", ENOTCONN).
The network folder still appears on this computer, but nothing is behind it any more.
//More: The server is named by the serverLine, and the same probe as error 53 can say
//whether the server itself answers

"""+Terms.glossary;
//Removed since pointless and on the fixing mindset instead or error reporting:
//"It leads somewhere again only once the connection to the server is re-established."

  //[Windows-only]
  static final String providerUnavailable= """
No software component on this computer accepted this network path (error 1203, ERROR_NO_NET_OR_BAD_PATH).
Windows hands each network path to a "network provider": a component that knows how to speak to one kind of network.
This network is of kind [[????]](can we discover the kind name of the network we asked for?)
This computer has providers for [[SMB, the ordinary Windows file sharing/WebDAV, folders
offered by web servers]](discovered by listing the installed providers: the registry
NetworkProvider order, or WNetOpenEnum), and none of them claimed this path.
"""+Terms.glossary;

  //[Windows-only]
  static final String networkResourceGone= NetInfo.serverLine+"""
The shared folder holding this path is no longer offered by the server named above(error 55, ERROR_DEV_NOT_EXIST).
It was reachable before; since then, [[the server still answers but refuses this share name - it stopped sharing the
folder/the server no longer answers at all - it went offline]](checked by the same
probe as error 53: name lookup, reachability, share).
"""+Terms.glossary;
//The server is now named above, but what about the 'shared folder', can we name it?

  //[Windows-only]
  static final String networkBusy= NetInfo.serverLine+"""
The connection to the server named above is too busy to handle the request right now (error 54, ERROR_NETWORK_BUSY).
This is a momentary state of the connection, not a property of the path.
"""+Terms.glossary;
}
// Error 53 gets its own class: it is the one network failure where active probing
// turns "could be a, b or c" into one concrete finding, so it carries a probe section.
//[Windows-only]
class NetworkPathUnavailable{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+msg+NetworkProbe.section(path, suppressed)+Terms.glossary;
  }
  private static final String msg= """
The computer or shared folder named in this path did not answer (error 53, ERROR_BAD_NETPATH).
""";
//a computer OR a shared folder? can we discover? also neither computer OR shared folder are discussed in the terminology.
//is computer here talking about a conventional physical machine or is another FS slang?
//how is this different from the other "The connection to the server named above is..."
//the change in terminology suggests this connection does not go/attempts to go via a 'server'.
}

class NetworkFailureText{
  //[Windows-only]
  static final String badResponse= NetInfo.serverLine+"""
The server named above answered, but its answer was malformed (error 58, ERROR_BAD_NET_RESP).
The fault is in the server or on the way between it and this computer; it is not a problem with the path itself.
The two are separable up to a point:
[[an immediately repeated trivial request also came back malformed, pointing at the
server/only this one answer was malformed, which can be either]](checked by retrying
one cheap request once, reported as a hint, not a verdict).
"""+Terms.glossary;

  //[Windows-only]
  static final String unexpected= NetInfo.serverLine+"""
Communication with this server failed in a way Windows did not classify (error 59, ERROR_UNEXP_NET_ERR).
"""+Terms.glossary;
}
//[any OS]
class TooManyOpenFiles{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+msg+OpenFilesInfo.section(path, suppressed);
  }
  private static final String msg= """
This process - this one running copy of Fearless - has reached the limit of files it
may hold open at the same time ("too many open files"). This is a limit on the process, not a problem with this file.
Max files that can be open: [[34?]](POSIX: getrlimit(RLIMIT_NOFILE) and ///proc/self/fd, readable by the process itself; Windows: handle/stream counts)
""";
}

class ResourceExhaustedText{
  //[POSIX-only]
  //"Closing other programs frees memory" (advice) removed; the text now only says whose memory ran out.
  static final String memory= """
The operating system could not allocate the memory needed to perform this «read» ("cannot allocate memory", ENOMEM).
This is memory of the operating system itself, not Fearless's own working memory:
Running out of the Fearless process memory while loading a large file surfaces as a different failure and is handled elsewhere.
""";
//Files.readAllBytes allocates it on the Java heap, and heap exhaustion surfaces as OutOfMemoryError,
// not as an IOException, so it never reaches this table. ENOMEM can still arrive here as an
//IOException when the KERNEL fails to allocate on behalf of the read: native/direct buffers, page tables, network-file-system structures.
//Rare, but real. The same holds for a write, whose bytes already sit on the heap.

  //[Windows-only]
  //CHANGED(2): "closing other programs, or simply waiting, usually resolves it"
  //(advice+prediction) replaced by what the error asserts: a momentary whole-computer
  //state.
  static final String systemResources= """
Windows ran out of internal resources while handling the request (error 1450, ERROR_NO_SYSTEM_RESOURCES).
This describes a momentary state of the whole computer, not a property of this file.
""";
//Windows does not tell WHICH internal pool ran out (paged/non-paged kernel
//memory, handles, ...) and Windows offers no per-error breakdown.
}

//[JVM]  Read-only kind: writes hand Fearless the bytes, they never build the array.
class FileTooLargeForByteArray{
  static String of(Op op, Path path, Suppressed suppressed){
    String size;
    try{ size= String.format("%,d bytes", Files.size(path)); }
    catch(IOException e){ size= "<reading the size failed>"+suppressed.mark(e); }//documented: Files.size throws when the file cannot be read
    return CommonInfo.of(op, path, suppressed)
      +"This file is "+size+" long. Fearless reads a file into one block of memory, and\n"
      +"one block can hold at most 2,147,483,647 bytes (about 2 GB).\n";
  }
}

//[any OS]
class PathResolutionFileNotFound{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)
      +(op == Op.Read ? msgRead : msgWrite)
      +FolderInfo.similarNames(path, suppressed);
  }
  //The MEANING forks on the operation, so this branches instead of using «..»:
  //- read: the file itself is missing;
  //- write (with CREATE): the file is ALLOWED to be missing - creating it is the
  //  point - so this failure means a FOLDER on the way to it is missing.
  private static final String msgRead= """
No file exists at this location.
If a chain of links is shown above, the missing piece is the final location the chain points to.
[[FolderInfo.similarNames]](reuse here, but what about a chain with symlinks?)
//similar names on all the elments of the chain? or what?
""";
  private static final String msgWrite= """
The file itself was allowed to be missing - this write would have created it - but a
folder on the way to it does not exist, so there is nowhere to create the file.
If a chain of links is shown above, the missing folder is on the final location the chain points to.
[[FolderInfo.similarNames]](against the first missing component, ranked from the
deepest EXISTING ancestor - the walk the FolderInfo comment already describes)
""";
}

//[any OS]
class PathResolutionParentIsNotAFolder{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+msg;
  }
  private static final String msg= """
Path 
  yy
is not a folder but is a parent of the requested path
  yyxxx
the rest of the path cannot exist under it.

[[backup.zip]](last file name with extension) looks like a zip archive:
files inside an archive cannot be reached by path; they are read through the archive-reading API instead.
//Todo: code example here.
//For Op.Write the archive sentence needs its writing twin (or a neutral "reached
//through the archive API"): review together with the code example above.
[[The zip file backup.zip cointains/does not contan xxx inside/is not a valid zip]](using the zip api) 
""";
}

//[Windows-only]
class PathResolutionInvalidName{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+msg+example;
  }
  private static final String msg= """
The path contains a name that is not valid on this system (error 123, ERROR_INVALID_NAME).
""";
  private static final String example= """

The first invalid character is marked below:

  C:\\data\\report<final>.txt
                ^

'<' cannot appear in a file or folder name on Windows.
""";
}

//[any OS]
class PathResolutionPathTooLong{
  // Typical limits, stated in the texts below; a real implementation should ask the
  // system instead: Windows: 259 unless long-path support is enabled, then about
  // 32,000; POSIX: pathconf PATH_MAX (commonly 4096 bytes) and NAME_MAX (commonly 255
  // bytes) for a single name.
  static String of(Op op, Path path, Suppressed suppressed){
    var length= path.toString().length();//Java characters; POSIX limits are in bytes, see the comment above for the real per-OS measure
    return CommonInfo.of(op, path, suppressed)
      +"The path is "+length+" characters long, more than this system allows (\"file name too long\").\n"
      +(Fs.isWindows() ? windowsLimits : posixLimits);
  }
  private static final String windowsLimits= """
On Windows the limit is 259 characters, unless long-path support is enabled, in which case it is about 32,000.
System setting -> registry value LongPathsEnabled.
On this computer the system setting is [[enabled/not enabled]](read from the registry value).
""";
  
  private static final String posixLimits= """
On this system, a whole path may be at most [[4096]](asked to the system: pathconf
of this path, PATH_MAX) bytes, and a single file or folder name at most
[[255]](asked the same way, NAME_MAX) bytes.
""";
}

class PathResolutionText{
  //[POSIX-only]
  static final String symlinkLoop= """
As shown by the chain above, 
[[The path goes through too many links,The path loops back to itself]](inspect the chain before, or set a variable that we can read here) 
("too many levels of symbolic links").
""";
}
//[any OS]  In practice only reached by writeNew (CREATE_NEW); classified typed, from
//FileAlreadyExistsException, before the text matcher runs.
class FileAlreadyExists{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+msg;
  }
  private static final String msg= """
This «read» was asked to create a new file, with the guarantee of never touching
anything already at this location. Something is already there:
[[a file of 12,041 bytes, last changed 2026-07-02 09:14:03/a folder]](one metadata
query, the isFolder one plus size and times), so the operation was refused, and what
is there was not touched.
""";
}
//[any OS]
class AccessDenied{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+msg+PermissionsInfo.section(op, path, suppressed);
  }
  private static final String msg= """
The operating system did not allow Fearless to «read» this file (access denied).
""";
}

class AccessText{
  //[any OS]
  static final String accessDeniedNetwork= NetInfo.serverLine+"""
The server named above refused to let Fearless «read» at this path.
Given this refusal we cannot even confirm whether this path points to an actual file.
"""+Terms.glossary;
}
//[any OS]
class PathIsFolder{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+msg+FolderInfo.listing(path, suppressed);
  }
  private static final String msg= """
This path is a folder, not a file, so it cannot be «written» as a file.
""";//CHANGED: was "so there are no file contents to read", which had no sensible
//write twin; passive position, so it takes the participle token (see Op.fill).
}//TODO: should look if there is a file with the same name and some extension.

class MediaIoText{

//[any OS]
  static final String generic= """
The device named above reported a failure while «reading» (I/O error).
This failure does not report whether this file's stored bytes are damaged or the device itself is failing.
- stored bytes damaged: the medium is damaged at the spots holding this file's data.
- device failing: the hardware is degrading; errors appear on other files of the same volume too, and grow over time.
"""+Terms.glossary;
//CHANGED: "the medium is unreadable at the spots" -> "damaged at the spots": for a
//write the medium is failing to ACCEPT bytes, not to give them back; "damaged" holds
//for both directions.

  //[Windows-only]
  static final String cyclicRedundancyCheck= """
The device named above returned data for this file, but the data failed its integrity check
(error 23, cyclic redundancy check).
The bytes stored on the device are damaged at the spots holding this file's data,
but the device's own reading machinery worked.
"""+Terms.glossary;
//CHANGED: dropped the trailing "does not report whether ... stored bytes damaged /
//device failing" block: it was pasted from `generic` and contradicted the sentence
//just above it (a CRC failure DOES localize the damage to the stored bytes).
//Op.Write review question: a CRC during a write points at verify-after-write or at
//an internal read the write needed; does the "returned data" sentence still hold?

  //[Windows-only]
  static final String seekFailure= """
The device could not move to the position on its storage medium where this file's
data is kept (error 25, ERROR_SEEK). This is a fault in the device, not in the file.
"""+Terms.glossary;

  //[Windows-only]
  static final String sectorNotFound= """
The device could not find the place on its storage medium where this file's data
should be (error 27, ERROR_SECTOR_NOT_FOUND). That part of the medium is unreadable.
"""+Terms.glossary;

  //[Windows-only]
  //The CONTENT forks on the operation - a write fault during a write is the plain
  //event; during a read it needs the "surprising but not impossible" paragraph - so
  //this branches instead of using «..».
  static String writeFault(Op op){ return op == Op.Write ? writeFaultOnWrite : writeFaultOnRead; }
  private static final String writeFaultOnRead= """
The device named above reported a write failure (error 29, ERROR_WRITE_FAULT).
A write failure during a read is surprising but not impossible:
some devices and file systems perform small internal writes even while a file is only being read,
for example to record the time of last access.
Either way it points to a problem with the device, not with the file.
"""+Terms.glossary;
  private static final String writeFaultOnWrite= """
The device named above could not store this file's data (error 29, ERROR_WRITE_FAULT).
This points to a problem with the device, not with the file.
"""+Terms.glossary;

//[Windows-only]
  static final String readFault= """
The device could not deliver this file's data (error 30, ERROR_READ_FAULT).
This is a local failure: the device is attached to this computer, and either the
device failed internally, or the data was lost in transit along
[[the USB connection between the device and this computer/the memory-card reader
the card sits in/the internal connection to the disk]](from the device's bus-type
query, the same one that feeds the future "Device:" line).
"""+Terms.glossary;
//The mirror of writeFault: a read fault during a WRITE means the operation needed an
//internal read. Symmetric op-branching to consider once one is observed in practice.

  //[Windows-only]
  static final String deviceNotFunctioning= """
The device named above is not working correctly (error 31, ERROR_GEN_FAILURE).
This failure does not report further detail.
"""+Terms.glossary;
}
//[any OS]
class InvalidHandleOrDescriptor{
  static String of(Op op, Path path, Suppressed suppressed){
    return CommonInfo.of(op, path, suppressed)+msg+InterferenceInfo.section(path, suppressed)+ReportText.please;
  }
  private static final String msg= """
The operating system rejected Fearless's reference to the open file.
This should not happen in normal operation: it points to a bug in the JVM
[[,or to another program interfering with this program's open files]](windows only).
""";
}

class InvalidOperationText{
//[POSIX-only]
  static final String invalidArgument= """
The operating system rejected the «read» request as nonsensical for this file ("invalid argument").
This file is [[not an ordinary file: it is a device endpoint, which can not be «written» by the Fearless API
/on a volume of an unusual kind, which sets its own rules on how its files may be accessed
/an ordinary file on an ordinary volume, so the malformed request shows a JVM bug]]
(checked by asking the file's kind and the volume's kind: one metadata query plus the Device-line queries).
"""+Terms.glossary+ReportText.please;

  //Same failure as "invalid argument" above, reported by Windows instead of POSIX:
  //the OS judged a value in the request nonsensical for the target. One kind per
  //error vocabulary, not two different events.
  //[Windows-only]
  static final String invalidParameter= """
The operating system rejected the «read» request as nonsensical for this file
(error 87, ERROR_INVALID_PARAMETER).
This file is [[not an ordinary file: it is a device endpoint, which can not be «written» by the Fearless API
/on a volume of an unusual kind, which sets its own rules on how its files may be accessed
/an ordinary file on an ordinary volume, so the malformed request shows a JVM bug]]
(checked by asking the file's kind and the volume's kind: one metadata query plus the Device-line queries).
"""+Terms.glossary+ReportText.please;

  //[Windows-only]
  static final String incorrectFunction= """
A component involved in «reading» this file refused an operation as one it does not
perform (error 1, ERROR_INVALID_FUNCTION).
This failure does not report the failure level:
it could be the device, its driver, or the file-system.

[[The device behind the volume named above is a special-purpose device (a virtual
volume provided by another program), and such devices set their own rules on how
they may be accessed./The device behind the volume named above is an ordinary disk with
an ordinary file system, which supports everything «reading» needs - so the refusal
itself is the anomaly, and it points to a bug in the driver, in Windows, or in the
JVM.]](checked by asking the device kind and volume type: the Device-line queries.)
"""+Terms.glossary;
}

class UnsupportedText{
//[any OS]
  static final String unsupportedFileSystem= """
A component involved in «reading» this file refused an operation as one it does not perform.
Ordinary disk and network volumes support everything needed.
This file is [[not an ordinary file: it is a device endpoint, which can not be «written»
by the Fearless API
/on a volume of an unusual kind - one that presents device controls or live system information as if they were files - which sets its own rules on how its files may be accessed
/an ordinary file on an ordinary volume; thus the refusal itself is the anomaly: a bug in the driver, in the operating system, or in the JVM]]
(checked by asking the file's kind and the volume's kind: one metadata query plus the Device-line queries).
"""+Terms.glossary+ReportText.please;

  //[Windows-only]
  //Not a fifth sibling of the error-1/EINVAL/87/ENOTSUP family: unlike those, this
  //error DOES name the level - the boundary between device and driver - so no branch
  //check applies, and Fearless/JVM are exonerated structurally (they never speak to
  //the device; the driver composes every device command).
  static final String deviceDoesNotRecognizeCommand= """
While «reading» this file, the conversation between the device and its driver broke down:
the driver reported that the device rejected a command as one it does not know (error 22, ERROR_BAD_COMMAND).
A rejection at that boundary means one of the two sides is wrong 
- Device malfunction or Device built-in software bug,
- Device driver bug, or the wrong driver for this device
The device is [[the USB storage device "SanDisk Ultra"]] and its driver is
 [[disk.sys, version 10.0.26100]](both identities from the Device-line queries).
"""+Terms.glossary;
}

class InterruptionText{
  //[JVM]
  //CHANGED(2): "the file must be reopened before trying again" was instruction;
  //replaced by the fact it encoded (stopping this way also closes the file).
  static final String channelClosedByInterrupt= """
Fearless stopped this «read» mid way on purpose; 
for example because of a shutdown, a timeout, or a cancelled task.
Stopping a «read» this way also closes the file.
""";
//Op.Write review question: a write stopped mid way may leave the file with only part
//of the new content on disk. True and important; is stating it "information" (yes)
//and can we DISCOVER whether bytes had already been written when the stop landed?

//[JVM]
  //Sibling of channelClosedByInterrupt: both are the JVM's own bookkeeping ("the file was closed under an operation in progress"), arriving by different doors - that one via
  //the cancellation mechanism, this one via a direct close.
  static final String channelClosedExternally= """
While this «read» was in progress, the file was closed from inside this program,
not by the operating system and not through a cancellation.
This must be a JVM bug, or a bug in some native code loaded by Fearless.
"""+ReportText.please;

//[JVM]
  //Not a sibling of the text above, despite the similar name. That one is the JVM's
  //own cancellation (deliberate, closes the file). This one is the OS pausing the
  //operation to deliver a signal - and neither the JVM nor pure Fearless code installs
  //signal handlers that can do that; the JVM converts its own interruption into the
  //kind above. Reaching here requires native code inside this process having installed
  //its own signal handling - the population InterferenceInfo enumerates on Linux.
  static final String ioInterrupted= """
The operating system paused this «read» to deliver a signal - a notification sent to a
running process - and the «read» was not resumed ("interrupted system call"). Unlike a
cancellation by Fearless, this does not close the file.
Neither the JVM nor pure Fearless code installs signal handling that stops operations
this way: this should only be possible if some native code loaded by Fearless has installed its own.
"""+ReportText.please;
//CHANGED: merged the dangling "Neither Fearless nor the JVM" fragment of the previous
//draft into the closing sentence it was clearly meant to start.
}
class UnknownText{
  //[any OS]
  static final String unknownOpenFailure= """
Opening the file failed, and the operating system's error is not one Fearless recognizes yet.
The raw operating-system error is attached to this message.
"""+ReportText.please;
//The original throwable needs to be visible everywhere so that we can attach it here and in a few other places.
//"Opening" covers the create step of a write: on every OS the create IS an open.
}
