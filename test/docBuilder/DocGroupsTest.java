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
  @Test void withNoGroupLinesPublicTypesAreInGroupPublicAndPrivateTypesInGroupPrivate(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
_Hidden:{ .h: A -> A; }
""");
    utils.Err.strCmp("""
package p

group public
  A
group private
  _Hidden

A
\t.m:A

_Hidden
\t.h:A

""", docs.text());
  }
  @Test void aNamedGroupComesBeforePublicAndPrivateAndAChildFollowsItsParent(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "Collections"
///Many things.
Lst:{ .size: A -> A; }
//@Group `Lst`
_Node:{ .next: A -> A; }
""");
    utils.Err.strCmp("""
package p

group Collections
  Lst
group public
  A

Lst
  see also: _Node
  Many things.
\t.size:A

_Node
  under: Lst
\t.next:A

A
\t.m:A

""", docs.text());
  }
  @Test void aChildOfTwoParentsIsRenderedOnceAndListedByBoth(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Foo:{ .f: A -> A; }
Bar:{ .b: A -> A; }
//@Group `Foo`
//@Group `Bar`
_Shared:{ .s: A -> A; }
""");
    utils.Err.strCmp("""
package p

group public
  A
  Bar
  Foo

A
\t.m:A

Bar
  see also: _Shared
\t.b:A

_Shared
  under: Foo, Bar
\t.s:A

Foo
  see also: _Shared
\t.f:A

""", docs.text());
  }
  @Test void aTypeInAGroupAndUnderATypeIsListedInTheGroupAndUnderTheType(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "Gui"
//@Group `A`
Button:{ .b: A -> A; }
""");
    utils.Err.strCmp("""
package p

group Gui
  Button
group public
  A

Button
  under: A
\t.b:A

A
  see also: Button
\t.m:A

""", docs.text());
  }
  @Test void theTableOfContentsShowsTheGroupsAndHidesTheChildren(@TempDir Path tmp){
    var html= docs(tmp, a+"""
//@Group `A`
_Child:{ .c: A -> A; }
""").html();
    utils.Err.strCmp("""
[###]<ul id="toclist">
<li class="group">public</li>
<li><a href="#type-p_2e_A-0">A</a></li>
</ul>[###]""", html);
    utils.Err.strCmp("""
[###]<h2>A</h2>
<p class="doc missing">No documentation yet.</p>
<p class="extends"><b>See also:</b> <a href="#type-p_2e__5f_Child-0">_Child</a></p>
[###]<h2>_Child</h2>
<p class="doc missing">No documentation yet.</p>
<p class="extends"><b>Under:</b> <a href="#type-p_2e_A-0">A</a></p>
[###]""", html);
  }
  @Test void aGroupLineIsNotShownAsDocumentation(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "Tools"
///First.
///Second.
B:{ .b: A -> A; }
""");
    utils.Err.strCmp("[###]\nB\n  First.\n  Second.\n\t.b:A\n[###]", docs.text());
    assertFalse(docs.html().contains("@Group"));
  }
  @Test void anAnonymousLiteralWithOnlyAGroupLineIsShownUnderItsParent(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Maker:{
  .make: A ->
    //@Group `Maker`
    A{ .m -> A };
  }
""");
    utils.Err.strCmp("[###]Maker\n  see also: Anon@_rank_app:5\n[###]\nAnon@_rank_app:5 : A\n  under: Maker\n[###]", docs.text());
  }
  @Test void aPublicTypeCanNotBeInGroupPrivate(@TempDir Path tmp){
    utils.Err.strCmp("""
Broken group in a doc comment.

In file: fear:/_p/_rank_app.fear

002| //@Group "private"
   | ^^^^^^^^^^^^^^^^^^
[###]"B" is public, so it cannot be in the group "private".
The group "private" holds only private types: the types whose name starts with _.""", error(tmp, a+"""
//@Group "private"
B:{ .b: A -> A; }
"""));
  }
  @Test void aPrivateTypeCanNotBeInGroupPublic(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]"_B" is private, so it cannot be in the group "public".
