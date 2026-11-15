package docBuilder;

import static docBuilder.DocFromSourceTest.compiled;
import static docBuilder.DocFromSourceTest.docs;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import core.OtherPackages;
import userMessages.UserError;

final class DocGroupsTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  private static final String a= "A:{ .m: A -> this; }\n";
  static String error(Path tmp, String source){ return assertThrows(UserError.class, () -> docs(tmp, source)).getMessage(); }
  @Test void withNoGroupLinesEveryPublicNameIsListedRightUnderThePackageAndAPrivateTypeIsLeftOut(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
_Hidden:{ .h: A -> A; }
""");
    utils.Err.strCmp("""
package p
  A

A
\t.m:A
""", docs.text());
    assertFalse(docs.html().contains("_Hidden"));
  }
  @Test void aPrivateTypeIsShownWhenAMethodOfAShownTypeHasItInItsSignature(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
B:{ .b: _C -> _C; }
_C:{ .c(d: _D): A -> A; }
_D:{ .d: A -> A; }
_E:{ .e: _F -> _F; }
_F:{ .f: A -> A; }
""");
    utils.Err.strCmp("""
package p
  A
  B
  _C
  _D

A
\t.m:A

B
\t.b:_C

_C
\t.c(d:_D):A

_D
\t.d:A
""", docs.text());
    utils.Err.strCmp("""
[###]<span class="sig">.b:<a href="#type-p_2e__5f_C-0">_C</a></span>[###]""", docs.html());
  }
  @Test void aShownPrivateTypeTakesGroupLinesAndHasTypesUnderIt(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
B:{ .b: _C -> _C; }
//@Group `B`
_C:{ .c: A -> A; }
//@Group `_C`
D:{ .d: A -> A; }
""");
    utils.Err.strCmp("""
package p
  A
  B
    _C
      D

[###]""", docs.text());
  }
  @Test void aGroupListsItsNamesAndANameUnderANameFollowsThatName(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "Collections"
///Many things.
Lst:{ .size: A -> A; }
//@Group `Lst`
Node:{ .next: A -> A; }
""");
    utils.Err.strCmp("""
package p
  "Collections"
    Lst: Many things.
      Node
  A

A
\t.m:A

Lst
  Many things.
\t.size:A

Node
\t.next:A
""", docs.text());
  }
  @Test void aNameUnderTwoNamesIsListedUnderBoth(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Foo:{ .f: A -> A; }
Bar:{ .b: A -> A; }
//@Group `Foo`
//@Group `Bar`
Shared:{ .s: A -> A; }
""");
    utils.Err.strCmp("""
package p
  A
  Bar
    Shared
  Foo
    Shared

A
\t.m:A

Bar
\t.b:A

Foo
\t.f:A

Shared
\t.s:A
""", docs.text());
  }
  @Test void aNameInAGroupAndUnderANameIsListedInTheGroupAndUnderTheName(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "Gui"
//@Group `A`
Button:{ .b: A -> A; }
""");
    utils.Err.strCmp("""
package p
  "Gui"
    Button
  A
    Button

A
\t.m:A

Button
\t.b:A
""", docs.text());
  }
  @Test void aNameInTwoGroupsIsListedInBoth(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "Gui"
//@Group "Tools"
Button:{ .b: A -> A; }
""");
    utils.Err.strCmp("package p\n  \"Gui\"\n    Button\n  \"Tools\"\n    Button\n  A\n[###]", docs.text());
  }
  @Test void theTypesOfOneNameAreOneEntryOfTheGroupOfAnyOfThem(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Box:{ .b: A -> A; }
//@Group "Tools"
Box[X:imm]:{ .c: A -> A; }
""");
    utils.Err.strCmp("""
package p
  "Tools"
    Box
  A

A
\t.m:A

Box
\t.b:A

Box[X:imm]
\t.c:A
""", docs.text());
  }
  @Test void aTypeNameWithOrWithoutItsTypeParametersPutsATypeUnderThatName(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Box:{ .b: A -> A; }
Box[X:imm]:{ .b: A -> A; }
//@Group `Box`
B:{ .b: A -> A; }
//@Group `Box[_]`
C:{ .c: A -> A; }
""");
    utils.Err.strCmp("package p\n  A\n  Box\n    B\n    C\n[###]", docs.text());
  }
  @Test void aTypeCanNotBeUnderATypeWithItsOwnName(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]003| //@Group `Box`
   | ^^^^^^^^^^^^^^
