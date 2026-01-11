package realSourceOracle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import utils.Join;

final class Policy{
  static String showRel(Path rel){
    var s= rel.toString();
    return s.indexOf('\\') < 0 ? s : s.replace('\\','/');
  }
  static UncheckedIOException fail(Path rel, String wentWrong, String howToFix){
    Objects.requireNonNull(rel);
    Objects.requireNonNull(wentWrong);
    Objects.requireNonNull(howToFix);

    String msg=
        "Invalid path in this project folder.\n"
      + "\n"
      + "Path: `"+showRel(rel)+"`\n"
      + "\n"
      + "What went wrong\n"
      + wentWrong+"\n"
      + "\n"
      + "How to fix\n"
      + howToFix+"\n"
      + "\n"
      + rulesWallS();

    return new UncheckedIOException(msg, new IOException(msg));
  }

  static final Set<String> allowedNoExtFilesS= """
readme
license
copying
notice
authors
contributors
changelog
changes
news
todo
thanks
security
contributing
code_of_conduct
""".lines().collect(Collectors.toCollection(LinkedHashSet::new));

  static final Set<String> allowedMultiDotExtsS= """
tar.gz
tar.bz2
tar.xz
tar.zst
tar.lz
tar.lz4
d.ts
d.ts.map
js.map
css.map
min.js
min.css
""".lines().collect(Collectors.toCollection(LinkedHashSet::new));

  static String tickList(Set<String> xs){ return Join.of(xs.stream().map(x->"`"+x+"`"),"",", ",""); }

  static String rulesWallS(){ return """
We check this so that you do not get surprises later.
Fearless enforces simple, portable names so the same data/repository works on Windows, Linux, and Mac,
and in common tools such as Git/GitHub and file browsers.

While scanning the project, we reject any path that could cause trouble later, including:

- Folder names and file base names must:
  - use only lowercase letters (a-z), digits (0-9), and underscore (_)
  - start with a letter or underscore
  - never contain a double underscore (__)
  - not use Windows reserved device names: `con`, `prn`, `aux`, `nul`, `com1`..`com9`, `lpt1`..`lpt9`
- Files must:
  - have a single extension "name.ext" with exactly one dot
  - use an extension made of lowercase letters and digits, length 1..16 characters
  - stay within a reasonable total path length of 200 characters

Exceptions (explicit allowlists)
- A small set of well-known files are allowed to have no extension: %s
- A small set of well-known multi-part extensions are allowed: %s

Protected paths
Many tools treat names starting with '.' (for example `.gitignore`) as private tool data.
Fearless respects this convention and ignores those files and folders.
But we still require them to be safe to check out using a relaxed set of checks:
  - no control characters
  - no Windows-forbidden characters like < > : " / \\ | ? *
  - no name ending with '.' or a space
  - not using Windows reserved device names
  - no two items in the same folder that differ only by case or by Unicode-equivalent spellings

Other rules (everywhere)
- Symbolic links are not allowed.
- Only normal files and folders are allowed.
""".formatted(tickList(allowedNoExtFilesS), tickList(allowedMultiDotExtsS));
  }
}
