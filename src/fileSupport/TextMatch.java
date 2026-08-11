package fileSupport;
import static fileSupport.ByteFiles.Kind.*;

import fileSupport.ByteFiles.Kind;

enum TextMatch {
    MFileTooLargeForByteArray(FileTooLargeForByteArray,
      // OpenJDK jdk-26+26 FileInputStream.java
      // String.format("Required array size too large for %s: %d = %d - %d", ...)
      // https://github.com/openjdk/jdk/blob/jdk-26%2B26/src/java.base/share/classes/java/io/FileInputStream.java#L298-L301
      // And OpenJDK jdk-26+26 InputStream.java
      // throw new OutOfMemoryError("Required array size too large")
      // https://github.com/openjdk/jdk/blob/jdk-26%2B26/src/java.base/share/classes/java/io/InputStream.java#L419
      //keeping the second one only since it would match also for the first case
      "required array size too large"
      ),
    MPathIsFolder(PathIsFolder,
      // OpenJDK jdk-26+26 ZipFileSystem.java:
      // throw new FileSystemException(getString(path), null, "is a directory")
      // https://github.com/openjdk/jdk/blob/jdk-26%2B26/src/jdk.zipfs/share/classes/jdk/nio/zipfs/ZipFileSystem.java#L894
      // Also covers glibc/musl/Darwin EISDIR ("Is a directory"), which a WRITE on a
      // folder raises at open time on POSIX; on Windows a write on a folder raises
      // access denied instead, and the isFolder check in ByteFiles reroutes it here.
      "is a directory"
      ),

    MPathResolutionFailure_FileNotFound(PathResolutionFailure_FileNotFound,
      // glibc sysdeps/gnu/errlist.h line 13:
      // _S (ENOENT, N_("No such file or directory"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#13
      // musl matches glibc verbatim: E(ENOENT, "No such file or directory")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      // Darwin matches too: #define ENOENT 2 /* No such file or directory */
      // https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/errno.h  (pin to your macOS xnu tag)
      "no such file or directory",

      // Microsoft System Error Codes page lines 49-53:
      // ERROR_FILE_NOT_FOUND: "The system cannot find the file specified."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the system cannot find the file specified",

      // Microsoft System Error Codes page lines 55-59:
      // ERROR_PATH_NOT_FOUND: "The system cannot find the path specified."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the system cannot find the path specified"
      ),

    MPathResolutionFailure_ParentIsNotAFolder(PathResolutionFailure_ParentIsNotAFolder,
      // glibc sysdeps/gnu/errlist.h line 134:
      // _S (ENOTDIR, N_("Not a directory"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#134
      // musl matches glibc verbatim: E(ENOTDIR, "Not a directory")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "not a directory"
      ),

    MPathResolutionFailure_SymlinkLoop(PathResolutionFailure_SymlinkLoop,
      // NOTE: on Files.readAllBytes this arrives as a *generic* FileSystemException,
      // not FileSystemLoopException (the JDK only throws that type from the file-tree
      // walker, not from open()), so these needles are the real classifier here.
      //
      // glibc sysdeps/gnu/errlist.h line 428:
      // _S (ELOOP, N_("Too many levels of symbolic links"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#428
      "too many levels of symbolic links",

      // musl src/errno/__strerror.h:
      // E(ELOOP, "Symbolic link loop")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "symbolic link loop"
      ),
    MPathResolutionFailure_InvalidName(PathResolutionFailure_InvalidName,
      // Microsoft System Error Codes page lines 568-572:
      // ERROR_INVALID_NAME: "The filename, directory name, or volume label syntax is incorrect."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the filename, directory name, or volume label syntax is incorrect",

      // Microsoft System Error Codes page lines 788-792:
      // ERROR_BAD_PATHNAME: "The specified path is invalid."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the specified path is invalid",

      // Microsoft System Error Codes page lines 1137-1141:
      // ERROR_DIRECTORY: "The directory name is invalid."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the directory name is invalid",

      // glibc sysdeps/gnu/errlist.h line 502:
      // _S (EILSEQ, N_("Invalid or incomplete multibyte or wide character"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#502
      "invalid or incomplete multibyte or wide character"
      ),

