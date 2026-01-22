package coordinatorMessages;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import tools.SourceOracle.Ref;

@SuppressWarnings("serial")
public final class UserTreeError extends RuntimeException{
  private UserTreeError(String msg){ super(msg); }
  ///UserTreeError = problems in the user-provided project folder.
 
  public static UserTreeError reservedBeforePkg(Ref file){
    return new UserTreeError("""
    TODO
    """);}
  
  public static UserTreeError noPackageSegment(Ref file){
    return new UserTreeError("""
      Cannot assign a source file to a package.

      File:
        "%s"

      Rule:
        Each ".fear" file must be under exactly one folder whose name starts with "_".
        That folder defines the package name (without the leading "_").

      Valid examples (folders may appear before and after the package folder):

        projectRoot/
        +-- src/
        |   +-- _bla/
        |       +-- baz/
        |           +-- foo.fear
        +-- test/
            +-- _bla/
                +-- bar.fear

      Here both "foo.fear" and "bar.fear" are in package "bla".

      Fix:
        Move the file under a "_pkgName" folder (for example "_bla").
      """.formatted(file));
  }
  public static UserTreeError emptyProject(Path root){ 
  return new UserTreeError("""
      This folder contains no *.fear files
      Folder:
      "%s"
      """.formatted(root));
  }
  public static UserTreeError ambiguousPackageSegment(Ref file, List<String> candidates){
    return new UserTreeError("""
      Cannot assign a source file to a unique package.

      File:
        "%s"

      The path to this file contains more than one folder whose name starts with "_":
        %s

      This is ambiguous because exactly one "_pkg" folder must define the package.

      Invalid example:

        projectRoot/
        +-- src/
            +-- _bla/
                +-- _beer/
                    +-- bar.fear

      Is "bar.fear" in package "bla" or in package "beer"? The structure is ambiguous.

      Valid alternatives:

        projectRoot/
        +-- src/
            +-- _bla/
                +-- beer/
                    +-- bar.fear

        projectRoot/
        +-- src/
            +-- bla/
                +-- _beer/
                    +-- bar.fear

      Fix:
        Ensure the path to each ".fear" file contains exactly one "_pkgName" folder.
      """.formatted(file, candidates));
  }

  public static UserTreeError missingRankFile(String pkg, Path pkgRoot){
    return new UserTreeError("""
      Missing rank file for a package.

      Package:
        "%s"

      Package folder:
        "%s"

      Rule:
        Each package folder must contain exactly one rank file.

      Rank file name pattern:
        "_rank_<rankName><NNN>.fear"

      Where:
        <rankName> is one of:
          base, core, driver, worker, framework, accumulator, tool, app
        <NNN> are digits

      Examples:
        _rank_core043.fear
        _rank_worker999.fear

      Example of a valid package folder:

        projectRoot/
        +-- src/
            +-- _%s/
                +-- _rank_core043.fear
                +-- foo.fear
                +-- sub/
                    +-- bar.fear

      Fix:
        Create exactly one rank file inside the package folder.
      """.formatted(pkg, pkgRoot, pkg));
  }

  //TODO: bad error, just printing the list?
  public static UserTreeError multipleRankFiles(String pkg, List<Ref> rankFiles){
    return new UserTreeError("""
      Multiple rank files for the same package.

      Package:
        "%s"

      Rank files:
        %s

      Rule:
        Each package must contain exactly one rank file named like:
          "_rank_<rankName><NNN>.fear"

      Fix:
        Keep exactly one rank file and delete or rename the others.
      """.formatted(pkg, rankFiles));
  }

  public static UserTreeError malformedRankFileName(Ref rankFile){
    return new UserTreeError("""
      Malformed rank file name.

      File:
        "%s"

      Expected name pattern:
        "_rank_<rankName><NNN>.fear"

      Where:
        <rankName> is one of:
          base, core, driver, worker, framework, accumulator, tool, app
        <NNN> are exactly three digits

      Examples:
        _rank_core043.fear
        _rank_app999.fear

      Fix:
        Rename the file to match the pattern.
      """.formatted(rankFile));
  }

  public static UserTreeError _unused_mapMentionsUnknownPackage(URI rankFile, String unknownPkg, String exampleStmt){
    return new UserTreeError("""
      Virtualization map mentions an unknown package.

      Rank file:
        "%s"

      Unknown package name:
        "%s"

      The virtualization map syntax is:

        map <virtual> as <real> in <scopePkg>;

      Example:

        map goo as boo in foo_bar;
        map gor as goo in foo;

      Meaning:
        Inside package "foo_bar", any use of package name "goo" is rewritten to "boo".
        Inside package "foo", any use of "gor" is rewritten to "goo".
        Note that map renaming goes from virtual name to real name, so no 'multi step renaming' is possible.
      Problem:
        The map statement refers to a package name that does not exist as a "_pkg" folder.

      Example statement:
        %s

      Fix:
        Create the missing package folder (for example "_%s"), or change the map.
      """.formatted(rankFile, unknownPkg, exampleStmt.isEmpty() ? "(no example available)" : exampleStmt, unknownPkg));
  }

  public static UserTreeError _unused_dependencyOnHigherRank(String fromPkg, int fromRank, String toPkg, int toRank){
    return new UserTreeError("""
      Invalid dependency across ranks.

      Package "%s" (rank %d) depends on "%s" (rank %d).

      Rule:
        A package may only depend on packages of lower or equal rank.

      Fix:
        - change the dependency, or
        - change ranks (by changing the rank file), or
        - change the virtualization map so the dependency rewrites to a lower/equal-rank package.
      """.formatted(fromPkg, fromRank, toPkg, toRank));
  }
}