[###]These //@Group lines put "Box" under itself:
  "Box" is under "Box"
A type cannot be under itself, not even through other types.
The documentation shows together all the types with the same name, whatever the number of their generic type parameters.""", error(tmp, a+"""
Box:{ .b: A -> A; }
//@Group `Box`
Box[X:imm]:{ .b: A -> A; }
"""));
  }
  @Test void theTreeHasAFolderForEachGroupAndForEachNameWithNamesUnderIt(@TempDir Path tmp){
    var html= docs(tmp, a+"""
//@Group "Tools"
B:{ .b: A -> A; }
//@Group `B`
C:{ .c: A -> A; }
""").html();
    utils.Err.strCmp("""
[###]<ul id="tree">
<li class="folder"><a href="#group-Tools">Tools</a><ul>
<li class="folder"><a href="#type-p_2e_B-0">B</a><ul>
<li><a href="#type-p_2e_C-0">C</a></li>
</ul></li>
</ul></li>
<li><a href="#type-p_2e_A-0">A</a></li>
</ul>
<ul id="found" hidden>
<li><a href="#type-p_2e_A-0">A</a></li>
<li><a href="#type-p_2e_B-0">B</a></li>
<li><a href="#type-p_2e_C-0">C</a></li>
</ul>[###]""", html);
  }
  @Test void theTextBoxHasAListOfTheMethodsEachUnderTheTypeWhoseViewShowsTheMethod(@TempDir Path tmp){
    var html= docs(tmp, a+"""
B:{ .b: A -> A; .b(x: A): A -> A; }
C:B{ .b -> A; .c: A -> A; }
""").html();
    utils.Err.strCmp("""
[###]<ul id="methods" hidden>
<li><a href="#method-p_2e_B-0-_2e_b-0">B<b>.b()</b></a></li>
<li><a href="#method-p_2e_C-0-_2e_b-0">C<b>.b()</b></a></li>
<li><a href="#method-p_2e_B-0-_2e_b-1">B<b>.b(_)</b></a></li>
<li><a href="#method-p_2e_C-0-_2e_c-0">C<b>.c()</b></a></li>
<li><a href="#method-p_2e_A-0-_2e_m-0">A<b>.m()</b></a></li>
</ul>
</nav>[###]""", html);
  }
  @Test void theViewOfANameSaysWhereTheNameIsAndListsTheNamesUnderIt(@TempDir Path tmp){
    var html= docs(tmp, a+"""
//@Group "Tools"
B:{ .b: A -> A; }
//@Group `B`
///Doc of C.
C:{ .c: A -> A; }
""").html();
    utils.Err.strCmp("""
[###]<section class="view" id="type-p_2e_B-0">
<p class="where">In: <a href="#group-Tools">Tools</a></p>
<h2>B</h2>
<h3>Methods</h3>
[###]<h3>Under B</h3>
<p class="entry"><a href="#type-p_2e_C-0">C</a><span class="brief">Doc of C.</span></p>
</section>
<section class="view" id="type-p_2e_C-0">
<p class="where">In: <a href="#type-p_2e_B-0">B</a></p>
<h2>C</h2>
[###]""", html);
  }
  @Test void theFirstViewListsTheGroupsAndTheViewOfAGroupListsItsNamesWithTheFirstParagraphOfTheirDocumentation(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "Tools"
///First
///line.
///
///Second paragraph.
B:{ .b: A -> A; }
""");
    utils.Err.strCmp("""
[###]<section class="view" id="top">
<p class="entry"><a href="#group-Tools">Tools</a><span class="brief">B</span></p>
<p class="entry"><a href="#type-p_2e_A-0">A</a></p>
</section>
<section class="view" id="group-Tools">
<h2 class="plain">Tools</h2>
<p class="entry"><a href="#type-p_2e_B-0">B</a><span class="brief">First line.</span></p>
</section>
[###]""", docs.html());
    utils.Err.strCmp("package p\n  \"Tools\"\n    B: First line.\n  A\n[###]", docs.text());
  }
  @Test void aGroupLineIsNotShownAsDocumentation(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "Tools"
///First.
///Second.
B:{ .b: A -> A; }
""");
    utils.Err.strCmp("[###]\nB\n  First.\n  Second.\n\t.b:A", docs.text());
    assertFalse(docs.html().contains("@Group"));
  }
  @Test void anEmptyDocLineRightAfterTheGroupLinesIsTrimmed(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "Tools"
///
///Doc.
B:{ .b: A -> A; }
""");
    utils.Err.strCmp("[###]\nB\n  Doc.\n\t.b:A", docs.text());
  }
  @Test void groupsAreSortedIgnoringCase(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "UI"
B:{ .b: A -> A; }
//@Group "algebra"
C:{ .c: A -> A; }
""");
    utils.Err.strCmp("package p\n  \"algebra\"\n    C\n  \"UI\"\n    B\n  A\n[###]", docs.text());
  }
  @Test void noGroupNameIsSpecial(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "private"
B:{ .b: A -> A; }
//@Group "Public"
C:{ .c: A -> A; }
""");
    utils.Err.strCmp("package p\n  \"private\"\n    B\n  \"Public\"\n    C\n  A\n[###]", docs.text());
  }
  @Test void aGroupLineAfterTheDeclarationOnTheSameLineDocumentsThatDeclaration(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
B:{ .b: A -> A; } //@Group "Tools"
""");
    utils.Err.strCmp("package p\n  \"Tools\"\n    B\n  A\n[###]", docs.text());
  }
  @Test void aGroupLineOnAPrivateTypeThatTheDocumentationDoesNotShowIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
Broken group in a doc comment.

In file: fear:/_p/_rank_app.fear

002| //@Group "Tools"
   | ^^^^^^^^^^^^^^^^
[###]This //@Group line documents the private type "_B".
The documentation shows the public types: the named types whose name does not start with _.
The documentation shows a private type only when a method of a type that the
documentation shows has that private type in its signature.
A //@Group line says where the documentation shows a type, so delete this line.""", error(tmp, a+"""
//@Group "Tools"
_B:{ .b: A -> A; }
"""));
  }
  @Test void aGroupLineOnAnAnonymousLiteralIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]004|     //@Group `Maker`
   |     ^^^^^^^^^^^^^^^^