    MPathResolutionFailure_PathTooLong(PathResolutionFailure_PathTooLong,
      // glibc sysdeps/gnu/errlist.h line 435:
      // _S (ENAMETOOLONG, N_("File name too long"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#435
      "file name too long",

      // musl src/errno/__strerror.h (note: "Filename" one word, so it does NOT
      // contain the glibc "file name too long" substring above):
      // E(ENAMETOOLONG, "Filename too long")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "filename too long",

      // Microsoft System Error Codes page lines 510-514:
      // ERROR_BUFFER_OVERFLOW: "The file name is too long."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the file name is too long",

      // Microsoft System Error Codes page lines 963-967:
      // ERROR_FILENAME_EXCED_RANGE: "The filename or extension is too long."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the filename or extension is too long"
      ),

    MFileAlreadyExists(FileAlreadyExists,
      // This usually arrives typed (FileAlreadyExistsException) and is classified
      // before the text matcher runs; these needles are the backstop for untyped arrivals.
      //
      // glibc sysdeps/gnu/errlist.h:
      // _S (EEXIST, N_("File exists"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html  (pin the line, as above)
      // musl matches glibc verbatim: E(EEXIST, "File exists")
      // Darwin matches too: #define EEXIST 17 /* File exists */
      // This needle also covers ERROR_FILE_EXISTS: "The file exists." (contains it).
      "file exists",

      // Microsoft System Error Codes page:
      // ERROR_ALREADY_EXISTS: "Cannot create a file when that file already exists."
      // (does NOT contain the contiguous "file exists", so it needs its own entry)
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "cannot create a file when that file already exists"
      ),

    MAccessDenied_Network(AccessDenied_Network,
      // Microsoft System Error Codes page lines 347-351:
      // ERROR_NETWORK_ACCESS_DENIED: "Network access is denied."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "network access is denied"
      ),

    MAccessDenied(AccessDenied,
      // Microsoft System Error Codes page lines 67-71:
      // ERROR_ACCESS_DENIED: "Access is denied."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "access is denied",

      // glibc sysdeps/gnu/errlist.h line 90:
      // _S (EACCES, N_("Permission denied"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#90
      // musl matches glibc verbatim: E(EACCES, "Permission denied")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "permission denied",

      // glibc sysdeps/gnu/errlist.h line 6:
      // _S (EPERM, N_("Operation not permitted"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#6
      // musl matches glibc verbatim: E(EPERM, "Operation not permitted")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "operation not permitted"
      ),

    MFileBusy_WindowsSharingViolation(FileBusy_WindowsSharingViolation,
      // Microsoft System Error Codes page lines 225-229:
      // ERROR_SHARING_VIOLATION:
      // "The process cannot access the file because it is being used by another process."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the process cannot access the file because it is being used by another process"
      ),

    MFileBusy_WindowsFileRegionLocked(FileBusy_WindowsFileRegionLocked,
      // Microsoft System Error Codes page lines 231-234:
      // ERROR_LOCK_VIOLATION:
      // "The process cannot access the file because another process has locked a portion of the file."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the process cannot access the file because another process has locked a portion of the file"
      ),

    MFileBusy_PosixTextFileBusy(FileBusy_PosixTextFileBusy,
      // glibc sysdeps/gnu/errlist.h line 179:
      // _S (ETXTBSY, N_("Text file busy"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#179
      // musl matches glibc verbatim: E(ETXTBSY, "Text file busy")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "text file busy"
      ),

    MFileBusy_WindowsFileCheckedOut(FileBusy_WindowsFileCheckedOut,
      // Microsoft System Error Codes page lines 1027-1031:
      // ERROR_FILE_CHECKED_OUT: "This file is checked out or locked for editing by another user."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "this file is checked out or locked for editing by another user"
      ),