The group "public" holds only public types: the types whose name does not start with _.""", error(tmp, a+"""
//@Group "public"
_B:{ .b: A -> A; }
"""));
  }
  @Test void aPublicTypeUnderOnlyPrivateTypesIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]003| //@Group `_P`
   | ^^^^^^^^^^^^^
[###]"B" is public, but every type it is under is private: "_P".
A public type must be in a group, or under at least one public type.""", error(tmp, a+"""
_P:{ .p: A -> A; }
//@Group `_P`
B:{ .b: A -> A; }
"""));
  }
  @Test void aPublicTypeUnderAPrivateTypeAndAPublicTypeIsListedUnderBoth(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
_P:{ .p: A -> A; }
//@Group `_P`
//@Group `A`
B:{ .b: A -> A; }
""");
    utils.Err.strCmp("[###]B\n  under: _P, A\n[###]", docs.text());
  }
  @Test void aPublicTypeUnderAPrivateTypeAndInAGroupIsListedInTheGroup(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
_P:{ .p: A -> A; }
//@Group `_P`
//@Group "Tools"
B:{ .b: A -> A; }
""");
    utils.Err.strCmp("[###]group Tools\n  B\n[###]", docs.text());
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
A type cannot be under itself, not even through other types.""", error(tmp, a+"""
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
  @Test void aTypeNameMatchingSeveralTypesNeedsItsTypeParameters(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]Several types match "Box": "Box[]" and "Box[_]".
Write one of these to say which one.""", error(tmp, a+"""
Box:{ .b: A -> A; }
Box[X:imm]:{ .b: A -> A; }
//@Group `Box`
B:{ .b: A -> A; }
"""));
  }
  @Test void aTypeParameterCountSelectsOneOfSeveralTypes(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Box:{ .b: A -> A; }
Box[X:imm]:{ .b: A -> A; }
//@Group `Box[_]`
B:{ .b: A -> A; }
""");
    utils.Err.strCmp("[###]B\n  under: Box[X:imm]\n[###]", docs.text());
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
[###]This does not name a type.
In a //@Group `Type` line, the reference must name a type of this package.""", error(tmp, a+"""
//@Group `A.m`
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
A group name is not empty, and does not start or end with a space.""", error(tmp, a+"""
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
    utils.Err.strCmp("[###]This is not a group directive.[###]A group name is not empty, and does not start or end with a space.", error(tmp, a+"""
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
  @Test void groupsAreSortedIgnoringCase(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "UI"
B:{ .b: A -> A; }
//@Group "algebra"
C:{ .c: A -> A; }
""");
    utils.Err.strCmp("package p\n\ngroup algebra\n  C\ngroup UI\n  B\ngroup public\n  A\n[###]", docs.text());
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
    utils.Err.strCmp("[###]This is not a group directive.[###]", error(tmp, a+"""
//@Group "Tools"\s
B:{ .b: A -> A; }
"""));
  }
  @Test void aGroupLineAfterTheDeclarationOnTheSameLineDocumentsThatDeclaration(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
B:{ .b: A -> A; } //@Group "Tools"
""");
    utils.Err.strCmp("package p\n\ngroup Tools\n  B\ngroup public\n  A\n[###]", docs.text());
  }
  @Test void aTypeCanNotBeUnderATypeParameter(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]"X" is a type parameter.
In a //@Group `Type` line, the reference must name a type of this package.""", error(tmp, a+"""
//@Group `X`
B[X:imm]:{ .b: A -> A; }
"""));
  }
  @Test void aGroupDifferingFromGroupPublicOnlyByCaseIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
[###]002| //@Group "Public"
   | ^^^^^^^^^^^^^^^^^
[###]The group "Public" differs from the group "public" only by case.
Write the name of a group the same way every time.""", error(tmp, a+"""
//@Group "Public"
B:{ .b: A -> A; }
"""));
  }
  @Test void aPublicTypeCanNotBeInGroupPrivateWrittenWithACapital(@TempDir Path tmp){
    utils.Err.strCmp("Broken group in a doc comment.[###]002| //@Group \"Private\"[###]", error(tmp, a+"""
//@Group "Private"
B:{ .b: A -> A; }
"""));
  }
  @Test void anEmptyDocLineRightAfterTheGroupLinesIsTrimmed(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
//@Group "Tools"
///
///Doc.
B:{ .b: A -> A; }
""");
    utils.Err.strCmp("[###]\nB\n  Doc.\n\t.b:A\n[###]", docs.text());
  }
}
