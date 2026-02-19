package coordinatorMessages;

import java.nio.file.Path;
import java.util.List;

import metaParser.Message;
import tools.SourceOracle.Ref;
import utils.Join;

@SuppressWarnings("serial")
public final class UserTreeError extends RuntimeException{
  private UserTreeError(String msg){ super(msg); }
  ///UserTreeError = problems in the user provided project folder.
 
  public static UserTreeError reservedBeforePkg(Ref file,String reserved){
    return new UserTreeError("""
No folder or file can be named %s
before a package name is specified.
    """.formatted(Message.displayString(reserved)));}
  
  public static UserTreeError noPackageSegment(Ref file){ return new UserTreeError("""
This file must be placed inside a package.
File:
  %s

Each ".fear" file must be under exactly one folder whose name starts with "_".
That folder name, without the leading "_", defines the package name.

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
""".formatted(Message.displayString(file.fearPath())));
  }
  public static UserTreeError emptyProject(Path root){ return new UserTreeError("""
The fearless project folder contains no *.fear files
Folder:
 "%s"
""".formatted(root));
  }
  public static UserTreeError ambiguousPackageSegment(Ref file, List<String> candidates){ return new UserTreeError("""
This path contains more than one folder whose name starts with "_":
  %s

Exactly one "_pkg" folder must define the package.

Example showing this ambiguity:

  projectRoot/
  +-- src/
      +-- _bla/
          +-- _beer/
              +-- bar.fear

Is "bar.fear" in package "bla" or in package "beer"?

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
      """.formatted(file, candidates));
  }
public static UserTreeError missingRankFile(String pkg, Path pkgRoot){ return new UserTreeError("""
Missing rank file for a package.

Package:
  %s

Each package folder must contain exactly one file whose name follows this pattern:
  "_rank_<rankName>.fear"
or
  "_rank_<rankName><NNN>.fear"

<rankName> is one of:
  base, core, driver, worker, framework, accumulator, tool, app
<NNN> are digits

Examples:
  _rank_app.fear
  _rank_core043.fear
  _rank_worker999.fear

Example of a valid package folder:
  projectRoot/
  +-- src/
      +-- _%s/
          +-- _rank_app.fear
          +-- foo.fear
          +-- sub/
              +-- bar.fear
""".formatted(Message.displayString(pkg), pkg));
  }

  public static UserTreeError multipleRankFiles(String pkg, List<Ref> rankFiles){ return new UserTreeError("""
Multiple rank files for the same package.
Package:
  %s

Rank files:
  %s

Each package must contain exactly one rank file.
""".formatted(Message.displayString(pkg), Join.of(rankFiles.stream().map(f->Message.displayString(f.toString())), "",", ","")));
  }

  public static UserTreeError malformedRankFileName(Ref rankFile){ return new UserTreeError("""
Malformed rank file name.
File:
  %s

Each package folder must contain exactly one file whose name follows this pattern:
  "_rank_<rankName>.fear"
or
  "_rank_<rankName><NNN>.fear"

<rankName> is one of:
  base, core, driver, worker, framework, accumulator, tool, app
<NNN> are digits

Examples:
  _rank_app.fear
  _rank_core043.fear
  _rank_worker999.fear

""".formatted(Message.displayString(rankFile.toString())));
  }
}
//TODO: test cases where virtualization map mentions not existing stuff
//TODO: test cases where dependency order is violated. Both cases are likely giving bad unreadable errors.