    MStorageBusy_DeviceOrResourceBusy(StorageBusy_DeviceOrResourceBusy,
      // glibc sysdeps/gnu/errlist.h line 110:
      // _S (EBUSY, N_("Device or resource busy"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#110
      "device or resource busy",

      // musl src/errno/__strerror.h (shorter form; glibc's needle above does not
      // contain it, so it needs its own entry). This same needle is also expected
      // to cover Darwin, whose EBUSY strerror is "Resource busy" -- VERIFY on macOS:
      // #define EBUSY 16 /* Device / Resource busy */
      // https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/errno.h  (pin to your macOS xnu tag)
      // E(EBUSY, "Resource busy")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "resource busy",

      // Microsoft System Error Codes page lines 812-816:
      // ERROR_BUSY: "The requested resource is in use."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the requested resource is in use"
      ),
    MStorageBusy_DriveLocked(StorageBusy_DriveLocked,
      // Microsoft System Error Codes page lines 492-496:
      // ERROR_DRIVE_LOCKED: "The disk is in use or locked by another process."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the disk is in use or locked by another process"
      ),

    MStorageFull_NoSpaceOnVolume(StorageFull_NoSpaceOnVolume,
      // glibc sysdeps/gnu/errlist.h:
      // _S (ENOSPC, N_("No space left on device"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html  (pin the line, as above)
      // musl matches glibc verbatim: E(ENOSPC, "No space left on device")
      // Darwin matches too: #define ENOSPC 28 /* No space left on device */
      "no space left on device",

      // Microsoft System Error Codes page:
      // ERROR_HANDLE_DISK_FULL: "The disk is full."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the disk is full",

      // Microsoft System Error Codes page:
      // ERROR_DISK_FULL: "There is not enough space on the disk."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "there is not enough space on the disk"
      ),

    MStorageFull_QuotaExceeded(StorageFull_QuotaExceeded,
      // glibc: _S (EDQUOT, N_("Disk quota exceeded")); Darwin: "Disc quota exceeded";
      // both contain this needle. -- VERIFY musl at your pinned tag: if its EDQUOT
      // wording is not "Quota exceeded" (e.g. "Quota limit exceeded" would NOT
      // contain this needle), add its own entry.
      "quota exceeded",

      // Microsoft: ERROR_DISK_QUOTA_EXCEEDED (1295) -- VERIFY the exact message text
      // surfaced through Java before trusting this needle (it is on the 1000-1299
      // page, not the 0-499 page cited elsewhere in this file):
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--1000-1299-
      "storage quota was exceeded"
      ),

    MStorageReadOnly_VolumeMountedReadOnly(StorageReadOnly_VolumeMountedReadOnly,
      // glibc sysdeps/gnu/errlist.h:
      // _S (EROFS, N_("Read-only file system"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html  (pin the line, as above)
      // musl matches glibc verbatim: E(EROFS, "Read-only file system")
      // Darwin matches too: #define EROFS 30 /* Read-only file system */
      "read-only file system"
      ),

    MStorageReadOnly_MediaWriteProtected(StorageReadOnly_MediaWriteProtected,
      // Microsoft System Error Codes page:
      // ERROR_WRITE_PROTECT: "The media is write protected."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the media is write protected"
      ),

    MFileLock_WindowsFileRegionLockFailed(FileLock_WindowsFileRegionLockFailed,
      // Microsoft System Error Codes page lines 806-810:
      // ERROR_LOCK_FAILED: "Unable to lock a region of a file."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "unable to lock a region of a file"
      ),

    MStorageUnavailable_StaleNetworkFile(StorageUnavailable_StaleNetworkFile,
      // glibc sysdeps/gnu/errlist.h line 471:
      // _S (ESTALE, N_("Stale file handle"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#471
      "stale file handle",

      // Darwin uses the classic BSD "NFS" wording; the "NFS " in the middle means
      // the glibc needle above is NOT a substring, so this needs its own entry.
      // strerror wording matches the header comment:
      // #define ESTALE 70 /* Stale NFS file handle */
      // https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/errno.h  (pin to your macOS xnu tag)
      "stale nfs file handle"
      ),

    MStorageUnavailable_DeviceNotReady(StorageUnavailable_DeviceNotReady,
      // Microsoft System Error Codes page lines 160-164:
      // ERROR_NOT_READY: "The device is not ready."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the device is not ready"
      ),