[###]This //@Group line documents an anonymous literal.
The documentation shows the public types: the named types whose name does not start with _.
The documentation shows a private type only when a method of a type that the
documentation shows has that private type in its signature.
A //@Group line says where the documentation shows a type, so delete this line.""", error(tmp, a+"""
Maker:{
  .make: A ->
    //@Group `Maker`
    A{ .m -> A };
  }
"""));
  }
  @Test void aTypeCanNotBeUnderAPrivateTypeThatTheDocumentationDoesNotShow(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]003| //@Group `_P`
   | ^^^^^^^^^^^^^
[###]The documentation does not show the private type "_P".
The documentation shows a private type only when a method of a type that the
documentation shows has that private type in its signature.
A //@Group line can only put a type under a type that the documentation shows.""", error(tmp, a+"""
_P:{ .p: A -> A; }
//@Group `_P`
B:{ .b: A -> A; }
"""));
  }
  @Test void typesUnderEachOtherAreACycle(@TempDir Path tmp){
    utils.Err.strCmp("""
Broken group in a doc comment.

In file: fear:/_p/_rank_app.fear

002| //@Group `C`
   | ^^^^^^^^^^^^
[###]These //@Group lines put "B" under itself:
  "B" is under "C"
  "C" is under "B"
A type cannot be under itself, not even through other types.[###]""", error(tmp, a+"""
//@Group `C`
B:{ .b: A -> A; }
//@Group `B`
C:{ .c: A -> A; }
"""));
  }
  @Test void aTypeUnderItselfIsACycle(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]These //@Group lines put "B" under itself:
  "B" is under "B"
[###]""", error(tmp, a+"""
//@Group `B`
B:{ .b: A -> A; }
"""));
  }
  @Test void aTypeCanNotBeUnderATypeOfAnotherPackage(@TempDir Path tmp){
    var q= compiled("q", "Thing:{}\n", OtherPackages.empty());
    var ex= assertThrows(UserError.class, () -> docs(tmp, OtherPackages.start(Map.of(), q, 0L), """
//@Group `q.Thing`
User:{ .m: User -> this; }
"""));
    utils.Err.strCmp("""
[###]"q.Thing" is a type of package q.
A //@Group line can only put a type under a type of the same package, p.""", ex.getMessage());
  }
  @Test void aGroupLineNamingNoTypeIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]002| //@Group `Nope`
   | ^^^^^^^^^^^^^^^
[###]There is no type "Nope".""", error(tmp, a+"""
//@Group `Nope`
B:{ .b: A -> A; }
"""));
  }
  @Test void aGroupLineNamingAMethodIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]"A.m" names a method, not a type.
In a //@Group `Type` line, the reference must name a type of this package.""", error(tmp, a+"""
//@Group `A.m`
B:{ .b: A -> A; }
"""));
  }
  @Test void aGroupLineWithALowercaseNameIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]"x" does not name a type.
In a //@Group `Type` line, the reference must name a type of this package.""", error(tmp, a+"""
//@Group `x`
B:{ .b: A -> A; }
"""));
  }
  @Test void aGroupLineAboveAnEmptyLineIsAttachedToNothing(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]002| //@Group "Tools"
   | ^^^^^^^^^^^^^^^^[###]
This documentation is attached to nothing.
A ///, //>, //- or //@ block documents the declaration right below it, and an
empty line ends the block. Delete the empty line below this block, or write ///
on it to keep an empty line in the documentation.""", error(tmp, a+"""
//@Group "Tools"

B:{ .b: A -> A; }
"""));
  }
  @Test void aMalformedGroupLineIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]002| //@Grop "Tools"
   | ^^^^^^^^^^^^^^^