    MStorageUnavailable_DeviceDisconnected(StorageUnavailable_DeviceDisconnected,
      // glibc sysdeps/gnu/errlist.h line 43:
      // _S (ENXIO, N_("No such device or address"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#43
      // musl matches glibc verbatim: E(ENXIO, "No such device or address")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "no such device or address",

      // Darwin ENXIO uses different wording; strerror matches the header comment:
      // #define ENXIO 6 /* Device not configured */
      // https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/errno.h  (pin to your macOS xnu tag)
      "device not configured",

      // glibc sysdeps/gnu/errlist.h line 129:
      // _S (ENODEV, N_("No such device"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#129
      // musl matches glibc verbatim: E(ENODEV, "No such device")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      // NOTE: Darwin ENODEV strerror is "Operation not supported by device", which
      // would text-match the "operation not supported" needle under
      // MUnsupportedFileSystem and land there instead. Decide deliberately whether
      // that routing is acceptable before adding a Darwin ENODEV needle here.
      "no such device",

      // Microsoft System Error Codes page lines 155-159:
      // ERROR_BAD_UNIT: "The system cannot find the device specified."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the system cannot find the device specified"
      ),

    MStorageUnavailable_TransportEndpointDisconnected(StorageUnavailable_TransportEndpointDisconnected,
      // glibc sysdeps/gnu/errlist.h line 395:
      // _S (ENOTCONN, N_("Transport endpoint is not connected"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#395
      "transport endpoint is not connected",

      // musl src/errno/__strerror.h -- VERIFY exact wording at your pinned tag:
      // E(ENOTCONN, "Socket not connected")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "socket not connected",

      // Darwin ENOTCONN; strerror matches the header comment (note the "is",
      // so it is distinct from the musl needle above):
      // #define ENOTCONN 57 /* Socket is not connected */
      // https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/errno.h  (pin to your macOS xnu tag)
      "socket is not connected"
      ),

    MStorageUnavailable_NetworkPathUnavailable(StorageUnavailable_NetworkPathUnavailable,
      // Microsoft System Error Codes page lines 266-269:
      // ERROR_REM_NOT_LIST:
      // "Windows cannot find the network path. Verify that the network path is correct..."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "windows cannot find the network path",

      // Microsoft System Error Codes page lines 277-280:
      // ERROR_BAD_NETPATH: "The network path was not found."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the network path was not found",

      // Microsoft System Error Codes page lines 358-362:
      // ERROR_BAD_NET_NAME: "The network name cannot be found."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the network name cannot be found"
      ),

    MStorageUnavailable_NetworkResourceGone(StorageUnavailable_NetworkResourceGone,
      // Microsoft System Error Codes page lines 288-292:
      // ERROR_DEV_NOT_EXIST:
      // "The specified network resource or device is no longer available."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the specified network resource or device is no longer available",

      // Microsoft System Error Codes page lines 341-345:
      // ERROR_NETNAME_DELETED: "The specified network name is no longer available."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the specified network name is no longer available"
      ),

    MStorageUnavailable_NetworkBusy(StorageUnavailable_NetworkBusy,
      // Microsoft System Error Codes page lines 282-286:
      // ERROR_NETWORK_BUSY: "The network is busy."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the network is busy"
      ),

    MNetworkFailure_BadResponse(NetworkFailure_BadResponse,
      // Microsoft System Error Codes page lines 306-310:
      // ERROR_BAD_NET_RESP: "The specified server cannot perform the requested operation."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the specified server cannot perform the requested operation"
      ),

    MNetworkFailure_Unexpected(NetworkFailure_Unexpected,
      // Microsoft System Error Codes page lines 312-316:
      // ERROR_UNEXP_NET_ERR: "An unexpected network error occurred."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "an unexpected network error occurred"
      ),