[###]This is not a group directive.
A //@ line is either //@Group "Name", to put the type it documents in the
group Name, or //@Group `Type`, to put the type it documents under the type Type.
A group name is not empty, and does not start or end with a space.
Nothing follows the closing " or `, not even a space.""", error(tmp, a+"""
//@Grop "Tools"
B:{ .b: A -> A; }
"""));
  }
  @Test void aGroupLineOnAMethodIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]003|   //@Group "Tools"
   |   ^^^^^^^^^^^^^^^^
[###]This //@Group line documents a method.
A //@Group line puts a type in a group or under another type, and a method is
neither. Move this line above the declaration of the type.""", error(tmp, a+"""
B:{
  //@Group "Tools"
  .b: A -> A;
  }
"""));
  }
  @Test void aGroupLineWithNothingAfterTheMarkIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]002| //@
   | ^^^
[###]This is not a group directive.[###]""", error(tmp, a+"""
//@
B:{ .b: A -> A; }
"""));
  }
  @Test void aBlankGroupNameIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("[###]This is not a group directive.[###]A group name is not empty, and does not start or end with a space.[###]", error(tmp, a+"""
//@Group "  "
B:{ .b: A -> A; }
"""));
  }
  @Test void aGroupNameStartingWithASpaceIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("[###]This is not a group directive.[###]", error(tmp, a+"""
//@Group " Tools"
B:{ .b: A -> A; }
"""));
  }
  @Test void twoGroupsDifferingOnlyByCaseAreAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]004| //@Group "Algebra"
   | ^^^^^^^^^^^^^^^^^^
[###]The group "Algebra" differs from the group "algebra" only by case.
Write the name of a group the same way every time.""", error(tmp, a+"""
//@Group "algebra"
C:{ .c: A -> A; }
//@Group "Algebra"
D:{ .d: A -> A; }
"""));
  }
  @Test void aGroupLineAfterProseIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]003| //@Group "Tools"
   | ^^^^^^^^^^^^^^^^
[###]This //@Group line comes after other documentation of the same declaration.
Move every //@Group line above the first ///, //> and //- line of the documentation.""", error(tmp, a+"""
///Doc.
//@Group "Tools"
B:{ .b: A -> A; }
"""));
  }
  @Test void aGroupLineBetweenExamplesIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("[###]004| //@Group \"Tools\"\n[###]This //@Group line comes after other documentation of the same declaration.[###]", error(tmp, a+"""
///Doc.
//> A.m
//@Group "Tools"
//> A.m
B:{ .b: A -> A; }
"""));
  }
  @Test void aGroupLineInsideACodeBlockIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("[###]This //@Group line comes after other documentation of the same declaration.[###]", error(tmp, a+"""
///```
//@Group "Tools"
///```
B:{ .b: A -> A; }
"""));
  }
  @Test void aRepeatedGroupLineIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]003| //@Group "Tools"
   | ^^^^^^^^^^^^^^^^
[###]This //@Group line repeats an earlier //@Group line of the same type.
Delete this line.""", error(tmp, a+"""
//@Group "Tools"
//@Group "Tools"
B:{ .b: A -> A; }
"""));
  }
  @Test void aRepeatedParentIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("[###]003| //@Group `A`\n[###]This //@Group line repeats an earlier //@Group line of the same type.[###]", error(tmp, a+"""
//@Group `A`
//@Group `A`
B:{ .b: A -> A; }
"""));
  }
  @Test void aSpaceAfterTheMarkIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("[###]This is not a group directive.[###]", error(tmp, a+"""
//@ Group "Tools"
B:{ .b: A -> A; }
"""));
  }
  @Test void aSpaceAfterTheGroupNameIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("[###]This is not a group directive.[###]Nothing follows the closing \" or `, not even a space.", error(tmp, a+"""
//@Group "Tools"\s
B:{ .b: A -> A; }
"""));
  }
  @Test void aTypeCanNotBeUnderATypeParameter(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]"X" is a generic type parameter.
In a //@Group `Type` line, the reference must name a type of this package.""", error(tmp, a+"""
//@Group `X`
B[X:imm]:{ .b: A -> A; }
"""));
  }
  @Test void aGroupLineAfterTwoDeclarationsOnOneLineIsMarkedFromItsSlashes(@TempDir Path tmp){
    utils.Err.strCmp("""
Misplaced doc comment.

In file: fear:/_p/_rank_app.fear

002| B:{ .b: A -> A; } C:{ .c: A -> A; } //@Group "X"
   |                                     ^^^^^^^^^^^^
[###]""", error(tmp, a+"""
B:{ .b: A -> A; } C:{ .c: A -> A; } //@Group "X"
"""));
  }
}