    MResourceExhausted_TooManyOpenFiles(ResourceExhausted_TooManyOpenFiles,
      // glibc sysdeps/gnu/errlist.h line 164:
      // _S (ENFILE, N_("Too many open files in system"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#164
      //And glibc sysdeps/gnu/errlist.h line 157:
      // _S (EMFILE, N_("Too many open files"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#157
      //keeping the second one only since it would match also for the first case
      // (musl ENFILE "Too many open files in system" also contains this needle)
      "too many open files",

      // musl EMFILE (the per-process limit, the common one) uses different wording
      // that does NOT contain "too many open files", so it needs its own entry:
      // E(EMFILE, "No file descriptors available")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "no file descriptors available",

      // Microsoft System Error Codes page lines 61-65:
      // ERROR_TOO_MANY_OPEN_FILES: "The system cannot open the file."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the system cannot open the file",

      // Microsoft System Error Codes page lines 242-246:
      // ERROR_SHARING_BUFFER_EXCEEDED: "Too many files opened for sharing."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "too many files opened for sharing"
      ),

    MResourceExhausted_Memory(ResourceExhausted_Memory,
      // glibc sysdeps/gnu/errlist.h line 85:
      // _S (ENOMEM, N_("Cannot allocate memory"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#85
      "cannot allocate memory",

      // musl ENOMEM uses different wording that the glibc needle above does not
      // cover, so it needs its own entry:
      // E(ENOMEM, "Out of memory")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "out of memory",

      // Microsoft System Error Codes page lines 84-88:
      // ERROR_NOT_ENOUGH_MEMORY:
      // "Not enough memory resources are available to process this command."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "not enough memory resources are available to process this command",

      // Microsoft System Error Codes page lines 120-123:
      // ERROR_OUTOFMEMORY:
      // "Not enough storage is available to complete this operation."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "not enough storage is available to complete this operation"
      ),

    MResourceExhausted_SystemResources(ResourceExhausted_SystemResources,
      // glibc sysdeps/gnu/errlist.h line 381:
      // _S (ENOBUFS, N_("No buffer space available"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#381
      // musl matches glibc verbatim: E(ENOBUFS, "No buffer space available")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "no buffer space available",

      // Microsoft System Error Codes page lines 707-711:
      // ERROR_IS_JOIN_PATH:
      // "Not enough resources are available to process this command."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "not enough resources are available to process this command"
      ),

    MMediaIoFailure_Generic(MediaIoFailure_Generic,
      // glibc sysdeps/gnu/errlist.h line 34:
      // _S (EIO, N_("Input/output error"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#34
      // (Darwin EIO strerror is also "Input/output error", covered by this needle)
      "input/output error",

      // musl EIO uses the abbreviated form, which the glibc needle above does NOT
      // contain ("input/output error" has no "i/o error" substring):
      // E(EIO, "I/O error")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "i/o error"
      ),

    MMediaIoFailure_CyclicRedundancyCheck(MediaIoFailure_CyclicRedundancyCheck,
      // Microsoft System Error Codes page lines 172-176:
      // ERROR_CRC: "Data error (cyclic redundancy check)."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "data error (cyclic redundancy check)"
      ),

    MMediaIoFailure_SeekFailure(MediaIoFailure_SeekFailure,
      // Microsoft System Error Codes page lines 184-188:
      // ERROR_SEEK: "The drive cannot locate a specific area or track on the disk."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the drive cannot locate a specific area or track on the disk"
      ),

    MMediaIoFailure_SectorNotFound(MediaIoFailure_SectorNotFound,
      // Microsoft System Error Codes page lines 196-199:
      // ERROR_SECTOR_NOT_FOUND: "The drive cannot find the sector requested."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the drive cannot find the sector requested"
      ),

    MMediaIoFailure_WriteFault(MediaIoFailure_WriteFault,
      // Microsoft System Error Codes page lines 207-211:
      // ERROR_WRITE_FAULT: "The system cannot write to the specified device."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the system cannot write to the specified device"
      ),

    MMediaIoFailure_ReadFault(MediaIoFailure_ReadFault,
      // Microsoft System Error Codes page lines 213-217:
      // ERROR_READ_FAULT: "The system cannot read from the specified device."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the system cannot read from the specified device"
      ),

    MMediaIoFailure_DeviceNotFunctioning(MediaIoFailure_DeviceNotFunctioning,
      // Microsoft System Error Codes page lines 219-223:
      // ERROR_GEN_FAILURE: "A device attached to the system is not functioning."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "a device attached to the system is not functioning"
      ),

    MInvalidHandleOrDescriptor(InvalidHandleOrDescriptor,
      // glibc sysdeps/gnu/errlist.h line 64:
      // _S (EBADF, N_("Bad file descriptor"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#64
      // musl matches glibc verbatim: E(EBADF, "Bad file descriptor")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "bad file descriptor",

      // Microsoft System Error Codes page lines 73-77:
      // ERROR_INVALID_HANDLE: "The handle is invalid."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the handle is invalid"
      ),

    MInvalidOperationOrParameter_InvalidArgument(InvalidOperationOrParameter_InvalidArgument,
      // glibc sysdeps/gnu/errlist.h line 146:
      // _S (EINVAL, N_("Invalid argument"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#146
      // musl matches glibc verbatim: E(EINVAL, "Invalid argument")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "invalid argument"
      ),

    MInvalidOperationOrParameter_IncorrectFunction(InvalidOperationOrParameter_IncorrectFunction,
      // Microsoft System Error Codes page lines 43-47:
      // ERROR_INVALID_FUNCTION: "Incorrect function."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "incorrect function"
      ),

    MUnsupportedFileSystem_DeviceDoesNotRecognizeCommand(UnsupportedFileSystem_DeviceDoesNotRecognizeCommand,
      // Microsoft System Error Codes page lines 166-170:
      // ERROR_BAD_COMMAND: "The device does not recognize the command."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the device does not recognize the command"
      ),

    MInvalidOperationOrParameter_InvalidParameter(InvalidOperationOrParameter_InvalidParameter,
      // Microsoft System Error Codes page lines 428-432:
      // ERROR_INVALID_PARAMETER: "The parameter is incorrect."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the parameter is incorrect",

      // Microsoft System Error Codes page lines 782-786:
      // ERROR_BAD_ARGUMENTS: "One or more arguments are not correct."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "one or more arguments are not correct"
      ),

    MUnsupportedFileSystem(UnsupportedFileSystem,
      // glibc sysdeps/gnu/errlist.h line 323:
      // _S (EOPNOTSUPP, N_("Operation not supported"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#323
      // musl matches glibc verbatim: E(EOPNOTSUPP, "Operation not supported")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "operation not supported",

      // glibc sysdeps/gnu/errlist.h line 496:
      // _S (ENOSYS, N_("Function not implemented"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#496
      // musl matches glibc verbatim: E(ENOSYS, "Function not implemented")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "function not implemented",

      // Microsoft System Error Codes page lines 260-264:
      // ERROR_NOT_SUPPORTED: "The request is not supported."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the request is not supported",

      // Microsoft System Error Codes page lines 545-549:
      // ERROR_BAD_DRIVER_LEVEL: "The system does not support the command requested."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "the system does not support the command requested",

      // Microsoft System Error Codes page lines 551-554:
      // ERROR_CALL_NOT_IMPLEMENTED: "This function is not supported on this system."
      // https://learn.microsoft.com/en-us/windows/win32/debug/system-error-codes--0-499-
      "this function is not supported on this system"
      ),

    MIoInterrupted(IoInterrupted,
      // glibc sysdeps/gnu/errlist.h line 29:
      // _S (EINTR, N_("Interrupted system call"))
      // https://codebrowser.dev/glibc/glibc/sysdeps/gnu/errlist.h.html#29
      // musl matches glibc verbatim: E(EINTR, "Interrupted system call")
      // https://git.musl-libc.org/cgit/musl/tree/src/errno/__strerror.h  (pin to your musl release tag, e.g. v1.2.5)
      "interrupted system call"
      );

    private final Kind kind;
    private final String[] needles;
    TextMatch(Kind kind, String... needles){ this.kind= kind; this.needles= needles; }
    boolean has(String text) {
      for (var needle : needles){ if (text.contains(needle)){ return true; } }
      return false;
    }
    static Kind match(String text) {
      for (var value : values()){ if (value.has(text)){ return value.kind; } }
      return UnknownFailureAfterSuccessfulOpen;
    }
  }
